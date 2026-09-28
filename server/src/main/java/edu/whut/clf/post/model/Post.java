package edu.whut.clf.post.model;

import lombok.Data;

import java.time.LocalDateTime;

/** posts 表实体。 */
@Data
public class Post {
    private Long id;
    private Long publisherId;
    private String type;               // PostType
    private String title;
    private String category;
    private String publicDescription;
    private String campus;
    private String eventLocation;
    private LocalDateTime eventTime;   // 丢失/拾取事件时间（≠发布时间）
    private LocalDateTime publishedAt;
    private String status;             // PostStatus
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
