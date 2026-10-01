# 测试执行报告 (Test Report)

> 如实记录。禁止把未执行写成"通过"（任务书 §0.1.8）。

## 1. 环境
- JDK 17.0.18 / Maven 3.9.4 / 后端 Spring Boot 3.3.5 / 本机 MySQL 8.0.34。
- 2026-09-28：取得 MySQL 凭据后，**已在真实 MySQL 上完成 Flyway 建表、端到端业务冒烟与并发集成测试**。
- 无 Docker，但已直接用本机 MySQL 执行，无需 Testcontainers。

## 2. 实际执行：单元测试（本地，2026-09-28）
命令：`cd server && mvn -B -ntp test`
结果：**BUILD SUCCESS，Tests run: 14, Failures: 0, Errors: 0, Skipped: 0**

| 测试类 | 数量 | 覆盖 |
|---|---|---|
| `match/MatchScorerTest` | 4 | 完美匹配高分、拾取早于丢失降权、缺时间说明、类别不同 0 分；分数可复算 |
| `file/FileTypeSniffTest` | 5 | JPEG/PNG/WEBP 识别、伪装图片/改名文本被拒（NFR-SEC-04） |
| `common/enums/ClaimStatusTest` | 3 | 终态/有效态判定；不可既终态又有效 |
| `common/web/ApiResponseTest` | 2 | 统一响应封装 |

## 3. 前端构建验证（本地）
| 端 | 命令 | 结果 |
|---|---|---|
| 小程序 | `npm run build:mp-weixin` | ✅ Build complete，AppID 已入产物 |
| 管理后台 | `npm run build` | ✅ 1672 modules transformed，built |

## 4. 真实 DB 端到端冒烟（2026-09-28，已执行，活链路 curl）

> ⚠️ 本表为早期手动冒烟记录，无原始输出存档；已被 `tests/e2e/e2e-smoke.sh` 自动化取代，当前有效证据见 §8 整改验证。

后端 `mvn spring-boot:run`（dev，DB=campus_lost_found，Flyway 建表成功），健康检查 200。经 API 实测：

| 用例 | 场景 | 实际结果 | 判定 |
|---|---|---|---|
| E2E 主流程 | 登录→发布FOUND→列表可见→认领→接受→双方确认 | post ACTIVE→HANDOVER→COMPLETED；claim→COMPLETED | ✅ |
| TC-CLAIM-01 | userB 认领自己的 FOUND | `SELF_CLAIM_FORBIDDEN` | ✅ |
| TC-CLAIM-02 | userA 重复认领 | `ACTIVE_CLAIM_EXISTS` | ✅ |
| TC-HANDOVER-01 | 仅一方确认 | 仍 `WAITING_HANDOVER` | ✅ |
| TC-HANDOVER-02 | 双方确认无争议 | 一次 `COMPLETED` | ✅ |
| TC-DISPUTE-01 | WAITING 期发起争议后确认 | `HANDOVER_PAUSED_BY_DISPUTE` | ✅ |
| 争议裁决 | 管理员 CONTINUE 后再确认 | 恢复 `WAITING_HANDOVER` | ✅ |
| TC-AUTH-02 | 普通用户访问 `/admin/posts` | HTTP 403 | ✅ |
| TC-ADMIN-01 | 管理员下架发布 | 公开详情返回 404 | ✅ |
| TC-MATCH-01 | LOST 对 ACTIVE FOUND 匹配 | score 0.9352，原因：类别一致/地点100%/时间100%/关键词57%；含"仅供参考"声明 | ✅ |
| 管理员登录 | bootstrap admin | role=ADMIN | ✅ |

