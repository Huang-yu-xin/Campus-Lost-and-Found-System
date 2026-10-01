# 历史独立终审快照（整改前）

本报告描述整改前 HEAD 的状态，保留原发现与输出摘要；当前修复验证见 [P10](P10-report.md)。用户已要求删除中间文件，原临时日志、截图、独立脚本不随此快照发布。

# 校园失物招领系统独立终审验收报告

审计日期：2026-10-01（Asia/Shanghai）。仓库：D:/software/Campus Lost and Found System。审计 HEAD：ed195bd29e7f8a972d7accd12dc5c870d23da97e。

本次只审计，未修改仓库代码、文档或提交 git。独立脚本、日志、浏览器截图和本报告均放在仓库外的临时目录。构建生成 target/dist；测试库写入实验数据；开发库只进行了授权的服务启动、33 项冒烟及事实对只读评估。未执行数据库导出或隔离恢复，未把旧报告当作实测证据。

## 1. 总体验收结论

**不可验收。**

必须先修复核心并发状态错误、私密内容生产日志泄漏、生产守卫绕过、会话签发冲突和争议授权问题，并完成针对这些实际故障的回归；同时更正不能成立的“66 项全部闭合”声明。

构建和现有测试全部通过，但不能证明产品可验收。本次独立真实 MySQL 实验得到：

- 双方确认已各写入一条记录，认领仍停在 WAITING_HANDOVER。
- 最后确认与争议并发，最终 claim/post 均 COMPLETED，争议却仍 OPEN。
- 一条已完成 claim 并发关联了两条 LOST。
- 已撤回帖子仍产生 PENDING 认领。
- CLOSED 线索被并发请求重新改成 VIEWED。
- 纯 prod 下私密认领证明正文进入 DEBUG 日志；prod,dev 组合仍可用弱密钥和 mock 登录。

这些不是用户已经接受的简化方案；实际实现违反状态、安全或裁决基线。

## 2. 分级发现清单

下列位置相对仓库根；证据链接指向本次独立审计产物。API 前缀统一为 /api/v1。Python 脚本默认调用测试服务 localhost:8081，真实 MySQL 测试库。脚本通过测试事务持有行锁 3–4 秒来确定请求交错，没有改动业务代码，也没有修改 dev 库已有业务记录。

### P0：阻塞

**P0-01：核心“争议冻结完成”不变量失效。** 位置：server/src/main/java/edu/whut/clf/claim/ClaimService.java:213、server/src/main/java/edu/whut/clf/dispute/DisputeService.java:55。先让一方确认，再锁帖子行，发最后确认，0.6 秒后发争议；释放锁。两请求均 HTTP 200。SQL 实测 claim 164 / post 为 COMPLETED，dispute 14 为 OPEN。普通快照查询两次 countOpen 并不能使两个事务互斥。命令：python audit.py races；脚本（原临时证据已清理，输出摘要保留于本文）、原始结果（原临时证据已清理，输出摘要保留于本文）。违反原任务书 NFR-CONS-01、TC-DISPUTE-01 和 state-machines 的锁说明。

**P0-02：P9“66 项修复、书面接受 0、全部闭合”声明不成立。** 位置：docs/reports/P9-report.md:5、闭合表 B7/D9/D17/E11/E12/E14/E19，以及 §4 R2/R4/R8/R10。代码有改、顺序测试或 build 有通过，但 B7 一 claim 两链接、D9 CLOSED 回退、D17 撤回后新申请、E11 旧分页混入新筛选、E14 登录 loading 未定义、E19 第二页不可达均被本次独立实验复现；E12 六处 image 没有 @error。R2 警示没有落实，P9 自己也在 §5.8 承认延期。按用户指定“发现虚报/漏报即 P0”定级；这里判断的是闭合证据失实，不推断开发人员动机。证据：races.log（原临时证据已清理，输出摘要保留于本文）、extra.log（原临时证据已清理，输出摘要保留于本文）、frontend.log（原临时证据已清理，输出摘要保留于本文）、frontend-extra.log（原临时证据已清理，输出摘要保留于本文）、contract.log（原临时证据已清理，输出摘要保留于本文）。

### P1：真实缺陷

**P1-01：同时双向确认不能保证完成。** 位置：ClaimService.java:213。锁 claim 行，使两方确认事务均在原始 WAITING_HANDOVER 快照上开始，随后并发执行 confirmations。两请求均 200，但各只看到自己的确认；最终 claim 163=WAITING_HANDOVER、post=HANDOVER、确认数=2。命令：python audit.py races；races.log（原临时证据已清理，输出摘要保留于本文）。违反 TC-HANDOVER-02；现有并发测试只测 ACCEPT。

**P1-02：一条 claim 可并发关联两条寻物帖，R4/B7 未闭合。** 位置：ClaimService.java:311/318/341；server/src/main/resources/db/migration/V4__post_resolution_link.sql中的 resolved_by_claim_id 为非唯一索引。两个目标帖子分别加锁，两个 resolve-lost 都先读“尚未关联”，再更新各自目标。两请求 200，SQL：25507 和 25508 均 COMPLETED、resolved_by_claim_id=162。详情 LIMIT 1 只展示 25507，掩盖另一链接。命令：python audit.py races；races.log（原临时证据已清理，输出摘要保留于本文）。现有 b7 用例仅验证顺序第二次关联。

**P1-03：撤回与新认领并发后出现 WITHDRAWN + PENDING。** 位置：server/src/main/java/edu/whut/clf/post/PostService.java:146、ClaimService.java:68。锁帖子行，启动 withdraw，0.3 秒后 submit claim，再释放锁。两请求 200；post 25512=WITHDRAWN，claim 165=PENDING。D17 仅在撤回方锁后复核，提交方没有同一锁和权威状态复核。命令：python audit.py races；races.log（原临时证据已清理，输出摘要保留于本文）。现有 d17 是先有申请再撤回的顺序测试。

**P1-04：线索状态白名单仅顺序有效，并发可重开 CLOSED。** 位置：server/src/main/java/edu/whut/clf/lead/LeadService.java:128/154、server/src/main/java/edu/whut/clf/lead/LostLeadMapper.java:29。初始 SUBMITTED；锁线索行，先发 CLOSED、再发 VIEWED，两方均读旧状态后无条件 UPDATE。两请求 200，最终 VIEWED。命令：python extra.py；extra.log（原临时证据已清理，输出摘要保留于本文）。违反 R8；d9_illegalLeadTransition_409 只覆盖顺序非法转换。

