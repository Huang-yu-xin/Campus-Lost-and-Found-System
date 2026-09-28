# 成员 C 贡献记录（M5 认领与交接 / M6 沟通与争议）

> 模板。实际 PR/提交/测试由本人据实填写。

## 负责模块
- M5 `FR-CLAIM-*` / `FR-HANDOVER-*`：提交/审核/撤销、单活跃交接、双向确认、受控取消。
- M6 `FR-MSG/LEAD/DISPUTE-*`：申请内留言、寻物线索、争议发起与管理员裁决。

## 关键代码位置
- `server/.../claim/*`、`handover/*`、`message/*`、`lead/*`、`dispute/*`
- 前端：`apps/miniapp/src/pages/claim/detail.vue`（审核/确认/留言/争议）；后台 `apps/admin-web/src/views/Disputes.vue`
- 并发测试：`it/ClaimConcurrencyIT`（TC-CLAIM-03，CI 执行）。

## 需求→设计→实现→测试
| FR | 设计 | 提交/PR | 测试 | 状态 |
|---|---|---|---|---|
| FR-CLAIM-04 单活跃交接 | state-machines§4 | _(填)_ | ClaimConcurrencyIT | 🟡 已编码待 CI 跑 |
| FR-HANDOVER-01 双向确认 | state-machines§4 | _(填)_ | TC-HANDOVER-02 | ⬜ |
| FR-DISPUTE-02 裁决 | state-machines§3 | _(填)_ | TC-DISPUTE-02 | ⬜ |
| ... | | | | |

## 我的测试记录
- _(填写)_

## 交叉复测（由 A 或 B）
- _(填写)_
