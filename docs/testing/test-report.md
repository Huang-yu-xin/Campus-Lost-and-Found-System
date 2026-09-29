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
| `CLF_IT=true DB_NAME=campus_lost_found_test mvn verify`（集成，failsafe） | **单测 19 + 集成 9（ApiAuthzIT 8 + ClaimConcurrencyIT 1）全绿**，BUILD SUCCESS |
| `tests/e2e/e2e-smoke.sh`（对运行中 dev 服务器） | **PASS 31 / FAIL 0** |

ApiAuthzIT 覆盖：TC-AUTH-01(用户→后台403)、未登录写401、TC-CLAIM-01(自认领)、TC-POST-02(编辑他人403 + 有效申请后 POST_EDIT_LOCKED)、陌生人读申请404、TC-ADMIN-02(受限用户写403)、B5(未知路由404/非法JSON400)、B3(logout 撤销会话)。
e2e-smoke 覆盖：登录/权限/发布/搜索/匹配(含 reasons)/自认领/重复认领/陌生人404/审核→HANDOVER/留言隔离/收到申请聚合/争议暂停/管理员受理+裁决/双向确认→COMPLETED/mark-found/审计过滤(含 CLAIM_REVIEW)/受限用户拒写(含留言 B2)/私密文件陌生人404+owner200/未知路由404/refresh+logout。

> 整改中发现并修复的脚本/配置问题（非后端逻辑缺陷）已在 `docs/reports/P6-remediation-report.md` 登记。

## 9. 仍待执行（外部条件）
性能 P95（造 1 万数据集）、微信真机（合法 HTTPS 域名）、备份隔离恢复实跑（离线脚本）。未执行项一律标"待验证"，不写"已达标"。
