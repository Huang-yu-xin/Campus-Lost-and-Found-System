# 部署与本地运行 (Deployment)

> **生产部署必须显式设置 SPRING_PROFILES_ACTIVE=prod，否则开发默认会绕过生产启动守卫。禁止生产混用 dev/ci/test；显式 prod 的弱密钥或 mock 登录会拒绝启动。**

所有新写入时间采用UTC；数据库连接设置会话 time_zone=+00:00，API输出带Z。既有无时区历史数据不能仅凭时间值辨认来源：上线前按数据来源核对，确认原为Asia/Shanghai的记录后再在离线维护窗口转换，不能不分来源批量减8小时。

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

显式prod与dev/test/ci混用时直接拒绝启动，即使密钥和mock设置本身安全，也不运行开发初始化逻辑。