**P1-05：同秒登录/刷新产生完全相同 JWT，唯一键冲突返回 500。** 位置：server/src/main/java/edu/whut/clf/common/security/JwtService.java:31、server/src/main/java/edu/whut/clf/auth/SessionService.java:26/31。JWT 没有随机 jti，iat/exp 精度为秒。对同一身份连续 login，4 次重复请求均 500；紧接 login 做 refresh，4 次实验中 3 次 500。服务日志明确 sessions.uk_session_token Duplicate entry。命令：python audit.py probes；probes.log（原临时证据已清理，输出摘要保留于本文）、服务异常（原临时证据已清理，输出摘要保留于本文）。不是要求新增幂等键，而是现有认证功能无法稳定签发/刷新。

**P1-06：纯生产环境泄漏私密证明正文到日志。** 位置：server/src/main/resources/application.yml:85；application-prod.yml 未覆盖该日志级别。纯 prod、强 JWT_SECRET、MOCK_LOGIN_ENABLED=false 启动成功后提交标记正文。实际 prod 日志出现 MyBatis ClaimMapper.insert Parameters 中 AUDIT_PRIVATE_PROOF_PROD_... 完整正文。命令：python extra.py（会短暂启动并停止 prod 实验进程）；extra.log（原临时证据已清理，输出摘要保留于本文）、生产日志（原临时证据已清理，输出摘要保留于本文）。违反 NFR-PRIV-01 日志脱敏要求。

**P1-07：prod 与 dev 同时激活可绕过生产启动守卫。** 位置：server/src/main/java/edu/whut/clf/common/config/StartupSecurityValidator.java:34。SPRING_PROFILES_ACTIVE=prod,dev + 开发默认弱密钥 + MOCK_LOGIN_ENABLED=true：实际启动成功，mock/login=200。纯 prod 的弱密钥/开启 mock 均正确拒绝启动。命令：python prod.py；prod-summary.log（原临时证据已清理，输出摘要保留于本文）、混合 profile 日志（原临时证据已清理，输出摘要保留于本文）。默认 dev 本身为既定接受方案；这里报告显式 prod 配置仍被 dev 豁免。

**P1-08：未受理管理员能读取争议私密正文并直接裁决。** 位置：server/src/main/java/edu/whut/clf/dispute/DisputeService.java:104/125。创建 OPEN 争议，assignedAdminId=null，管理员直接 GET admin/disputes/id 得到 private-audit-description 和证据 ID；直接 POST resolution(CONTINUE) 返回 200。同管理员下载 PRIVATE_DISPUTE 文件却返回 404。后端 resolve 不核验 assignedAdminId，前端按钮检查不能代替授权。命令：python audit.py probes；probes.log（原临时证据已清理，输出摘要保留于本文）。违反“仅获授权/受理管理员”与 TC-DISPUTE-02。

**P1-09：已受理争议管理员无法读取关联认领证明。** 位置：server/src/main/java/edu/whut/clf/claim/ClaimEvidenceAccessChecker.java:31、ClaimService.java:104。将 PRIVATE_CLAIM 图片绑定到认领，建立争议并 assign 当前管理员；GET claims/id 和 GET files/原认领证明均 404。管理员能处理争议，却无法核查原始申请证据。命令：python extra.py；extra.log（原临时证据已清理，输出摘要保留于本文）。ER 字典明确认领证明允许“授权争议管理员”，§8.2 要求查看关联申请及证据。

**P1-10：下架帖子图片仍可匿名读取。** 位置：server/src/main/java/edu/whut/clf/file/FileService.java:126。PUBLIC_POST 上传后未绑定匿名 404（通过）；绑定公开帖子后匿名 200（通过）；管理员下架帖子后，相同 fileId 仍匿名 200。canAccess 只看 PUBLIC/bound，不看关联帖 REMOVED 状态。命令：python audit.py probes；probes.log（原临时证据已清理，输出摘要保留于本文）。保留历史不等于继续对公众提供已治理图片。

**P1-11：UTC 及带时区时间契约未实现。** 位置：server/src/main/java/edu/whut/clf/post/dto/PostDtos.java:20/34/39、PostService.create、Mapper 的 NOW()。输入 eventTime=2026-09-27T12:00:00Z，响应变为无时区的 2026-09-27T12:00:00；publishedAt=15:07:44，而 UTC_TIMESTAMP()=07:07:44，DB published_at=15:07:44。LocalDateTime 与 JVM/DB 本地时间绕过 jackson.time-zone 的作用。命令：python audit.py probes；probes.log（原临时证据已清理，输出摘要保留于本文）。与 conventions §1、ER“DB 存 UTC”冲突，影响跨时区事件过滤及匹配。

**P1-12：后台信息治理功能不完整。** 位置：apps/admin-web/src/views/Posts.vue:22、Audit.vue:19。真实浏览器登录并打开 posts/audit，只有列表与下架/恢复；没有公开详情、图片、描述、治理历史入口。Audit 仅显示 actorType，没有 actorId、reason/metadata 可核查内容；用户页也没有基线要求的审计链接。命令：node browser.cjs；browser.log（原临时证据已清理，输出摘要保留于本文）、信息页截图（原临时证据已清理，输出摘要保留于本文）、审计页截图（原临时证据已清理，输出摘要保留于本文）。与原 §8.2、SRS FR-ADMIN-01 偏差。

**P1-13：小程序分页竞态守卫未覆盖 loadMore。** 位置：apps/miniapp/src/pages/index/index.vue:70、search/search.vue:87、mine/mine.vue:88。执行真实 SFC 方法及实际 Vue 响应式代理，挂起旧 loadMore，先完成新筛选 reload，再完成旧请求。index/search 最终 [new99,old12]；mine 混入旧 tab 记录。reload 的 _seq 守卫工作，loadMore 没有守卫。命令：node frontend.cjs；frontend.log（原临时证据已清理，输出摘要保留于本文）。违反既定 R10，E11 未闭合；这不是要求升级最小实现。

**P1-14：登录和发布不能防止连点重复请求。** 位置：apps/miniapp/src/pages/login/login.vue:9/13、publish/publish.vue:30/105。login 模板绑定 loading，但 data 和方法没有该字段；两次点击产生两次登录请求，loading 为 undefined。publish 只有 loading 外观，没有 disabled 或方法入口 submitting 防护，两次调用产生两个创建请求。命令：node frontend.cjs；frontend.log（原临时证据已清理，输出摘要保留于本文）。实际方法测试，无微信真机冒充；E14 登录部分未落实，且发布没有数据库去重约束。

**P1-15：手动关联寻物帖可能永远到不了下一页。** 位置：apps/miniapp/src/pages/claim/detail.vue:98/173。myPosts 第一页 20 条全是 FOUND，第二页有 LOST；前端过滤第一页后 manualPosts=[]，manualNoMore=false，但“加载更多”要求 manualPosts.length，按钮不可见，错误显示没有进行中的寻物帖。命令：node frontend-extra.cjs；frontend-extra.log（原临时证据已清理，输出摘要保留于本文）。E19/V4 手动关联入口不完整。

