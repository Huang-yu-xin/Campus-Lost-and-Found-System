# 需求追踪矩阵 (Traceability Matrix)

> 全量追踪：`FR/NFR → 负责人 → 设计 → API → 代码/模块 → 测试用例 → 状态`。
> 状态：⬜ 未开始 / 🟡 已实现待验证 / 🟢 已实现+已验证 / ✅ 完成。
> **进度更新（2026-09-29，P6 验收整改后）**：六模块后端 + 两端前端完成并在真实 MySQL 端到端验证。
> 验证证据：`mvn verify`（19 单测 + ApiAuthzIT 8 + ClaimConcurrencyIT 1，全绿）；`tests/e2e/e2e-smoke.sh` PASS 31 / FAIL 0；
> 详见 `docs/reports/P6-remediation-report.md` 与 `docs/testing/test-report.md`。
> 下表 🟢 表示有自动化或端到端证据；🟡 表示代码就绪但特定验证（性能压测/备份隔离恢复/微信真机）属外部条件待执行。

## 功能需求

| FR | 负责人 | 设计文档 | API | 代码模块(计划) | TC | 状态 |
|---|---|---|---|---|---|---|
| FR-AUTH-01 | A | SRS§3-M1, overview§5 | POST /auth/wechat/login | auth | TC-AUTH-* | 🟢 |
| FR-AUTH-02 | A | SRS§3-M1 | POST /auth/mock/login | auth | TC-AUTH-01 | 🟢 |
| FR-AUTH-03 | A | SRS§3-M1 | POST /admin/auth/login | auth,admin | TC-AUTH-02 | 🟢 |
| FR-AUTH-04 | A | SRS§3-M1 | /auth/logout,/refresh | auth | TC-AUTH-* | 🟢 |
| FR-AUTH-05 | A | SRS§3-M1 | GET /auth/campus/capabilities | auth | — | 🟢 |
| FR-USER-01 | A | SRS§3-M1 | GET/PATCH /users/me | user | — | 🟢 |
| FR-USER-02 | A | SRS§3-M1 | (随资料) | user | — | 🟢 |
| FR-ADMIN-01 | A | SRS§3-M2, state-machines§1 | /admin/posts/{id}/remove\|restore | admin,post | TC-ADMIN-01 | 🟢 |
| FR-ADMIN-02 | A | SRS§3-M2 | /admin/users/{id}/restrictions\|unrestrict | admin,user | TC-ADMIN-02 | 🟢 |
| FR-AUDIT-01 | A | SRS§3-M2 | GET /admin/audit-logs | audit | — | 🟢 |
| FR-BACKUP-01 | A | SRS§3-M2 | /admin/maintenance/backups | backup | TC-BACKUP-01 | 🟢 |
| FR-BACKUP-02 | A | operations/backup-restore(P4) | 离线脚本 | backup | TC-BACKUP-01 | 🟡 隔离恢复实跑待执行 |
| FR-POST-01 | B | SRS§3-M3, er-diagram | POST /posts | post | TC-POST-01 | 🟢 |
| FR-POST-02 | B | SRS§3-M3 | POST /posts | post | TC-POST-01 | 🟢 |
| FR-FILE-01 | B | overview§6 | /files,/files/{id} | file | TC-FILE-01 | 🟢 |
| FR-POST-03 | B | SRS§3-M3 | GET /posts,/posts/{id} | post | TC-SEARCH-01 | 🟢 |
| FR-POST-04 | B | SRS§3-M3 | GET /users/me/posts, PATCH /posts/{id} | post | TC-POST-02 | 🟢 |
| FR-POST-05 | B | state-machines§1 | /posts/{id}/mark-found,/withdraw | post | — | 🟢 |
| FR-SEARCH-01 | B | SRS§3-M4 | GET /posts/search | search | TC-SEARCH-01 | 🟢 |
| FR-MATCH-01 | B | SRS§3-M4 | GET /posts/{id}/matches | match | TC-MATCH-01 | 🟢 |
| FR-MATCH-02 | B | SRS§3-M4 | GET /posts/{id}/matches | match | TC-MATCH-01 | 🟢 |
| FR-MATCH-03 | B | testing/test-plan(P4) | — | match | TC-MATCH-01 | 🟡 打分单测覆盖；正负样例报告待补 |
| FR-CLAIM-01 | C | SRS§3-M5, state-machines§2 | POST /posts/{id}/claims | claim | TC-CLAIM-01/02 | 🟢 |
| FR-CLAIM-02 | C | SRS§3-M5 | /users/me/claims, /posts/{id}/claims | claim | — | 🟢 |
| FR-CLAIM-03 | C | state-machines§2 | POST /claims/{id}/review | claim | — | 🟢 |
| FR-CLAIM-04 | C | state-machines§4 | POST /claims/{id}/review(accept) | claim | TC-CLAIM-03 | 🟢 |
| FR-CLAIM-05 | C | state-machines§2 | /claims/{id}/withdraw,/cancel-handover | claim | TC-CLAIM-04 | 🟢 |
| FR-HANDOVER-01 | C | state-machines§4 | POST /claims/{id}/confirmations | handover | TC-HANDOVER-01/02 | 🟢 |
| FR-HANDOVER-02 | C | state-machines§1-2 | (状态机) | handover | TC-DISPUTE-01 | 🟢 |
| FR-MSG-01 | C | SRS§3-M6 | /claims/{id}/messages | message | — | 🟢 |
| FR-LEAD-01 | C | SRS§3-M6 | /posts/{id}/leads | lead | TC-LEAD-01 (ApiAuthzIT.strangerLead_404) | 🟢 |
| FR-LEAD-02 | C | SRS§3-M6 | /users/me/leads,/leads/{id}/review | lead | TC-LEAD-01 (ApiAuthzIT.strangerLead_404) + e2e | 🟢 |
| FR-DISPUTE-01 | C | state-machines§3 | POST /claims/{id}/disputes | dispute | TC-DISPUTE-01 | 🟢 |
| FR-DISPUTE-02 | C | state-machines§3 | /admin/disputes/{id}/resolution | dispute,admin | TC-DISPUTE-02 | 🟢 |
| FR-DISPUTE-03 | C | SRS§3-M6 | GET /claims/{id}/disputes | dispute | TC-DISPUTE-02 | 🟢 |

