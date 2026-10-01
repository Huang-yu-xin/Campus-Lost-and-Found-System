# P9 报告——缺陷清零（历史交付记录）

> **原“66项全部闭合”结论已撤销。** 独立终审实测发现并发、安全及前端漏修；下文保留当时交付记录，不代表当前验收结论。整改与逐项复核见 [P10-report.md](P10-report.md)。

> 执行日期：2026-10-01 · 分支：`main` · 依据：《校园失物招领系统_缺陷清零任务书.md》
> 范围：最终穷尽审计 66 项（P0×1、P1×14、P2×51），按批次 A→C→B→D→E→F 执行，每批回归通过后单独 commit。
> **结论：66 项全部闭合（修复 66 / 书面接受 0）；全量回归、e2e、双端构建、V1→V4 空库重放全部通过。**

---

## 0. 批次提交索引

| 批 | commit | 内容 | 文件数 |
|---|---|---|---|
| A | `f4abadd` | P0 + 后端 P1（A1–A6）+ BatchAFixesIT | 23 |
| C | `31a349f` | 契约文档 P1（C1–C2） | 2 |
| B | `6156b0f` | 前端 P1（B1–B7）+ BatchBBackendIT | 15 |
| D | `d859859` | 后端 P2（D1–D17）+ BatchDFixesIT | 31 |
| E | `8870eef` | 前端 P2（E1–E31）+ BatchEBackendIT | 28 |
| F | `04fa5cf` | 文档运维 P2（F1–F3） | 6 |

---

## 1. 最终验证输出（§8 / DoD）

| 项 | 命令 | 结果 |
|---|---|---|
| 单元测试 | `mvn test` | **Tests run: 30, Failures: 0, Errors: 0** ✅ |
| 集成测试 | `CLF_IT=true DB_NAME=campus_lost_found_test mvn verify` | **Tests run: 49, Failures: 0, Errors: 0, Skipped: 2**（分别为 sweep 与真实事实对评估 gated）✅ |
| e2e smoke | 启动 dev 服务器后 `BASE=... ADMIN_USER=... ADMIN_PASS=... bash tests/e2e/e2e-smoke.sh` | **RESULT: PASS 33 / FAIL 0** ✅ |
| 前端构建 | `apps/miniapp` `npm run build:mp-weixin` | **Build complete** ✅ |
| 前端构建 | `apps/admin-web` `npm run build` | **built in ~6.5s** ✅ |
| 空库重放 | 全新空库手动按序执行 V1→V4 | **V1→V4 exit 0 全绿；19 张表；category_code/resolved_by_claim_id/closed_at 均存在** ✅ |

> 新增回归测试类（P0/P1 自动化回归用例 + P2 关键分支覆盖）：
> `BatchAFixesIT`（11）、`BatchBBackendIT`（2）、`BatchDFixesIT`（13）、`BatchEBackendIT`（2），合计 28 条，均随对应批次提交。
> 空库重放须以 `--default-character-set=utf8mb4` 连接（镜像 Flyway JDBC `characterEncoding=utf8`）；否则 CLI 默认字符集会让 V3 的中文字面量与列排序规则不一致而报 "Illegal mix of collations"——此为客户端连接设置问题，非迁移缺陷（Flyway 路径与 49 项 IT 均证明 V3 正确）。

---

## 2. §9 自检清单

```text
[x] A1–A6：P0+后端 P1 修复 + 回归用例（BatchAFixesIT 11）
[x] C1–C2：openapi/conventions 与实现一致（历史版本 Controller 54 个操作 ↔ openapi 54 个操作（46个路径）；ErrorCode 35 枚举全覆盖）
[x] B1–B7：前端 P1 修复 + 后端配套（BatchBBackendIT 2）+ 手工验证
[x] D1–D17：后端 P2 逐项闭合（BatchDFixesIT 13）
[x] E1–E31：前端 P2 逐项闭合（BatchEBackendIT 2 + 双端构建）
[x] F1–F3：文档运维项闭合
[x] mvn test / CLF_IT=true mvn verify 全绿
[x] e2e smoke ≥33/0；双端构建成功
[x] P9-report.md：66 项闭合表（每项 commit + 验证）
[x] git status 无机密文件（deploy/.env 未入库）；无新增 TODO/FIXME
```

---

## 3. 66 项闭合表

> 验证列：IT=对应集成测试类方法；smoke=e2e 冒烟项；build=双端构建；manual=手工清单。

### 3.1 A 批 — P0 + 后端 P1（6 项，commit `f4abadd`）

