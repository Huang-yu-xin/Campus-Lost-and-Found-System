package edu.whut.clf.post;

import edu.whut.clf.common.enums.FilePurpose;
import edu.whut.clf.match.CategoryDictionary;
import edu.whut.clf.common.enums.PostStatus;
import edu.whut.clf.common.enums.PostType;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.common.web.PageResult;
import edu.whut.clf.file.FileService;
import edu.whut.clf.post.dto.PostDtos.*;
import edu.whut.clf.post.model.Post;
import edu.whut.clf.post.model.PostImage;
import edu.whut.clf.user.UserService;
import edu.whut.clf.user.model.User;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class PostService {

    private final PostMapper postMapper;
    private final PostImageMapper imageMapper;
    private final FileService fileService;
    private final UserService userService;
    private final ObjectProvider<PostClaimGuard> claimGuard;
    private final CategoryDictionary categoryDictionary;

    public PostService(PostMapper postMapper, PostImageMapper imageMapper, FileService fileService,
                       UserService userService, ObjectProvider<PostClaimGuard> claimGuard,
                       CategoryDictionary categoryDictionary) {
        this.postMapper = postMapper;
        this.imageMapper = imageMapper;
        this.fileService = fileService;
        this.userService = userService;
        this.claimGuard = claimGuard;
        this.categoryDictionary = categoryDictionary;
    }

    @Transactional
    public PostDetail create(Long userId, CreatePostRequest req) {
        userService.requireNotRestricted(userId);
        PostType type;
        try {
            type = PostType.valueOf(req.type());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "type 必须为 LOST 或 FOUND");
        }
        Post p = new Post();
        p.setPublisherId(userId);
        p.setType(type.name());
        p.setTitle(req.title().trim());
        p.setCategory(req.category().trim());
        p.setCategoryCode(categoryDictionary.codeFor(req.category()).orElse(null));
        p.setPublicDescription(req.publicDescription().trim());
        p.setCampus(req.campus());
        p.setEventLocation(req.eventLocation());
        p.setEventTime(req.eventTime());
        p.setPublishedAt(LocalDateTime.now());
        p.setStatus(PostStatus.ACTIVE.name());
        postMapper.insert(p);
        bindImages(p.getId(), userId, req.imageFileIds());
        return detail(p.getId(), userId);
    }

    public PageResult<PostSummary> publicList(String keyword, String type, String category, String campus,
                                              LocalDateTime eventFrom, LocalDateTime eventTo, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = pageSize <= 0 || pageSize > 100 ? 20 : pageSize;
        String normalizedType = normalizeType(type);
        int offset = (p - 1) * size;
        List<Post> posts = postMapper.searchPublic(blankToNull(keyword), normalizedType,
                blankToNull(category), blankToNull(campus), eventFrom, eventTo, offset, size);
        long total = postMapper.countPublic(blankToNull(keyword), normalizedType,
                blankToNull(category), blankToNull(campus), eventFrom, eventTo);
        return PageResult.of(posts.stream().map(this::toSummary).toList(), total, p, size);
    }

    public PostDetail detail(Long postId, Long currentUserId) {
        Post post = postMapper.findById(postId);
        if (post == null) {
            throw BusinessException.of(ErrorCode.POST_NOT_FOUND);
        }
        // 非公开状态仅发布者/管理员可看（此处按发布者可见；管理员经后台接口）
        boolean mine = Objects.equals(post.getPublisherId(), currentUserId);
        if (!PostStatus.ACTIVE.name().equals(post.getStatus())
                && !PostStatus.COMPLETED.name().equals(post.getStatus())
                && !PostStatus.HANDOVER.name().equals(post.getStatus())
                && !mine) {
            // WITHDRAWN / REMOVED 对非本人返回 404
            throw BusinessException.of(ErrorCode.POST_NOT_FOUND);
        }
        User publisher = userService.load(post.getPublisherId());
        List<Long> imageIds = imageMapper.findByPost(postId).stream().map(PostImage::getFileId).toList();
        return new PostDetail(post.getId(), post.getType(), post.getTitle(), post.getCategory(),
                post.getPublicDescription(), post.getCampus(), post.getEventLocation(), post.getEventTime(),
                post.getPublishedAt(), post.getStatus(), post.getVersion(), post.getPublisherId(),
                publisher.getNickname(), mine, imageIds);
    }

    public PageResult<PostSummary> myPosts(Long userId, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = pageSize <= 0 || pageSize > 100 ? 20 : pageSize;
        int offset = (p - 1) * size;
        List<Post> posts = postMapper.findByPublisher(userId, offset, size);
        long total = postMapper.countByPublisher(userId);
        return PageResult.of(posts.stream().map(this::toSummary).toList(), total, p, size);
    }

    @Transactional
    public PostDetail update(Long postId, Long userId, UpdatePostRequest req) {
        Post post = requireOwned(postId, userId);
        if (!PostStatus.ACTIVE.name().equals(post.getStatus())) {
            throw BusinessException.of(ErrorCode.POST_NOT_EDITABLE);
        }
        boolean locked = hasActiveClaim(postId);
        if (locked && coreFieldChanged(post, req)) {
            throw BusinessException.of(ErrorCode.POST_EDIT_LOCKED);
        }
        if (req.title() != null) post.setTitle(req.title().trim());
        if (req.category() != null) {
            post.setCategory(req.category().trim());
            post.setCategoryCode(categoryDictionary.codeFor(req.category()).orElse(null));
        }
        if (req.publicDescription() != null) post.setPublicDescription(req.publicDescription().trim());
        if (req.campus() != null) post.setCampus(req.campus());
        if (req.eventLocation() != null) post.setEventLocation(req.eventLocation());
        if (req.eventTime() != null) post.setEventTime(req.eventTime());
        postMapper.updateEditable(post);
        if (req.imageFileIds() != null && !locked) {
            bindImages(postId, userId, req.imageFileIds());
        }
        return detail(postId, userId);
    }

    @Transactional
    public void withdraw(Long postId, Long userId) {
        Post post = requireOwned(postId, userId);
        if (!PostStatus.ACTIVE.name().equals(post.getStatus())) {
            throw BusinessException.of(ErrorCode.POST_NOT_EDITABLE);
        }
        if (hasActiveClaim(postId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "存在有效申请，请先处理后再撤回");
        }
        int n = postMapper.changeStatus(postId, PostStatus.ACTIVE.name(), PostStatus.WITHDRAWN.name());
        if (n == 0) {
            throw BusinessException.of(ErrorCode.CONFLICT);
        }
    }

    @Transactional
    public void markFound(Long postId, Long userId) {
        Post post = requireOwned(postId, userId);
        if (!PostType.LOST.name().equals(post.getType())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅寻物信息可标记已找回");
        }
        int n = postMapper.changeStatus(postId, PostStatus.ACTIVE.name(), PostStatus.COMPLETED.name());
        if (n == 0) {
            throw BusinessException.of(ErrorCode.CONFLICT);
        }
    }

    // ---- 供其它模块在同一事务内调用的领域方法 ----

    /** 事务内锁定并返回发布（供认领接受等并发关键操作）。 */
    public Post lockForUpdate(Long postId) {
        Post p = postMapper.lockById(postId);
        if (p == null) {
            throw BusinessException.of(ErrorCode.POST_NOT_FOUND);
        }
        return p;
    }

    /** 条件状态转换；失败(0 行)抛冲突。 */
    public void requireTransition(Long postId, PostStatus from, PostStatus to) {
        int n = postMapper.changeStatus(postId, from.name(), to.name());
        if (n == 0) {
            throw BusinessException.of(ErrorCode.CONFLICT);
        }
    }

    public int tryTransition(Long postId, PostStatus from, PostStatus to) {
        return postMapper.changeStatus(postId, from.name(), to.name());
    }

    public Post getById(Long postId) {
        Post p = postMapper.findById(postId);
        if (p == null) {
            throw BusinessException.of(ErrorCode.POST_NOT_FOUND);
        }
        return p;
    }

    // ---- V4：认领完成 → 寻物帖闭环链接（供 ClaimService 同一事务内调用）----

    /** 申请人名下可关联的寻物帖候选池（本人 + LOST + ACTIVE）；类别/时间/排序由调用方处理。 */
    public List<Post> activeLostByPublisher(Long publisherId) {
        return postMapper.findActiveLostByPublisher(publisherId);
    }

    /** 事务内把寻物帖关联到 claim 并置 COMPLETED（含 closed_at）；返回受影响行数，0 表示并发冲突。 */
    public int linkResolvedLost(Long lostPostId, Long claimId) {
        return postMapper.resolveLost(lostPostId, claimId);
    }

    // ---- 内部辅助 ----

    private boolean hasActiveClaim(Long postId) {
        PostClaimGuard guard = claimGuard.getIfAvailable();
        return guard != null && guard.hasActiveClaim(postId);
    }

    private boolean coreFieldChanged(Post post, UpdatePostRequest req) {
        return changed(req.title(), post.getTitle())
                || changed(req.category(), post.getCategory())
                || changed(req.publicDescription(), post.getPublicDescription())
                || changed(req.eventLocation(), post.getEventLocation())
                || (req.eventTime() != null && !req.eventTime().equals(post.getEventTime()))
                || (req.imageFileIds() != null && !req.imageFileIds().isEmpty());
    }

    private boolean changed(String incoming, String current) {
        return incoming != null && !incoming.trim().equals(current == null ? "" : current);
    }

    private Post requireOwned(Long postId, Long userId) {
        Post post = postMapper.findById(postId);
        if (post == null) {
            throw BusinessException.of(ErrorCode.POST_NOT_FOUND);
        }
        if (!Objects.equals(post.getPublisherId(), userId)) {
            // 不暴露他人发布的可编辑性
            throw BusinessException.of(ErrorCode.FORBIDDEN);
        }
        return post;
    }

    private void bindImages(Long postId, Long userId, List<Long> imageFileIds) {
        if (imageFileIds == null) {
            return;
        }
        int order = 0;
        for (Long fileId : imageFileIds) {
            fileService.requireOwnedFile(fileId, userId, FilePurpose.PUBLIC_POST);
            PostImage img = new PostImage();
            img.setPostId(postId);
            img.setFileId(fileId);
            img.setSortOrder(order++);
            imageMapper.insert(img);
            fileService.markBound(fileId);
        }
    }

    private PostSummary toSummary(Post post) {
        List<Long> imageIds = imageMapper.findByPost(post.getId()).stream().map(PostImage::getFileId).toList();
        return new PostSummary(post.getId(), post.getType(), post.getTitle(), post.getCategory(),
                post.getCampus(), post.getEventLocation(), post.getEventTime(), post.getPublishedAt(),
                post.getStatus(), imageIds);
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return PostType.valueOf(type).name();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "type 非法");
        }
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
