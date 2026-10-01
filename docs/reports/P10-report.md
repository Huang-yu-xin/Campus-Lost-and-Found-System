# P10 终审整改与验证报告

日期：2026-10-01。环境：Windows、JDK17.0.18、MySQL8.0.34。整改基线为 ed195bd29e7f8a972d7accd12dc5c870d23da97e，历史独立审计见 [整改前快照](independent-audit-20261001.md)。

本轮按用户授权修改并执行测试，未以原交付报告代替验证。已处理终审31项发现及Q1清理风险；不重新宣称历史66项在微信真机、离线恢复等环境下“全部实测闭合”。本报告记录执行范围，最终验收仍应结合外部部署条件。

## 实际执行

| 项目 | 命令/方法 | 结果 |
|---|---|---|
| 单元 | server: mvn test（亦随verify执行） | 33项，0失败/错误 |
| 全量 | CLF_IT=true DB_NAME=campus_lost_found_test mvn verify | 33单元；66集成计数，64实跑通过、2手动评估跳过；BUILD SUCCESS |
| 新增终审回归 | FinalAcceptanceIT | 17项，包括真实MySQL持锁交错、权限/时间/输入/文件清理 |
| 前端行为 | node --test tests/frontend/regression.cjs；双端 npm test | 21项，0失败，已加入CI |
| HTTP | dev服务 + tests/e2e/e2e-smoke.sh | PASS35 / FAIL0；同时核对HTTP与业务码、指定申请/审计记录、refresh撤销及新会话有效 |
| 迁移 | 新建空库，mysql逐一执行V1..V4 | 每条脚本exit0；19业务表、26外键 |
| 服务/Swagger | dev 8080及测试8081；health / swagger-ui/index.html | HTTP200；正常prod下Swagger和api-docs404 |
| 生产守卫 | 8个独立Java进程配置 | 7个不安全配置拒启（含强密钥+mock关闭的prod,dev）；安全prod可启动，mock403；私密请求正文不在生产日志 |
| 双端构建 | admin npm run build；mini npm run build:mp-weixin | 均成功；后台bundle大小提示为非失败 |
| 浏览器 | Chrome无头实际五后台视图与未登录跳转 | 无Runtime异常；posts/users翻页；无会话转/login |
| 独立并发HTTP复核 | 同时确认、最后确认/争议、双关联、撤回/提交 | 双确认COMPLETED/COMPLETED/2；最后确认200而争议409；双关联仅1成功；撤回200而提交409 |
| 契约 | 枚举Controller映射对比OpenAPI | 55操作、47路径；missing_doc=[]、extra_doc=[] |
| 调参评估 | CLF_SWEEP=true mvn test -Dtest=MatchEvalSweepIT | 340配置，120/80切分；现行.40/.25/.30/.05、14天，测试Hit@1=76.3% |
| 事实评估 | CLF_EVAL_REAL=true DB_NAME=campus_lost_found mvn test -Dtest=MatchRealPairsIT | 29事实对，召回29/29，Hit@1/@5/@20=10%/10%/24% |

dev与ci两种profile的完整verify均通过。混合prod与dev/test/ci即使强密钥且mock关闭也被显式拒启；隔离CI库启用mock仅供登录回归。冒烟使用独立临时图片与EXIT清理，兼容Git Bash调用Windows curl。

测试先设置JAVA_HOME与PATH，加载deploy/.env，使用隔离上传/备份目录；密码未归档。手动评估由显式环境变量启用，绝不把skip当pass。并发测试只允许预期409业务冲突，意外异常使测试失败。

## 发现对应的修改与证据

