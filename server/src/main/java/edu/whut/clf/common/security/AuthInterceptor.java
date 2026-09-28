package edu.whut.clf.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 从 Authorization: Bearer 解析登录态填入 AuthContext（不在此处拒绝，由业务 require() 决定）。
 * 同时为每个请求生成 requestId 供日志与错误响应关联。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

    public AuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("requestId", UUID.randomUUID().toString());
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Principal principal = jwtService.parse(header.substring(7));
            if (principal != null) {
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
