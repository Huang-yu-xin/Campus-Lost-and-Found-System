package edu.whut.clf.dispute.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public class DisputeDtos {

    public record RaiseDisputeRequest(
            @NotBlank String reason, String description, List<Long> evidenceFileIds) {}

    public record ResolveRequest(
            @NotBlank String resolutionType,   // CONTINUE / TERMINATE_REOPEN / CLOSE
            @NotBlank String resolutionNote) {}

    /** 当事人可见视图（不含他人证据的越权访问）。 */
    public record DisputeView(
            Long id, Long claimId, String reason, String status, String resolutionType,
            String resolutionNote, LocalDateTime createdAt, LocalDateTime resolvedAt) {}

    /** 管理员受限视图：含证据文件与调查信息。 */
    public record AdminDisputeView(
            Long id, Long claimId, Long raisedBy, String reason, String description, String status,
            Long assignedAdminId, String resolutionType, String resolutionNote,
            LocalDateTime createdAt, LocalDateTime resolvedAt, List<Long> evidenceFileIds) {}
}
