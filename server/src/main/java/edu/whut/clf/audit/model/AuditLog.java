package edu.whut.clf.audit.model;

import lombok.Data;

import java.time.LocalDateTime;

/** audit_logs 表实体。仅记录必要元数据，不含完整私密证明。 */
@Data
public class AuditLog {
    private Long id;
    private Long actorId;
    private String actorType;   // USER / ADMIN / SYSTEM
    private String action;
    private String targetType;
    private Long targetId;
    private String requestId;
    private String result;      // SUCCESS / FAILURE
    private String metadata;    // JSON
    private LocalDateTime createdAt;
}
