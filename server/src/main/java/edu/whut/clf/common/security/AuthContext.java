package edu.whut.clf.common.security;

import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;

/**
 * 请求级登录上下文（ThreadLocal）。由 AuthInterceptor 填充/清理。
 * 业务代码通过此处取 userId，绝不从请求体读取身份。
 */
public final class AuthContext {

    private static final ThreadLocal<Principal> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(Principal principal) {
        HOLDER.set(principal);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Principal current() {
        return HOLDER.get();
    }

    /** 要求已登录，否则 401。 */
    public static Principal require() {
        Principal p = HOLDER.get();
        if (p == null) {
            throw BusinessException.of(ErrorCode.UNAUTHENTICATED);
        }
        return p;
    }

    public static Long currentUserId() {
        return require().userId();
    }

    /** 要求管理员，否则 403。 */
    public static Principal requireAdmin() {
        Principal p = require();
        if (!p.isAdmin()) {
            throw BusinessException.of(ErrorCode.FORBIDDEN);
        }
        return p;
    }
}
