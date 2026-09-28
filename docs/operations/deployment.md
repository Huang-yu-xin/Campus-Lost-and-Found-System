# 部署与本地运行 (Deployment)

## 1. 前置
JDK 17 / Maven 3.9.4 / Node 24 / MySQL 8 / 微信开发者工具。确认 `JAVA_HOME` 指向 JDK17。

## 2. 数据库
```sql
CREATE DATABASE campus_lost_found DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE campus_lost_found_test DEFAULT CHARACTER SET utf8mb4;
-- 建议专用账号
CREATE USER 'clf_app'@'localhost' IDENTIFIED BY '<强口令>';
GRANT ALL PRIVILEGES ON campus_lost_found.* TO 'clf_app'@'localhost';
GRANT ALL PRIVILEGES ON campus_lost_found_test.* TO 'clf_app'@'localhost';
FLUSH PRIVILEGES;
```

## 3. 配置
- 复制 `deploy/env.example` → `deploy/.env`，填 `DB_USERNAME/DB_PASSWORD`、`JWT_SECRET`、`ADMIN_BOOTSTRAP_PASSWORD`。
- 微信机密放 `server/src/main/resources/application-local.yml`（已 gitignore）：`app.wechat.appid/appsecret`。
- 后端读取环境变量：可用 `set -a; . deploy/.env; set +a`（Git Bash）导出，或在 IDE 运行配置里填。

## 4. 后端启动
```bash
cd server
mvn spring-boot:run          # dev profile；Flyway 自动建表；DevSeeder 生成管理员
```
- 健康检查：`GET http://localhost:8080/api/v1/health`
- OpenAPI UI：`http://localhost:8080/api/v1/swagger-ui/index.html`
- 演示数据（可选）：`mysql -u clf_app -p campus_lost_found < db/seeds/dev_seed.sql`

## 5. 管理后台
```bash
cd apps/admin-web
npm install && npm run dev    # http://localhost:5173 ，/api 代理到 8080
```
用 `deploy/.env` 的 `ADMIN_BOOTSTRAP_*` 账号登录。

## 6. 学生端小程序
```bash
cd apps/miniapp
npm install
npm run dev:mp-weixin        # 用微信开发者工具导入 dist/dev/mp-weixin
```
- AppID 已配置（manifest.json）。
- 开发工具需在"详情 → 本地设置"勾选"不校验合法域名"以访问 localhost；真机/正式须在微信后台配置 request 合法 HTTPS 域名。

## 7. 生产/正式发布外部条件（本期不承诺）
- 微信小程序主体资质、服务类目、合法 HTTPS 域名、隐私政策与平台审核。
- 生产必须：`MOCK_LOGIN_ENABLED=false`、强 `JWT_SECRET`、强管理员口令、机密仅经环境注入。
