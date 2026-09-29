package edu.whut.clf.lead.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 寻物发布者"收到的线索"聚合项（联表 posts 取标题）。 */
@Data
public class ReceivedLeadItem {
    private Long id;
    private Long lostPostId;
    private String postTitle;
    private Long reporterId;
    private String body;
    private String status;
    private LocalDateTime createdAt;
}
