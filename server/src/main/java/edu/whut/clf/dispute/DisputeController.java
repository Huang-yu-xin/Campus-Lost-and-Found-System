package edu.whut.clf.dispute;

import edu.whut.clf.common.security.AuthContext;
import edu.whut.clf.common.web.ApiResponse;
import edu.whut.clf.dispute.dto.DisputeDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** M6 争议（当事人视角，FR-DISPUTE-01/03）。 */
@RestController
@RequestMapping("/claims/{claimId}/disputes")
@Tag(name = "M6-Dispute", description = "争议")
public class DisputeController {

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @PostMapping
    @Operation(summary = "发起争议 FR-DISPUTE-01（创建后暂停交接完成）")
    public ApiResponse<DisputeView> raise(@PathVariable Long claimId, @Valid @RequestBody RaiseDisputeRequest req) {
        return ApiResponse.ok("争议已提交", disputeService.raise(claimId, AuthContext.currentUserId(), req));
    }

    @GetMapping
    @Operation(summary = "当事人查看争议 FR-DISPUTE-03")
    public ApiResponse<List<DisputeView>> list(@PathVariable Long claimId) {
        return ApiResponse.ok(disputeService.listForParticipant(claimId, AuthContext.currentUserId()));
    }
}
