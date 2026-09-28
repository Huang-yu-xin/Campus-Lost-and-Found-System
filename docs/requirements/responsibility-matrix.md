# 六模块责任矩阵 (Responsibility Matrix)

> 每项 FR/NFR 指定负责人（A/B/C）、优先级（P=本期必做）、关联接口与关键测试用例。
> 完整字段级追踪见 `docs/requirements/traceability.md`。

## 功能需求 (FR)

| ID | 功能 | 负责人 | 优先级 | 主要接口 | 关键用例 |
|---|---|---|---|---|---|
| FR-AUTH-01 | 微信登录 | A | 必做 | `POST /auth/wechat/login` | 有凭据环境登录成功 |
| FR-AUTH-02 | 受限测试登录 | A | 必做 | `POST /auth/mock/login` | TC-AUTH-01 |
| FR-AUTH-03 | 管理员登录与权限 | A | 必做 | `POST /admin/auth/login` | TC-AUTH-02 |
| FR-AUTH-04 | 会话生命周期 | A | 必做 | `POST /auth/logout`,`/auth/refresh` | 未登录 401、失效 Token 拒绝 |
| FR-AUTH-05 | 校园认证扩展点 | A | 必做 | `GET /auth/campus/capabilities` | 返回 disabled |
| FR-USER-01 | 用户资料 | A | 必做 | `GET/PATCH /users/me` | 只能改自己 |
| FR-USER-02 | 校园身份展示 | A | 必做 | （随资料返回状态） | 不误标"已认证" |
| FR-ADMIN-01 | 信息治理(下架/恢复) | A | 必做 | `POST /admin/posts/{id}/remove\|restore` | TC-ADMIN-01 |
| FR-ADMIN-02 | 用户治理(限制/解除) | A | 必做 | `POST /admin/users/{id}/restrictions\|unrestrict` | TC-ADMIN-02 |
| FR-AUDIT-01 | 审计日志 | A | 必做 | `GET /admin/audit-logs` | 普通用户不可访问 |
| FR-BACKUP-01 | 数据备份 | A | 必做 | `POST/GET /admin/maintenance/backups` | TC-BACKUP-01 |
| FR-BACKUP-02 | 恢复验证 | A | 必做 | 独立离线脚本 | TC-BACKUP-01(隔离恢复) |
| FR-POST-01 | 发布寻物(LOST) | B | 必做 | `POST /posts` | TC-POST-01 |
| FR-POST-02 | 发布招领(FOUND) | B | 必做 | `POST /posts` | TC-POST-01 |
| FR-FILE-01 | 安全上传 | B | 必做 | `POST /files`,`GET /files/{id}` | TC-FILE-01 |
| FR-POST-03 | 列表及详情 | B | 必做 | `GET /posts`,`GET /posts/{id}` | TC-SEARCH-01 |
| FR-POST-04 | 我的发布与编辑 | B | 必做 | `GET /users/me/posts`,`PATCH /posts/{id}` | TC-POST-02 |
| FR-POST-05 | 完成/关闭展示 | B | 必做 | `POST /posts/{id}/mark-found`,`/withdraw` | 状态机校验 |
| FR-SEARCH-01 | 组合筛选 | B | 必做 | `GET /posts/search` | TC-SEARCH-01 |
| FR-MATCH-01 | 双向候选匹配 | B | 必做 | `GET /posts/{id}/matches` | TC-MATCH-01 |
| FR-MATCH-02 | 可解释排序 | B | 必做 | `GET /posts/{id}/matches` | 分数可复算 |
| FR-MATCH-03 | 规则验证 | B | 必做 | （测试报告） | 误/漏匹配分析 |
| FR-CLAIM-01 | 提交申请 | C | 必做 | `POST /posts/{id}/claims` | TC-CLAIM-01 |
| FR-CLAIM-02 | 申请列表 | C | 必做 | `GET /users/me/claims`,`GET /posts/{id}/claims` | 无关用户不可读 |
| FR-CLAIM-03 | 审核 | C | 必做 | `POST /claims/{id}/review` | 终态不可审核 |
| FR-CLAIM-04 | 单活跃交接 | C | 必做 | `POST /claims/{id}/review`(accept) | TC-CLAIM-03 并发 |
| FR-CLAIM-05 | 撤销/失败恢复 | C | 必做 | `POST /claims/{id}/withdraw`,`/cancel-handover` | TC-CLAIM-04 |
| FR-HANDOVER-01 | 双方确认 | C | 必做 | `POST /claims/{id}/confirmations` | TC-HANDOVER-01/02 |
| FR-HANDOVER-02 | 终态限制 | C | 必做 | （状态机） | 争议期暂停 |
| FR-MSG-01 | 申请内留言 | C | 必做 | `GET/POST /claims/{id}/messages` | 非参与者不可读 |
| FR-LEAD-01 | 寻物线索反馈 | C | 必做 | `POST/GET /posts/{id}/leads` | TC-LEAD-01 |
| FR-LEAD-02 | 线索处理 | C | 必做 | `GET /users/me/leads`,`POST /leads/{id}/review` | 不擅定归属 |
| FR-DISPUTE-01 | 发起争议 | C | 必做 | `POST /claims/{id}/disputes` | TC-DISPUTE-01 |
| FR-DISPUTE-02 | 争议处理 | C | 必做 | `POST /admin/disputes/{id}/resolution` | TC-DISPUTE-02 |
| FR-DISPUTE-03 | 结果可见 | C | 必做 | `GET /claims/{id}/disputes` | 不展示他人证据 |

## 非功能需求 (NFR)

| ID | 指标/风险 | 负责人 | 验收方法 |
|---|---|---|---|
| NFR-SEC-01 | 越权/水平权限 | 全员(A 主导框架) | 改 URL/ID 访问他人资源全部被拒 |
| NFR-SEC-02 | 私密文件保护 | B(文件)+C(证据) | 直连物理路径/越权文件 ID 不可取 |
| NFR-SEC-03 | 安全认证 | A | 认证单测、生产禁用 Mock、弱口令检查 |
| NFR-SEC-04 | 输入与上传 | 全员 | 注入/超长/伪装图片/重复请求负测 |
| NFR-PRIV-01 | 数据最小化 | 全员 | schema 核对、日志抽样无敏感正文 |
| NFR-CONS-01 | 状态一致性 | C(主)+B | 并发接受/最后确认与争议竞态测试 |
| NFR-REL-01 | 备份可恢复 | A | 隔离库恢复 + 校验和 |
| NFR-PERF-01 | 查询性能 | B | 实测 P95（目标示例，测后填实测） |
| NFR-UX-01 | 可操作性 | 全员 | 小程序端全流程实操 |
| NFR-MAINT-01 | 可维护性 | 全员 | 新环境按 README 复现 |

## 跨模块写操作的事务所有者（关键）

| 场景 | 事务所有者 | 说明 |
|---|---|---|
| 接受认领 → 招领 ACTIVE→HANDOVER + 其他待审申请 CLOSED | M5 (C) | 同事务锁定父发布，保证单活跃交接 |
| 双方确认齐全 → 招领 HANDOVER→COMPLETED + 申请 COMPLETED | M5 (C) | 服务端自动归并，禁止任意 PATCH |
| 争议 OPEN → 暂停交接完成 | M6 (C) | 由"存在 OPEN 争议"派生暂停 |
| 管理员下架 → 招领 REMOVED + 关联申请/争议按治理规则处置 | M2 (A) 调用 M3/M5 领域服务 | 保留下架前状态，不擅自重置 |
| 发布服务状态推进 | M3 (B) 提供业务接口，C 调用 | C 不直接改发布状态 |
