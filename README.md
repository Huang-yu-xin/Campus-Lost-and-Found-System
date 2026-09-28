# 校园失物招领系统 (Campus Lost & Found System)

面向校园失物招领场景的完整业务闭环系统：**发布信息 → 搜索/匹配 → 提供线索或申请认领 → 核验/沟通 → 交接 → 归还/找回 → 争议与治理**。

> 三人软件工程课程项目。学生端微信小程序 + Web 管理后台 + 单体后端。
> 本仓库以 `校园失物招领系统_Agent完整开发执行任务书.md` 为唯一功能覆盖基线。

---

## 1. 产品形态

| 端 | 技术栈 | 目录 |
|---|---|---|
| 学生端 | uni-app + Vue 3（构建目标：微信小程序） | `apps/miniapp/` |
| 管理后台 | Vue 3 + Element Plus + Vite | `apps/admin-web/` |
| 后端 | Spring Boot 单体 + MyBatis + MySQL | `server/` |

## 2. 技术版本基线（按本机实测环境锁定）

> 详细取舍见 `docs/architecture/tech-decisions.md`。后端基线为 **JDK 17 + Spring Boot 3.x**。

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17.0.18 (LTS, ms build) | 后端编译/运行 |
| Maven | 3.9.4 | 后端构建 |
| Node.js | 24.19.0 | 前端构建 |
| npm | 11.17.0 | 前端包管理 |
| MySQL | 8.0.34 | 数据库 |
| Spring Boot | 3.3.5 | 后端框架 |
| MyBatis Spring Boot Starter | 3.0.3 | ORM |
| MySQL Connector/J | 8.3.0（Boot 管理版本） | JDBC 驱动 |
| Flyway | 10.10.0 (+ flyway-mysql) | 数据库版本化迁移 |
| springdoc-openapi | 2.6.0（starter-webmvc-ui） | OpenAPI 3 契约生成 |
| Vue | 3.4.x | 前端框架 |
| Vite | 5.x | 前端构建工具 |
| Element Plus | 2.x | 后台 UI 组件库 |

> Git: 2.45.1。微信开发者工具版本待接入 AppID 时记录。

## 3. 目录结构

```text
campus-lost-found/
├── apps/
│   ├── miniapp/            # 学生端 uni-app / Vue3（微信小程序）
│   └── admin-web/          # 管理后台 Vue3 / Element Plus
├── server/                 # Spring Boot 单体后端
├── db/
│   ├── migrations/         # Flyway 版本化迁移
│   └── seeds/              # 非生产示例数据
├── tests/                  # integration / e2e / performance
├── deploy/                 # docker-compose.dev / env.example
├── docs/                   # 需求 / 架构 / API / 测试 / 运维 / 演示 / 贡献 / 阶段报告
└── README.md
```

## 4. 快速开始（开发环境）

> ⚠️ 当前进度：**已完成 P0（脚手架/文档）+ P1（需求/架构/接口契约）**。P2 最小垂直切片尚未实现，下列启动命令为脚手架级别，业务功能待 P2 后可用。

### 4.1 准备环境变量

```bash
cp deploy/env.example deploy/.env        # 按需修改数据库连接、端口等
# 后端本地机密放 server/src/main/resources/application-local.yml（已在 .gitignore 中）
```

### 4.2 数据库

```sql
CREATE DATABASE campus_lost_found DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE campus_lost_found_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci; -- 测试/恢复隔离库
```

迁移由 Flyway 在后端启动时自动执行（迁移脚本位于 `db/migrations/`，见 `docs/architecture/er-diagram.md`）。

### 4.3 后端

```bash
cd server
mvn spring-boot:run          # 默认端口 8080，API 前缀 /api/v1
# 健康检查： GET http://localhost:8080/api/v1/health
# OpenAPI 文档： http://localhost:8080/swagger-ui/index.html
```

### 4.4 管理后台

```bash
cd apps/admin-web
npm install
npm run dev
```

### 4.5 学生端小程序

```bash
cd apps/miniapp
npm install
npm run dev:mp-weixin       # 产物用微信开发者工具打开 apps/miniapp/dist/dev/mp-weixin
```

## 5. 文档索引

| 文档 | 说明 |
|---|---|
| [任务书](校园失物招领系统_Agent完整开发执行任务书.md) | 唯一功能覆盖基线 |
| [项目章程](docs/requirements/project-charter.md) | 目标、边界、角色、外部依赖、风险 |
| [责任矩阵](docs/requirements/responsibility-matrix.md) | FR/NFR → A/B/C → 优先级 → 接口/测试 |
| [SRS 需求规格](docs/requirements/SRS.md) | 六模块功能需求 + NFR + 验收 |
| [追踪矩阵](docs/requirements/traceability.md) | 需求→设计→API→代码→测试 全量追踪 |
| [架构总览](docs/architecture/overview.md) | 架构、模块边界、跨模块事务所有者 |
| [ER 图与数据字典](docs/architecture/er-diagram.md) | 数据模型、索引、约束 |
| [状态机](docs/architecture/state-machines.md) | 发布/申请/争议状态与竞态处理 |
| [技术决策](docs/architecture/tech-decisions.md) | 锁定版本与取舍 |
| [OpenAPI 契约](docs/api/openapi.yaml) | 全部 API schema/权限/错误码 |
| [错误码与约定](docs/api/conventions.md) | 统一响应、错误码、分页、鉴权 |
| [页面原型](docs/design/wireframes.md) | 小程序与后台低保真交互 |
| [风险与决策](docs/risks-and-decisions.md) | 已冻结默认值、未决事项、外部依赖 |
| [阶段报告](docs/reports/) | P0–P5 各阶段可复核交付 |

## 6. 重要边界声明

- **不接入真实校园身份认证**：仅保留内部 `CampusIdentityProvider` 扩展点，本期正式返回 `disabled`。登录态**不能证明**用户为在校师生。
- **微信登录**：仅在合法取得 AppID/密钥后启用；开发期使用受限测试登录，生产环境强制禁用测试登录。
- **规则匹配不代替认领核验**：匹配结果仅供参考，不证明物品归属。
- 安全规则由**后端强制执行**，前端隐藏不等于安全。
