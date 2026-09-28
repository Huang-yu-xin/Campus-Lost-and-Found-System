# 成员 B 贡献记录（M3 发布与浏览 / M4 检索与匹配）

> 模板。实际 PR/提交/测试由本人据实填写。

## 负责模块
- M3 `FR-POST-*` / `FR-FILE-*`：发布/编辑/撤回/完成、安全上传、公开列表与详情、我的发布。
- M4 `FR-SEARCH-*` / `FR-MATCH-*`：组合筛选、双向候选匹配、可解释评分与规则验证。

## 关键代码位置
- `server/.../post/*`、`file/*`、`match/*`
- 前端：`apps/miniapp/src/pages/{index,detail,publish}/`
- 匹配打分：`match/MatchScorer.java`（单测 `MatchScorerTest`）；上传嗅探单测 `FileTypeSniffTest`。

## 需求→设计→实现→测试
| FR | 设计 | 提交/PR | 测试 | 状态 |
|---|---|---|---|---|
| FR-POST-01/02 | SRS§3-M3 | _(填)_ | TC-POST-01 | ⬜ |
| FR-MATCH-02 | SRS§3-M4 | _(填)_ | MatchScorerTest(4) | 🟡 单测通过 |
| ... | | | | |

## 我的测试记录
- _(填写)_

## 交叉复测（由 A 或 C）
- _(填写)_
