package edu.whut.clf.claim;

import edu.whut.clf.claim.dto.ClaimDtos.*;
import edu.whut.clf.claim.model.Claim;
import edu.whut.clf.common.enums.*;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.common.web.PageResult;
import edu.whut.clf.file.FileService;
import edu.whut.clf.handover.HandoverConfirmationMapper;
import edu.whut.clf.post.PostService;
import edu.whut.clf.post.model.Post;
import edu.whut.clf.user.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * M5 认领申请、审核、单活跃交接、双向确认。状态机见 docs/architecture/state-machines.md。
 * 并发不变量由 锁父发布(FOR UPDATE) + 条件更新 + DB 唯一索引 共同保证。
 */
@Service
public class ClaimService {

    private final ClaimMapper claimMapper;
    private final ClaimEvidenceFileMapper evidenceMapper;
    private final HandoverConfirmationMapper handoverMapper;
    private final PostService postService;
    private final UserService userService;
    private final FileService fileService;
    private final ObjectProvider<ClaimDisputeGuard> disputeGuard;

    public ClaimService(ClaimMapper claimMapper, ClaimEvidenceFileMapper evidenceMapper,
                        HandoverConfirmationMapper handoverMapper, PostService postService,
                        UserService userService, FileService fileService,
                        ObjectProvider<ClaimDisputeGuard> disputeGuard) {
        this.claimMapper = claimMapper;
        this.evidenceMapper = evidenceMapper;
        this.handoverMapper = handoverMapper;
        this.postService = postService;
        this.userService = userService;
        this.fileService = fileService;
        this.disputeGuard = disputeGuard;
    }

    @Transactional
    public ClaimDetail submit(Long postId, Long userId, SubmitClaimRequest req) {
        userService.requireNotRestricted(userId);
        Post post = postService.getById(postId);
        if (!PostType.FOUND.name().equals(post.getType())
                || !PostStatus.ACTIVE.name().equals(post.getStatus())) {
            throw BusinessException.of(ErrorCode.POST_NOT_CLAIMABLE);
        }
        if (Objects.equals(post.getPublisherId(), userId)) {
            throw BusinessException.of(ErrorCode.SELF_CLAIM_FORBIDDEN);
        }
        if (claimMapper.countActiveByPostAndApplicant(postId, userId) > 0) {
            throw BusinessException.of(ErrorCode.ACTIVE_CLAIM_EXISTS);
        }
        // 校验证据文件归属与用途
        List<Long> files = req.evidenceFileIds();
        if (files != null) {
            for (Long fid : files) {
                fileService.requireOwnedFile(fid, userId, FilePurpose.PRIVATE_CLAIM);
            }
        }
        Claim claim = new Claim();
        claim.setPostId(postId);
        claim.setApplicantId(userId);
        claim.setDescription(req.description().trim());
        claim.setStatus(ClaimStatus.PENDING.name());
        try {
            claimMapper.insert(claim);
        } catch (DuplicateKeyException e) {
            // 唯一索引 uk_claim_active_applicant 兜底
            throw BusinessException.of(ErrorCode.ACTIVE_CLAIM_EXISTS);
        }
        if (files != null) {
            for (Long fid : files) {
                evidenceMapper.insert(claim.getId(), fid);
                fileService.markBound(fid);
            }
        }
        return detail(claim.getId(), userId);
    }

