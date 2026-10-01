# API 公共约定与错误码 (API Conventions)

## 1. 基础
- 前缀：`/api/v1`（由 `server.servlet.context-path` 提供）。
- 授权：`Authorization: Bearer <accessToken>`。
- 内容类型：`application/json`；字段命名 camelCase。
- ID：服务端生成，前端不可指定，对外不可枚举。
- 分页：请求 `page`（从 1）、`pageSize`；排序 `sort` 采用**白名单字段**。
- 时间：DB 存 UTC；输出 ISO 8601 带时区（如 `2026-09-28T09:00:00Z`）；区分丢失/拾取/发布时间。

## 2. 统一响应
成功：HTTP 2xx +
```json
{ "code": "OK", "message": "success", "data": { } }
```
分页 `data`：`{ "items": [], "total": 0, "page": 1, "pageSize": 20 }`

失败：正确 HTTP 状态 + 稳定业务码 + 非敏感提示 + `requestId`：
```json
{ "code": "SELF_CLAIM_FORBIDDEN", "message": "不能认领自己的发布", "data": null, "requestId": "..." }
```
> 错误响应不得泄漏堆栈。生产日志不记录密码、微信凭据、令牌、完整证明、隐私图片地址。

## 3. HTTP 状态 → 业务码映射

| HTTP | code 示例 | 场景 |
|---|---|---|
| 400 | `INVALID_ARGUMENT` | 参数/校验失败 |
| 401 | `UNAUTHENTICATED` | 未登录/Token 失效 |
| 403 | `FORBIDDEN` | 无权限（非私密对象枚举场景） |
| 404 | `*_NOT_FOUND` | 资源不存在；**私密对象无权限亦返回 404**（D-12） |
| 409 | `*_CONFLICT` | 状态冲突/并发/重复 |
| 413 | `FILE_TOO_LARGE` | 文件超限 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 非法文件类型 |
| 429 | `RATE_LIMITED` | 限流 |
| 500 | `INTERNAL_ERROR` | 服务端异常 |

## 4. 全量业务错误码（与 `ErrorCode.java` 枚举一一对应）

> 对照规则：本表每行对应 `edu.whut.clf.common.error.ErrorCode` 的一个枚举值，HTTP 取其 `httpStatus()`。

**通用**

| code | HTTP | 触发 |
|---|---|---|
| `INVALID_ARGUMENT` | 400 | 参数/校验失败（含 DTO 长度、文件数量上限、非法枚举值） |
| `UNAUTHENTICATED` | 401 | 未登录/Token 失效 |
| `FORBIDDEN` | 403 | 无权限（非私密对象） |
| `NOT_FOUND` | 404 | 资源不存在（含私密对象无权限防枚举） |
| `CONFLICT` | 409 | 通用状态冲突/并发 |
| `RATE_LIMITED` | 429 | 限流（管理员登录防爆破，D5） |
| `INTERNAL_ERROR` | 500 | 服务端异常 |

**认证**

| code | HTTP | 触发 |
|---|---|---|
| `MOCK_LOGIN_DISABLED` | 403 | 非开发环境调用测试登录 |
| `WECHAT_LOGIN_UNAVAILABLE` | 503 | 微信登录未配置/不可用 |
| `ADMIN_LOGIN_FAILED` | 401 | 管理员账号或密码错误 |
| `USER_RESTRICTED` | 403 | 被限制用户执行受限动作（发布/申请/留言/线索/争议/上传） |

**文件**

| code | HTTP | 触发 |
|---|---|---|
| `FILE_TOO_LARGE` | 413 | 文件超出大小限制 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 真实文件头与允许类型不符 |
| `INVALID_EVIDENCE_FILE` | 400 | 附件不属本人/用途错误/已绑定（拒绝二次绑定） |

**发布**

| code | HTTP | 触发 |
|---|---|---|
| `POST_NOT_FOUND` | 404 | 发布不存在（含非本人看不可见态） |
| `POST_EDIT_LOCKED` | 409 | 有有效申请后改核心字段 |
| `POST_NOT_EDITABLE` | 409 | 当前状态不可编辑 |

**认领**

| code | HTTP | 触发 |
|---|---|---|
| `SELF_CLAIM_FORBIDDEN` | 403 | 认领自己的发布 |
| `POST_NOT_CLAIMABLE` | 409 | 目标非 FOUND/非 ACTIVE |
| `ACTIVE_CLAIM_EXISTS` | 409 | 已存在有效申请（唯一约束兜底） |
| `CLAIM_NOT_FOUND` | 404 | 申请不存在（含非参与方防枚举） |
| `CLAIM_NOT_PENDING` | 409 | 申请不处于待审核状态 |
| `CLAIM_ACCEPT_CONFLICT` | 409 | 并发接受，单活跃交接冲突 |
| `CLAIM_STATE_INVALID` | 409 | 申请当前状态不允许该操作 |

**认领完成 → 寻物帖闭环链接（V4 / FR-POST-06）**

| code | HTTP | 触发端点 |
|---|---|---|
| `CLAIM_NOT_COMPLETED` | 409 | `resolved-candidates` / `resolve-lost`：认领尚未完成 |
| `RESOLVE_NOT_OWNER` | 403 | `resolve-lost`：关联了非本人发布的寻物帖 |
| `RESOLVE_ALREADY_RESOLVED` | 409 | `resolve-lost`：寻物帖已被关联/非 ACTIVE（一 claim 一链接，R4） |
| `RESOLVE_CATEGORY_MISMATCH` | 400 | `resolve-lost`：寻物帖与招领类别不一致 |

**交接**

| code | HTTP | 触发 |
|---|---|---|
| `HANDOVER_PAUSED_BY_DISPUTE` | 409 | 存在 OPEN 争议，暂停完成/取消（confirm/cancel-handover） |
| `HANDOVER_NOT_PARTICIPANT` | 404 | 非交接参与方（防枚举，HTTP 404，裁决 R3/D10；错误码名保留） |

**线索**

| code | HTTP | 触发 |
|---|---|---|
| `LEAD_NOT_FOUND` | 404 | 线索不存在（含非参与方防枚举） |
| `POST_NOT_LOST` | 409 | 仅寻物信息可提交线索 |

**争议**

| code | HTTP | 触发 |
|---|---|---|
| `DISPUTE_NOT_FOUND` | 404 | 争议不存在 |
| `DISPUTE_OPEN_EXISTS` | 409 | 已存在未决争议 |
| `DISPUTE_NOT_OPEN` | 409 | 争议不处于处理中状态 |

## 5. 幂等与并发
- 认领等写操作的重复提交幂等**由 DB 唯一约束 + 状态条件更新保障**（如 `uk_claim_active_applicant`），
  **未启用 `Idempotency-Key` 请求头**；`idempotency_records` 表为预留未引用（裁决 R1）。
- 并发核心操作用事务 + 唯一性/条件更新/行锁保证不变量，而非仅禁用按钮。

## 6. 权限
- 登录态从会话/Token 取用户 ID；请求体中的 `userId/role/status` 不可信。
- "我的发布/申请/留言/线索/争议/私密文件"必须登录且逐记录授权。
- 搜索结果可公开；私密对象无权限统一 404。

## 7. OpenAPI
- 权威契约由代码 springdoc 生成：运行期 `GET /api/v1/v3/api-docs`，UI `.../swagger-ui/index.html`。
- 设计基线契约见 `openapi.yaml`（P1 初版，随实现同步）。