**P1-16：停用账户的存量会话仍能正常使用。** 位置：server/src/main/java/edu/whut/clf/common/security/AuthInterceptor.java:37、SessionService.java:45。在测试库将已登录用户改为 DISABLED，原 token GET users/me 仍 200；实验随后恢复 ACTIVE。过期签名 token 实测 401、logout 撤销实测通过，故问题是账户停用未同步会话。命令：python extra.py；extra.log（原临时证据已清理，输出摘要保留于本文）。ER 会话生命周期“停用后失效”未落实；当前后台只有 restrict/unrestrict，不把 RESTRICTED 当作必须退出。

**P1-17：可把必填帖子字段编辑为空。** 位置：server/src/main/java/edu/whut/clf/post/dto/PostDtos.java:24、PostService.java:120。合法帖子 PATCH title="   ",category="",publicDescription=""，返回 200，库/详情三个字段均空。Update DTO 仅校验长度，没有“提供字段时不可为空”的约束。命令：python audit.py probes；probes.log（原临时证据已清理，输出摘要保留于本文）。不是把 partial PATCH 的未提供字段错误要求为必填。

**P1-18：固定数据集分页出现重复记录。** 位置：server/src/main/java/edu/whut/clf/user/UserMapper.java:36，仅 ORDER BY created_at DESC。在无写入的两次 GET admin/users?page=1/2&pageSize=20 中，两页重复用户 ID 共 10 个：[921,925,926,927,928,929,932,934,936,937]。浏览器在开发库也看到了重叠。命令：python tail.py；tail.log（原临时证据已清理，输出摘要保留于本文）、browser.log（原临时证据已清理，输出摘要保留于本文）。同秒创建时间并列导致 LIMIT/OFFSET 不稳定，违反固定数据集不能重复的分页要求；其他只按 created_at 排序的 mapper 也需检查，但未把未实测的列表列为已证实。

### P2：轻微缺陷及契约/证据差异

**P2-01：常见非法参数被当作服务器异常。** 位置：server/src/main/java/edu/whut/clf/common/web/GlobalExceptionHandler.java:77。GET posts?page=abc、posts/abc、posts/search?eventFrom=bogus，以及已登录 POST files 缺 multipart，实际均 500 INTERNAL_ERROR。缺少类型转换/缺参数/非 multipart 等请求错误处理。命令：python audit.py probes；probes.log（原临时证据已清理，输出摘要保留于本文）。超长、非法枚举、超页上限均能正确 400，不将已通过部分否定。

**P2-02：取消交接的合法换行理由导致 500。** 位置：ClaimService.java:277/421。reason="first\\nsecond" 中实际包含换行；自制 jsonString 只转义反斜杠和引号，没有转义控制字符，audit_logs JSON 写入失败、事务回滚。实际 500，claim/post 仍 WAITING_HANDOVER/HANDOVER。命令：python tail.py；tail.log（原临时证据已清理，输出摘要保留于本文）、test-server.log（原临时证据已清理，输出摘要保留于本文）。新增审计存在，但理由处理不完整。

**P2-03：防枚举 404 裁决未统一。** 位置：ClaimService.java:141/144、LeadService.java:104/107/135。陌生人 GET posts/id/claims、GET posts/id/leads、POST leads/id/review 实测 403；另不存在资源为 404。认领详情/线索详情/confirm/cancel 的 404 则通过。命令：python matrix.py；matrix.log（原临时证据已清理，输出摘要保留于本文）、逐请求矩阵（原临时证据已清理，输出摘要保留于本文）。确认是实际裁决违例，不重复报告原有接受口径。

**P2-04：后台 401 去重标志永久锁死。** 位置：apps/admin-web/src/api/request.js:17。同一 SPA 先输错密码产生 401，再成功登录，后续 token 失效 401：tokenStillStored=true、warnings=0、redirects=0。unauthorizedHandled 未在登录成功/新会话重置。命令：node frontend-extra.cjs；frontend-extra.log（原临时证据已清理，输出摘要保留于本文）。E4 只处理首次并发去重。

**P2-05：“我的”资料请求失败被当作未登录。** 位置：apps/miniapp/src/pages/mine/mine.vue 的 loadMe。模拟资料请求 502，得到 me=null、error=false，显示“去登录”而不是失败/重试态。命令：node frontend-extra.cjs；frontend-extra.log（原临时证据已清理，输出摘要保留于本文）。E29 列表分支有错误态，用户资料分支仍缺。

**P2-06：图片失败处理未覆盖闭合表声称的各缩略图。** 位置：miniapp detail/claim/lead/publish 的 image。代码枚举得到 6 个 image 没有 @error；灰底 CSS 不是失败事件处理。构建通过不证明图片 404 能显示正确占位。命令：node frontend.cjs；frontend.log（原临时证据已清理，输出摘要保留于本文）。运行层面应再用微信开发者工具让这些 fileId 返回 404 确认实际平台表现；缺少事件处理为已确认，原生图片失败具体外观标记待平台确认。

**P2-07：后台状态/动作未汉化；申请提交时间线缺数据。** 位置：admin-web Posts/Users/Disputes/Audit 模板；miniapp claim/detail.vue:17；server/src/main/java/edu/whut/clf/claim/dto/ClaimDtos.java。真实后台显示 ACTIVE/UNVERIFIED/LOST/USER_RESTRICT；认领详情响应没有 createdAt，但时间线读取 claim.createdAt，提交时间始终“—”。证据：browser.log（原临时证据已清理，输出摘要保留于本文）、races.log（原临时证据已清理，输出摘要保留于本文） 中完整 ClaimDetail。小程序主要状态已有 label 映射，不泛称全部未汉化。

**P2-08：OpenAPI 路径齐全，语义仍不一致。** 位置：docs/api/openapi.yaml。54 个 method+path 与 Controller 集合完全一致，但仅 46 个 path，P9“54 路径”口径错误；files/{id} 和公开 matches 错误继承 bearerAuth，refresh 标成无需认证；创建 post/claim/lead/dispute/message/file 文档 201 实际 200，备份文档 202 实际 200；部分 ID 定义 string、实际 number，常用成功响应没有完整 schema。命令：python contract.py、python matrix.py、冒烟；contract.log（原临时证据已清理，输出摘要保留于本文）、matrix.log（原临时证据已清理，输出摘要保留于本文）、smoke.log（原临时证据已清理，输出摘要保留于本文）。C1 的路由覆盖通过，不等于三方契约完整通过。

