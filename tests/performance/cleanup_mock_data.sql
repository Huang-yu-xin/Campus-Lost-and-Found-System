-- 清理上一轮导入的模拟数据（按外键依赖顺序删除；只动 @pb/@ub 之后的新数据，不碰种子与业务数据）
-- 用法：mysql ... campus_lost_found < cleanup_mock_data.sql
SET NAMES utf8mb4;
SET @pb = (SELECT MAX(id) FROM posts) - 10000;
SET @ub = (SELECT MAX(id) FROM users) - 80;

DELETE FROM moderation_actions   WHERE target_type='POST' AND target_id > @pb;
DELETE FROM disputes             WHERE claim_id IN (SELECT id FROM claims WHERE post_id > @pb);
DELETE FROM claim_messages       WHERE claim_id IN (SELECT id FROM claims WHERE post_id > @pb);
DELETE FROM handover_confirmations WHERE claim_id IN (SELECT id FROM claims WHERE post_id > @pb);
DELETE FROM claims               WHERE post_id > @pb;
DELETE FROM lost_leads           WHERE lost_post_id > @pb;
DELETE FROM posts                WHERE id > @pb;
DELETE FROM sessions             WHERE user_id > @ub;
DELETE FROM auth_identities      WHERE user_id > @ub;
DELETE FROM users                WHERE id > @ub;

SELECT 'posts remaining' k, COUNT(*) v FROM posts
UNION ALL SELECT 'users remaining', COUNT(*) FROM users;
