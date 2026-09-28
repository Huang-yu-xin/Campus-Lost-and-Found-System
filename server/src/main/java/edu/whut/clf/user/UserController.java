package edu.whut.clf.user;

import edu.whut.clf.common.security.AuthContext;
import edu.whut.clf.common.web.ApiResponse;
import edu.whut.clf.user.dto.UserDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/** M1 用户资料（FR-USER-01/02）。 */
@RestController
@RequestMapping("/users/me")
@Tag(name = "M1-User", description = "用户资料")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "获取本人资料 FR-USER-01/02")
    public ApiResponse<UserProfileResponse> me() {
        return ApiResponse.ok(userService.getProfile(AuthContext.currentUserId()));
    }

    @PatchMapping
    @Operation(summary = "修改本人资料 FR-USER-01")
    public ApiResponse<UserProfileResponse> update(@RequestBody UpdateProfileRequest req) {
        return ApiResponse.ok(userService.updateProfile(AuthContext.currentUserId(), req));
    }
}
