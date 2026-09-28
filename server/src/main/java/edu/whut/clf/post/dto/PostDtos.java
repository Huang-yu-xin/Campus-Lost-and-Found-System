package edu.whut.clf.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class PostDtos {

    public record CreatePostRequest(
            @NotNull String type,               // LOST / FOUND
            @NotBlank String title,
            @NotBlank String category,
            @NotBlank String publicDescription,
            String campus,
            String eventLocation,
            LocalDateTime eventTime,
            List<Long> imageFileIds) {}

    public record UpdatePostRequest(
            String title, String category, String publicDescription,
            String campus, String eventLocation, LocalDateTime eventTime,
            List<Long> imageFileIds) {}

    public record PostSummary(
            Long id, String type, String title, String category, String campus,
            String eventLocation, LocalDateTime eventTime, LocalDateTime publishedAt,
            String status, List<Long> imageFileIds) {}

    public record PostDetail(
            Long id, String type, String title, String category, String publicDescription,
            String campus, String eventLocation, LocalDateTime eventTime, LocalDateTime publishedAt,
            String status, Integer version, Long publisherId, String publisherNickname,
            boolean mine, List<Long> imageFileIds) {}
}
