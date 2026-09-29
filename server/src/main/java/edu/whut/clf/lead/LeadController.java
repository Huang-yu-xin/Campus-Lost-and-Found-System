package edu.whut.clf.lead;

import edu.whut.clf.common.security.AuthContext;
import edu.whut.clf.common.web.ApiResponse;
import edu.whut.clf.common.web.PageResult;
import edu.whut.clf.lead.dto.LeadDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** M6 寻物线索（FR-LEAD-01/02）。 */
@RestController
@Tag(name = "M6-Lead", description = "寻物线索")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping("/posts/{postId}/leads")
    @Operation(summary = "对 LOST 提交线索 FR-LEAD-01")
    public ApiResponse<LeadItem> submit(@PathVariable Long postId, @Valid @RequestBody SubmitLeadRequest req) {
        return ApiResponse.ok("线索已提交", leadService.submit(postId, AuthContext.currentUserId(), req));
    }

    @GetMapping("/posts/{postId}/leads")
    @Operation(summary = "寻物发布者查看线索 FR-LEAD-01")
    public ApiResponse<List<LeadItem>> postLeads(@PathVariable Long postId) {
        return ApiResponse.ok(leadService.postLeads(postId, AuthContext.currentUserId()));
    }

    @GetMapping("/users/me/leads")
    @Operation(summary = "我提交的线索 FR-LEAD-02")
    public ApiResponse<PageResult<LeadItem>> myLeads(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(leadService.myLeads(AuthContext.currentUserId(), page, pageSize));
    }

    @GetMapping("/users/me/received-leads")
    @Operation(summary = "收到的线索（寻物发布者视角）FR-LEAD-02")
    public ApiResponse<PageResult<edu.whut.clf.lead.dto.ReceivedLeadItem>> receivedLeads(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(leadService.receivedLeads(AuthContext.currentUserId(), page, pageSize));
    }

    @GetMapping("/leads/{leadId}")
    @Operation(summary = "查看单条线索（提交者/寻物发布者）FR-LEAD-02")
    public ApiResponse<LeadItem> get(@PathVariable Long leadId) {
        return ApiResponse.ok(leadService.get(leadId, AuthContext.currentUserId()));
    }

    @PostMapping("/leads/{leadId}/review")
    @Operation(summary = "寻物发布者处理线索 FR-LEAD-02")
    public ApiResponse<Void> review(@PathVariable Long leadId, @Valid @RequestBody ReviewLeadRequest req) {
        leadService.review(leadId, AuthContext.currentUserId(), req.status());
        return ApiResponse.ok(null);
    }
}