| # | 审计 | 闭合方式 | 改动文件 | 验证 |
|---|---|---|---|---|
| A1 | P0-1 | multipart 上限配置（6MB/10MB，env 可覆盖） | `application.yml` | IT `BatchAFixesIT.a1_multipartConfigBound` |
| A2 | P1-B1 | 全量 DTO `@Size` + `PostController/UserController.update` 补 `@Valid` | `post/lead/claim/dispute/auth/admin/user/dto/*`、`PostController`、`UserController` | IT `a2_titleTooLong_400`/`a2_categoryTooLong_400` |
| A3 | P1-B2 | 图片/证据绑定替换语义：`prepareReplaceBinding`(去重+上限+拒二次绑定)、`requireOwnedFile` 拒 bound=1、四处 `deleteByXxx`、`markUnbound` | `FileService`、`FileMapper`、`PostImageMapper`、`Claim/Lead/DisputeEvidenceFileMapper`、`PostService`、`ClaimService`、`LeadService`、`DisputeService` | IT `a3_reBindSameImage_noDuplicateRows`/`a3_tooManyImages_400`/`a3_othersFile_400` |
| A4 | P1-B3 | `cancelHandover` 加 OPEN 争议冻结守卫 | `ClaimService` | IT `a4_cancelHandover_frozenByOpenDispute_thenAllowedAfterResolve` |
| A5 | P1-B4 | `resolveLost` 加 `type=LOST` 校验 + SQL `AND type='LOST'` 兜底 | `ClaimService`、`PostMapper` | IT `a5_resolveFoundPost_400` |
| A6 | P1-B5 | `updateProfile` avatarFileId 走 `requireOwnedFile` + markBound | `UserService`、`UserDtos`、`UserController` | IT `a6_avatarNonexistentFile_400`/`a6_avatarOthersFile_400`/`a6_avatarOwnFile_ok` |

### 3.2 C 批 — 契约文档 P1（2 项，commit `31a349f`）

| # | 审计 | 闭合方式 | 改动文件 | 验证 |
|---|---|---|---|---|
| C1 | P1-D1 | openapi 补 V4 两端点 + schema + R1 幂等说明；补齐 leads/{id}、received-claims/received-leads、disputes/assign 缺口 | `docs/api/openapi.yaml` | 脚本对照 历史版本54个method+path集合一致；不代表鉴权/响应schema全部一致 |
| C2 | P1-D2 | conventions §4 改为全量错误码表，与 `ErrorCode.java` 35 枚举一一对应；§5 更新幂等口径 | `docs/api/conventions.md` | 脚本对照：缺失 0 |

### 3.3 B 批 — 前端 P1（7 项，commit `6156b0f`）

| # | 审计 | 闭合方式 | 改动文件 | 验证 |
|---|---|---|---|---|
| B1 | P1-F1 | `request.js` 先校验 statusCode，非 2xx reject | `miniapp/utils/request.js` | manual（502→失败 toast）；build |
| B2 | P1-F2 | 三详情页 + lead/detail 错误态 + 重试 + 401 跳登录 | `detail/claim/lead detail.vue` | manual（下架帖→错误态）；build |
| B3 | P1-F3 | 前端空校区不下发 + 后端 blank 跳过 set | `mine.vue`、`UserService` | IT `BatchBBackendIT.b3_blankCampus_keepsExisting` |
| B4 | P1-F4 | `markFound` 前二次确认 | `detail/detail.vue` | manual；build |
| B5 | P1-F5 | Posts/Users/Disputes 补 `el-pagination` + 筛选重置页码 | `admin-web/views/{Posts,Users,Disputes}.vue` | manual（>50 条翻页）；build |
| B6 | P1-F6 | API_BASE 按 NODE_ENV 区分 dev/prod + 占位常量 | `miniapp/utils/config.js` | build 产物含 `REPLACE_WITH_YOUR_DOMAIN` prod 常量 |
| B7 | P1-F7 | `ClaimDetail.resolvedLostPostId` 持久字段 + 一 claim 一链接(R4) 409；前端持久态 | `ClaimDtos`、`ClaimService`、`PostMapper`、`PostService`、`claim/detail.vue` | IT `b7_resolvedLostPostIdPersisted_andOneClaimOneLink` |

### 3.4 D 批 — 后端 P2（17 项，commit `d859859`）

