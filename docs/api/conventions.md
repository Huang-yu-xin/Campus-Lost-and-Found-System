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

## 4. 关键业务错误码

| code | HTTP | 触发 |
|---|---|---|
| `SELF_CLAIM_FORBIDDEN` | 403 | 认领自己的发布 |
| `POST_NOT_CLAIMABLE` | 409 | 目标非 FOUND/非 ACTIVE |
| `ACTIVE_CLAIM_EXISTS` | 409 | 已存在有效申请 |
| `INVALID_EVIDENCE_FILE` | 400 | 附件不属申请人/用途错误 |
| `CLAIM_ACCEPT_CONFLICT` | 409 | 并发接受，单活跃交接冲突 |
| `HANDOVER_PAUSED_BY_DISPUTE` | 409 | 存在 OPEN 争议，暂停完成 |
| `POST_EDIT_LOCKED` | 409 | 有有效申请后改核心字段 |
| `USER_RESTRICTED` | 403 | 被限制用户执行受限动作 |
| `MOCK_LOGIN_DISABLED` | 403 | 非开发环境调用测试登录 |

## 5. 幂等与并发
- 写操作对重复提交策略明确：必要时 `Idempotency-Key` + DB 唯一约束（`idempotency_records`）。
- 并发核心操作用事务 + 唯一性/条件更新/行锁保证不变量，而非仅禁用按钮。

## 6. 权限
- 登录态从会话/Token 取用户 ID；请求体中的 `userId/role/status` 不可信。
- "我的发布/申请/留言/线索/争议/私密文件"必须登录且逐记录授权。
- 搜索结果可公开；私密对象无权限统一 404。

## 7. OpenAPI
- 权威契约由代码 springdoc 生成：运行期 `GET /api/v1/v3/api-docs`，UI `.../swagger-ui/index.html`。
- 设计基线契约见 `openapi.yaml`（P1 初版，随实现同步）。
