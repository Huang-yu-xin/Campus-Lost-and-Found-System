package edu.whut.clf.lead.model;

import lombok.Data;

import java.time.LocalDateTime;

/** lost_leads 表实体（仅 LOST；私密线索不进公开搜索）。 */
@Data
public class LostLead {
    private Long id;
    private Long lostPostId;
    private Long reporterId;
    private String body;
    private String status;   // LeadStatus
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