**P2-09：R2 明确要求的生产 profile 警示缺失。** 位置：docs/operations/deployment.md:1、docs/reports/P9-report.md §5.8。deployment.md 实际存在，未提示必须显式 SPRING_PROFILES_ACTIVE=prod，也没有顶部警示；P9称当前以 README 承载并延期。命令：python contract.py + 文件审读；contract.log（原临时证据已清理，输出摘要保留于本文）。默认 dev 保持原裁决，不作为缺陷；未执行裁决的配套警示才是发现。

**P2-10：评估归档与当前实现/结果混淆。** 位置：docs/testing/match-eval/report-real-pairs.md:3、server/src/test/java/edu/whut/clf/it/MatchEvalSweepIT.java 的报告默认参数行。仓库“真实对”文件仍是旧启发式推导 160 对/召回 0%；当前事实链接实跑为 28 对/召回 100%（冒烟新增两条使旧 26 对增加，不算数据造假）。sweep 实跑仍写“现行默认 .40/.25/.20/.15/30d”，实际 application.yml 为 .40/.25/.30/.05/14d。P9 把 2 个 skipped 都说成真实对 gated，但实际分别是 sweep 与 real-pairs。命令：CLF_SWEEP=true mvn test -Dtest=MatchEvalSweepIT、CLF_EVAL_REAL=true mvn test -Dtest=MatchRealPairsIT；sweep.log（原临时证据已清理，输出摘要保留于本文）、real-eval.log（原临时证据已清理，输出摘要保留于本文）、verify.log（原临时证据已清理，输出摘要保留于本文）。低 Hit@K 本身为既定外部说明，不作为缺陷。

**P2-11：Pinia 移除不彻底。** 位置：apps/admin-web/package-lock.json 根依赖仍 pinia ^2.2.4，README 也仍宣称 Pinia；package.json/main 已删除。命令：node frontend-extra.cjs；frontend-extra.log（原临时证据已清理，输出摘要保留于本文）。不影响本次 build，但 E7“移除依赖”不能表述为完全一致。

### 待确认（不混入已证实缺陷）

**Q1：孤儿文件物理删除失败后的重试/竞态。** 位置：FileService.java:223–235。代码捕获 IOException 后仍无条件删除数据库行，与“下次重试”注释冲突；findOrphans 到 deleteById 之间也没有再次确认 bound。现有 d13 通过只证明正常数据库行清理。待验证方法：在测试库上传并回填 >24h 文件，用 Windows 不允许 delete-share 的句柄锁定物理文件，触发定时清理，检查 DB 是否删除而磁盘残留；另在查 orphan 后并发绑定，确认不会删新绑定文件。本次没有为了可疑路径修改调度配置或业务代码，故不称运行已复现。

**Q2：真实微信 code 交换和真机完整流程。** 已审读 3 秒超时、URL 编码、外呼不占数据库事务；未使用真实微信一次性 code、未连接真机。微信 HTTPS/平台条件为用户明确外部条件，不定级为产品缺陷。

## 3. 实际运行且通过的项

运行前统一使用 JDK17 JAVA_HOME=C:/Users/huangyx/.jdks/ms-17.0.18。deploy/.env 凭据只加载、不打印。下面为等价命令；完整输出见同名日志。

| 检查 | 实际命令/方式 | 独立结果 |
|---|---|---|
| 单元 | server: mvn -B -ntp test | 30 / failures 0 / errors 0 / skipped 0；unit.log |
| 全量真实 MySQL 回归 | CLF_IT=true DB_NAME=campus_lost_found_test mvn -B -ntp verify | 单元 30；IT 总49、失败0、错误0、跳过2，即实际执行47；verify.log |
| 空库迁移 | python audit.py migrate；mysql.exe --default-character-set=utf8mb4 顺序执行 V1–V4 | MySQL8.0.34；4 个 exit0；19业务表/26外键；migration.log |
| 开发服务 | env 后 mvn spring-boot:run；GET health、swagger-ui/index.html | 两者 HTTP200；dev-server.log/probes.log |
| 原仓库冒烟 | Git Bash: BASE=http://localhost:8080/api/v1 ADMIN_USER/ADMIN_PASS 已注入 bash tests/e2e/e2e-smoke.sh | PASS33 FAIL0；smoke.log |
| 小程序生产构建 | apps/miniapp: npm.cmd run build:mp-weixin | Build complete；mini-build.log |
| 后台生产构建 | apps/admin-web: npm.cmd run build | built，约9.54秒；admin-build.log。大 chunk 为告警，不是失败 |
| 事实链接评估 | CLF_EVAL_REAL=true SPRING_PROFILES_ACTIVE=test DB_NAME=campus_lost_found mvn test -Dtest=MatchRealPairsIT | 1通过；28/28召回，Hit@1/5/20=2/2/6（7%/7%/21%）；real-eval.log |
| sweep 单独打开 | CLF_SWEEP=true DB_NAME=campus_lost_found_test mvn test -Dtest=MatchEvalSweepIT | 1通过；340组，120/80时间切分；测试 Hit1 76.3%、Hit5 100%，tau .8 时难负34/201；sweep.log |
| 正常状态路径 | python matrix.py | 三裁决类型、撤回/拒绝、mark-found、取消、下架恢复终态、线索顺序白名单均实测；matrix.log |
| 鉴权 | python matrix.py / audit.py probes / extra.py | 未登录受保护端点401、普通用户管理端403、篡改 JWT role401、签名合法但过期401、logout撤销；资源口径差异另列 |
| 上传/输入 | python audit.py probes；python matrix.py | 2MB上传200；5MB+1和6MB+1都413；15项超长度400；未绑定PUBLIC匿名404；归属/数量用例真实IT通过 |
| 后台浏览器 | node browser.cjs：真实 Chromium、真实API、6视图、posts/users翻页 | 未登录路由返回login；无浏览器exception；可查看截图，功能缺口另列 |
| 小程序方法 | node frontend.cjs；node frontend-extra.cjs：实际Vue运行时+实际SFC方法、受控API响应 | HTTP502拒绝、timeout15秒、三详情404错误态、mark-found取消不发请求、关闭线索取消不发请求；未声称真机已通过 |
| prod 负面/正面 | python prod.py | 纯prod弱密钥拒绝、mock开启拒绝；安全纯prod mock403、Swagger/api-docs404；混合profile绕过另列 |

匹配代码审读确认是规则线性公式、category/location/time/keyword 子分和 reasons；没有模型打分。正常单活跃 ACCEPT 在真实 MySQL 测试中通过；不把它扩张为双确认/争议/链接竞态都正确。

数据维护：顺序结束路径上的 closed_at 已实测；取消/TERMINATE_REOPEN 清空，CLOSE/mark-found/withdraw 填充，下架再恢复保留终态关闭时间。resolve-lost 填充 resolved_by_claim_id/closed_at，但一 claim 多帖并发一致性失败。迁移重放证明 schema 可创建，不证明缺少的业务唯一约束被代码弥补。

