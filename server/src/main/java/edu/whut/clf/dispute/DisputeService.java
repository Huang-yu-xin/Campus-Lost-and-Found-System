package edu.whut.clf.dispute;

import edu.whut.clf.claim.ClaimAccessService;
import edu.whut.clf.claim.ClaimMapper;
import edu.whut.clf.claim.model.Claim;
import edu.whut.clf.common.enums.*;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.common.web.PageResult;
import edu.whut.clf.dispute.dto.DisputeDtos.*;
import edu.whut.clf.dispute.model.Dispute;
import edu.whut.clf.file.FileService;
import edu.whut.clf.post.PostService;
import edu.whut.clf.audit.AuditService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * M6 争议发起与裁决（FR-DISPUTE-01/02/03）。OPEN 争议冻结交接完成；
 * 仅授权管理员可裁决并读取受限证据；每次裁决都写审计。
 */
@Service
public class DisputeService {

    private final DisputeMapper disputeMapper;
    private final DisputeEvidenceFileMapper evidenceMapper;
    private final ClaimAccessService claimAccess;
    private final ClaimMapper claimMapper;
    private final PostService postService;
    private final FileService fileService;
    private final AuditService auditService;

    public DisputeService(DisputeMapper disputeMapper, DisputeEvidenceFileMapper evidenceMapper,
                          ClaimAccessService claimAccess, ClaimMapper claimMapper, PostService postService,
                          FileService fileService, AuditService auditService) {
        this.disputeMapper = disputeMapper;
        this.evidenceMapper = evidenceMapper;
        this.claimAccess = claimAccess;
        this.claimMapper = claimMapper;
        this.postService = postService;
        this.fileService = fileService;
        this.auditService = auditService;
    }

