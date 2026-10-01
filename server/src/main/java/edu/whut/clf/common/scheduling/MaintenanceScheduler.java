package edu.whut.clf.common.scheduling;

import edu.whut.clf.auth.SessionService;
import edu.whut.clf.backup.BackupService;
import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.file.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 运维定时任务（D13/D14/R6/R7）：
 * - 每日清理孤儿文件（bound=0 且超 24h）——开关 app.file.orphan-cleanup-enabled；
 * - 每日清理过期/已撤销超 30 天的会话行；
 * - 启动时把超时（>1h）仍 RUNNING 的备份记录置 FAILED（清理崩溃遗留僵尸批次）。
 * 具体逻辑下沉到各 Service 的函数（便于单测），本类仅做调度编排。
 */
@Component
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    private final FileService fileService;
    private final SessionService sessionService;
    private final BackupService backupService;
    private final AppProperties props;

    public MaintenanceScheduler(FileService fileService, SessionService sessionService,
                                BackupService backupService, AppProperties props) {
        this.fileService = fileService;
        this.sessionService = sessionService;
        this.backupService = backupService;
        this.props = props;
    }

    /** 每日 03:10 清理孤儿文件。 */
    @Scheduled(cron = "${app.maintenance.orphan-cleanup-cron:0 10 3 * * *}")
    public void cleanupOrphanFiles() {
        if (!props.getFile().isOrphanCleanupEnabled()) {
            return;
        }
        try {
            fileService.cleanupOrphanFiles();
        } catch (Exception e) {
            log.error("scheduled orphan file cleanup failed", e);
        }
    }

    /** 每日 03:20 清理过期/撤销超 30 天的会话。 */
    @Scheduled(cron = "${app.maintenance.session-cleanup-cron:0 20 3 * * *}")
    public void cleanupStaleSessions() {
        try {
            int n = sessionService.purgeStaleSessions();
            if (n > 0) {
                log.info("session cleanup: removed {} stale session row(s)", n);
            }
        } catch (Exception e) {
            log.error("scheduled session cleanup failed", e);
        }
    }

    /** 启动完成后清理僵尸 RUNNING 备份记录。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            backupService.failTimedOutRunning();
        } catch (Exception e) {
            log.error("startup backup zombie cleanup failed", e);
        }
    }
}
