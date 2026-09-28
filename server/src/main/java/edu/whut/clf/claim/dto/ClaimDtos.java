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
}
