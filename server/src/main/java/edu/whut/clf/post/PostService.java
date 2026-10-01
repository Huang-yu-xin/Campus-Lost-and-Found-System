package edu.whut.clf.post;

import edu.whut.clf.audit.AuditService;
import edu.whut.clf.common.enums.FilePurpose;
import edu.whut.clf.match.CategoryDictionary;
import edu.whut.clf.common.enums.PostStatus;
import edu.whut.clf.common.enums.PostType;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.common.web.PageResult;
import edu.whut.clf.common.web.Pageable;
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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostMapper postMapper;
    private final PostImageMapper imageMapper;
    private final FileService fileService;
    private final UserService userService;
    private final ObjectProvider<PostClaimGuard> claimGuard;
    private final CategoryDictionary categoryDictionary;
    private final AuditService auditService;

    public PostService(PostMapper postMapper, PostImageMapper imageMapper, FileService fileService,
                       UserService userService, ObjectProvider<PostClaimGuard> claimGuard,
                       CategoryDictionary categoryDictionary, AuditService auditService) {
        this.postMapper = postMapper;
        this.imageMapper = imageMapper;
        this.fileService = fileService;
        this.userService = userService;
        this.claimGuard = claimGuard;
        this.categoryDictionary = categoryDictionary;
        this.auditService = auditService;
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
        validateEventTime(req.eventTime());
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
        Pageable pg = Pageable.of(page, pageSize);
        String normalizedType = normalizeType(type);
        List<Post> posts = postMapper.searchPublic(blankToNull(keyword), normalizedType,
                blankToNull(category), blankToNull(campus), eventFrom, eventTo, pg.offset(), pg.size());
        long total = postMapper.countPublic(blankToNull(keyword), normalizedType,
                blankToNull(category), blankToNull(campus), eventFrom, eventTo);
        return PageResult.of(toSummaries(posts), total, pg.page(), pg.size());
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
        Pageable pg = Pageable.of(page, pageSize);
        List<Post> posts = postMapper.findByPublisher(userId, pg.offset(), pg.size());
        long total = postMapper.countByPublisher(userId);
        return PageResult.of(toSummaries(posts), total, pg.page(), pg.size());
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
        if (req.eventTime() != null) { validateEventTime(req.eventTime()); post.setEventTime(req.eventTime()); }
        postMapper.updateEditable(post);
        if (req.imageFileIds() != null && !locked) {
            bindImages(postId, userId, req.imageFileIds());
        }
        return detail(postId, userId);
    }

    @Transactional
    public void withdraw(Long postId, Long userId) {
        requireOwned(postId, userId);
        // D17：锁帖后以 FOR UPDATE 权威读复核状态与有效申请，消除"撤回与新申请"竞态产生的孤儿 PENDING
        Post locked = lockForUpdate(postId);
        if (!PostStatus.ACTIVE.name().equals(locked.getStatus())) {
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
        // D17：锁帖后权威读复核（与 withdraw/resolve-lost 同模式）
        Post locked = lockForUpdate(postId);
        if (!PostStatus.ACTIVE.name().equals(locked.getStatus())) {
            throw BusinessException.of(ErrorCode.CONFLICT);
        }
        if (hasActiveClaim(postId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "存在有效申请，请先处理后再标记找回");
        }
        int n = postMapper.changeStatus(postId, PostStatus.ACTIVE.name(), PostStatus.COMPLETED.name());
        if (n == 0) {
            throw BusinessException.of(ErrorCode.CONFLICT);
        }
        // D12/R9：标记已找回审计
        auditService.record(userId, Principal.ROLE_USER, "LOST_MARK_FOUND", "POST", postId, "SUCCESS", null);
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

    /** 被指定 claim 关联的寻物帖 id（B7/R4），无则 null。 */
    public Long resolvedLostPostIdByClaim(Long claimId) {
        return postMapper.findResolvedPostIdByClaim(claimId);
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
        // A3(P1-B2) 替换语义：先释放并删除本帖旧图，再按去重后的新集重建，杜绝重复行。
        // 事务内执行：若后续校验失败（超限/他人文件）整体回滚，旧图不丢失。
        List<Long> old = imageMapper.findByPost(postId).stream().map(PostImage::getFileId).toList();
        imageMapper.deleteByPost(postId);
        for (Long fid : old) {
            fileService.markUnbound(fid);
        }
        List<Long> unique = fileService.prepareReplaceBinding(imageFileIds, userId, FilePurpose.PUBLIC_POST);
        int order = 0;
        for (Long fileId : unique) {
            PostImage img = new PostImage();
            img.setPostId(postId);
            img.setFileId(fileId);
            img.setSortOrder(order++);
            imageMapper.insert(img);
            fileService.markBound(fileId);
        }
    }

    /**
     * D2(P2-2)：列表摘要图片改批量取（findByPostIds 一次 + 分组 Map），消除逐帖 N+1 查询。
     * 与 MatchService 的候选图片取法同法。
     */
    private List<PostSummary> toSummaries(List<Post> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> ids = posts.stream().map(Post::getId).toList();
        Map<Long, List<Long>> byPost = imageMapper.findByPostIds(ids).stream()
                .collect(Collectors.groupingBy(PostImage::getPostId,
                        Collectors.mapping(PostImage::getFileId, Collectors.toList())));
        return posts.stream()
                .map(post -> new PostSummary(post.getId(), post.getType(), post.getTitle(), post.getCategory(),
                        post.getCampus(), post.getEventLocation(), post.getEventTime(), post.getPublishedAt(),
                        post.getStatus(), byPost.getOrDefault(post.getId(), List.of())))
                .toList();
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

    /** D8(P2-8)：事件时间下界校验（@PastOrPresent 管上界，这里管下界）。 */
    private static final LocalDateTime EVENT_TIME_FLOOR = LocalDateTime.of(2000, 1, 1, 0, 0);

    private void validateEventTime(LocalDateTime eventTime) {
        if (eventTime != null && eventTime.isBefore(EVENT_TIME_FLOOR)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "事件时间不得早于 2000-01-01");
        }
    }
}
