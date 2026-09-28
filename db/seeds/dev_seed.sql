-- =====================================================================
-- 开发/演示种子数据（非生产）。在 Flyway 建表后手动执行：
--   mysql -u <user> -p campus_lost_found < db/seeds/dev_seed.sql
-- 管理员账号由后端 dev profile 的 DevSeeder 依据 deploy/.env 的 ADMIN_BOOTSTRAP_* 生成（BCrypt），此处不放明文口令。
-- 普通用户通过“测试登录”自动创建（provider=MOCK, subject=输入的用户名）。
-- 本脚本仅补充若干公开发布，便于首页/搜索/匹配演示。
-- 幂等性：重复执行会重复插入；建议在空库或清理后运行。
-- =====================================================================

-- 演示用户（与测试登录 userA/userB 对应，便于登录后即为发布者）
INSERT INTO users (nickname, campus, status, campus_verification_status)
VALUES ('测试-userA', '南湖校区', 'ACTIVE', 'UNVERIFIED'),
       ('测试-userB', '马房山校区', 'ACTIVE', 'UNVERIFIED');

SET @uA = (SELECT id FROM users WHERE nickname='测试-userA' ORDER BY id DESC LIMIT 1);
SET @uB = (SELECT id FROM users WHERE nickname='测试-userB' ORDER BY id DESC LIMIT 1);

INSERT INTO auth_identities (user_id, provider, provider_subject) VALUES
  (@uA, 'MOCK', 'userA'),
  (@uB, 'MOCK', 'userB');

-- 演示发布：一条 FOUND（可被认领）、一条 LOST（可提供线索/参与匹配）
INSERT INTO posts (publisher_id, type, title, category, public_description, campus, event_location, event_time, status, version)
VALUES
  (@uB, 'FOUND', '捡到一个黑色钱包', '钱包', '在图书馆一楼捡到黑色钱包，内有若干卡片。请联系认领。', '南湖校区', '图书馆一楼', '2026-09-25 14:00:00', 'ACTIVE', 0),
  (@uA, 'LOST',  '丢失黑色钱包',     '钱包', '9月25日下午在图书馆附近丢失黑色钱包，内有校园卡。', '南湖校区', '图书馆', '2026-09-25 12:00:00', 'ACTIVE', 0),
  (@uB, 'FOUND', '捡到一把钥匙',     '钥匙', '教三楼捡到一串钥匙，有蓝色挂饰。', '南湖校区', '教三楼', '2026-09-26 09:30:00', 'ACTIVE', 0);
