package edu.whut.clf.auth;

import edu.whut.clf.auth.dto.AuthDtos.*;
import edu.whut.clf.common.security.AuthContext;
import edu.whut.clf.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** M1 认证入口。FR-AUTH-01/02/04/05。 */
@RestController
@RequestMapping("/auth")
@Tag(name = "M1-Auth", description = "认证与会话")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/wechat/login")
    @Operation(summary = "微信登录 FR-AUTH-01")
    public ApiResponse<LoginResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest req) {
        return ApiResponse.ok(authService.wechatLogin(req));
    }

    @PostMapping("/mock/login")
    @Operation(summary = "受限测试登录 FR-AUTH-02（生产禁用）")
    public ApiResponse<LoginResponse> mockLogin(@Valid @RequestBody MockLoginRequest req) {
        return ApiResponse.ok(authService.mockLogin(req));
    }

    @PostMapping("/logout")
    @Operation(summary = "退出 FR-AUTH-04（撤销服务端会话，令牌立即失效）")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authService.logout(bearer(request));
        return ApiResponse.ok(null);
    }

    @PostMapping("/refresh")
    @Operation(summary = "续期 FR-AUTH-04（签发新令牌并撤销旧会话）")
    public ApiResponse<LoginResponse> refresh(HttpServletRequest request) {
        return ApiResponse.ok(authService.refresh(AuthContext.require(), bearer(request)));
    }

    private String bearer(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        return h != null && h.startsWith("Bearer ") ? h.substring(7) : null;
    }

    @GetMapping("/campus/capabilities")
    @Operation(summary = "校园认证能力 FR-AUTH-05（本期 disabled）")
    public ApiResponse<CampusCapabilitiesResponse> campusCapabilities() {
        return ApiResponse.ok(authService.campusCapabilities());
    }
}