    public ClaimDetail detail(Long claimId, Long userId) {
        Claim claim = claimMapper.findById(claimId);
        if (claim == null) {
            throw BusinessException.of(ErrorCode.CLAIM_NOT_FOUND);
        }
        Post post = postService.getById(claim.getPostId());
        boolean isApplicant = Objects.equals(claim.getApplicantId(), userId);
        boolean isPublisher = Objects.equals(post.getPublisherId(), userId);
        if (!isApplicant && !isPublisher) {
            // 无权者按 404 避免枚举
            throw BusinessException.of(ErrorCode.CLAIM_NOT_FOUND);
        }
        boolean applicantConfirmed = handoverMapper.exists(claimId, claim.getApplicantId()) > 0;
        boolean publisherConfirmed = handoverMapper.exists(claimId, post.getPublisherId()) > 0;
        List<Long> evidence = isApplicant || isPublisher ? evidenceMapper.findFileIds(claimId) : List.of();
        return new ClaimDetail(claim.getId(), claim.getPostId(), claim.getApplicantId(), claim.getDescription(),
                claim.getStatus(), claim.getReviewReason(), claim.getReviewedAt(), claim.getAcceptedAt(),
                claim.getCompletedAt(), post.getPublisherId(), isApplicant, isPublisher,
                applicantConfirmed, publisherConfirmed, evidence);
    }

    public PageResult<ClaimSummary> myClaims(Long userId, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = pageSize <= 0 || pageSize > 100 ? 20 : pageSize;
        List<Claim> list = claimMapper.findByApplicant(userId, (p - 1) * size, size);
        long total = claimMapper.countByApplicant(userId);
        return PageResult.of(list.stream().map(this::toSummary).toList(), total, p, size);
    }

    public List<ClaimSummary> postClaims(Long postId, Long userId) {
        Post post = postService.getById(postId);
        if (!Objects.equals(post.getPublisherId(), userId)) {
            throw BusinessException.of(ErrorCode.FORBIDDEN);
        }
        return claimMapper.findByPost(postId).stream().map(this::toSummary).toList();
    }

    @Transactional
    public void withdraw(Long claimId, Long userId) {
        Claim claim = claimMapper.findById(claimId);
        if (claim == null) {
            throw BusinessException.of(ErrorCode.CLAIM_NOT_FOUND);
        }
        if (!Objects.equals(claim.getApplicantId(), userId)) {
            throw BusinessException.of(ErrorCode.CLAIM_NOT_FOUND);
        }
        int n = claimMapper.changeStatus(claimId, ClaimStatus.PENDING.name(), ClaimStatus.WITHDRAWN.name());
        if (n == 0) {
            throw BusinessException.of(ErrorCode.CLAIM_STATE_INVALID);
        }
    }