## 5. 并发集成测试（真实 MySQL，已执行）
命令：`CLF_IT=true DB_NAME=campus_lost_found_test SPRING_PROFILES_ACTIVE=ci mvn -Dtest=ClaimConcurrencyIT test`
结果：**Tests run: 1, Failures: 0, Errors: 0；BUILD SUCCESS**
- TC-CLAIM-03：两线程并发接受同一 FOUND 的两个申请 → 恰好一个进入 WAITING_HANDOVER，另一个冲突失败，post=HANDOVER。**单活跃交接不变量在真实行锁 + 生成列唯一索引下成立。**

## 6. 待执行（仍阻塞于外部条件）
| 用例 | 说明 |
|---|---|
| 性能 NFR-PERF-01 | 需生成 1万发布数据集实测 P95（未测不写达标） |
| 微信真机 | 需合法 HTTPS 域名（外部条件） |
| 备份隔离恢复 TC-BACKUP-01 | 图片备份已实现；DB 导出+隔离恢复按 ops 脚本执行并回填 |
| 其余 API 负例细化（TC-FILE-01 私密文件枚举等） | 建议补 MockMvc IT，已具备 DB 环境 |

## 7. 缺陷与修复
- 发现并修复：`MatchScorer` 双构造器导致 Spring 无法实例化（`No default constructor found`）→ 在注入构造器加 `@Autowired`，重启后上下文正常。（提交见对应 commit）

## 8. 整改验证（P6，2026-09-29）
整改任务 A/B/C/D 组完成后的实测：

| 命令 | 结果 |
|---|---|
| `mvn test`（单元） | **19 passed**（含 B7/B8 StartupSecurityValidatorTest 5 项）BUILD SUCCESS |
| `CLF_IT=true DB_NAME=campus_lost_found_test mvn verify`（集成，failsafe） | **单测 19 + 集成 10（ApiAuthzIT 9 + ClaimConcurrencyIT 1）全绿**，BUILD SUCCESS（2026-09-29 收尾轮） |
| `tests/e2e/e2e-smoke.sh`（对运行中 dev 服务器） | **PASS 31 / FAIL 0**（2026-09-29 收尾轮复跑） |

ApiAuthzIT 覆盖：TC-AUTH-01(用户→后台403)、未登录写401、TC-CLAIM-01(自认领)、TC-POST-02(编辑他人403 + 有效申请后 POST_EDIT_LOCKED)、陌生人读申请404、**TC-LEAD-01(第三方读他人线索404 + 提交者/发布者可读)**、TC-ADMIN-02(受限用户写403)、B5(未知路由404/非法JSON400)、B3(logout 撤销会话)。
e2e-smoke 覆盖：登录/权限/发布/搜索/匹配(含 reasons)/自认领/重复认领/陌生人404/审核→HANDOVER/留言隔离/收到申请聚合/争议暂停/管理员受理+裁决/双向确认→COMPLETED/mark-found/审计过滤(含 CLAIM_REVIEW)/受限用户拒写(含留言 B2)/私密文件陌生人404+owner200/未知路由404/refresh+logout。

> 整改中发现并修复的脚本/配置问题（非后端逻辑缺陷）已在 `docs/reports/P6-remediation-report.md` 登记。

## 9. 性能测试（NFR-PERF-01，2026-10-01 实测）

**数据集**：`tests/performance/gen_mock_data.py` 生成（种子 20260929）并导入开发库，导入后核对计数全部一致：

| 表 | 条数 | 说明 |
|---|---|---|
| posts | **10,000** | LOST 4,668 / FOUND 5,332；ACTIVE 9,023，其余状态与发布时长关联分布 |
| users | 80 | 模拟昵称（全库昵称零重复），campus 按南湖/马房山/余家头加权 |
| claims | 2,428 | 与帖子状态联动（COMPLETED 申请 181 条含双向确认 362 条；HANDOVER 帖每帖唯一 WAITING_HANDOVER，靠生成列唯一索引兜底） |
| lost_leads / claim_messages / moderation_actions | 3,040 / 834 / 137 | 文案贴合校园场景；REMOVED 帖配套治理记录；争议 2 条（OPEN/RESOLVED 各一） |