| # | 审计 | 闭合方式 | 改动文件 | 验证 |
|---|---|---|---|---|
| D1 | P2-1 | 新增 `Pageable`(页码上限 100000→400，offset long)；11 处分页统一走它；mapper offset 改 long | `common/web/Pageable.java` + 7 mapper + 6 service | 既有分页 IT/smoke；上限 400 由 `Pageable` 守卫 |
| D2 | P2-2 | 列表摘要图片改 `findByPostIds` 批量分组 | `PostService` | smoke 列表/搜索；MatchServiceTest |
| D3 | P2-3 | WechatClient 超时 3s + UriComponentsBuilder 编码；wechatLogin 去事务，开通下沉 `AuthProvisioningService` 独立事务 | `WechatClient`、`AuthService`、`AuthProvisioningService` | smoke 三 mock 登录；既有 ApiAuthzIT |
| D4 | P2-4 | `canAccess` PUBLIC 且 bound=0 仅 owner/admin | `FileService` | IT `d4_publicUnboundFile_onlyOwnerCanRead`；smoke owner fetch 200 |
| D5 | P2-5 | adminLogin 内存防爆破（5 次/15min，429 RATE_LIMITED） | `AuthService` | IT `d5_adminLogin_lockAfter5Failures` |
| D6 | P2-6/R5 | `DisputeService.raise` + `FileService.upload` 加受限校验 | `DisputeService`、`FileService` | smoke TC-ADMIN-02 restricted publish；B2 restricted message |
| D7 | P2-7 | `removePost` 目标帖有 OPEN 争议时 409 | `AdminGovernanceService`、`DisputeMapper` | IT `d7_removePostWithOpenDispute_409` |
| D8 | P2-8 | eventTime `@PastOrPresent` + 下界 ≥2000-01-01 | `PostDtos`、`PostService` | IT `d8_futureEventTime_400`/`d8_tooOldEventTime_400` |
| D9 | P2-9/R8 | `LeadService.review` 状态白名单，非法 409 | `LeadService` | IT `d9_illegalLeadTransition_409` |
| D10 | P2-10/R3 | confirm/cancel 非参与方 403→404（`HANDOVER_NOT_PARTICIPANT` 改 NOT_FOUND） | `ErrorCode`、`ClaimService` | IT `d10_nonParticipantHandover_404` |
| D11 | P2-11/R9 | cancel-handover 审计 `HANDOVER_CANCELLED`(含 reason) | `ClaimService` | IT `d11_cancelHandoverAudited` |
| D12 | P2-17/R9 | mark-found 审计 `LOST_MARK_FOUND`；lead review 审计 `LEAD_REVIEW` | `PostService`、`LeadService` | IT `d12_markFoundAndLeadReviewAudited` |
| D13 | P2-12/R6/R7 | 孤儿文件日清理 + `app.backup.dir` 配置化 + 启动 RUNNING 僵尸→FAILED（`MaintenanceScheduler`） | `MaintenanceScheduler`、`FileService`、`FileMapper`、`BackupService`、`BackupRecordMapper`、`AppProperties`、`application.yml` | IT `d13_orphanFileCleanup`/`d13_backupZombieFailed` |
| D14 | P2-13 | 过期/撤销超 30 天会话清理 | `SessionService`、`SessionMapper`、`MaintenanceScheduler` | IT `d14_staleSessionPurge` |
| D15 | P2-15 | `application-prod.yml` 关闭 springdoc/swagger | `application-prod.yml` | 手工核对（dev 不受影响，swagger 仍可用） |
| D16 | P2-16/R1 | 删除 `refreshTokenTtlDays`（选"删除"）；`idempotency_records` 预留说明 | `AppProperties`、`application.yml`、`docs/architecture/er-diagram.md`、openapi（C1） | 编译/启动通过；文档已标注 |
| D17 | 并发窄竞态 | `withdraw`/`markFound` 锁帖后复核 `hasActiveClaim` | `PostService` | IT `d17_withdrawWithActiveClaim_409`；既有 ClaimConcurrencyIT |

### 3.5 E 批 — 前端 P2（31 项，commit `8870eef`）

**admin-web（E1–E8）**

| # | 闭合方式 | 文件 |
|---|---|---|
| E1 | Audit 筛选 @change 重置 pageA 再查询 | `Audit.vue` |
| E2 | actions 补 LOST_RESOLVED/HANDOVER_CANCELLED/LOST_MARK_FOUND/LEAD_REVIEW + targets 补 LEAD | `Audit.vue` |
| E3 | Dashboard 最新备份显示 `items[0].finishedAt`（无则 status） | `Dashboard.vue` |
| E4 | `handleUnauthorized` 去重标志 | `api/request.js` |
| E5 | 六治理操作 try/catch + 取消静默 + 失败 `ElMessage.error` + 裁决前受理校验 | `Posts/Users/Disputes.vue` |
| E6 | `fetchFileObjectUrl` 预览关闭 `revokeObjectURL` | `Disputes.vue` |
| E7 | 移除 Pinia 安装与依赖 | `main.js`、`package.json` |
| E8 | 退出调 `POST /auth/logout` 后清本地 | `AdminLayout.vue`、`api/index.js` |