    @Transactional
    public void review(Long claimId, Long userId, ReviewRequest req) {
        Claim claim = claimMapper.findById(claimId);
        if (claim == null) {
            throw BusinessException.of(ErrorCode.CLAIM_NOT_FOUND);
        }
        Post post = postService.getById(claim.getPostId());
        if (!Objects.equals(post.getPublisherId(), userId)) {
            throw BusinessException.of(ErrorCode.FORBIDDEN);
        }
        if (!ClaimStatus.PENDING.name().equals(claim.getStatus())) {
            throw BusinessException.of(ErrorCode.CLAIM_NOT_PENDING);
        }
        String decision = req.decision() == null ? "" : req.decision().trim().toUpperCase();
        LocalDateTime now = LocalDateTime.now();
        if ("REJECT".equals(decision)) {
            int n = claimMapper.reject(claimId, userId, req.reason(), now);
            if (n == 0) {
                throw BusinessException.of(ErrorCode.CLAIM_NOT_PENDING);
            }
            return;
        }
        if (!"ACCEPT".equals(decision)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "decision 必须为 ACCEPT 或 REJECT");
        }
        try {
            // 锁定父发布，串行化并发接受
            Post locked = postService.lockForUpdate(post.getId());
            if (!PostStatus.ACTIVE.name().equals(locked.getStatus())
                    || !PostType.FOUND.name().equals(locked.getType())) {
                throw BusinessException.of(ErrorCode.CLAIM_ACCEPT_CONFLICT);
            }
            int n = claimMapper.accept(claimId, userId, now);
            if (n == 0) {
                throw BusinessException.of(ErrorCode.CLAIM_NOT_PENDING);
            }
            postService.requireTransition(post.getId(), PostStatus.ACTIVE, PostStatus.HANDOVER);
            claimMapper.closeOtherPending(post.getId(), claimId);
        } catch (DuplicateKeyException e) {
            // uk_claim_active_handover 兜底
            throw BusinessException.of(ErrorCode.CLAIM_ACCEPT_CONFLICT);
        }
    }

    @Transactional
    public HandoverStatus confirmHandover(Long claimId, Long userId) {
        Claim claim = claimMapper.findById(claimId);
        if (claim == null) {
            throw BusinessException.of(ErrorCode.CLAIM_NOT_FOUND);
        }
        Post post = postService.getById(claim.getPostId());
        boolean isApplicant = Objects.equals(claim.getApplicantId(), userId);
        boolean isPublisher = Objects.equals(post.getPublisherId(), userId);
        if (!isApplicant && !isPublisher) {
            throw BusinessException.of(ErrorCode.HANDOVER_NOT_PARTICIPANT);
        }
        if (!ClaimStatus.WAITING_HANDOVER.name().equals(claim.getStatus())) {
            throw BusinessException.of(ErrorCode.CLAIM_STATE_INVALID);
        }
        if (hasOpenDispute(claimId)) {
            throw BusinessException.of(ErrorCode.HANDOVER_PAUSED_BY_DISPUTE);
        }
        handoverMapper.insertIgnore(claimId, userId); // 幂等
        boolean applicantConfirmed = handoverMapper.exists(claimId, claim.getApplicantId()) > 0;
        boolean publisherConfirmed = handoverMapper.exists(claimId, post.getPublisherId()) > 0;

        String claimStatus = claim.getStatus();
        if (applicantConfirmed && publisherConfirmed) {
            // 完成前再次确认无 OPEN 争议（最后确认与争议创建竞态）
            if (hasOpenDispute(claimId)) {
                throw BusinessException.of(ErrorCode.HANDOVER_PAUSED_BY_DISPUTE);
            }
            int n = claimMapper.complete(claimId, LocalDateTime.now());
            if (n > 0) {
                postService.requireTransition(post.getId(), PostStatus.HANDOVER, PostStatus.COMPLETED);
                claimStatus = ClaimStatus.COMPLETED.name();
            }
        }
        return new HandoverStatus(claimStatus, publisherConfirmed, applicantConfirmed);
    }

    @Transactional
    public void cancelHandover(Long claimId, Long userId, String reason) {
        Claim claim = claimMapper.findById(claimId);
        if (claim == null) {
            throw BusinessException.of(ErrorCode.CLAIM_NOT_FOUND);
        }
        Post post = postService.getById(claim.getPostId());
        boolean isApplicant = Objects.equals(claim.getApplicantId(), userId);
        boolean isPublisher = Objects.equals(post.getPublisherId(), userId);
        if (!isApplicant && !isPublisher) {
            throw BusinessException.of(ErrorCode.FORBIDDEN);
        }
        if (!ClaimStatus.WAITING_HANDOVER.name().equals(claim.getStatus())) {
            throw BusinessException.of(ErrorCode.CLAIM_STATE_INVALID);
        }
        int n = claimMapper.changeStatus(claimId, ClaimStatus.WAITING_HANDOVER.name(), ClaimStatus.CLOSED.name());
        if (n == 0) {
            throw BusinessException.of(ErrorCode.CLAIM_STATE_INVALID);
        }
        // 招领恢复为可申请
        postService.requireTransition(post.getId(), PostStatus.HANDOVER, PostStatus.ACTIVE);
    }

    private boolean hasOpenDispute(Long claimId) {
        ClaimDisputeGuard guard = disputeGuard.getIfAvailable();
        return guard != null && guard.hasOpenDispute(claimId);
    }

    private ClaimSummary toSummary(Claim c) {
        return new ClaimSummary(c.getId(), c.getPostId(), c.getApplicantId(), c.getStatus(),
                c.getCreatedAt(), c.getReviewedAt());
    }
}
