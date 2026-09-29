package edu.whut.clf.common.security;

import edu.whut.clf.auth.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 从 Authorization: Bearer 解析登录态填入 AuthContext（不在此处拒绝，由业务 require() 决定）。
 * JWT 有效且对应服务端会话活跃（未撤销/未过期）时才注入 Principal，实现退出即失效（FR-AUTH-04）。
 * 同时为每个请求生成 requestId 供日志与错误响应关联。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;
    private final SessionService sessionService;

    public AuthInterceptor(JwtService jwtService, SessionService sessionService) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("requestId", UUID.randomUUID().toString());
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Principal principal = jwtService.parse(token);
            // JWT 签名有效 且 会话仍活跃 才认定已登录
            if (principal != null && sessionService.isActive(token)) {
                AuthContext.set(principal);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }
}