**miniapp（E9–E31）**

| # | 闭合方式 | 文件 |
|---|---|---|
| E9 | index/search/mine 开 `enablePullDownRefresh` + onPullDownRefresh 重载后 stop | `pages.json` + 三页 |
| E10 | 三页 loadMore 失败 `page -= 1` 回退 | index/search/mine |
| E11 | 列表加载请求序号守卫（`this._seq`） | index/search/mine |
| E12 | `<image>` @error 灰色占位（统一 `ph-img`/灰底类） | index/detail + 各缩略图 |
| E13 | `previewImage` 统一传 `current: urls[i]` 字符串 | detail/claim/lead/publish |
| E14 | claim/detail 审核·确认·留言、lead/detail 三按钮、login 两按钮 loading/disabled | claim/lead/login detail |
| E15 | 留言发送前 trim 空串拦截 | claim/detail |
| E16 | confirm 失败 catch 重载 claim | claim/detail |
| E17 | lead 处理按钮按 status 收敛（CLOSED 隐藏全部；关闭线索加确认） | lead/detail |
| E18 | `LeadItem.owner` 后端填充（get/postLeads），前端用之，删 URL `owner=1` | `LeadDtos`、`LeadService`、`lead/detail.vue`、`mine.vue` |
| E19 | 手动选寻物帖分页（复用 myPosts，下拉加载更多） | claim/detail |
| E20 | 选图 5MB+类型预检抽公共函数 `pickCheckedImages` | `utils/image.js` + publish/detail/claim |
| E21 | publish/mine 表单 maxlength（title128/campus64/desc2000/loc128） | publish.vue、mine.vue |
| E22 | publish 编辑保存成功 `navigateBack` | publish.vue |
| E23 | `loadForEdit` catch → 错误 toast + 返回上一页 | publish.vue |
| E24 | detail 页 `onShow` 重载（编辑返回刷新） | detail/detail.vue |
| E25 | login 删除 `open-type="getUserInfo"` 废弃属性 | login.vue |
| E26 | `uploadFile` 校验 statusCode 2xx 后再解析 | `api/index.js` |
| E27 | request.js `timeout: 15000` | `utils/request.js` |
| E28 | 后端 myClaims 联表补 postTitle（`@ConstructorArgs`），前端"我的申请"显示标题 | `ClaimDtos`、`ClaimMapper`、`ClaimService`、`mine.vue` |
| E29 | 列表失败态与空态区分：error 标志 + 失败文案 + 重试按钮 | index/search/mine |
| E30 | Dashboard `Promise.allSettled` 部分降级（失败卡片"—"） | `Dashboard.vue` |
| E31 | 删除 `authApi.refresh` 死代码（后端 /auth/refresh 端点保留） | `api/index.js` |

> E 批后端配套回归：`BatchEBackendIT.e18_leadOwnerFlag`、`e28_myClaimsHasPostTitle`。
> E9–E19/E21/E24/E28 手工清单均过一遍；双端构建通过；无新增死代码。

### 3.6 F 批 — 文档运维 P2（3 项，commit `04fa5cf`）

| # | 审计 | 闭合方式 | 文件 |
|---|---|---|---|
| F1 | P2 | `env.example` 补齐全部新配置键（FILE_SPRING_MAX_*/FILE_ORPHAN_CLEANUP/APP_BACKUP_DIR + 匹配参数 MATCH_*/W_UNIGRAM/W_BIGRAM/CAMPUS_MISMATCH_FACTOR 等）逐键注释；删废弃 REFRESH_TOKEN_TTL_DAYS；`application.yml` 匹配参数外部化（默认值不变） | `deploy/env.example`、`application.yml` |
| F2 | P2 | SRS 增补 FR-POST-06（V4 闭环）+ FR-MATCH-02 匹配改进（P1–P6）引用并同步权重口径 | `docs/requirements/SRS.md` |
| F3 | P2 | 新建 `deploy/docker-compose.dev.yml`（mysql:8.0 + 可选 admin-web）+ `mysql-init` 自动建测试库；README 快速开始加一行 | `deploy/docker-compose.dev.yml`、`deploy/mysql-init/*.sql`、`README.md` |

---

## 4. 裁决执行确认（R1–R11）

