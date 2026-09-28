# P1 阶段报告：需求冻结、架构与契约

**日期**：2026-09-28
**阶段目标**：输出 SRS、架构与状态图、ER、页面原型、OpenAPI 初版与迁移草案，冻结未决业务默认值。

## 1. 完成事项

| 项 | 产出 | 状态 |
|---|---|---|
| 软件需求规格 SRS | `docs/requirements/SRS.md`（六模块 FR + NFR + 业务规则 + 变更流程） | ✅ |
| 需求追踪矩阵 | `docs/requirements/traceability.md`（FR/NFR→设计→API→模块→TC→状态） | ✅ |
| 架构总览 | `docs/architecture/overview.md`（分层、模块边界、跨模块事务所有者、鉴权、文件安全） | ✅ |
| 状态机与并发 | `docs/architecture/state-machines.md`（发布/申请/争议状态 + 竞态方案 + 时序图） | ✅ |
| ER 图与数据字典 | `docs/architecture/er-diagram.md`（Mermaid ER + 索引 + 隐私规则） | ✅ |
| API 公共约定 | `docs/api/conventions.md`（统一响应、错误码、分页、鉴权、幂等） | ✅ |
| OpenAPI 初版 | `docs/api/openapi.yaml`（40 路径，覆盖 §7.2 全部端点，无语法 tab） | ✅ |
| 页面原型 | `docs/design/wireframes.md`（小程序 10 页 + 后台 5 页，重点区分 LOST/FOUND） | ✅ |
| 数据库迁移草案 | `server/src/main/resources/db/migration/V1__baseline.sql`（19 张表 + 唯一约束 + 索引） | ✅ |
| 冻结默认值 | `docs/risks-and-decisions.md` D-01~D-16 | ✅ |

## 2. 关键冻结决策（摘要，详见 risks-and-decisions.md）
- 不接学校认证（正式 disabled）；游客仅公开浏览；证明文字必填、图片可选。
- 匹配权重 `0.40C/0.25L/0.20T/0.15K`。
- 双方确认 + 无 OPEN 争议才完成；争议 OPEN 派生暂停。
- 单活跃交接唯一性：**锁父发布 + 条件更新 + 生成列唯一索引**（V1 已含 `uk_claim_active_handover` 等）。
- 无权限私密对象统一 404；ID 服务端生成不可枚举；时间存 UTC 输出 ISO 8601。

## 3. 数据库迁移草案要点
V1 覆盖任务书 §6 全部逻辑实体：users, auth_identities, admin_credentials, sessions, files, posts, post_images, claims(+2 生成列唯一约束), claim_evidence_files, handover_confirmations, claim_messages, lost_leads, lead_evidence_files, disputes(+1 生成列唯一约束), dispute_evidence_files, moderation_actions, audit_logs, backup_records, idempotency_records。
> 迁移草案将在 P2/P3 按实现以新增 Vx 迁移演进，不回改历史。

## 4. 实际运行结果
- OpenAPI 语法自检：无制表符、40 个路径条目、456 行。
- 迁移脚本尚**未**对真实数据库执行（需数据库与后端启动，属 P2 门禁）；本阶段为草案。
- Mermaid 图为文本源，随文档渲染。

## 5. 未完成 / 外部阻塞
- 微信登录端到端链路：待 AppID（外部阻塞）。
- 迁移对真实 MySQL 的重放验证：P2 执行。
- 性能/备份恢复/真机：分别在 P4 执行。

## 6. 阶段门禁核对
- ✅ 六模块 需求—接口—用例 可追踪（traceability.md）。
- ✅ 所有跨模块状态变更有明确事务所有者（overview.md#4）。
- ✅ 业务默认值冻结，不再依赖开发时临时决定。

## 7. 贡献者 / 审核者
本阶段文档由开发 Agent 生成；实际作者/审核由团队据实填写。

## 8. 下阶段（P2，待验收后启动）
基础平台 + 最小垂直切片：受限测试登录 → 上传公开图片 → 发布 FOUND → 列表 → 详情；打通后端+MySQL+Flyway，生成种子数据。
