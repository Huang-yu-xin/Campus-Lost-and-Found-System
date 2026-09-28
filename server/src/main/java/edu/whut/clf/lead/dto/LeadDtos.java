package edu.whut.clf.lead.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public class LeadDtos {

    public record SubmitLeadRequest(@NotBlank String body, List<Long> evidenceFileIds) {}

    public record ReviewLeadRequest(@NotBlank String status) {} // VIEWED / HELPFUL / CLOSED

    public record LeadItem(
            Long id, Long lostPostId, Long reporterId, String body, String status,
            LocalDateTime createdAt, List<Long> evidenceFileIds) {}
}
