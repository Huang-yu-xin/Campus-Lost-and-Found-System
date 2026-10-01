-- V2 (P1)：匹配候选按事件时间窗选取（替代"最新 100 条"截断）。
-- 窗口臂需要按 (type, status, event_time) 范围扫描；本索引使每臂均为 range scan。
ALTER TABLE posts ADD INDEX idx_post_match_time (type, status, event_time);
