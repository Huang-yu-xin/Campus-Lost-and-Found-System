package edu.whut.clf.admin;

import edu.whut.clf.audit.AuditService;
import edu.whut.clf.audit.model.AuditLog;
import edu.whut.clf.backup.BackupService;
import edu.whut.clf.backup.model.BackupRecord;
import edu.whut.clf.common.security.AuthContext;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.common.web.ApiResponse;
import edu.whut.clf.common.web.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/** M2 审计与维护（FR-AUDIT-01 / FR-BACKUP-01）。要求管理员。 */
@RestController
@RequestMapping("/admin")
@Tag(name = "M2-Admin", description = "后台治理")
public class AdminMaintenanceController {

    private final AuditService auditService;
    private final BackupService backupService;

    public AdminMaintenanceController(AuditService auditService, BackupService backupService) {
        this.auditService = auditService;
        this.backupService = backupService;
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "审计日志 FR-AUDIT-01")
    public ApiResponse<PageResult<AuditLog>> auditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        AuthContext.requireAdmin();
        return ApiResponse.ok(auditService.search(action, targetType, page, pageSize));
    }

    @GetMapping("/maintenance/backups")
    @Operation(summary = "备份记录 FR-BACKUP-01")
    public ApiResponse<PageResult<BackupRecord>> backups(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        AuthContext.requireAdmin();
        return ApiResponse.ok(backupService.list(page, pageSize));
    }

    @PostMapping("/maintenance/backups")
    @Operation(summary = "触发授权备份 FR-BACKUP-01")
    public ApiResponse<BackupRecord> runBackup() {
        Principal admin = AuthContext.requireAdmin();
        return ApiResponse.ok(backupService.initiate(admin.userId()));
    }
}
