package edu.whut.clf.auth;

import edu.whut.clf.auth.dto.AuthDtos.*;
import edu.whut.clf.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    @Operation(summary = "退出 FR-AUTH-04（无状态令牌，客户端丢弃）")
    public ApiResponse<Void> logout() {
        // 无状态 JWT：客户端删除令牌即可。可撤销会话在后续迭代加入 sessions 表。
        return ApiResponse.ok(null);
    }

    @GetMapping("/campus/capabilities")
    @Operation(summary = "校园认证能力 FR-AUTH-05（本期 disabled）")
    public ApiResponse<CampusCapabilitiesResponse> campusCapabilities() {
        return ApiResponse.ok(authService.campusCapabilities());
    }
}
