package edu.whut.clf.claim;

import edu.whut.clf.claim.dto.ClaimDtos.*;
import edu.whut.clf.common.security.AuthContext;
import edu.whut.clf.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** M5 发布维度的认领入口（对某 FOUND 发布提交/查看申请）。 */
@RestController
@RequestMapping("/posts/{postId}/claims")
@Tag(name = "M5-Claim", description = "认领与交接")
public class ClaimPostController {

    private final ClaimService claimService;

    public ClaimPostController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    @Operation(summary = "对 FOUND 发起认领 FR-CLAIM-01")
    public ApiResponse<ClaimDetail> submit(@PathVariable Long postId, @Valid @RequestBody SubmitClaimRequest req) {
        return ApiResponse.ok("申请已提交", claimService.submit(postId, AuthContext.currentUserId(), req));
    }

    @GetMapping
    @Operation(summary = "发布者查看收到的申请 FR-CLAIM-02")
    public ApiResponse<List<ClaimSummary>> received(@PathVariable Long postId) {
        return ApiResponse.ok(claimService.postClaims(postId, AuthContext.currentUserId()));
    }
}
