# 技术决策记录 (Tech Decisions)

> 本文件记录锁定的技术版本与关键取舍。版本以**开发机实测环境**为准（见第 1 节探测结果），不编造。
> 团队一致后不得无审批随意更换架构；变更须在本文件追加记录并同步 README/CI。

## 1. 开发机环境探测结果（2026-09-28）

| 组件 | 实测 | 备注 |
|---|---|---|
| JDK（选定） | OpenJDK 17.0.18 (Microsoft build, LTS) | 路径 `C:\Users\huangyx\.jdks\ms-17.0.18` |
| JDK（机器另有） | 8u401 / 21.0.6(corretto) / 23 / 25(JBR) | 8 为原系统默认，已弃用于本项目 |
| Maven | 3.9.4 | runtime Java 需指向 JDK17 |
| Node.js / npm | 24.19.0 / 11.17.0 | |
| MySQL | 8.0.34 (Community, win64) | |
| Git | 2.45.1.windows.1 | |

> ⚠️ 系统 `JAVA_HOME` 曾指向 JDK8，团队成员须将 `JAVA_HOME` 指向 JDK17 并把 `%JAVA_HOME%\bin` 前置到 `PATH`；CI 使用 temurin 17。

## 2. 后端版本基线

| 依赖 | 版本 | 理由 |
|---|---|---|
| Spring Boot | 3.3.5 | 与 JDK17 匹配的主流稳定线；生态/文档最全；使用 `jakarta.*` 命名空间 |
| MyBatis Spring Boot Starter | 3.0.3 | 兼容 Spring Boot 3.x |
| MySQL Connector/J | 8.3.0（由 Boot 依赖管理） | 匹配 MySQL 8 |
| Flyway | 10.10.0（由 Boot 管理）+ `flyway-mysql` | 数据库版本化迁移；MySQL 8 需单独引入 `flyway-mysql` |
| springdoc-openapi | 2.6.0（`springdoc-openapi-starter-webmvc-ui`） | 由代码生成 OpenAPI 3 + Swagger UI |
| Lombok | 1.18.34（由 Boot 管理） | 精简样板代码 |
| 测试 | JUnit 5 + Spring Boot Test + Testcontainers（并发/集成） | 关键并发用真实/等价 MySQL，不用 Mock 仓储冒充 |
| 密码散列 | BCrypt（Spring Security Crypto） | 管理员密码强散列，禁明文 |

### 取舍说明
- **为何 Spring Boot 3.3 而非 2.7**：JDK 已升级到 17；3.x 是长期演进方向，`jakarta.*` 迁移在无业务代码阶段成本为零。
- **为何 Flyway 而非手工改表**：任务书 §3.1 强制数据库版本化迁移，禁止手工改表部署。
- **鉴权方案**：短期 Access Token（JWT）+ 可撤销服务端会话；具体时长为配置项，不硬编码（任务书 §7.1）。是否引入完整 Spring Security 过滤链在 P2 定，P1 先定接口契约与错误码。

## 3. 前端版本基线

| 端 | 依赖 | 版本 | 理由 |
|---|---|---|---|
| 管理后台 | Vue | 3.4.x | 任务书指定 Vue3 |
| | Vite | 5.x | 与 Node 24 兼容的主流构建工具 |
| | Element Plus | 2.x | 任务书指定后台 UI |
| | Vue Router / Pinia / Axios | 4.x / 2.x / 1.x | 路由/状态/请求 |
| 学生端 | uni-app (Vue3 + Vite) | 3.x（`@dcloudio/uni-*`） | 构建目标微信小程序 |

## 4. Mock 与真实平台的区别（关键红线）

| 能力 | 开发/测试 | 生产/正式 |
|---|---|---|
| 微信登录 | 无 AppID 时不可用；用受限测试登录代替 | 需合法 AppID/密钥；后端交换凭据 |
| 测试登录 `/auth/mock/login` | 启用（`MOCK_LOGIN_ENABLED=true`） | **强制禁用** |
| 校园身份认证 | `MockProvider`（仅测试） | `DisabledProvider`（正式默认返回 disabled） |
| 文件存储 | 受控本地目录 | 后续可抽象为对象存储 |

> 任何自填学号不视为学校认证；登录态仅证明持有系统登录态。

## 5. 待核验项（离线/环境限制）
- Spring Boot 3.3.5 / Flyway 10.10.0 / springdoc 2.6.0 的具体可下载性，在首次 `mvn` 联网构建时确认；如某补丁号不可得，取同一 minor 下最近可用补丁并回填本表。
- 微信开发者工具版本：待接入 AppID 时记录。

## 变更记录
| 日期 | 变更 | 触发 |
|---|---|---|
| 2026-09-28 | 初版：JDK8→JDK17，后端定为 Spring Boot 3.3.5 | 开发者升级 JDK；团队确认 |
