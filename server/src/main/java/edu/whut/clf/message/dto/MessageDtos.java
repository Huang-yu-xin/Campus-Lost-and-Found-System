package edu.whut.clf.message.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public class MessageDtos {

    public record SendMessageRequest(@NotBlank String body) {}

    public record MessageItem(
            Long id, Long senderId, boolean mine, String body, LocalDateTime createdAt, boolean read) {}
}
