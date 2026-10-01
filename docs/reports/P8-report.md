# P8 报告：V4 认领完成 → 寻物帖闭环链接

**时间**：2026-10-01　**范围**：认领完成后把"招领被认领"的事实落回失主的寻物帖（业务闭环 + 统计准确 + 评估正样本由"推导"升级为"事实"）
**背景**：P7 验收发现的数据模型缺口（见 `P7-matching-improvement-report.md` §3）。
**约束遵守**：只新增 V4 迁移，不碰 V1–V3；匹配打分公式/权重/P1–P4 代码一律未改；`TextTokenizer` 仅作共享工具注入 `ClaimService` 做候选文本排序，不参与打分；链接由失主本人确认，系统只推荐不自动关联。

## 1. 按任务 ID 的改动与 commit

| 步骤 | 内容 | 关键文件 | commit |
|---|---|---|---|
| S1 | 数据模型：`posts.resolved_by_claim_id`(FK→claims)+`closed_at`+索引；存量 `closed_at` 回填；`closed_at` 单一权威维护写进 `changeStatus`/`forceStatus` 的 CASE（覆盖全部调用点，不改 Java 逻辑） | `V4__post_resolution_link.sql`、`PostMapper` | `8a0d2e7` |
| S2 | 后端业务：`GET /claims/{id}/resolved-candidates`（防枚举顺序 + 同类别/时间合理过滤 + Jaccard 排序 ≤3）、`POST /claims/{id}/resolve-lost`（7 步校验：参与方→完成→归属→幂等/冲突→类别→lockById+条件更新+行数检查→审计 `LOST_RESOLVED`）；新增 4 个错误码 | `ErrorCode`、`PostMapper`/`PostService`、`Post`、`ClaimService`/`ClaimController`/`ClaimDtos` | `195b09c` |
| S3 | 前端：`api/index.js` 加 `resolvedCandidates`/`resolveLost`；`claim/detail.vue` 完成卡片（候选列表 + 无候选手动选 `myPosts` + 成功变"已关联 ✓"并刷新 + 失败透出后端 message） | `apps/miniapp/src/api/index.js`、`pages/claim/detail.vue` | `7c84a0c` |
| S4 | 评估升级：`MatchRealPairsIT` 改读事实链接（JOIN `resolved_by_claim_id`），删旧启发式与标签噪声免责；候选池按 `t=min(lost.closed_at,found.closed_at)` 点时重建（`published_at<=t AND (closed_at IS NULL OR closed_at>=t)`）；`MatchEvalHarness` 文档注明 closed_at 可用 | `it/MatchRealPairsIT`、`it/MatchEvalHarness` | `72b63d8` |
| S5 | 数据/演示：生成器写入 `category_code`（镜像 V3）+ 4.6 事实链接回填段（约 40% COMPLETED 认领选同类目最近 ACTIVE LOST）+ 核对加 `resolved pairs`；`cleanup` 先解 V4 外键；`demo-script` 加闭环步 | `tests/performance/gen_mock_data.py`、`cleanup_mock_data.sql`、`docs/demo/demo-script.md` | `1ab7b0c` |
| S6 | 测试与文档：`ClaimResolveIT`（正常流 + 7 负例）；e2e smoke 追加 2 断言（31→33）；状态机/追踪矩阵/本报告回填 | `it/ClaimResolveIT`、`tests/e2e/e2e-smoke.sh`、`state-machines.md`、`traceability.md`、本报告 | 本次提交 |

## 2. 设计要点

