package edu.whut.clf.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class PostDtos {

    public record CreatePostRequest(
            @NotNull String type,               // LOST / FOUND
            @NotBlank @Size(max = 128) String title,
            @NotBlank @Size(max = 48) String category,
            @NotBlank @Size(max = 2000) String publicDescription,
            @Size(max = 64) String campus,
            @Size(max = 128) String eventLocation,
            LocalDateTime eventTime,
            List<Long> imageFileIds) {}

    public record UpdatePostRequest(
            @Size(max = 128) String title,
            @Size(max = 48) String category,
            @Size(max = 2000) String publicDescription,
            @Size(max = 64) String campus,
            @Size(max = 128) String eventLocation,
            LocalDateTime eventTime,
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
