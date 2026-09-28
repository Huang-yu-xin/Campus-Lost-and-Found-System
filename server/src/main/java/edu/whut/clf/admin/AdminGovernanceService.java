package edu.whut.clf.admin;

import edu.whut.clf.admin.model.ModerationAction;
import edu.whut.clf.audit.AuditService;
import edu.whut.clf.claim.ClaimMapper;
import edu.whut.clf.common.enums.PostStatus;
import edu.whut.clf.common.enums.UserStatus;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.common.web.PageResult;
import edu.whut.clf.post.PostMapper;
import edu.whut.clf.post.model.Post;
import edu.whut.clf.user.UserMapper;
import edu.whut.clf.user.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** M2 后台治理：信息下架/恢复、用户限制/解除。所有动作留治理记录与审计。 */
@Service
public class AdminGovernanceService {

    private final PostMapper postMapper;
    private final ClaimMapper claimMapper;
    private final UserMapper userMapper;
    private final ModerationActionMapper moderationMapper;
    private final AuditService auditService;

    public AdminGovernanceService(PostMapper postMapper, ClaimMapper claimMapper, UserMapper userMapper,
                                  ModerationActionMapper moderationMapper, AuditService auditService) {
        this.postMapper = postMapper;
        this.claimMapper = claimMapper;
        this.userMapper = userMapper;
        this.moderationMapper = moderationMapper;
        this.auditService = auditService;
    }

    // ---- 信息治理 ----

    public PageResult<Post> listPosts(String status, String type, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = pageSize <= 0 || pageSize > 100 ? 20 : pageSize;
        List<Post> items = postMapper.adminSearch(status, type, (p - 1) * size, size);
        // total 简化：无独立 count，返回当前页数量（后台可加专用 count，P4 完善）
        return PageResult.of(items, items.size(), p, size);
    }

    @Transactional
    public void removePost(Long adminId, Long postId, String reason) {
        Post post = postMapper.findById(postId);
        if (post == null) {
            throw BusinessException.of(ErrorCode.POST_NOT_FOUND);
        }
        if (PostStatus.REMOVED.name().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该信息已被下架");
        }
        String before = post.getStatus();
        postMapper.forceStatus(postId, PostStatus.REMOVED.name());
        // 关联有效申请一并关闭，保留历史
        claimMapper.closeActiveByPost(postId, "POST_REMOVED");
        logModeration(adminId, "POST", postId, "REMOVE", reason, before, PostStatus.REMOVED.name());
        auditService.record(adminId, Principal.ROLE_ADMIN, "POST_REMOVE", "POST", postId, "SUCCESS", null);
    }

    @Transactional
    public void restorePost(Long adminId, Long postId, String reason) {
        Post post = postMapper.findById(postId);
        if (post == null) {
            throw BusinessException.of(ErrorCode.POST_NOT_FOUND);
        }
        if (!PostStatus.REMOVED.name().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅已下架信息可恢复");
        }
        String before = moderationMapper.findLastRemovedBeforeState(postId);
        // 不简单一律置 ACTIVE：若下架前处于 HANDOVER（关联申请已关闭），恢复为 ACTIVE 供重新招领；
        // 若为 COMPLETED/WITHDRAWN 则恢复原终态。
        String target;
        if (before == null || PostStatus.HANDOVER.name().equals(before) || PostStatus.ACTIVE.name().equals(before)) {
            target = PostStatus.ACTIVE.name();
        } else {
            target = before;
        }
        postMapper.forceStatus(postId, target);
        logModeration(adminId, "POST", postId, "RESTORE", reason, PostStatus.REMOVED.name(), target);
        auditService.record(adminId, Principal.ROLE_ADMIN, "POST_RESTORE", "POST", postId, "SUCCESS", null);
    }

    // ---- 用户治理 ----

    public PageResult<User> listUsers(String keyword, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = pageSize <= 0 || pageSize > 100 ? 20 : pageSize;
        List<User> items = userMapper.search(keyword, (p - 1) * size, size);
        long total = userMapper.countSearch(keyword);
        return PageResult.of(items, total, p, size);
    }

    @Transactional
    public void restrictUser(Long adminId, Long userId, String reason) {
        User u = userMapper.findById(userId);
        if (u == null) {
            throw BusinessException.of(ErrorCode.NOT_FOUND);
        }
        String before = u.getStatus();
        userMapper.updateStatus(userId, UserStatus.RESTRICTED.name());
        logModeration(adminId, "USER", userId, "RESTRICT", reason, before, UserStatus.RESTRICTED.name());
        auditService.record(adminId, Principal.ROLE_ADMIN, "USER_RESTRICT", "USER", userId, "SUCCESS", null);
    }

    @Transactional
    public void unrestrictUser(Long adminId, Long userId, String reason) {
        User u = userMapper.findById(userId);
        if (u == null) {
            throw BusinessException.of(ErrorCode.NOT_FOUND);
        }
        String before = u.getStatus();
        userMapper.updateStatus(userId, UserStatus.ACTIVE.name());
        logModeration(adminId, "USER", userId, "UNRESTRICT", reason, before, UserStatus.ACTIVE.name());
        auditService.record(adminId, Principal.ROLE_ADMIN, "USER_UNRESTRICT", "USER", userId, "SUCCESS", null);
    }

    private void logModeration(Long adminId, String targetType, Long targetId, String action,
                               String reason, String before, String after) {
        ModerationAction m = new ModerationAction();
        m.setAdminId(adminId);
        m.setTargetType(targetType);
        m.setTargetId(targetId);
        m.setAction(action);
        m.setReason(reason);
        m.setBeforeState(before);
        m.setAfterState(after);
        moderationMapper.insert(m);
    }
}
