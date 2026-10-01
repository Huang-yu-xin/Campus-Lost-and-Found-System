package edu.whut.clf.admin;

import edu.whut.clf.admin.dto.AdminDtos.ReasonRequest;
import edu.whut.clf.common.security.AuthContext;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.common.web.ApiResponse;
import edu.whut.clf.common.web.PageResult;
import edu.whut.clf.post.model.Post;
import edu.whut.clf.user.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** M2 信息治理与用户治理（FR-ADMIN-01/02）。所有接口要求管理员。 */
@RestController
@RequestMapping("/admin")
@Tag(name = "M2-Admin", description = "后台治理")
public class AdminGovernanceController {

    private final AdminGovernanceService service;

    public AdminGovernanceController(AdminGovernanceService service) {
        this.service = service;
    }

    @GetMapping("/posts")
    @Operation(summary = "治理检索发布 FR-ADMIN-01")
    public ApiResponse<PageResult<Post>> listPosts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        AuthContext.requireAdmin();
        return ApiResponse.ok(service.listPosts(status, type, page, pageSize));
    }

    @GetMapping("/posts/{postId}")
    @Operation(summary = "查看发布公开详情与治理历史")
    public ApiResponse<AdminGovernanceService.PostGovernanceDetail> detail(@PathVariable Long postId) {
        AuthContext.requireAdmin();
        return ApiResponse.ok(service.postDetail(postId));
    }

    @PostMapping("/posts/{postId}/remove")
    @Operation(summary = "下架信息 FR-ADMIN-01")
    public ApiResponse<Void> remove(@PathVariable Long postId, @Valid @RequestBody ReasonRequest req) {
        Principal admin = AuthContext.requireAdmin();
        service.removePost(admin.userId(), postId, req.reason());
        return ApiResponse.ok(null);
    }

    @PostMapping("/posts/{postId}/restore")
    @Operation(summary = "恢复信息 FR-ADMIN-01")
    public ApiResponse<Void> restore(@PathVariable Long postId, @Valid @RequestBody ReasonRequest req) {
        Principal admin = AuthContext.requireAdmin();
        service.restorePost(admin.userId(), postId, req.reason());
        return ApiResponse.ok(null);
    }

    @GetMapping("/users")
    @Operation(summary = "治理检索用户 FR-ADMIN-02")
    public ApiResponse<PageResult<User>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        AuthContext.requireAdmin();
        return ApiResponse.ok(service.listUsers(keyword, page, pageSize));
    }

    @PostMapping("/users/{userId}/restrictions")
    @Operation(summary = "限制用户 FR-ADMIN-02")
    public ApiResponse<Void> restrict(@PathVariable Long userId, @Valid @RequestBody ReasonRequest req) {
        Principal admin = AuthContext.requireAdmin();
        service.restrictUser(admin.userId(), userId, req.reason());
        return ApiResponse.ok(null);
    }

    @PostMapping("/users/{userId}/unrestrict")
    @Operation(summary = "解除限制 FR-ADMIN-02")
    public ApiResponse<Void> unrestrict(@PathVariable Long userId, @Valid @RequestBody ReasonRequest req) {
        Principal admin = AuthContext.requireAdmin();
        service.unrestrictUser(admin.userId(), userId, req.reason());
        return ApiResponse.ok(null);
    }
}