| 编号 | 原问题 | 修复与可重跑证据 |
|---|---|---|
| P0-01 | 争议与完成互斥 | 共享帖子/申请行锁、READ_COMMITTED；FinalAcceptanceIT.finalConfirmationAndDisputeCannotBothCommit |
| P0-02 | 虚假闭合声明 | 撤销 P9 全部闭合结论，保留历史；逐项列出本轮验证和外部条件 |
| P1-01 | 并发双确认 | 锁后读取确认数；simultaneousConfirmationsAlwaysComplete |
| P1-02 | 一个申请关联两寻物帖 | 共同锁 claim 后复核；oneClaimCannotResolveTwoLostPostsConcurrently |
| P1-03 | 撤回与新认领竞争 | 提交与撤回同锁帖子；withdrawingPostCannotLeaveNewPendingClaim |
| P1-04 | CLOSED 线索回退 | 帖子/线索锁与条件更新；closedLeadNeverReopensUnderConcurrentReview |
| P1-05 | 同秒 JWT 冲突 | 随机 jti；JwtServiceTest + 登录/refresh 集成 + HTTP 冒烟 |
| P1-06 | 生产日志私密正文 | 全局/生产 INFO；真实 prod 私密认领 HTTP200，唯一 marker 不在日志 |
| P1-07 | 混合 profile 绕过 | prod 优先守卫；单测及 prod,dev/prod,test/prod,ci 实际拒启 |
| P1-08 | 未受理管理员裁决及正文 | 未受理私密字段不返回，裁决 404；unassignedAdminCannotReadPrivateDetailsOrResolve |
| P1-09 | 已受理管理员原证明 | 按 assignedAdminId 授权原认领正文/文件；assignedAdminCanReadOriginalClaimProofButAnotherCannot |
| P1-10 | 下架图片仍公开 | 公开下载核实真实关联和帖子状态；removedPostImageNotAnonymousAndGovernanceHistoryAvailable |
| P1-11 | UTC 契约 | Clock.systemUTC、MySQL session +00:00、偏移解析与 Z 输出、小程序本地展示；UTC 单测与真实DB/API测试；历史数据需来源核对，见部署文档 |
| P1-12 | 治理详情/历史/用户审计 | 新增管理员帖子详情、治理历史、对象ID审计过滤和相应后台入口；API IT + 后台构建/浏览器页验证 |
| P1-13 | 旧分页污染筛选 | 分页也校验请求序号，成功后更新页码；index/search/mine 前端回归 |
| P1-14 | 登录发布连点 | 定义 loading、调用入口防连点与 finally；前端回归 |
| P1-15 | 手动筛选第一页为空 | 更多入口取决于原始分页耗尽；前端空第一页到第二页回归 |
| P1-16 | 停用会话仍活跃 | 会话查验排除 DISABLED；disabledAccountInvalidatesExistingSession |
| P1-17 | PATCH 必填为空 | 显式非空白校验；badParametersAndBlankPartialUpdateAre400 |
| P1-18 | 分页重复 | created_at/started_at 加 ID 次序；fixedDatasetPaginationHasNoOverlap |
| P2-01 | 类型/缺少参数 500 | 常见参数异常统一400；真实 MockMvc 负例 |
| P2-02 | 换行理由破坏JSON | ObjectMapper JSON序列化；auditReasonsSupportControlCharacters |
| P2-03 | 资源防枚举 | 申请、线索的归属错误改为404；outsiderResourceLookupsUse404 |
| P2-04 | 401 永久去重 | 按会话更新标志，错误登录不触发，会话移除后的并发401也去重；前端回归 |
| P2-05 | 资料502显示未登录 | 独立 profileError/profileLoading 与重试；前端502/401分别回归 |
| P2-06 | 缺少图片失败态 | 详情/申请/线索/发布各缩略图增加 error 标记和占位；源码审读 + 小程序生产构建，真机视觉见外部条件 |
| P2-07 | 状态与时间线 | 后台状态/动作汉化、ClaimDetail.createdAt、备份返回新记录；浏览器各页、后端IT与构建 |
| P2-08 | OpenAPI 语义 | 55操作/47路径，HTTP200封装、公开matches、文件条件鉴权、int64 ID、准确DTO/返回类型；Controller集合差异为空 |
| P2-09 | 生产警示 | 部署文档与README明确显式prod，强密钥、禁止mock及混合环境 |
| P2-10 | 评估混淆 | 旧160推导对明确废弃；新29事实对100%候选召回；sweep340配置按生产SQL候选/四位小数/ID次序、现行默认动态读取，手动评估均实际执行 |
| P2-11 | Pinia残留 | 重建package-lock，README移除不实依赖；npm构建通过 |
| Q1 | 孤儿文件清理 | 失败保留DB行供重试；行锁后重新检查绑定，新增物理删除失败与绑定竞争两个真实MySQL回归 |