性能 P95≈312ms 是用户给定既有结论，本次未重跑压力测试，不把它写成“我已测”。离线 mysqldump/隔离恢复、微信真机/HTTPS 为用户认可外部未执行项，不列为缺陷。

## 4. 66 项闭合抽查结果

要求范围全部完成：P0/P1 的 A1–A6、B1–B7、C1–C2 共15项；P2用固定随机种子20261001从 D1–D17/E1–E31/F1–F3 共51项随机抽15项。抽样标记 ★。为了复核实际异常又补查部分项目，标记“追加”。剩余明确为“非抽样”，没有冒称66项全部行为已验。

“通过”仅就该整改点；“部分/失败”详见发现；“代码/构建通过，行为待平台验证”不等同微信真机通过。仓库自动化列回答测试确实存在与否，独立测试不会追认成仓库原有回归。

| 编号 | 范围 | 仓库回归证据 | 本次闭合判断 |
|---|---|---|---|
| A1 | 必查 | BatchAFixesIT.a1 | 通过：真实Tomcat 2MB=200，超5/6MB=413 |
| A2 | 必查 | BatchAFixesIT.a2×2 | 长度整改通过：15个HTTP负例400；空PATCH另见P1-17 |
| A3 | 必查 | BatchAFixesIT.a3×3 | 通过：替换不重复、上限、外人文件真实IT |
| A4 | 必查 | BatchAFixesIT.a4 | 顺序冻结取消通过；并发冻结非该用例覆盖，见P0-01 |
| A5 | 必查 | BatchAFixesIT.a5 | 通过：FOUND不能被resolve-lost |
| A6 | 必查 | BatchAFixesIT.a6×3 | 通过：头像存在/归属校验及合法绑定 |
| B1 | 必查 | 无对应前端自动化；manual/build | 本次实际request.js 502拒绝通过；仓库缺自动回归 |
| B2 | 必查 | 无对应前端自动化；manual/build | 本次3详情404错误态通过；仓库缺自动回归 |
| B3 | 必查 | BatchBBackendIT.b3 | 通过：空校区保持旧值 |
| B4 | 必查 | 无对应前端自动化；manual/build | 本次取消mark-found零请求通过；仓库缺自动回归 |
| B5 | 必查 | 无对应前端自动化；manual/build | 分页组件存在，真实posts/users翻页；用户稳定分页失败P1-18，争议大量数据翻页未完整实操 |
| B6 | 必查 | 无对应前端自动化；build | 代码/产物生产占位常量通过；合法HTTPS由外部配置 |
| B7 | 必查 | BatchBBackendIT.b7（顺序） | 失败：并发一claim两LOST，P1-02 |
| C1 | 必查 | 脚本path集合 | 部分：54操作路由齐全，响应/鉴权/schema不一致P2-08 |
| C2 | 必查 | 脚本枚举集合 | 通过：35错误码缺失0 |
| D1 | 非抽样 | 已有分页IT/本次API | 追加检查页100001=400；不把负页clamp视作新缺陷 |
| D2 | 非抽样 | MatchServiceTest/smoke | 非抽样；代码批量取图存在，未独立量化SQL次数 |
| D3 | ★ | 无真实微信外呼回归，ApiAuthzIT为mock链 | 代码通过：3秒双超时/编码/事务下沉；真实code待外部验证 |
| D4 | 追加 | BatchDFixesIT.d4 | 未绑定PUBLIC 404通过；下架已绑定仍公开另见P1-10 |
| D5 | ★ | BatchDFixesIT.d5 | 通过：5次失败封禁429，真实IT |
| D6 | 追加 | smoke及独立API | 受限claim/lead/dispute/message/upload 403通过 |
| D7 | 非抽样 | BatchDFixesIT.d7 | 随全回归通过；未另起定向实验 |
| D8 | ★ | BatchDFixesIT.d8×2 | 通过：未来/2000年前日期拒绝 |
| D9 | ★ | BatchDFixesIT.d9（顺序） | 失败：并发CLOSED被改VIEWED，P1-04 |
| D10 | ★ | BatchDFixesIT.d10 | 通过：confirm/cancel非参与方404；其余口径P2-03 |
| D11 | 追加 | BatchDFixesIT.d11 | 普通reason审计通过；换行reason 500，P2-02 |
| D12 | 非抽样 | BatchDFixesIT.d12 | 随全回归通过；并未逐条核查新审计字段完整性 |
| D13 | ★ | BatchDFixesIT.d13×2 | 正常孤儿DB行/备份僵尸通过；配置化已读；物理删除失败/并发绑定待确认Q1 |
| D14 | 非抽样 | BatchDFixesIT.d14 | 随全回归通过；未另起定向实验 |
| D15 | 追加 | 独立prod HTTP/启动 | 通过：纯prod swagger/api-docs404，dev200 |
| D16 | ★ | 编译/启动/文档 | 通过：refreshTTL删除、幂等表预留按已定裁决 |
| D17 | 追加 | BatchDFixesIT.d17（顺序） | 失败：withdraw与submit竞态，P1-03 |
| E1 | 非抽样 | manual/build | 未选入定向行为抽查 |
| E2 | 非抽样 | manual/build | 未选入定向行为抽查 |
| E3 | 非抽样 | manual/build | 未选入定向行为抽查 |
| E4 | ★ | 无前端自动回归 | 失败：错误登录后新会话401不再处理，P2-04 |
| E5 | 非抽样 | manual/build | 代码含catch/确认取消；后端未经受理仍裁决P1-08另列 |
| E6 | 非抽样 | manual/build | 未选入证据预览URL释放的定向行为抽查 |
| E7 | ★ | package/main/build | 部分：入口移除；package-lock根仍依赖Pinia，P2-11 |
| E8 | 非抽样 | manual/build | 代码调用logout；后端撤销用例通过，未另做UI退出实验 |
| E9 | ★ | 无前端自动回归 | pages.json及3页hook确有修改；构建通过；原生下拉手势待微信工具 |
| E10 | 非抽样 | manual/build | 未选入定向行为抽查 |
| E11 | 追加 | 无前端自动回归 | 失败：reload守卫有效但loadMore无守卫，P1-13 |
| E12 | 追加 | 无前端自动回归 | 失败/部分：6处image缺@error，平台失败外观待确认P2-06 |
| E13 | 非抽样 | manual/build | 代码current传URL已读；未真机执行previewImage |
| E14 | 追加 | 无前端自动回归 | 失败：login loading未定义，连点两请求P1-14 |
| E15 | 非抽样 | manual/build | 未选入定向行为抽查 |
| E16 | 非抽样 | manual/build | 未选入定向行为抽查 |
| E17 | ★ | 无前端自动回归 | 通过：CLOSED隐藏处理区、状态按钮收敛；取消关闭零review请求 |
| E18 | 非抽样 | BatchEBackendIT.e18 | 随全回归通过；owner后端填充存在，未另起定向实验 |
| E19 | 追加 | 无前端自动回归 | 失败：第一页筛空隐藏更多按钮，P1-15 |
| E20 | 非抽样 | manual/build | 代码公共预检存在；未平台选图定向测试 |
| E21 | ★ | 无前端自动回归 | maxlength代码已改、构建通过；微信输入边界未实操，后端长度通过 |
| E22 | ★ | 无前端自动回归 | 编辑成功分支navigateBack代码存在、构建通过；原生页面栈未实操 |
| E23 | 非抽样 | manual/build | 未选入定向行为抽查 |
| E24 | ★ | 无前端自动回归 | onShow条件重载代码存在、构建通过；微信编辑返回生命周期未实操 |
| E25 | 非抽样 | manual/build | 未选入定向行为抽查 |
| E26 | 非抽样 | manual/build | 代码statusCode检查存在；未定向调用uploadFile平台API |
| E27 | 追加 | 独立request.js方法执行 | 通过：实际timeout参数15000 |
| E28 | 非抽样 | BatchEBackendIT.e28 | 随全回归通过；postTitle有字段，未另起定向实验 |
| E29 | ★ | 无前端自动回归 | 部分失败：资料502变未登录，P2-05；列表错误分支存在 |
| E30 | 非抽样 | manual/build | 正常浏览器dashboard通过；部分请求失败降级未定向实测 |
| E31 | 非抽样 | build | 代码删除前端refresh已读；后端refresh同秒冲突P1-05另列 |
| F1 | 非抽样 | 配置对照/build | 配置键外部化已读；未对所有覆盖组合做运行证明 |
| F2 | 非抽样 | 文档审读 | V4/匹配引用已写；其他真实性差异见偏差表 |
| F3 | 非抽样 | 文件/README | compose及测试库init存在；未运行全新Docker依赖，不冒称通过 |

