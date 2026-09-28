# P2 阶段报告：基础平台与最小垂直切片

**日期**：2026-09-28
**阶段目标**：搭建后端基础设施与最小可运行链路（测试登录 → 上传图片 → 发布 → 列表 → 详情）。

## 1. 完成事项

| 项 | 产出 | 状态 |
|---|---|---|
| 统一响应/异常/错误码 | `common/web/ApiResponse`,`GlobalExceptionHandler`,`common/error/ErrorCode`(40+ 码) | ✅ |
| 鉴权基础设施 | JWT 签发/校验 `JwtService`、`AuthContext`(ThreadLocal)、`AuthInterceptor`、`WebConfig` | ✅ |
| 配置绑定 | `AppProperties`（auth/mock/campus/wechat/file/match）+ `application.yml`/`-dev`/`-local` | ✅ |
| 密码散列 | BCrypt（`SecurityBeans`） | ✅ |
| 受控文件存储 | `FileService`（magic-byte 真类型校验、随机名、用途/归属、鉴权下载、私密访问 checker 扩展点） | ✅ |
| M1 登录 | 测试登录、微信登录（code2session）、管理员登录、校园能力 disabled、用户资料 | ✅ |
| M3 发布/浏览 | 发布 LOST/FOUND、公开列表、详情、我的发布、编辑（有效申请锁定）、撤回、标记找回 | ✅ |
| 前端最小链路 | 小程序 login/index/detail/publish/mine + 请求封装；AppID 已接入 | ✅ |
| 种子数据 | `db/seeds/dev_seed.sql` + dev `DevSeeder`（管理员 BCrypt，读 env 强口令） | ✅ |

## 2. 实际运行结果
- 后端 `mvn compile` / `mvn test`：**BUILD SUCCESS，14 个单元测试全过**（匹配打分、文件类型嗅探、状态枚举、响应封装）。
- 小程序 `npm run build:mp-weixin`：**Build complete**，产物 `apps/miniapp/dist/build/mp-weixin`，AppID `wx7c29e056e8b06f1a` 已写入产物。

## 3. 门禁验证（2026-09-28，取得 MySQL 凭据后已完成）
- ✅ 后端连本机 MySQL 启动成功，Flyway V1 迁移在真实库重放成功（19 表 + 生成列唯一索引全部建立）。
- ✅ 最小垂直切片"真实 API 持久化"验证通过：测试登录 → 发布 FOUND → 列表可见 → 详情（详见 test-report §4）。
- ✅ DevSeeder 生成管理员，管理员登录 role=ADMIN。

## 4. 复现方式
```sql
CREATE DATABASE campus_lost_found DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE campus_lost_found_test DEFAULT CHARACTER SET utf8mb4;
-- 建议建专用账号（示例）：
CREATE USER 'clf_app'@'localhost' IDENTIFIED BY '<强口令>';
GRANT ALL PRIVILEGES ON campus_lost_found.* TO 'clf_app'@'localhost';
GRANT ALL PRIVILEGES ON campus_lost_found_test.* TO 'clf_app'@'localhost';
```
把账号口令填入 `deploy/.env`（DB_USERNAME/DB_PASSWORD），`cd server && mvn spring-boot:run` 即启动，Flyway 自动建表。

## 5. 贡献者
Agent 生成骨架与实现；实际提交作者/审核由团队据实填写。
