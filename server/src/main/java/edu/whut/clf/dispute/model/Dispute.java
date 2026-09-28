package edu.whut.clf.dispute.model;

import lombok.Data;

import java.time.LocalDateTime;

/** disputes 表实体。OPEN 冻结相关交接。生成列 open_dispute_key 由 DB 维护。 */
@Data
public class Dispute {
    private Long id;
    private Long claimId;
    private Long raisedBy;
    private String reason;
    private String description;
    private String status;            // DisputeStatus
    private Long assignedAdminId;
    private String resolutionType;    // ResolutionType
    private String resolutionNote;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}
