package edu.whut.clf.backup.model;

import lombok.Data;

import java.time.LocalDateTime;

/** backup_records 表实体。 */
@Data
public class BackupRecord {
    private Long id;
    private Long initiatedBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status;            // RUNNING / SUCCESS / FAILED
    private String manifestPath;
    private String checksum;
    private LocalDateTime restoreVerifiedAt;
    private LocalDateTime createdAt;
}