## 5. 四份任务书与基线文档偏差

| 基线 | 已落实面 | 剩余偏差及证据 |
|---|---|---|
| 原开发执行任务书 §5/§9/§10.2 | 单体/三端/规则匹配/正常闭环存在；单活跃ACCEPT测试通过 | 双确认、争议冻结、withdraw并发失败；日志脱敏、UTC、授权管理员证明、后台治理历史和稳定分页未达标 |
| 原任务书 §7.2 | 表中全部正式 method+path 可在Controller找到；当前总54操作 | 用户提供“43”与原表展开数量不同：表格按独立method+path是47；旁文禁止的PATCH /posts/id/status不能计为要求端点。没有用计数差异冒充缺失 |
| 原任务书 §8 | 原10项学生功能合并到8个注册路由，后台5功能视图+login共6；不是单靠路由数判缺页 | 匹配/申请提交/交接/争议嵌入detail/claim，线索列表整合mine。缺后台公开详情/治理历史/用户审计链接；认领提交时间线无createdAt；错误/连点/分页竞态另列 |
| 验收整改与补全任务书 | assign端点、private dispute图片checker、前端受理/预览入口、multipart上限等确有落地 | 原始claim证明对assigned admin仍404；后端可跳过受理裁决；宣称的“最后确认与争议竞态已覆盖”不成立 |
| V4闭环链接任务书 | candidates/resolve-lost、类型/归属/终态/类别、持久关联ID、正常closed_at路径存在 | R4并发一claim多帖；手动列表首屏筛空不能分页 |
| 缺陷清零任务书 | 后端新增28条batch IT，真实测试存在且运行通过；多数长度/文件归属/正常异常分支修复 | 全P0/P1要求自动回归，但B1/B2/B4/B5/B6只有manual/build，无仓库前端自动回归；R2未加生产profile警示；B7/D9/D17/E11/E14/E19等闭合不成立 |
| README / deployment | 能按现有配置构建启动，真机HTTPS外部条件已说明 | R2警示缺失；README Pinia过时；不把占位HTTPS当成代码故障 |
| SRS / conventions | FR-POST-06与匹配改进引用已补；35错误码齐全 | UTC、公开下载与refresh认证、状态码、治理详情历史、授权与并发说明有偏差 |
| state-machines | 顺序转换图与正常实跑相符 | 写称confirm/raise同帖锁或并发安全，实际两方法无同一锁；OPEN冻结实际失效 |
| ER/迁移 | 空库19表26FK，V1–V4成功；一活跃申请/open争议约束存在 | UTC/停用会话失效/授权管理员claim proof文档承诺未实现；R4约束无跨目标并发保证 |
| demo-script / traceability / test-report / P0–P9 | 正常API演示可运行，记录作为定位线索 | API脚本不是微信目标端完整验收；需求映射存在不代表异常/并发全覆盖；“清零”结论被本次实验否定 |
| 三份match-eval报告 | 本次重新运行 baseline/real/sweep；候选召回100%在当前事实对上可复核 | 旧真实推导对160/0%未标为废弃；sweep默认参数标签仍旧。历史不同种子/日期的数字不强行要求完全相等 |

## 6. A–H 覆盖结论与测试体系审读

| 维度 | 本次结论 |
|---|---|
| A 运行 | 全部要求的构建、test、verify、空库、启动、33冒烟、Swagger均真跑通过；不能覆盖下列失败 |
| B 功能 | 正式端点存在覆盖；正常闭环API实跑；后台功能和V4分页入口有真实缺口；微信目标端完整手势/平台流程未证明 |
| C 安全 | 全管理Controller requireAdmin已读；401/403矩阵、JWT签名/过期/撤销、归属负例通过；授权争议、生产日志/混合profile、停用会话、文件治理和404口径失败 |
| D 状态/并发 | 顺序维护closed_at路径通过、单活跃ACCEPT通过；独立五类真实MySQL交错实验均暴露错误 |
| E 数据/迁移 | 新空库重放19表26FK通过；schema可建不等于R4业务不变量成立；closed_at与resolved字段正常路径存在，竞态会产生业务矛盾 |
| F 前端 | 8小程序路由/6后台视图代码核查；后台真实浏览器，学生端实际方法和Vue代理测试；竞态、连点、分页、错误态和汉化有缺口 |
| G 文档 | 路由/错误码可复核，契约语义和完成声明存在差异，详见§5 |
| H 测试/评估 | 测试确实存在并可运行，当前门禁断言不足；评估真实复跑，历史文件口径需纠正 |

