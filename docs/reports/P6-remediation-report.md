# P6 整改报告（验收整改与补全）

**日期**：2026-09-29
**依据**：《校园失物招领系统_验收整改与补全任务书.md》（2026-09-28 验收产出）
**环境**：JDK 17.0.18 / Spring Boot 3.3.5 / MySQL 8.0.34（dev 库 campus_lost_found，隔离测试库 campus_lost_found_test）

## 0. 总体结果（2026-09-29 收尾轮）
- 后端 `mvn test`：**19 单元测试全绿**。
- 后端 `CLF_IT=true DB_NAME=campus_lost_found_test mvn verify`：**单测 19 + 集成 10（ApiAuthzIT 9 + ClaimConcurrencyIT 1）全绿，BUILD SUCCESS**（新增 TC-LEAD-01 用例后）。
- `tests/e2e/e2e-smoke.sh`：**PASS 31 / FAIL 0**（对运行中的 dev 服务器，收尾轮复跑，原始输出见 §10）。
- 小程序 `npm run build:mp-weixin`：成功。管理后台 `npm run build`：成功。
- 机密（AppSecret / DB 密码）仅在 gitignored 的 `deploy/.env` 与 `application-local.yml`，`git status` 确认未入库。

## 1. B 组：后端小修

| ID | 改动文件 | 验证 | 状态 |
|---|---|---|---|
| B1 认领审核补审计 | `claim/ClaimService.java` | e2e "B1 claim-review audit"；ApiAuthzIT | ✅ |
| B2 留言拦截受限用户 | `message/MessageService.java` | e2e "B2 restricted participant message" | ✅ |
| B3 会话撤销 + refresh | `auth/Session*.java`,`AuthService`,`AuthController`,`common/security/AuthInterceptor` | e2e refresh/logout；ApiAuthzIT logout_revokesSession | ✅ |
| B4 分页 total | `post/PostMapper`,`admin/AdminGovernanceService`,`backup/*` | 实测 admin/posts total=14 | ✅ |
| B5 错误码 404/400 | `common/web/GlobalExceptionHandler` | e2e/ApiAuthzIT 未知路由404、非法JSON400 | ✅ |
| B6 裁决行数检查 | `dispute/DisputeService` | 代码 + 事务回滚审阅 | ✅ |
| B7 生产禁用 mock | `common/config/StartupSecurityValidator`,`application-prod.yml` | StartupSecurityValidatorTest 5 项 | ✅ |
| B8 JWT 弱密钥拒绝 | `StartupSecurityValidator`,`common/security/JwtService` | 单测 | ✅ |
| B9 争议受理端点 | `dispute/DisputeMapper/Service/AdminDisputeController` | e2e "B9 admin assign" | ✅ |
| B10 收到申请/线索聚合 | `claim/*`,`lead/*`,`MyClaimsController`,`LeadController` | e2e "B10 received-claims" | ✅ |

## 2. D1：CI failsafe
- `server/pom.xml` 加 `maven-failsafe-plugin`（`**/*IT.java` 绑定 verify），surefire 排除 `*IT`。
- 验证：`mvn verify` 输出包含 `ApiAuthzIT` 与 `ClaimConcurrencyIT` 且全绿；`mvn test` 仅跑 19 单测不触发 IT。

## 3. A 组：小程序补全（`apps/miniapp`）
| ID | 说明 | 状态 |
|---|---|---|
| A1 | 图片上传/展示全链路（uploadFile/loadPrivateImage；发布公开图、认领/线索私密证据、详情预览） | ✅ build |
| A2 | 搜索筛选页 `pages/search`（关键词/类型/类别/校区/日期 + 触底分页） | ✅ |
| A3 | 我的页"收到申请/收到线索"tab + 线索处理三态 + `pages/lead/detail` | ✅ |
| A4 | 发布编辑模式（?id= 回填 + PATCH + 锁定错误透出） | ✅ |
| A5 | 资料编辑 + 校园认证状态(真实接口) + 状态汉化(utils/labels) | ✅ |
| A6 | 申请详情：撤销/取消交接/争议进度/暂停防御/时间线/证据图片 | ✅ |
| A7 | 首页+我的页触底分页 | ✅ |
> 配套后端新增 `GET /leads/{leadId}`（提交者/寻物发布者可见）。

## 4. C 组：管理后台补全（`apps/admin-web`）
| ID | 说明 | 状态 |
|---|---|---|
| C1 | 后台总览 Dashboard（四统计 + 最近动态），默认落地 /dashboard | ✅ build |
| C2 | 审计日志 action/targetType 过滤 + 分页 | ✅ |
| C3 | 争议受理 + 证据鉴权预览 + 裁决预览文案 + 二次确认 | ✅ |
| C4 | 401/UNAUTHENTICATED 清 token 跳登录 | ✅ |

## 5. D2/D3：测试
- D2 `ApiAuthzIT`（9 组，含收尾轮新增 TC-LEAD-01）：见 §0 verify 结果全绿。
- D3 `tests/e2e/e2e-smoke.sh` 入库并启用 refresh/logout 断言：PASS 31 / FAIL 0。

