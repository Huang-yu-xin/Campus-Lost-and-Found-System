package edu.whut.clf.auth;

import edu.whut.clf.auth.dto.AuthDtos.*;
import edu.whut.clf.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** M1 管理员登录入口（FR-AUTH-03）。 */
@RestController
@RequestMapping("/admin/auth")
@Tag(name = "M2-Admin", description = "后台治理")
public class AdminAuthController {

    private final AuthService authService;

    public AdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "管理员登录 FR-AUTH-03")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody AdminLoginRequest req) {
        return ApiResponse.ok(authService.adminLogin(req));
    }
}