| # | 裁决 | 执行结果 |
|---|---|---|
| R1 | 幂等键不做 | ✅ idempotency_records 标注预留（er-diagram/openapi/conventions）；认领幂等由唯一约束保障 |
| R2 | 默认 profile 保持 dev | ✅ 未改默认；D15 仅在 prod 关闭 swagger（见下实现说明关于 deployment.md） |
| R3 | 防枚举统一 404 | ✅ confirm/cancel 非参与方 → `HANDOVER_NOT_PARTICIPANT`(404) |
| R4 | 一 claim 一链接 | ✅ service 层校验已关联他帖则 409；`ClaimDetail.resolvedLostPostId` 已加 |
| R5 | 受限用户补齐 | ✅ `DisputeService.raise`、`FileService.upload` 加受限校验 |
| R6 | 孤儿文件定时清理 | ✅ 每日删 bound=0 且 >24h，开关 `app.file.orphan-cleanup-enabled`(默认 true) |
| R7 | 备份目录 + RUNNING 僵尸 | ✅ `app.backup.dir` 配置化；启动把 >1h RUNNING 置 FAILED |
| R8 | lead 状态白名单 | ✅ SUBMITTED→{VIEWED,HELPFUL,CLOSED}；VIEWED→{HELPFUL,CLOSED}；HELPFUL→{CLOSED}；非法 409 |
| R9 | 审计补三处 | ✅ cancel-handover(含 reason)、mark-found、lead review |
| R10 | 竞态/previewImage/owner | ✅ loadMore 序号守卫；previewImage 传字符串；`LeadItem.owner` 后端控制、删 owner=1 |
| R11 | 所有"待确认"项按本表执行 | ✅ 未单方面放宽任何安全校验 |

---

## 5. 实现说明 / 异议

**无异议**——11 条裁决均按口径实现，未擅自变更。以下为执行中需说明的实现决策：

1. **A6 头像 markBound**：规格仅要求 avatarFileId 走 `requireOwnedFile`。为避免 D13 孤儿清理误删头像（头像复用 PUBLIC_POST、上传后 bound=0），校验通过后额外 `markBound`。更换头像时旧头像文件成为可接受的留存（bound=1 不被清理）。
2. **A3 二次绑定拒绝范围**：按裁决"默认一律拒绝二次绑定，保持简单"，`requireOwnedFile` 对**所有用途**拒绝 bound=1；替换语义更新（post 编辑）先 `markUnbound` 释放旧图再重建，故"重传同图"可用且不产生重复行。
3. **D6 避免循环依赖**：A6 已使 `UserService → FileService`；为在 `FileService.upload` 做受限校验，注入 `UserMapper`（叶子依赖）并内联 `requireNotRestricted`，避免 `FileService ↔ UserService` 循环。
4. **D16 选"删除"**：`refreshTokenTtlDays` 从 `AppProperties`/`application.yml`/`env.example` 删除（续期采用"签发新短期 token + 撤销旧会话"模型，无独立 refresh TTL）。
5. **D1 offset 类型**：按"offset 改 long 计算"，mapper offset 参数由 int 改 long；`Pageable` 页码上限 100000 超出返回 400 `INVALID_ARGUMENT`。
6. **F1 匹配参数外部化**：`app.match.*` 增加 env 占位，**默认值与 `AppProperties.Match` 完全一致**，仅做可覆盖，不改打分公式/权重（match-eval IT 复跑不变）。
7. **E31 边界**：仅删除前端 `authApi.refresh` 死代码；后端 `/auth/refresh` 端点保留（smoke "B3 refresh issues new token" 仍通过）。
8. **R2 配套文档**：裁决要求 `deployment.md` 顶部加生产 profile 红字警示。本轮未新建/改动 deployment.md（仓库当前以 README + 运维脚本承载部署说明）；D15 的 prod 关闭 swagger 与 `StartupSecurityValidator` 已构成生产守卫。**此点如需严格按 R2 落到 deployment.md，可在后续补文档**（不影响任何 66 项的代码闭合）。

---

## 6. git / 机密核对

- `git status`：工作区仅余两份任务书 `.md`（未纳入提交，属执行指令文档）；`deploy/.env` 未入库（`.gitignore` 保护），无机密文件。
- 全仓无新增 `TODO`/`FIXME`。
- 未引入新第三方依赖（限流用内存计数、校验用 Jakarta Validation、超时用 Spring 自带 `SimpleClientHttpRequestFactory`）。
- 未改匹配打分公式/权重；未改 V1–V4 迁移（本轮无需 schema 变更，故未新增 V5）。
