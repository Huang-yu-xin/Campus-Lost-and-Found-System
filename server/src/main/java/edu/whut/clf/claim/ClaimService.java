package edu.whut.clf.claim;

import edu.whut.clf.claim.dto.ClaimDtos.*;
import edu.whut.clf.claim.model.Claim;
import edu.whut.clf.common.enums.*;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.common.web.PageResult;
import edu.whut.clf.audit.AuditService;
import edu.whut.clf.file.FileService;
import edu.whut.clf.handover.HandoverConfirmationMapper;
import edu.whut.clf.match.TextTokenizer;
import edu.whut.clf.post.PostService;
import edu.whut.clf.post.model.Post;
import edu.whut.clf.user.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
    private final AuditService auditService;
    private final ObjectProvider<ClaimDisputeGuard> disputeGuard;
    // 跨包复用：match 包的中文文本归一化/分词工具，这里仅用其 unigram 集做候选寻物帖的
    // 文本重合度排序（不参与任何匹配打分/权重，P1–P4 成果不受影响）。注入而非另造，
    // 以保证"关联推荐"与"匹配候选"的归一化口径一致。
    private final TextTokenizer textTokenizer;

    public ClaimService(ClaimMapper claimMapper, ClaimEvidenceFileMapper evidenceMapper,
                        HandoverConfirmationMapper handoverMapper, PostService postService,
                        UserService userService, FileService fileService,
                        AuditService auditService,
                        ObjectProvider<ClaimDisputeGuard> disputeGuard,
                        TextTokenizer textTokenizer) {
        this.claimMapper = claimMapper;
        this.evidenceMapper = evidenceMapper;
        this.handoverMapper = handoverMapper;
        this.postService = postService;
        this.userService = userService;
        this.fileService = fileService;
        this.auditService = auditService;
        this.disputeGuard = disputeGuard;
        this.textTokenizer = textTokenizer;
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
        // 校验证据文件归属与用途（A3：去重 + 数量上限 + 拒绝二次绑定）
        List<Long> files = fileService.prepareReplaceBinding(req.evidenceFileIds(), userId, FilePurpose.PRIVATE_CLAIM);
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
        // 替换语义：首次绑定 deleteByClaim 命中 0 行
        evidenceMapper.deleteByClaim(claim.getId());
        for (Long fid : files) {
            evidenceMapper.insert(claim.getId(), fid);
            fileService.markBound(fid);
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
        Long resolvedLostPostId = postService.resolvedLostPostIdByClaim(claimId); // B7/R4 持久态
        return new ClaimDetail(claim.getId(), claim.getPostId(), claim.getApplicantId(), claim.getDescription(),
                claim.getStatus(), claim.getReviewReason(), claim.getReviewedAt(), claim.getAcceptedAt(),
                claim.getCompletedAt(), post.getPublisherId(), isApplicant, isPublisher,
                applicantConfirmed, publisherConfirmed, resolvedLostPostId, evidence);
    }

    public PageResult<ClaimSummary> myClaims(Long userId, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = pageSize <= 0 || pageSize > 100 ? 20 : pageSize;
        List<Claim> list = claimMapper.findByApplicant(userId, (p - 1) * size, size);
        long total = claimMapper.countByApplicant(userId);
        return PageResult.of(list.stream().map(this::toSummary).toList(), total, p, size);
    }

    /** 我作为发布者收到的所有申请（B10 / FR-CLAIM-02）。 */
    public PageResult<edu.whut.clf.claim.dto.ReceivedClaimItem> receivedClaims(Long userId, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = pageSize <= 0 || pageSize > 100 ? 20 : pageSize;
        var items = claimMapper.findReceivedByPublisher(userId, (p - 1) * size, size);
        long total = claimMapper.countReceivedByPublisher(userId);
        return PageResult.of(items, total, p, size);
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
            auditService.record(userId, Principal.ROLE_USER, "CLAIM_REVIEW", "CLAIM", claimId,
                    "SUCCESS", "{\"decision\":\"REJECT\"}");
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
        auditService.record(userId, Principal.ROLE_USER, "CLAIM_REVIEW", "CLAIM", claimId,
                "SUCCESS", "{\"decision\":\"ACCEPT\"}");
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
        // A4(P1-B3)：与 confirmHandover 同口径——存在 OPEN 争议时冻结取消，避免绕过争议裁决
        if (hasOpenDispute(claimId)) {
            throw BusinessException.of(ErrorCode.HANDOVER_PAUSED_BY_DISPUTE);
        }
        int n = claimMapper.changeStatus(claimId, ClaimStatus.WAITING_HANDOVER.name(), ClaimStatus.CLOSED.name());
        if (n == 0) {
            throw BusinessException.of(ErrorCode.CLAIM_STATE_INVALID);
        }
        // 招领恢复为可申请
        postService.requireTransition(post.getId(), PostStatus.HANDOVER, PostStatus.ACTIVE);
    }

    // ================== V4：认领完成 → 寻物帖闭环链接 ==================

    /**
     * S2.1 推荐候选：申请人名下可被本次认领关联的寻物帖。
     * 顺序即防枚举设计：claim 存在 → 必须是申请人（否则 404）→ claim 必须 COMPLETED。
     * 过滤：同类别（任一侧无码回退原文相等）+ 时间合理（lost.event_time ≤ found.event_time+24h，
     * 容忍录入误差；任一侧无时间则不拦）；按标题+公开描述的 unigram Jaccard 降序，最多 3 条。
     */
    public ResolvedCandidates resolvedCandidates(Long claimId, Long userId) {
        Claim claim = requireApplicantClaim(claimId, userId);
        requireClaimCompleted(claim);
        Post found = postService.getById(claim.getPostId());
        Set<String> foundUni = textTokenizer.unigrams(textOf(found));
        List<ResolvedCandidate> items = postService.activeLostByPublisher(userId).stream()
                .filter(lost -> categoryMatches(lost, found))
                .filter(lost -> timeReasonable(lost, found))
                .map(lost -> new ResolvedCandidate(lost.getId(), lost.getTitle(), lost.getCampus(),
                        lost.getEventTime(),
                        round2(jaccard(textTokenizer.unigrams(textOf(lost)), foundUni))))
                .sorted(Comparator.comparingDouble(ResolvedCandidate::overlap).reversed())
                .limit(3)
                .toList();
        return new ResolvedCandidates(items);
    }

    /**
     * S2.2 失主确认关联：把寻物帖闭环为"已找回"。事务内按序校验（顺序即防枚举）：
     * 参与方 → claim 完成 → 帖子归属 → 幂等/冲突 → 类别一致 → 锁帖条件更新 → 审计。
     * 硬校验只有本人+ACTIVE+类别一致；时间合理仅用于推荐，不在此硬拦。
     */
    @Transactional
    public void resolveLost(Long claimId, Long userId, Long lostPostId) {
        Claim claim = requireApplicantClaim(claimId, userId);          // 1. 参与方
        requireClaimCompleted(claim);                                  // 2. claim 完成
        if (lostPostId == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "lostPostId 必填");
        }
        // R4：一个 claim 只能关联一条寻物帖——若本 claim 已关联其他帖，则 409（关联同一帖走后续幂等分支）
        Long alreadyLinked = postService.resolvedLostPostIdByClaim(claimId);
        if (alreadyLinked != null && !alreadyLinked.equals(lostPostId)) {
            throw BusinessException.of(ErrorCode.RESOLVE_ALREADY_RESOLVED);
        }
        Post found = postService.getById(claim.getPostId());
        Post lost = postService.getById(lostPostId);
        if (!PostType.LOST.name().equals(lost.getType())) {           // A5(P1-B4)：只能关联寻物帖
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "只能关联寻物帖");
        }
        if (!Objects.equals(lost.getPublisherId(), userId)) {         // 3. 帖子归属
            throw BusinessException.of(ErrorCode.RESOLVE_NOT_OWNER);
        }
        if (Objects.equals(lost.getResolvedByClaimId(), claimId)) {   // 4a. 幂等：本 claim 已关联
            return;
        }
        if (lost.getResolvedByClaimId() != null
                || !PostStatus.ACTIVE.name().equals(lost.getStatus())) {  // 4b. 被他 claim 关联 / 非 ACTIVE
            throw BusinessException.of(ErrorCode.RESOLVE_ALREADY_RESOLVED);
        }
        if (!categoryMatches(lost, found)) {                         // 5. 类别一致
            throw BusinessException.of(ErrorCode.RESOLVE_CATEGORY_MISMATCH);
        }
        // 6. 执行：锁帖后以 FOR UPDATE 的权威读复核（防并发），再条件更新。
        Post locked = postService.lockForUpdate(lostPostId);
        if (Objects.equals(locked.getResolvedByClaimId(), claimId)) {
            return; // 并发的同一请求已完成，幂等返回
        }
        if (locked.getResolvedByClaimId() != null
                || !PostStatus.ACTIVE.name().equals(locked.getStatus())) {
            throw BusinessException.of(ErrorCode.RESOLVE_ALREADY_RESOLVED);
        }
        int n = postService.linkResolvedLost(lostPostId, claimId);
        if (n == 0) {
            throw BusinessException.of(ErrorCode.CONFLICT);
        }
        // 7. 审计
        auditService.record(userId, Principal.ROLE_USER, "LOST_RESOLVED", "POST", lostPostId,
                "SUCCESS", "{\"claimId\":" + claimId + "}");
    }

    private Claim requireApplicantClaim(Long claimId, Long userId) {
        Claim claim = claimMapper.findById(claimId);
        if (claim == null || !Objects.equals(claim.getApplicantId(), userId)) {
            // 非申请人按 404 防枚举（与 detail 一致口径）
            throw BusinessException.of(ErrorCode.CLAIM_NOT_FOUND);
        }
        return claim;
    }

    private void requireClaimCompleted(Claim claim) {
        if (!ClaimStatus.COMPLETED.name().equals(claim.getStatus())) {
            throw BusinessException.of(ErrorCode.CLAIM_NOT_COMPLETED);
        }
    }

    /** 同类别判定：两侧均有 category_code 时比码；任一侧无码回退原文 category 相等。 */
    private boolean categoryMatches(Post a, Post b) {
        String ca = a.getCategoryCode(), cb = b.getCategoryCode();
        if (ca != null && cb != null) {
            return ca.equals(cb);
        }
        return Objects.equals(trim(a.getCategory()), trim(b.getCategory()));
    }

    /** 时间合理：lost.event_time ≤ found.event_time + 24h；任一侧无时间则不拦（仅用于推荐过滤）。 */
    private boolean timeReasonable(Post lost, Post found) {
        LocalDateTime le = lost.getEventTime(), fe = found.getEventTime();
        if (le == null || fe == null) {
            return true;
        }
        return !le.isAfter(fe.plusHours(24));
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String textOf(Post p) {
        return (p.getTitle() == null ? "" : p.getTitle()) + " "
                + (p.getPublicDescription() == null ? "" : p.getPublicDescription());
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
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
