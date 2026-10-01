-- 首次启动容器时自动创建隔离测试/恢复库（主库由 MYSQL_DATABASE 创建）。
CREATE DATABASE IF NOT EXISTS campus_lost_found_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
