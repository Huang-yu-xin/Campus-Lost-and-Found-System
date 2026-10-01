package edu.whut.clf.claim.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public class ClaimDtos {

    public record SubmitClaimRequest(
            @NotBlank String description,      // 私密证明文字（必填，D-04）
            List<Long> evidenceFileIds) {}     // 私密证明图片（可选）

    public record ReviewRequest(
            @NotBlank String decision,         // ACCEPT / REJECT
            String reason) {}

    public record ClaimSummary(
            Long id, Long postId, Long applicantId, String status,
            LocalDateTime createdAt, LocalDateTime reviewedAt) {}

    public record ClaimDetail(
            Long id, Long postId, Long applicantId, String description, String status,
            String reviewReason, LocalDateTime reviewedAt, LocalDateTime acceptedAt, LocalDateTime completedAt,
            Long publisherId, boolean applicant, boolean publisher,
            boolean applicantConfirmed, boolean publisherConfirmed,
            List<Long> evidenceFileIds) {}

    public record HandoverStatus(
            String claimStatus, boolean publisherConfirmed, boolean applicantConfirmed) {}

    // ---- V4：认领完成 → 寻物帖闭环链接 ----

    /** 推荐的可关联寻物帖候选。overlap = 标题+公开描述的 unigram Jaccard（保留两位）。 */
    public record ResolvedCandidate(
            Long id, String title, String campus, LocalDateTime eventTime, double overlap) {}

    public record ResolvedCandidates(List<ResolvedCandidate> items) {}

    public record ResolveLostRequest(Long lostPostId) {}
}