    @Transactional
    public DisputeView raise(Long claimId, Long userId, RaiseDisputeRequest req) {
        ClaimAccessService.Participants pt = claimAccess.requireParticipant(claimId, userId);
        // 仅在有效交接期（WAITING_HANDOVER）可发起
        if (!ClaimStatus.WAITING_HANDOVER.name().equals(pt.claimStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可发起争议");
        }
        if (disputeMapper.countOpenByClaim(claimId) > 0) {
            throw BusinessException.of(ErrorCode.DISPUTE_OPEN_EXISTS);
        }
        // A3：去重 + 数量上限 + 拒绝二次绑定
        List<Long> files = fileService.prepareReplaceBinding(req.evidenceFileIds(), userId, FilePurpose.PRIVATE_DISPUTE);
        Dispute d = new Dispute();
        d.setClaimId(claimId);
        d.setRaisedBy(userId);
        d.setReason(req.reason().trim());
        d.setDescription(req.description());
        try {
            disputeMapper.insert(d);
        } catch (DuplicateKeyException e) {
            throw BusinessException.of(ErrorCode.DISPUTE_OPEN_EXISTS);
        }
        // 替换语义：首次绑定 deleteByDispute 命中 0 行
        evidenceMapper.deleteByDispute(d.getId());
        for (Long fid : files) {
            evidenceMapper.insert(d.getId(), fid, userId);
            fileService.markBound(fid);
        }
        auditService.record(userId, Principal.ROLE_USER, "DISPUTE_RAISE", "DISPUTE", d.getId(), "SUCCESS", null);
        // 回填 DB 默认值，使返回视图完整（insert 仅回填自增 id）
        return toView(disputeMapper.findById(d.getId()));
    }

    public List<DisputeView> listForParticipant(Long claimId, Long userId) {
        claimAccess.requireParticipant(claimId, userId);
        return disputeMapper.findByClaim(claimId).stream().map(this::toView).toList();
    }

    // ---- 管理端 ----

    public PageResult<AdminDisputeView> adminList(String status, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = pageSize <= 0 || pageSize > 100 ? 20 : pageSize;
        List<Dispute> items = disputeMapper.adminSearch(status, (p - 1) * size, size);
        long total = disputeMapper.adminCount(status);
        return PageResult.of(items.stream().map(d -> toAdminView(d, false)).toList(), total, p, size);
    }

    public AdminDisputeView adminGet(Long disputeId) {
        Dispute d = disputeMapper.findById(disputeId);
        if (d == null) {
            throw BusinessException.of(ErrorCode.DISPUTE_NOT_FOUND);
        }
        return toAdminView(d, true);
    }

    /** 管理员受理争议（B9）：受理后方可读取受限证据（DisputeEvidenceAccessChecker）。 */
    @Transactional
    public void assign(Long disputeId, Long adminId) {
        Dispute d = disputeMapper.findById(disputeId);
        if (d == null) {
            throw BusinessException.of(ErrorCode.DISPUTE_NOT_FOUND);
        }
        int n = disputeMapper.assign(disputeId, adminId);
        if (n == 0) {
            throw BusinessException.of(ErrorCode.DISPUTE_NOT_OPEN);
        }
        auditService.record(adminId, Principal.ROLE_ADMIN, "DISPUTE_ASSIGN", "DISPUTE", disputeId, "SUCCESS", null);
    }

    @Transactional
    public void resolve(Long disputeId, Long adminId, ResolveRequest req) {
        Dispute d = disputeMapper.findById(disputeId);
        if (d == null) {
            throw BusinessException.of(ErrorCode.DISPUTE_NOT_FOUND);
        }
        if (!DisputeStatus.OPEN.name().equals(d.getStatus())) {
            throw BusinessException.of(ErrorCode.DISPUTE_NOT_OPEN);
        }
        ResolutionType type;
        try {
            type = ResolutionType.valueOf(req.resolutionType());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "非法裁决类型");
        }
        Claim claim = claimMapper.findById(d.getClaimId());
        if (claim == null) {
            throw BusinessException.of(ErrorCode.CLAIM_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        String disputeStatus = (type == ResolutionType.CLOSE)
                ? DisputeStatus.CLOSED.name() : DisputeStatus.RESOLVED.name();

        int n = disputeMapper.resolve(disputeId, disputeStatus, adminId, type.name(), req.resolutionNote(), now);
        if (n == 0) {
            throw BusinessException.of(ErrorCode.DISPUTE_NOT_OPEN);
        }

        switch (type) {
            case CONTINUE -> {
                // 恢复交接，回到双方确认流程；claim/post 状态不变
            }
            case TERMINATE_REOPEN -> applyTermination(claim, PostStatus.ACTIVE);
            case CLOSE -> applyTermination(claim, PostStatus.COMPLETED);
        }
        auditService.record(adminId, Principal.ROLE_ADMIN, "DISPUTE_RESOLVE", "DISPUTE", disputeId, "SUCCESS",
                "{\"resolutionType\":\"" + type.name() + "\"}");
    }

    /**
     * 终止本次交接并按目标状态处置招领；任一状态转换未命中(0 行)则抛冲突使整个裁决事务回滚，
     * 避免"争议已标裁决但 claim/post 静默未变"的不一致（B6）。
     */
    private void applyTermination(Claim claim, PostStatus postTarget) {
        int c = claimMapper.changeStatus(claim.getId(),
                ClaimStatus.WAITING_HANDOVER.name(), ClaimStatus.CLOSED.name());
        int p = postService.tryTransition(claim.getPostId(), PostStatus.HANDOVER, postTarget);
        if (c == 0 || p == 0) {
            throw BusinessException.of(ErrorCode.CLAIM_STATE_INVALID);
        }
    }

    /** 供文件访问 checker 判断管理员是否为该争议的受理人。 */
    public boolean isAssignedAdmin(Long disputeId, Long adminId) {
        Dispute d = disputeMapper.findById(disputeId);
        return d != null && adminId.equals(d.getAssignedAdminId());
    }

    private DisputeView toView(Dispute d) {
        return new DisputeView(d.getId(), d.getClaimId(), d.getReason(), d.getStatus(),
                d.getResolutionType(), d.getResolutionNote(), d.getCreatedAt(), d.getResolvedAt());
    }

    private AdminDisputeView toAdminView(Dispute d, boolean includeEvidence) {
        List<Long> ev = includeEvidence ? evidenceMapper.findFileIds(d.getId()) : List.of();
        return new AdminDisputeView(d.getId(), d.getClaimId(), d.getRaisedBy(), d.getReason(), d.getDescription(),
                d.getStatus(), d.getAssignedAdminId(), d.getResolutionType(), d.getResolutionNote(),
                d.getCreatedAt(), d.getResolvedAt(), ev);
    }
}
