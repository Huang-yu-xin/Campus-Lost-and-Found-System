package edu.whut.clf.admin.model;

import lombok.Data;

import java.time.LocalDateTime;

/** moderation_actions 表实体。治理行为持久化，不可静默覆盖。 */
@Data
public class ModerationAction {
    private Long id;
    private Long adminId;
    private String targetType;   // POST / USER
    private Long targetId;
    private String action;       // REMOVE / RESTORE / RESTRICT / UNRESTRICT
    private String reason;
    private String beforeState;
    private String afterState;
    private LocalDateTime createdAt;
}
