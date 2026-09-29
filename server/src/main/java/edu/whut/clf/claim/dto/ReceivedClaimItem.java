package edu.whut.clf.claim.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 发布者"收到的申请"聚合项（联表 posts 取标题/类型）。 */
@Data
public class ReceivedClaimItem {
    private Long id;
    private Long postId;
    private String postTitle;
    private String postType;
    private Long applicantId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
