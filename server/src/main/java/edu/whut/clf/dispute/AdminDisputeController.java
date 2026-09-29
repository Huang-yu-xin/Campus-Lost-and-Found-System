package edu.whut.clf.dispute;

import edu.whut.clf.common.security.AuthContext;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.common.web.ApiResponse;
import edu.whut.clf.common.web.PageResult;
import edu.whut.clf.dispute.dto.DisputeDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** M2/M6 管理员争议处理（FR-DISPUTE-02）。要求管理员。 */
@RestController
@RequestMapping("/admin/disputes")
@Tag(name = "M2-Admin", description = "后台治理")
public class AdminDisputeController {

    private final DisputeService disputeService;

    public AdminDisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @GetMapping
    @Operation(summary = "争议列表 FR-DISPUTE-02")
    public ApiResponse<PageResult<AdminDisputeView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        AuthContext.requireAdmin();
        return ApiResponse.ok(disputeService.adminList(status, page, pageSize));
    }

    @GetMapping("/{disputeId}")
    @Operation(summary = "受限查看争议（含证据）FR-DISPUTE-02")
    public ApiResponse<AdminDisputeView> get(@PathVariable Long disputeId) {
        AuthContext.requireAdmin();
        return ApiResponse.ok(disputeService.adminGet(disputeId));
    }

    @PostMapping("/{disputeId}/assign")
    @Operation(summary = "受理争议 FR-DISPUTE-02（受理后方可查看受限证据）")
    public ApiResponse<Void> assign(@PathVariable Long disputeId) {
        Principal admin = AuthContext.requireAdmin();
        disputeService.assign(disputeId, admin.userId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{disputeId}/resolution")
    @Operation(summary = "裁决争议 FR-DISPUTE-02")
    public ApiResponse<Void> resolve(@PathVariable Long disputeId, @Valid @RequestBody ResolveRequest req) {
        Principal admin = AuthContext.requireAdmin();
        disputeService.resolve(disputeId, admin.userId(), req);
        return ApiResponse.ok(null);
    }
}