### D2 与任务书最小用例集的偏差声明
| 任务书用例 | 现状 |
|---|---|
| adminEndpoint_forbiddenForUser (TC-AUTH-01) | ✅ 已实现 |
| selfClaim_forbidden (TC-CLAIM-01) | ✅ 已实现 |
| editOthersPost_forbidden (TC-POST-02) | ✅ 已实现（含 POST_EDIT_LOCKED） |
| privateEvidence_404ForStranger (TC-FILE-01) | 由 `e2e-smoke.sh`「TC-FILE-01 stranger 404 / owner fetch 200」覆盖（未在 ApiAuthzIT 重复） |
| lead_thirdParty_404 (TC-LEAD-01) | ✅ 已实现为 `ApiAuthzIT.strangerLead_404`（收尾轮补） |
| dispute_pausesHandover (TC-DISPUTE-01) | 由 `e2e-smoke.sh`「TC-DISPUTE-01 pause」+ 管理员受理/裁决 覆盖（未在 ApiAuthzIT 重复） |
| restrictedUser_writeRejected (TC-ADMIN-02) | ✅ 已实现 |
| **roleField_notTrusted (TC-AUTH-02)** | **未单独实现**。原因：登录 DTO 不接受 role/userId 字段，身份一律来自服务端会话/JWT（`AuthContext` 不读请求体），结构上无法伪造；已由 `unauthenticated_write_401`（无有效会话即 401）与 `adminEndpoint_forbiddenForUser`（普通用户 token 无法访问后台）间接覆盖。如需显式断言可后续补充。 |

> 额外补充：`unknownRoute_404_and_badJson_400`（B5）、`logout_revokesSession`（B3）也在 ApiAuthzIT 中。

## 6. E 组：文档回填
- E1 README：进度叙事更正、swagger 地址修正为 `/api/v1/swagger-ui/index.html`、JDK17 JAVA_HOME 注意、测试/e2e 用法、遗留目录说明。
- E2 demo-script：新增图片/会话生命周期/受理裁决演示，标注 e2e 验证。
- E3 traceability（逐行状态，多数 🟢，性能/备份恢复/真机 🟡）、test-report（§8 整改验证）、risks（§E 缺陷登记）。
- E4 本报告。

## 7. 整改中发现并修复的额外问题（非任务书列举）
- `MatchScorer` 双构造器导致 Spring 启动失败（`No default constructor found`）→ 注入构造器加 `@Autowired`。首次真实启动时暴露。
- `application-prod.yml` 初版硬编码 mock=false 且 JWT 有通过性默认值，使 B7/B8 守卫不可触发 → 改为 `${MOCK_LOGIN_ENABLED:false}` 与空 JWT 默认，守卫可正确拒绝。
- e2e 脚本 `post()` 在无 body 调用时 `set -u` 触发 `$3 unbound`，且文件上传读取字段名应为 `fileId` → 已修正，PASS 31/0。

## 8. 仍待验证（外部条件，未伪报）
| 项 | 原因 |
|---|---|
| 性能 NFR-PERF-01（P95） | 需生成约 1 万发布数据集实测 |
| 微信真机 | 需合法 HTTPS 域名（外部平台条件） |
| 备份隔离恢复 TC-BACKUP-01 实跑 | 按 `docs/operations/backup-restore.md` 离线脚本执行并回填 |
| 匹配规则正负样例报告 FR-MATCH-03 | 打分逻辑已单测；系统性样例报告待补 |

## 9. 提交记录
| commit | 内容 |
|---|---|
| `5f71942` | fix(B+D1) 后端整改 B1-B10 + failsafe |
| `f867c81` | feat(A) 小程序补全 A1-A7 + GET /leads/{id} |
| `3d64502` | feat(C) 管理后台补全 C1-C4 |
| `ed579e5` | test(D2) ApiAuthzIT 8 组 |
| `8ec6dff` | docs(E) 文档回填 E1-E4 |
| `727764a` | docs 重写 GitHub README |
| （收尾轮） | fix(A1.5) 争议证据图 / test(TC-LEAD-01) / chore 死代码清理 / docs 收尾回填 |

> 实际提交作者/审核由团队据实核对；本报告证据均来自实际运行输出。

## 10. 收尾轮 e2e-smoke.sh 原始输出（2026-09-29）
命令：`source deploy/.env && BASE=http://localhost:8080/api/v1 ADMIN_USER=$ADMIN_BOOTSTRAP_USERNAME ADMIN_PASS=$ADMIN_BOOTSTRAP_PASSWORD bash tests/e2e/e2e-smoke.sh`

```text
== smoke run #1790658368 ==
PASS  three mock logins
PASS  TC-AUTH-01 user->admin 403
PASS  publish FOUND+LOST
PASS  FR-SEARCH-01 combined search
PASS  FR-MATCH opposite-type candidates
PASS  match returns reasons
PASS  TC-CLAIM-01 self claim
PASS  TC-CLAIM-02 duplicate
PASS  claim submitted
PASS  NFR-SEC-01 stranger 404
PASS  accept -> post HANDOVER
PASS  FR-MSG-01 stranger 404
PASS  B10 received-claims
PASS  TC-DISPUTE-01 pause
PASS  user cannot resolve
PASS  B9 admin assign dispute
PASS  FR-DISPUTE-02 resolve CONTINUE
PASS  TC-HANDOVER-01 one-side
PASS  TC-HANDOVER-02 idempotent
PASS  both confirmed -> COMPLETED
PASS  post COMPLETED
PASS  TC-POST-05 mark found
PASS  FR-AUDIT-01 audit filter
PASS  B1 claim-review audit
PASS  TC-ADMIN-02 restricted publish
PASS  B2 restricted participant message
PASS  TC-FILE-01 stranger 404
PASS  owner fetch 200
PASS  B5 unknown route 404
PASS  B3 refresh issues new token
PASS  B3 logout revokes session
== RESULT: PASS 31 / FAIL 0 ==
```

> `mvn verify` 集成测试原始尾部：`Tests run: 10, Failures: 0, Errors: 0` → `BUILD SUCCESS`（ApiAuthzIT 9 + ClaimConcurrencyIT 1）。
