package edu.whut.clf.claim.model;

import lombok.Data;

import java.time.LocalDateTime;

/** claims 表实体（仅 FOUND）。生成列 active_handover_key/active_applicant_key 由 DB 维护。 */
@Data
public class Claim {
    private Long id;
    private Long postId;
    private Long applicantId;
    private String description;
    private String status;          // ClaimStatus
    private Long reviewedBy;
    private String reviewReason;
    private LocalDateTime reviewedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime completedAt;
    private Integer version;
    private LocalDateTime createdAt;
}