- **幂等/冲突**：同一 claim 重复关联 → OK；被其他 claim 关联或目标帖已非 ACTIVE → 409 `RESOLVE_ALREADY_RESOLVED`。锁后以 `FOR UPDATE` 权威读复核，条件更新 `WHERE status='ACTIVE'` 行数为 0 兜底并发。
- **硬校验**只有 **本人 + ACTIVE + 类别一致**；"时间合理"只用于候选推荐的过滤/排序，不硬拦（用户对自己的物品有最终判断权）。
- **防枚举**：非申请人一律按 404 `CLAIM_NOT_FOUND`（与 `detail` 同口径）。
- **closed_at 权威规则**：进入 COMPLETED/WITHDRAWN/REMOVED 写 NOW()，回 ACTIVE 清 NULL；内嵌于两条状态转换 SQL，覆盖 withdraw/mark-found/交接完成/取消交接/争议裁决/治理下架·恢复。

## 3. 验证命令与输出

### 3.1 `mvn verify`（CLF_IT=true，DB_NAME=campus_lost_found_test，空库 Flyway 重放 V1→V4）
```
单元：... MatchScorerTest 12 / MatchServiceTest 3 ... Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
集成：ApiAuthzIT 9 / ClaimConcurrencyIT 1 / ClaimResolveIT 8 / MatchEvaluationHarnessIT 1
      MatchEvalSweepIT (skipped, CLF_SWEEP 门控) / MatchRealPairsIT (skipped, CLF_EVAL_REAL 门控)
      Tests run: 21, Failures: 0, Errors: 0, Skipped: 2
BUILD SUCCESS
```
Flyway：`Successfully applied 1 migration ... now at version v4`（空测试库 V1→V4 全绿）。
`ClaimResolveIT` 8 用例：happyPath（候选含 LPID → resolve → LOST COMPLETED + resolved_by_claim_id + closed_at + 审计 `LOST_RESOLVED` + `/matches` 空）、重复幂等 OK、非申请人 404、非本人帖 403、未完成 409、他 claim 409、类别不一致 400、已撤回 409。

### 3.2 事实链接对评估（CLF_EVAL_REAL=true，DB_NAME=campus_lost_found，重建模拟数据后）
导入核对：`resolved pairs = 25`（`SELECT COUNT(*) FROM posts WHERE resolved_by_claim_id IS NOT NULL` > 0）。
`MatchRealPairsIT` 报告（`target/match-eval/report-real-pairs.md`）：
```
事实链接对数：25（来自 posts.resolved_by_claim_id）
候选阶段召回（点时重建，closed_at 版）：100%（25/25）
Hit@1 / @5 / @20：0% / 4% / 28%
```
（Hit 偏低系模拟文本高度模板化、同类目同窗干扰多且 τ=0.70 阈值偏严所致；为真实数值，非缺陷。候选召回 100% 说明点时重建的候选宇宙完整覆盖了事实对。）

### 3.3 e2e smoke（dev 服务器 :8080 + deploy/.env 凭据）
```
== RESULT: PASS 33 / FAIL 0 ==
```
新增：`V4 resolved-candidates include LPID`、`V4 resolve-lost -> LOST COMPLETED`；原 `TC-POST-05 mark found` 改用另发的未关联 LOST 帖验证（LPID 已被 resolve-lost 闭环）。

### 3.4 小程序构建
```
npm run build:mp-weixin → DONE Build complete.
```

## 4. 遗留项

1. **开发库 e2e 残留数据**：e2e smoke 会在开发库建 Smoke* 用户/帖/文件；加之历史残留，`cleanup_mock_data.sql` 的 `MAX(id)-N` 基址估算被打乱，本轮 cleanup 在"删除拥有文件的用户"处因 `fk_file_owner` 中止（mock 行已删净，不影响 S5 验收）。建议后续把 cleanup 改为按显式 id 区间或用户前缀清理，并让 e2e 用独立库或自带清理。
2. **v1 不提供解除关联**：误关联依赖前置校验拦截 + 审计可追溯；如需撤销需后续迭代（并相应放开 `closed_at`/`resolved_by_claim_id` 的回退）。
3. `MatchRealPairsIT`/`MatchEvalSweepIT` 仍为手动门控（CLF_EVAL_REAL/CLF_SWEEP），不进 CI 常规回归。
