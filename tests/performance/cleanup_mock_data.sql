-- 清理上一轮导入的模拟数据（按外键依赖顺序删除；只动 @pb/@ub 之后的新数据，不碰种子与业务数据）
-- 用法：mysql ... campus_lost_found < cleanup_mock_data.sql
SET NAMES utf8mb4;
SET @pb = (SELECT MAX(id) FROM posts) - 10000;
SET @ub = (SELECT MAX(id) FROM users) - 80;

-- V4：先解除 posts → claims 的闭环外键（resolved_by_claim_id），否则删除 claims 会被 fk_post_resolution 阻断。
UPDATE posts SET resolved_by_claim_id = NULL WHERE id > @pb AND resolved_by_claim_id IS NOT NULL;

DELETE FROM moderation_actions   WHERE target_type='POST' AND target_id > @pb;
DELETE FROM disputes             WHERE claim_id IN (SELECT id FROM claims WHERE post_id > @pb);
DELETE FROM claim_messages       WHERE claim_id IN (SELECT id FROM claims WHERE post_id > @pb);
DELETE FROM handover_confirmations WHERE claim_id IN (SELECT id FROM claims WHERE post_id > @pb);
DELETE FROM claims               WHERE post_id > @pb;
DELETE FROM lost_leads           WHERE lost_post_id > @pb;
DELETE FROM posts                WHERE id > @pb;
-- 冒烟残留：e2e 上传的文件属于 @ub 之后的用户，先删文件行（物理文件留待 storage 清理策略），
-- 否则删除用户会被 fk_file_owner 阻断（V4 验收发现的清理脆弱点）。
DELETE FROM files                WHERE owner_id > @ub;
DELETE FROM sessions             WHERE user_id > @ub;
DELETE FROM auth_identities      WHERE user_id > @ub;
DELETE FROM users                WHERE id > @ub;

SELECT 'posts remaining' k, COUNT(*) v FROM posts
UNION ALL SELECT 'users remaining', COUNT(*) FROM users;
