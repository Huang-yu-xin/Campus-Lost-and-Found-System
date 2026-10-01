package edu.whut.clf.lead.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class LeadDtos {

    public record SubmitLeadRequest(@NotBlank @Size(max = 1000) String body, List<Long> evidenceFileIds) {}

    public record ReviewLeadRequest(@NotBlank String status) {} // VIEWED / HELPFUL / CLOSED

    public record LeadItem(
            Long id, Long lostPostId, Long reporterId, String body, String status,
            LocalDateTime createdAt,
            boolean owner,   // E18：当前查看者是否为寻物发布者（可处理线索），前端据此控制处理卡可见性
            List<Long> evidenceFileIds) {}
}
