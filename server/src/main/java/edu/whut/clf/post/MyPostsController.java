package edu.whut.clf.post;

import edu.whut.clf.common.security.AuthContext;
import edu.whut.clf.common.web.ApiResponse;
import edu.whut.clf.common.web.PageResult;
import edu.whut.clf.post.dto.PostDtos.PostSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/** 我的发布（FR-POST-04）。 */
@RestController
@RequestMapping("/users/me/posts")
@Tag(name = "M3-Post", description = "发布与浏览")
public class MyPostsController {

    private final PostService postService;

    public MyPostsController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    @Operation(summary = "我的发布 FR-POST-04")
    public ApiResponse<PageResult<PostSummary>> myPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(postService.myPosts(AuthContext.currentUserId(), page, pageSize));
    }
}
