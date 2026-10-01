package edu.whut.clf.backup;

import edu.whut.clf.audit.AuditService;
import edu.whut.clf.backup.model.BackupRecord;
import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.common.web.PageResult;
import edu.whut.clf.common.web.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * M2 数据备份（FR-BACKUP-01）。备份受控图片目录并记录批次与校验信息。
 * 数据库导出与**隔离环境恢复验证**由运维离线脚本执行（见 docs/operations/backup-restore.md），
 * 不提供公网一键生产恢复接口（安全边界）。
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final BackupRecordMapper mapper;
    private final AppProperties props;
    private final AuditService auditService;

    public BackupService(BackupRecordMapper mapper, AppProperties props, AuditService auditService) {
        this.mapper = mapper;
        this.props = props;
        this.auditService = auditService;
    }

    public BackupRecord initiate(Long adminId) {
        BackupRecord rec = new BackupRecord();
        rec.setInitiatedBy(adminId);
        rec.setStartedAt(LocalDateTime.now());
        rec.setStatus("RUNNING");
        mapper.insert(rec);

        try {
            Path storageRoot = Paths.get(props.getFile().getStorageRoot());
            Path backupDir = Paths.get(props.getBackup().getDir(), "backup_" + rec.getId() + "_"
                    + System.currentTimeMillis());
            Files.createDirectories(backupDir);

            AtomicLong fileCount = new AtomicLong();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            if (Files.exists(storageRoot)) {
                Path filesTarget = backupDir.resolve("storage");
                copyTree(storageRoot, filesTarget, md, fileCount);
            }
            String checksum = toHex(md.digest());
            Path manifest = backupDir.resolve("manifest.json");
            String manifestJson = "{\"backupId\":" + rec.getId()
                    + ",\"fileCount\":" + fileCount.get()
                    + ",\"checksum\":\"" + checksum + "\""
                    + ",\"note\":\"数据库导出请用 ops 离线脚本；本备份含受控图片目录\"}";
            Files.writeString(manifest, manifestJson, StandardOpenOption.CREATE);

            mapper.finish(rec.getId(), "SUCCESS", LocalDateTime.now(), manifest.toString(), checksum);
            auditService.record(adminId, Principal.ROLE_ADMIN, "BACKUP_RUN", "BACKUP", rec.getId(), "SUCCESS", null);
            rec.setStatus("SUCCESS");
            rec.setManifestPath(manifest.toString());
            rec.setChecksum(checksum);
        } catch (Exception e) {
            log.error("backup failed id={}", rec.getId(), e);
            mapper.finish(rec.getId(), "FAILED", LocalDateTime.now(), null, null);
            auditService.record(adminId, Principal.ROLE_ADMIN, "BACKUP_RUN", "BACKUP", rec.getId(), "FAILURE", null);
            rec.setStatus("FAILED");
        }
        return rec;
    }

    public PageResult<BackupRecord> list(int page, int pageSize) {
        Pageable pg = Pageable.of(page, pageSize);
        List<BackupRecord> items = mapper.list(pg.offset(), pg.size());
        return PageResult.of(items, mapper.count(), pg.page(), pg.size());
    }

    /**
     * D13/R7：启动时把超时（&gt;1h）仍 RUNNING 的记录置 FAILED，清理进程崩溃遗留的僵尸批次。
     * 返回置为 FAILED 的行数。
     */
    public int failTimedOutRunning() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        int n = mapper.failRunningBefore(cutoff, LocalDateTime.now());
        if (n > 0) {
            log.warn("backup: marked {} timed-out RUNNING record(s) as FAILED", n);
        }
        return n;
    }

    private void copyTree(Path src, Path dst, MessageDigest md, AtomicLong count) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = src.relativize(file);
                Path target = dst.resolve(rel.toString());
                Files.createDirectories(target.getParent());
                byte[] bytes = Files.readAllBytes(file);
                md.update(bytes);
                Files.write(target, bytes, StandardOpenOption.CREATE);
                count.incrementAndGet();
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
