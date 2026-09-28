package edu.whut.clf.message.model;

import lombok.Data;

import java.time.LocalDateTime;

/** claim_messages 表实体（仅申请双方可见）。 */
@Data
public class ClaimMessage {
    private Long id;
    private Long claimId;
    private Long senderId;
    private String body;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