## 历史66项闭合抽查的重核范围

保留全部66项历史结果于整改前快照。这里按同一固定随机种子20261001重核必查A1–A6、B1–B7、C1–C2及15项P2；它们是30项抽查，不等同于66项全体的新实测声明。

| 编号 | 本轮证据 | 判断 |
|---|---|---|
| A1 | BatchAFixesIT上传上限/真类型，全量回归 | 通过 |
| A2 | 原长度负例 + 新空白PATCH负例 | 通过 |
| A3 | 图片替换/上限/归属IT | 通过 |
| A4 | 顺序冻结 + 最后确认/争议并发IT | 通过 |
| A5 | FOUND不能作为resolve-lost目标IT | 通过 |
| A6 | 头像存在/归属/绑定IT | 通过 |
| B1 | 实际request模块502/timeout前端回归 | 通过 |
| B2 | 3详情404错误态前端回归 | 通过 |
| B3 | BatchBBackendIT资料PATCH | 通过 |
| B4 | 取消mark-found/关闭线索零请求前端回归 | 通过 |
| B5 | 三个后台分页/筛选复位方法回归、浏览器posts/users翻页、DB稳定次序IT | 通过；未冒称大量争议数据浏览器翻页 |
| B6 | 生产/开发域名常量回归及生产构建 | 代码通过；合法HTTPS/真机为外部条件 |
| B7 | 单申请双LOST真实并发IT | 通过 |
| C1 | 路由集合 + DTO/schema/响应/鉴权校正 | 本轮范围通过，不等于每个任意负例已穷举 |
| C2 | 错误码全集对照及400/401/403/404/409真实断言 | 通过 |
| D16 | 配置/保留幂等表裁决及编译运行 | 通过 |
| E4 | 错误登录后新会话401、并发去重前端回归 | 通过 |
| E7 | package与lock移除Pinia，构建 | 通过 |
| E17 | 取消关闭零review调用、CLOSED按钮条件审读 | 方法通过；原生按钮视觉待真机 |
| E21 | maxlength源码与后端长度IT | 代码/后端通过；原生输入操作待真机 |
| E29 | 资料502与401分别回归、列表错误态 | 通过 |
| E24 | 详情onShow编辑返回刷新回归 | 方法通过；原生生命周期待真机 |
| E9 | 配置及下拉hook、生产构建 | 代码通过；原生下拉手势待真机 |
| D8 | 未来/过旧时间IT + UTC偏移测试 | 通过 |
| E22 | 编辑成功navigateBack前端回归 | 方法通过；原生页面栈待真机 |
| D10 | 参与方及非参与方404 IT | 通过 |
| D13 | 物理删除失败重试、并发绑定后不误删IT | 通过 |
| D5 | 失败登录限流429 IT | 通过 |
| D9 | 终态线索锁竞争IT | 通过 |
| D3 | 既有3秒双超时/事务下沉代码审读 | 代码保持；真实微信code外呼待合法平台条件 |

## 文档与外部条件

README沿用原有项目介绍、快速开始、测试和文档结构，补充当前使用/部署说明，不记录逐次修复流水。SRS、状态机、追踪矩阵、演示脚本、部署、契约与评估同步；P9为撤销全部闭合结论的历史记录。

以下不冒称完成：微信合法HTTPS真机交互、真实code2session、离线MySQL导出及隔离恢复。历史DATETIME无来源时区，不能安全地统一减8小时；新增写入/连接/JSON已统一UTC，旧数据上线前由运维按来源核对。原性能P95记录未在本轮重压，未作为新结果宣称。

测试产生的一次性业务数据仅用于验证；测试/开发数据库、用户既有上传与备份不作破坏性清理。按用户要求，临时脚本、日志、截图、浏览器profile及构建产物在验证后删除；正式代码、永久测试、评估结果与报告保留。日志内可能含测试token，未提交。

验收建议：本轮代码与自动化回归范围通过；部署前完成上述外部条件及历史时间核对。本轮不是新的微信平台全流程签字验收。
