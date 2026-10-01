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
    private String categoryCode;      // 类别字典归一码（P4，可空=未映射）
    private String publicDescription;
    private String campus;
    private String eventLocation;
    private LocalDateTime eventTime;   // 丢失/拾取事件时间（≠发布时间）
    private LocalDateTime publishedAt;
    private String status;             // PostStatus
    private Integer version;
    private Long resolvedByClaimId;    // V4：失主确认关联的 claim（招领侧），NULL=未关联
    private LocalDateTime closedAt;    // V4：进入终态的时间；回到 ACTIVE 清 NULL
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