**ClaimConcurrencyIT 审读：** 只有两个 ACCEPT 的并发，断言1success/1conflict、post HANDOVER、WAITING_HANDOVER 数1。runAccept catch(Exception) 把任何异常算“冲突”，没有确认输家是业务409，没有断言其他申请应CLOSED；不能证明死锁/500没发生，也不覆盖双确认/争议/withdraw/resolve。本次不否认它所验证的单活跃结果。

**ClaimResolveIT 审读：** 8项均顺序测试，覆盖正常关联、权限、类别/状态等；没有“同claim不同lost同时resolve”。BatchBBackendIT.b7、BatchDFixesIT.d9/d17同样是顺序测试。缺陷被本次真实行锁实验定位，不能再拿这几个绿灯作并发闭合依据。

**前端回归证据：** 仓库两前端没有与上述缺陷对应的自动化用例；package脚本和build只能证明编译。外部frontend脚本是本次审计新增测试工具，没有修改或补充仓库测试，因此不会追认开发方“已添加全部P0/P1自动化回归”。

**33项e2e口径：** 本次确实PASS33/FAIL0；但多数断言只检查JSON.code，未同时确认HTTP与数据库不变量。audit filter只验证OK没有确认目标操作条目；received列表只验证OK没有验证目标申请/线索包含；refresh只看token非空，没有证明token不同、旧会话已撤销、新会话可用；没有并发操作，没有三种完整争议裁决的最终SQL核查。故33不是33个严密业务不变量证明。

**评估口径：** MatchEvalGate确实调用真实候选/规则评分，baseline200对召回100%、Hit1约79%、Hit5/10/20=100%、MRR约.887在本次回归可复核。真实事实对按resolved_by_claim_id取样，28对召回100%，不再沿用启发式160对0%。sweep固定生产候选池，离线改时间子分而不重建候选窗口；排名只统计严格大于自身分数，未处理生产“同分按ID升序”会压过自身的候选，可能高估Hit@1。示例：真值id20与id10同分，生产第一是10，sweep better=0会把20计Hit1。这个代码级方法局限不冒充已量化全报告差异；后续应逐对把离线排序与生产matchesFor比对。没有将低Hit@K本身报告为缺陷。

### §10.2 关键用例逐项结论

| 原用例 | 本次证据/结论 |
|---|---|
| TC-AUTH-01 | 普通用户管理端403；纯prod mock403/弱配置拒启通过；混合profile失败P1-07 |
| TC-AUTH-02 | JWT role篡改401、后端角色由会话决定通过 |
| TC-POST-01 | 两类型发布/图片绑定/详情实跑；事件时间与发布时间不同；UTC契约失败P1-11 |
| TC-POST-02 | ApiAuthzIT其他人编辑403/申请后核心字段锁通过；PATCH空值失败P1-17 |
| TC-FILE-01 | 陌生私密证明404通过；授权管理员claim证明缺口P1-09 |
| TC-SEARCH-01 | 公开筛选/API分页可用；后台固定分页重复P1-18，小程序旧页污染P1-13；公开搜索所有过滤组合未穷举 |
| TC-MATCH-01 | 规则评分/reasons/相反类型/关闭排除单元+真实评估通过 |
| TC-LEAD-01 | 参与方读取/陌生详情404、mark-found正常通过；review403口径与并发白名单失败 |
| TC-CLAIM-01 | ApiAuthzIT selfClaim_forbidden与smoke通过 |
| TC-CLAIM-02 | 唯一约束/重复申请相关现有用例与冒烟通过；未补做独立同人提交高并发压力 |
| TC-CLAIM-03 | 真实MySQL ClaimConcurrencyIT单活跃ACCEPT通过；输家具体异常断言不足 |
| TC-CLAIM-04 | 撤回/拒绝及无效状态相关IT正常通过；withdraw与submit竞态失败P1-03 |
| TC-HANDOVER-01 | 一方确认仍WAITING正常通过 |
| TC-HANDOVER-02 | 顺序双确认正常；同时双确认失败P1-01 |
| TC-DISPUTE-01 | 已有OPEN顺序冻结正常；最后确认并发失败P0-01 |
| TC-DISPUTE-02 | 普通用户管理端403；未受理管理员可裁决/正文泄漏P1-08 |
| TC-ADMIN-01 | 下架/恢复列表及终态维护实跑；图片匿名仍200、治理历史UI不足 |
| TC-ADMIN-02 | restricted发布/认领/留言/线索/争议/上传实测或真实IT拒绝 |
| TC-BACKUP-01 | API图片manifest/backup record已实跑；DB导出与隔离恢复属用户明确未执行外部项 |
| TC-E2E-01 | API完整主流程33冒烟通过；微信目标端完整操作待外部平台验证 |
| TC-E2E-02 | 三裁决类型API+SQL顺序状态实测；并发终态失败及授权缺口另列 |

### 前端逐页核查

| 实际页面 | 原10+5功能映射及结论 |
|---|---|
| mini index | 信息流/分类，构建及实际Vue方法；旧loadMore混入 |
| mini search | 多条件搜索/加载更多，方法验证；旧loadMore混入 |
| mini login | mock/微信入口；loading未定义、双调用；真实code未验 |
| mini detail | 公开帖/匹配分数reasons/申请或线索表单/编辑撤回mark-found；404错误态及mark-found取消通过；图片/连点缺口 |
| mini publish | 发布与编辑合一、长度和图片预检、edit返回；双创建请求；原生页面栈待验 |
| mini claim/detail | 审核/沟通/确认/争议/V4手动关联；错误态通过；手动分页不可达、提交时间缺失 |
| mini lead/detail | 私密详情/状态处理；404错误态及关闭取消通过；图片失败处理缺；平台真机未验 |
| mini mine | 资料及5类相关记录聚合；owner由后端；资料错误和tab竞态失败 |
| admin Login | 真浏览器登录页面可打开；401去重错误已方法复现 |
| admin Dashboard | 真浏览器卡片/近期日志显示；无exception；部分失败降级未定向触发 |
| admin Posts | 真浏览器筛选/分页可用；公开详情和治理历史缺 |
| admin Users | 真浏览器列表/分页可用；跨页重复/汉化/审计链接缺 |
| admin Disputes | 真浏览器列表，代码受理/预览/裁决入口存在；实际后端绕过受理，无法读原claim proof |
| admin Audit | 真浏览器动作/对象筛选和备份tab；reason/metadata/actorId展示缺 |

### API 逐端点核对说明

以下54行来自本次 Controller 与 OpenAPI method+path 一一比对；“路由齐全”不是“行为全通过”。未登录/普通用户列为实际HTTP矩阵结果；公共登录和真实微信外呼另注。资源级、正常闭环、争议三裁决和输入测试见上文，所有失败保留明确编号。原§7.2正式47操作均包含于此；新增7项为health、leads/id详情、received两聚合、assign和V4两操作。


