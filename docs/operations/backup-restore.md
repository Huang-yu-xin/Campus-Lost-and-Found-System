# 备份与隔离恢复 (Backup & Restore)

> 安全边界：后台仅提供**触发备份**，**不提供**公网一键生产恢复。恢复仅通过运维离线脚本，且默认拒绝生产连接地址。

## 1. 备份内容
- 数据库：`mysqldump` 导出 `campus_lost_found`。
- 受控图片目录：`FILE_STORAGE_ROOT`（默认 `./storage`）。
- 记录：`backup_records`（批次、状态、清单路径、校验和）。后台"审计与维护 → 触发备份"会备份图片目录并写 `backup_records` + `manifest.json` + SHA-256 校验和；数据库导出用下方脚本。

## 2. 备份命令（运维执行）
```bash
TS=$(date +%Y%m%d_%H%M%S)
OUT="backups/db_${TS}.sql"
mysqldump -u <user> -p --single-transaction --routines campus_lost_found > "$OUT"
sha256sum "$OUT" > "$OUT.sha256"
# 图片目录随后台备份或手动打包： tar -czf backups/storage_${TS}.tgz storage/
```

## 3. 隔离恢复验证（TC-BACKUP-01，绝不覆盖在用库）
```bash
# 1) 目标必须是独立空白测试库，脚本拒绝指向生产库名
TEST_DB=campus_lost_found_restore_check
mysql -u <user> -p -e "DROP DATABASE IF EXISTS ${TEST_DB}; CREATE DATABASE ${TEST_DB} DEFAULT CHARACTER SET utf8mb4;"
# 2) 校验备份完整性
sha256sum -c backups/db_<TS>.sql.sha256
# 3) 导入隔离库
mysql -u <user> -p ${TEST_DB} < backups/db_<TS>.sql
# 4) 核验关键表数量与抽样
mysql -u <user> -p ${TEST_DB} -e "SELECT
  (SELECT COUNT(*) FROM posts) posts,
  (SELECT COUNT(*) FROM claims) claims,
  (SELECT COUNT(*) FROM disputes) disputes;"
# 5) 图片抽样校验和比对（恢复目录 vs 原 manifest）
# 6) 记录 restore_verified_at 到对应 backup_records 行
```

## 4. 安全约束
- 恢复脚本的 `TEST_DB` 名含 `restore_check`，**严禁**填生产库名；执行者需二次确认。
- 备份文件不入公开仓库（`backups/` 已在 .gitignore）。
- 本会话未持有 MySQL 凭据，未实际执行；请运维按上述步骤执行并把结果回填 `docs/testing/test-report.md` 与 `backup_records`。
