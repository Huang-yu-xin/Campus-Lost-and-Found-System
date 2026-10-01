package edu.whut.clf.post;

import edu.whut.clf.common.security.AuthContext;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.common.web.ApiResponse;
import edu.whut.clf.common.web.PageResult;
import edu.whut.clf.post.dto.PostDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/** M3 发布与浏览 + M4 搜索入口。 */
@RestController
@RequestMapping("/posts")
@Tag(name = "M3-Post", description = "发布与浏览")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    @Operation(summary = "发布寻物/招领 FR-POST-01/02")
    public ApiResponse<PostDetail> create(@Valid @RequestBody CreatePostRequest req) {
        return ApiResponse.ok("发布成功", postService.create(AuthContext.currentUserId(), req));
    }

    @GetMapping
    @Operation(summary = "公开列表 FR-POST-03")
    public ApiResponse<PageResult<PostSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String campus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(postService.publicList(keyword, type, category, campus, null, null, page, pageSize));
    }

    @GetMapping("/search")
    @Operation(summary = "组合筛选 FR-SEARCH-01")
    public ApiResponse<PageResult<PostSummary>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) String eventFrom,
            @RequestParam(required = false) String eventTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(postService.publicList(keyword, type, category, campus, edu.whut.clf.common.config.UtcTimeConfiguration.parse(eventFrom), edu.whut.clf.common.config.UtcTimeConfiguration.parse(eventTo), page, pageSize));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "详情 FR-POST-03")
    public ApiResponse<PostDetail> detail(@PathVariable Long postId) {
        Principal p = AuthContext.current();
        Long uid = p != null ? p.userId() : null;
        return ApiResponse.ok(postService.detail(postId, uid));
    }

    @PatchMapping("/{postId}")
    @Operation(summary = "本人编辑 FR-POST-04")
    public ApiResponse<PostDetail> update(@PathVariable Long postId, @Valid @RequestBody UpdatePostRequest req) {
        return ApiResponse.ok(postService.update(postId, AuthContext.currentUserId(), req));
    }

    @PostMapping("/{postId}/withdraw")
    @Operation(summary = "受控撤回 FR-POST-05")
    public ApiResponse<Void> withdraw(@PathVariable Long postId) {
        postService.withdraw(postId, AuthContext.currentUserId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{postId}/mark-found")
    @Operation(summary = "LOST 标记已找回 FR-POST-05")
    public ApiResponse<Void> markFound(@PathVariable Long postId) {
        postService.markFound(postId, AuthContext.currentUserId());
        return ApiResponse.ok(null);
    }
}
