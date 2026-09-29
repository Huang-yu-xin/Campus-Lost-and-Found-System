package edu.whut.clf.claim;

import edu.whut.clf.claim.dto.ClaimDtos.ClaimSummary;
import edu.whut.clf.claim.dto.ReceivedClaimItem;
import edu.whut.clf.common.security.AuthContext;
import edu.whut.clf.common.web.ApiResponse;
import edu.whut.clf.common.web.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/** 我的申请 / 收到的申请（FR-CLAIM-02）。 */
@RestController
@RequestMapping("/users/me")
@Tag(name = "M5-Claim", description = "认领与交接")
public class MyClaimsController {

    private final ClaimService claimService;

    public MyClaimsController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @GetMapping("/claims")
    @Operation(summary = "我的申请 FR-CLAIM-02")
    public ApiResponse<PageResult<ClaimSummary>> myClaims(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(claimService.myClaims(AuthContext.currentUserId(), page, pageSize));
    }

    @GetMapping("/received-claims")
    @Operation(summary = "收到的申请（发布者视角）FR-CLAIM-02")
    public ApiResponse<PageResult<ReceivedClaimItem>> receivedClaims(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(claimService.receivedClaims(AuthContext.currentUserId(), page, pageSize));
    }
}
