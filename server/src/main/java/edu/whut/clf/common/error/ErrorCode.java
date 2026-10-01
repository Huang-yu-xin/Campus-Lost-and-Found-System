package edu.whut.clf.common.error;

import org.springframework.http.HttpStatus;

/**
 * 稳定业务错误码。映射见 docs/api/conventions.md。
 * 提示信息为非敏感文案，不泄漏内部细节。
 */
public enum ErrorCode {

    // 通用
    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, "参数不合法"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "未登录或登录已失效"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权限"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    CONFLICT(HttpStatus.CONFLICT, "状态冲突"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),

    // 认证
    MOCK_LOGIN_DISABLED(HttpStatus.FORBIDDEN, "测试登录在当前环境不可用"),
    WECHAT_LOGIN_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "微信登录未配置或不可用"),
    ADMIN_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "管理员账号或密码错误"),
    USER_RESTRICTED(HttpStatus.FORBIDDEN, "账号已被限制该操作"),

    // 文件
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "文件超出大小限制"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的文件类型"),
    INVALID_EVIDENCE_FILE(HttpStatus.BAD_REQUEST, "证明文件无效或不属于当前用户"),

    // 发布
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "发布不存在"),
    POST_EDIT_LOCKED(HttpStatus.CONFLICT, "存在有效申请，关键字段禁止修改"),
    POST_NOT_EDITABLE(HttpStatus.CONFLICT, "当前状态不可编辑"),

    // 认领
    SELF_CLAIM_FORBIDDEN(HttpStatus.FORBIDDEN, "不能认领自己的发布"),
    POST_NOT_CLAIMABLE(HttpStatus.CONFLICT, "该发布当前不可认领"),
    ACTIVE_CLAIM_EXISTS(HttpStatus.CONFLICT, "已存在有效的认领申请"),
    CLAIM_NOT_FOUND(HttpStatus.NOT_FOUND, "申请不存在"),
    CLAIM_NOT_PENDING(HttpStatus.CONFLICT, "申请不处于待审核状态"),
    CLAIM_ACCEPT_CONFLICT(HttpStatus.CONFLICT, "并发冲突，已有其他申请进入交接"),
    CLAIM_STATE_INVALID(HttpStatus.CONFLICT, "申请当前状态不允许该操作"),

    // 认领完成 → 寻物帖闭环链接（V4）
    CLAIM_NOT_COMPLETED(HttpStatus.CONFLICT, "认领尚未完成，不能关联寻物帖"),
    RESOLVE_NOT_OWNER(HttpStatus.FORBIDDEN, "只能关联自己发布的寻物帖"),
    RESOLVE_ALREADY_RESOLVED(HttpStatus.CONFLICT, "该寻物帖已被关联或已不可关联"),
    RESOLVE_CATEGORY_MISMATCH(HttpStatus.BAD_REQUEST, "寻物帖与招领类别不一致"),

    // 交接
    HANDOVER_PAUSED_BY_DISPUTE(HttpStatus.CONFLICT, "存在未决争议，交接已暂停"),
    HANDOVER_NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "非交接参与方"),

    // 线索
    LEAD_NOT_FOUND(HttpStatus.NOT_FOUND, "线索不存在"),
    POST_NOT_LOST(HttpStatus.CONFLICT, "仅寻物信息可提交线索"),

    // 争议
    DISPUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "争议不存在"),
    DISPUTE_OPEN_EXISTS(HttpStatus.CONFLICT, "已存在未决争议"),
    DISPUTE_NOT_OPEN(HttpStatus.CONFLICT, "争议不处于处理中状态");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
