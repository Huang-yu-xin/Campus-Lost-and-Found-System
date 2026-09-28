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
        List<Long> files = req.evidenceFileIds();
        if (files != null) {
            for (Long fid : files) {
                fileService.requireOwnedFile(fid, userId, FilePurpose.PRIVATE_DISPUTE);
            }
        }
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
        if (files != null) {
            for (Long fid : files) {
                evidenceMapper.insert(d.getId(), fid, userId);
                fileService.markBound(fid);
            }
        }
        auditService.record(userId, Principal.ROLE_USER, "DISPUTE_RAISE", "DISPUTE", d.getId(), "SUCCESS", null);
        return toView(d);
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
            case TERMINATE_REOPEN -> {
                claimMapper.changeStatus(claim.getId(), ClaimStatus.WAITING_HANDOVER.name(), ClaimStatus.CLOSED.name());
                postService.tryTransition(claim.getPostId(), PostStatus.HANDOVER, PostStatus.ACTIVE);
            }
            case CLOSE -> {
                claimMapper.changeStatus(claim.getId(), ClaimStatus.WAITING_HANDOVER.name(), ClaimStatus.CLOSED.name());
                postService.tryTransition(claim.getPostId(), PostStatus.HANDOVER, PostStatus.COMPLETED);
            }
        }
        auditService.record(adminId, Principal.ROLE_ADMIN, "DISPUTE_RESOLVE", "DISPUTE", disputeId, "SUCCESS",
                "{\"resolutionType\":\"" + type.name() + "\"}");
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