数据真实性设计：校区-地点联动（南湖/马房山/余家头各自地点池）、类别加权（雨伞/校园卡/钥匙/耳机/水杯…）、事件时间 120 天内指数衰减且与发布时间分离、时段峰值（早八/午间/晚自习）、FOUND 描述含隐私提醒句、申请人/线索人排除发布者本人（约束自认领）。

**测试环境**：本机 Windows、MySQL 8.0.34（同机）、JDK 17.0.18、Spring Boot dev profile（内嵌 Tomcat 默认配置，未调优）。
**方法**：`tests/performance/perf_test.py`，20 并发 keep-alive，预热 200 + 测量 2,000 请求；场景混合 55% 公开列表分页（page 1–500）/ 30% 组合搜索（关键词+类型+类别）/ 15% 匹配候选（匹配为重查询，单次加载约 5,000 候选行打分）。

**结果：2,000/2,000 成功，0 失败，RPS 108.4**

| 指标 | 实测值 |
|---|---|
| mean | 158.9 ms |
| P50 | 120.9 ms |
| P90 | 362.4 ms |
| **P95** | **452.4 ms** |
| P99 | 535.7 ms |
| max | 677.4 ms |

**NFR-PERF-01 验收目标（1 万发布、20 并发，列表 P95 ≤ 2s）：PASS（P95=452.4ms）**。
1 万条数据集上复跑 `tests/e2e/e2e-smoke.sh`：**PASS 31 / FAIL 0**，无回归。

**附加证据与备注**：
- `EXPLAIN` 公开列表查询命中 `idx_post_public_list`（ref=const,const，rows≈4,935，filesort）——10k 规模可接受。
- 过载拆解（`perf_breakdown.py`，60 并发=3 场景×20 线程同时压，3 倍于 NFR 场景）：list P95=914ms、search P95=903ms、matches P95≈2,976ms。**匹配是当前最重查询**（每请求加载全部候选打分）；NFR 的 20 并发场景不受影响，若未来提高负载目标，建议把候选筛选下推 SQL（LIMIT + 预过滤）。
- `mock_data.sql`（3.8MB）不入库（已加 .gitignore），可由生成器一键重现；脚本已入库 `tests/performance/`。

## 10. 匹配算法改进与评估（P7，2026-10-01）

改进 P1 召回截断 / P2 N+1 / P3 token / P4 类别归一 / P6 校区门控 + Stage2 评估体系与调参，全程保持可解释规则打分。

| 指标（合成评估集 200 对+2400 干扰帖） | 改进前 | 改进后 |
|---|---|---|
| 候选阶段召回 | 9.0%（"最新100条"截断实证） | **100%**（事件时间窗多臂） |
| Hit@1 / MRR | 8.5% / 0.088 | **79.0% / 0.887** |
| 难负/随机负误报率 | 100% / 58.4% | **27.4% / 0.3%** |
| 匹配接口 60 并发 P95 | ~2,976ms | 与列表同量级；整体混合场景 P95 312.0ms（20 并发，2000/2000 成功） |

- 新默认参数：wC=0.40 wL=0.25 wT=0.30 wK=0.05、窗口 14 天、minScore=0.70（依据 `match-eval/report-sweep.md`，Top5 配置测试集 Hit@1 全部 76.3%，τ 按保留率优先取保守档）。
- 评估门禁进入 CI：`MatchEvaluationHarnessIT` 断言候选召回≥95%、Hit@20≥90%。
- 真实推导对评估发现数据模型缺口（无 lost↔found 链接），结论与建议见 `match-eval/report-real-pairs.md` 与 `docs/reports/P7-matching-improvement-report.md`。
- e2e 冒烟在 V2/V3 迁移后的开发库复跑：PASS 31 / FAIL 0。

## 11. 仍待执行（外部条件）
微信真机（合法 HTTPS 域名）、备份隔离恢复实跑（离线脚本）。未执行项一律标"待验证"，不写"已达标"。