| method | path | Controller↔OpenAPI | 未登录/普通用户管理端实测 | 主要行为证据或限制 |
|---|---|---|---|---|
| GET | /admin/audit-logs | 存在/集合匹配 | unauth=401；ordinary=403 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /admin/auth/login | 存在/集合匹配 | 公开/特例：见右栏 | 登录成功；限流IT通过；同秒token缺陷 |
| GET | /admin/disputes | 存在/集合匹配 | unauth=401；ordinary=403 | 代码及正常API/IT链路已核；资源失败详见发现 |
| GET | /admin/disputes/{disputeId} | 存在/集合匹配 | unauth=401；ordinary=403 | 未受理读取私密正文200 |
| POST | /admin/disputes/{disputeId}/assign | 存在/集合匹配 | unauth=401；ordinary=403 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /admin/disputes/{disputeId}/resolution | 存在/集合匹配 | unauth=401；ordinary=403 | 三裁决顺序SQL通过；未受理也能裁决 |
| GET | /admin/maintenance/backups | 存在/集合匹配 | unauth=401；ordinary=403 | API图片manifest成功/DB记录；离线DB导出恢复未执行 |
| POST | /admin/maintenance/backups | 存在/集合匹配 | unauth=401；ordinary=403 | API图片manifest成功/DB记录；离线DB导出恢复未执行 |
| GET | /admin/posts | 存在/集合匹配 | unauth=401；ordinary=403 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /admin/posts/{postId}/remove | 存在/集合匹配 | unauth=401；ordinary=403 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /admin/posts/{postId}/restore | 存在/集合匹配 | unauth=401；ordinary=403 | 代码及正常API/IT链路已核；资源失败详见发现 |
| GET | /admin/users | 存在/集合匹配 | unauth=401；ordinary=403 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /admin/users/{userId}/restrictions | 存在/集合匹配 | unauth=401；ordinary=403 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /admin/users/{userId}/unrestrict | 存在/集合匹配 | unauth=401；ordinary=403 | 代码及正常API/IT链路已核；资源失败详见发现 |
| GET | /auth/campus/capabilities | 存在/集合匹配 | 公开/特例：见右栏 | 公开能力说明；不虚构真实校园认证 |
| POST | /auth/logout | 存在/集合匹配 | 公开/特例：见右栏 | 已登录退出撤销IT；无token安全退出不报401为设计特例 |
| POST | /auth/mock/login | 存在/集合匹配 | 公开/特例：见右栏 | dev成功；纯prod403；混合profile200绕过 |
| POST | /auth/refresh | 存在/集合匹配 | unauth=401 | 需token；紧接登录实测500，P1-05 |
| POST | /auth/wechat/login | 存在/集合匹配 | 公开/特例：见右栏 | 真实微信code外呼未执行；代码审读、mock替代正常链路 |
| GET | /claims/{claimId} | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /claims/{claimId}/cancel-handover | 存在/集合匹配 | unauth=401 | 顺序OPEN冻结通过；换行reason500 |
| POST | /claims/{claimId}/confirmations | 存在/集合匹配 | unauth=401 | 顺序正常；同时确认停滞、争议并发终态错 |
| GET | /claims/{claimId}/disputes | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /claims/{claimId}/disputes | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| GET | /claims/{claimId}/messages | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /claims/{claimId}/messages | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /claims/{claimId}/resolve-lost | 存在/集合匹配 | unauth=401 | 顺序权限/类别/终态IT8通过；一claim多帖失败 |
| GET | /claims/{claimId}/resolved-candidates | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /claims/{claimId}/review | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /claims/{claimId}/withdraw | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /files | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| GET | /files/{fileId} | 存在/集合匹配 | 公开/特例：见右栏 | 未绑定匿名404/绑定公开200/私密外人404；下架仍200、assigned claim proof404 |
| GET | /health | 存在/集合匹配 | 公开/特例：见右栏 | HTTP200 |
| GET | /leads/{leadId} | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /leads/{leadId}/review | 存在/集合匹配 | unauth=401 | 顺序白名单409；并发CLOSED回退；外人403违裁决 |
| GET | /posts | 存在/集合匹配 | 公开/特例：见右栏 | 公开列表/筛选/详情实跑；非法类型转换500 |
| POST | /posts | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| GET | /posts/search | 存在/集合匹配 | 公开/特例：见右栏 | 公开列表/筛选/详情实跑；非法类型转换500 |
| GET | /posts/{postId} | 存在/集合匹配 | 公开/特例：见右栏 | 公开列表/筛选/详情实跑；非法类型转换500 |
| PATCH | /posts/{postId} | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| GET | /posts/{postId}/claims | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /posts/{postId}/claims | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| GET | /posts/{postId}/leads | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /posts/{postId}/leads | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| POST | /posts/{postId}/mark-found | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| GET | /posts/{postId}/matches | 存在/集合匹配 | 公开/特例：见右栏 | 公开规则匹配、reasons与真实评估；OpenAPI误标bearer |
| POST | /posts/{postId}/withdraw | 存在/集合匹配 | unauth=401 | 正常撤回/closed_at通过；并发新申请失败 |
| GET | /users/me | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| PATCH | /users/me | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| GET | /users/me/claims | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| GET | /users/me/leads | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| GET | /users/me/posts | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| GET | /users/me/received-claims | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |
| GET | /users/me/received-leads | 存在/集合匹配 | unauth=401 | 代码及正常API/IT链路已核；资源失败详见发现 |

## 7. 输出及环境善后

审计新建的空库为 clf_audit_empty_20261001_1790838438（保留作为迁移证据，未删除其他数据库）。测试库保留故障实验记录；开发库保留一次性冒烟数据。服务、Vite和审计专用浏览器进程在取证后停止，不清除用户正在使用的其他进程。

构建目录为工具正常输出。git diff --stat 为空；不提交git。审计开始时有两份未跟踪任务书；结束时另出现start-admin.bat/start-backend.bat，本次命令未创建或编辑这些文件，均未处理。不能把git status中未跟踪文件消失作为“只审计”的证明。

证据均位于本报告目录；测试token已脱敏。独立脚本只加载本机env，不含固化密码。重跑前按脚本说明启动测试服务8081/JDK17/MySQL，并配置环境变量，实验会新增测试记录；prod.py/extra.py会使用测试库临时启动8082生产实验服务。node frontend*.cjs使用实际仓库Vue/SFC方法和受控API响应；browser.cjs需运行后台5173与独立Chrome CDP9223。报告结论不依赖报告文件本身，原始输出与脚本均提供。
