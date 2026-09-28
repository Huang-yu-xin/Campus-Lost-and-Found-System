# 成员 A 贡献记录（M1 用户与身份 / M2 后台治理与维护）

> 模板。实际 PR/提交/测试由本人据实填写，不得把 Agent 生成代码虚报为个人独立完成。

## 负责模块
- M1 `FR-AUTH-*` / `FR-USER-*`：微信/测试/管理员登录、会话、资料、校园认证扩展点(disabled)。
- M2 `FR-ADMIN/AUDIT/BACKUP-*`：信息治理、用户治理、审计日志、备份与隔离恢复。

## 关键代码位置
- `server/.../auth/*`、`user/*`、`admin/*`、`audit/*`、`backup/*`
- 前端后台：`apps/admin-web/src/views/{Posts,Users,Audit}.vue`、`Login.vue`、`layouts/AdminLayout.vue`

## 需求→设计→实现→测试
| FR | 设计 | 提交/PR | 测试 | 状态 |
|---|---|---|---|---|
| FR-AUTH-01 | SRS§3-M1 | _(填 PR)_ | _(填用例)_ | ⬜ |
| ... | | | | |

## 我的测试记录
- 命令 / 环境 / 输入 / 预期 / 实际 / 通过 / 失败日志 / 修复提交 / 回归：_(填写)_

## 交叉复测（由 B 或 C）
- _(填写复测人与结论)_
