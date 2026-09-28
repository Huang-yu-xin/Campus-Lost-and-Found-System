package edu.whut.clf.claim;

import edu.whut.clf.claim.dto.ClaimDtos.*;
import edu.whut.clf.common.security.AuthContext;
import edu.whut.clf.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** M5 认领申请（claim 级操作）。 */
@RestController
@RequestMapping("/claims")
@Tag(name = "M5-Claim", description = "认领与交接")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @GetMapping("/{claimId}")
    @Operation(summary = "查看申请（本人/发布者）FR-CLAIM-02")
    public ApiResponse<ClaimDetail> detail(@PathVariable Long claimId) {
        return ApiResponse.ok(claimService.detail(claimId, AuthContext.currentUserId()));
    }

    @PostMapping("/{claimId}/withdraw")
    @Operation(summary = "申请人撤销待审申请 FR-CLAIM-05")
    public ApiResponse<Void> withdraw(@PathVariable Long claimId) {
        claimService.withdraw(claimId, AuthContext.currentUserId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{claimId}/review")
    @Operation(summary = "发布者审核 FR-CLAIM-03/04")
    public ApiResponse<Void> review(@PathVariable Long claimId, @Valid @RequestBody ReviewRequest req) {
        claimService.review(claimId, AuthContext.currentUserId(), req);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{claimId}/confirmations")
    @Operation(summary = "单方确认交接 FR-HANDOVER-01")
    public ApiResponse<HandoverStatus> confirm(@PathVariable Long claimId) {
        return ApiResponse.ok(claimService.confirmHandover(claimId, AuthContext.currentUserId()));
    }

    @PostMapping("/{claimId}/cancel-handover")
    @Operation(summary = "受控取消交接 FR-CLAIM-05")
    public ApiResponse<Void> cancel(@PathVariable Long claimId, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        claimService.cancelHandover(claimId, AuthContext.currentUserId(), reason);
        return ApiResponse.ok(null);
    }
}
