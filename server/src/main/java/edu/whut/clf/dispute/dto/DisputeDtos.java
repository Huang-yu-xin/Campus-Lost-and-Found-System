package edu.whut.clf.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class DisputeDtos {

    public record RaiseDisputeRequest(
            @NotBlank @Size(max = 255) String reason,
            @Size(max = 2000) String description,
            List<Long> evidenceFileIds) {}

    public record ResolveRequest(
            @NotBlank String resolutionType,   // CONTINUE / TERMINATE_REOPEN / CLOSE
            @NotBlank @Size(max = 500) String resolutionNote) {}

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
