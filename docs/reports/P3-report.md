# P3 阶段报告：六模块全部业务功能

**日期**：2026-09-28
**阶段目标**：实现六模块全部本期必做业务；跨模块写操作有事务边界；前端页面对接。

## 1. 后端实现（全部编译通过）

| 模块 | 负责人 | 实现要点 | 关键类 |
|---|---|---|---|
| M1 身份 | A | 微信/测试/管理员登录、会话、资料、校园能力 disabled、用户限制校验 | auth/*, user/* |
| M2 治理 | A | 信息下架/恢复（保留下架前状态、关闭关联申请）、用户限制/解除、审计日志、备份（图片目录+清单+校验和） | admin/*, audit/*, backup/* |
| M3 发布 | B | LOST/FOUND 发布、编辑锁定、撤回、标记找回、公开列表/详情、我的发布；安全上传（真类型校验/随机名/鉴权下载） | post/*, file/* |
| M4 匹配 | B | 组合筛选、双向候选匹配、可解释评分 S=0.40C+0.25L+0.20T+0.15K、同分稳定排序、异常时间降权 | search(合并到 post)/match/* |
| M5 认领 | C | 提交/审核/撤销、单活跃交接（锁父发布+条件更新+唯一索引）、双向确认、受控取消 | claim/*, handover/* |
| M6 沟通/线索/争议 | C | 申请内留言、寻物线索、争议发起、管理员裁决（CONTINUE/TERMINATE_REOPEN/CLOSE）、私密证据分级鉴权 | message/*, lead/*, dispute/* |

## 2. 跨模块一致性（事务所有者，实际落地）
- 接受认领：`ClaimService.review(ACCEPT)` 在事务内 `postService.lockForUpdate` → 校验 ACTIVE → `claims.accept` → `post ACTIVE→HANDOVER` → 关闭其他 PENDING；`DuplicateKeyException`→`CLAIM_ACCEPT_CONFLICT`。
- 双向确认完成：`confirmHandover` 幂等插入确认 → 两方齐全且无 OPEN 争议 → `claim→COMPLETED` + `post HANDOVER→COMPLETED`。
- 争议：`DisputeGuardImpl` 向 M5 提供 `hasOpenDispute` 派生暂停；裁决在事务内同步 claim/post 状态并写审计。
- 模块解耦：`PostClaimGuard`/`ClaimDisputeGuard`/`FilePrivateAccessChecker` 三个扩展点避免反向依赖，保证私密证据逐记录鉴权。

## 3. 前端实现
- 学生端（uni-app）：登录（测试+微信）、首页列表/搜索/类型切换、详情（认领/线索/匹配/撤回/标记找回）、发布、申请详情（审核/确认/留言/争议）、我的（发布/申请/线索/退出）。`npm run build:mp-weixin` 通过。
- 管理后台（Vue3+Element Plus）：登录、信息治理（下架/恢复）、用户治理（限制/解除）、争议处理（查看证据+裁决）、审计与维护（日志+备份）。路由守卫 + 后端逐资源鉴权。`npm run build` 通过（1672 模块）。

## 4. 实际运行结果
- `mvn test`：14 单元测试通过。
- 两端 `build`：均成功。
- 并发/集成测试：编写完成（`it/ClaimConcurrencyIT` TC-CLAIM-03），**因本地无 DB 跳过**，已挂 CI（MySQL service + `CLF_IT=true`）运行。

## 5. 端到端验证（2026-09-28，取得 MySQL 凭据后已执行）
后端已在真实 MySQL 启动（Flyway 建表成功）。经活链路 curl 实测通过：主流程（发布 FOUND→认领→接受→双方确认→COMPLETED）、SELF_CLAIM_FORBIDDEN、ACTIVE_CLAIM_EXISTS、单方确认不完成、争议暂停(HANDOVER_PAUSED_BY_DISPUTE)与裁决恢复、管理员鉴权(普通用户 403)、下架后 404、匹配可解释评分。详见 `docs/testing/test-report.md §4`。
并发 TC-CLAIM-03（真实 DB）通过（§5）。

## 6. 未验证（外部条件）
- 微信真实登录端到端：需真机 + 合法域名（开发工具可编译预览）。
- 性能/备份隔离恢复：见 P4。

## 7. 阶段门禁核对
- ✅ 六模块业务代码完成，无伪功能按钮（前端按钮均调用真实 API）。
- ✅ 跨模块写操作有明确事务边界与并发保护（真实 DB 并发测试通过）。
- ✅ 在开发环境实际运行并端到端跑通主流程/争议/治理。
