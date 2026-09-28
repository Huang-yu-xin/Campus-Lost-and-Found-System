# 测试计划 (Test Plan)

## 1. 分层
- 单元测试（无 DB）：匹配打分、状态枚举守卫、文件类型嗅探、权限判断、错误码。
- DB 集成/并发测试：真实 MySQL，验证迁移、事务锁、生成列唯一约束、回滚。**不用 Mock 仓储替代并发**。
- API 契约/集成：MockMvc + 真实 DB，覆盖 401/403/404/409、上传下载权限、分页排序。
- 前端：构建 + 关键页面手测；小程序开发者工具编译。
- E2E：两普通用户 + 一管理员走通主流程/异常/争议。
- 非功能：隔离恢复、安全负例、性能。

## 2. 关键用例（对应任务书 §10.2）
| 用例 | 场景 | 预期 | 载体 |
|---|---|---|---|
| TC-AUTH-01 | 测试登录访问后台 | 403；生产禁用 mock | API IT |
| TC-AUTH-02 | 改请求 role/userId | 不提权 | API IT（身份取自会话） |
| TC-POST-01 | 发布 LOST/FOUND 带事件时间 | 列表正确，事件≠发布时间 | API IT |
| TC-POST-02 | 编辑他人/有效申请后改核心字段 | 拒绝 / POST_EDIT_LOCKED | API IT |
| TC-FILE-01 | 猜私密证据文件 ID | 无法获取（404） | API IT |
| TC-SEARCH-01 | 多条件+分页+同分稳定 | 数据/total 正确 | API IT |
| TC-MATCH-01 | 近似地点/合理时间 | 可解释分数；下架/完成不入选 | 单元(MatchScorerTest) + API IT |
| TC-LEAD-01 | 对 LOST 提交线索，第三方读取 | 双方可见，第三方拒绝 | API IT |
| TC-CLAIM-01 | 认领自己的 FOUND | 403 SELF_CLAIM_FORBIDDEN | API IT |
| TC-CLAIM-02 | 重复提交认领 | 至多一个有效/409 | API IT（唯一索引） |
| TC-CLAIM-03 | 两申请并发接受 | 至多一个 WAITING_HANDOVER | **ClaimConcurrencyIT（已编码）** |
| TC-CLAIM-04 | 撤回后审核/已归还再申请 | 全拒绝，历史保留 | API IT |
| TC-HANDOVER-01 | 仅一方确认 | 仍 WAITING_HANDOVER | API IT |
| TC-HANDOVER-02 | 双方确认无争议 | 仅一次 COMPLETED | API IT |
| TC-DISPUTE-01 | OPEN 争议时确认 | 暂停 HANDOVER_PAUSED_BY_DISPUTE | API IT |
| TC-DISPUTE-02 | 无权管理员猜争议 | 无法读证据 | API IT |
| TC-ADMIN-01 | 下架有效信息 | 公开/匹配消失，历史保留 | API IT |
| TC-ADMIN-02 | 受限用户仍调写接口 | 服务端拒绝 USER_RESTRICTED | API IT |
| TC-BACKUP-01 | 联合备份+隔离恢复 | 校验通过，不影响原库 | 离线脚本 |
| TC-E2E-01/02 | 主流程 / 争议裁决 | 端到端一致 | 手测 + 脚本 |

## 3. 执行方式
- 本地单元：`cd server && mvn test`
- DB 集成/并发：`CLF_IT=true`（+ DB_* 环境）`mvn verify`；CI 自动执行。
- 前端：`npm run build`（两端）+ 小程序开发者工具导入。

## 4. 证据留存
每用例记录：命令、环境、DB 版本、输入/种子、预期、实际、通过/失败、失败日志、修复提交、回归结果。汇总入 `test-report.md`。