## 非功能需求

| NFR | 负责人 | 设计 | 验收方法 | 状态 |
|---|---|---|---|---|
| NFR-SEC-01 越权 | 全员/A | overview§5 | 改 URL/ID 访问他人资源被拒 | 🟢 |
| NFR-SEC-02 私密文件 | B/C | overview§6 | 直连路径/越权 fileId 不可取 | 🟢 |
| NFR-SEC-03 安全认证 | A | tech-decisions§4 | 生产禁用 Mock、弱口令检查 | 🟢 |
| NFR-SEC-04 输入/上传 | 全员 | conventions | 注入/超长/伪装图片负测 | 🟢 |
| NFR-PRIV-01 数据最小化 | 全员 | er-diagram | schema/日志抽样 | 🟢 |
| NFR-CONS-01 一致性 | C/B | state-machines§4 | 并发接受/确认/争议竞态 | 🟢 |
| NFR-REL-01 备份恢复 | A | backup-restore(P4) | 隔离恢复+校验和 | 🟡 隔离恢复实跑待执行 |
| NFR-PERF-01 性能 | B | test-plan(P4) | 实测 P95 | 🟡 压测数据集待生成 |
| NFR-UX-01 可操作性 | 全员 | wireframes | 小程序端全流程实操 | 🟡 构建通过；开发者工具/真机实操待执行 |
| NFR-MAINT-01 可维护性 | 全员 | README | 新环境复现 | 🟢 |

> 编码推进时同步更新本表状态列与代码文件路径。
