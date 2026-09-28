package edu.whut.clf.common.security;

/**
 * 当前登录主体。role 由服务端会话决定，绝不采信前端传入。
 */
public record Principal(Long userId, String role) {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }
}
