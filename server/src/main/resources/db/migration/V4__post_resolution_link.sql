-- V4：认领完成 → 寻物帖闭环链接。
-- 背景见 docs/reports/P7-matching-improvement-report.md §3（数据模型缺口：
-- "招领被认领完成"无法落回失主的寻物帖）。本迁移只新增字段/约束，不回改 V1–V3。
--
-- resolved_by_claim_id：失主确认关联的那条 claim（招领侧）；NULL 表示未关联。
-- closed_at：帖子进入终态（COMPLETED/WITHDRAWN/REMOVED）的时间；回到 ACTIVE 时清 NULL。
--   用于评估"点时重建"（按关闭时间还原历史候选池）与统计准确。

ALTER TABLE posts ADD COLUMN resolved_by_claim_id BIGINT NULL;
ALTER TABLE posts ADD COLUMN closed_at DATETIME NULL;
ALTER TABLE posts ADD INDEX idx_post_resolution (resolved_by_claim_id);
ALTER TABLE posts ADD CONSTRAINT fk_post_resolution
    FOREIGN KEY (resolved_by_claim_id) REFERENCES claims (id);

-- 存量回填（近似：以 updated_at 作为结束时间，供评估点时重建用）
UPDATE posts SET closed_at = updated_at
 WHERE status IN ('COMPLETED','WITHDRAWN','REMOVED') AND closed_at IS NULL;
