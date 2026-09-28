package edu.whut.clf.common.enums;

/** 发布状态。COMPLETED 前端名：LOST=已找回，FOUND=已归还。LOST 不进入 HANDOVER。 */
public enum PostStatus {
    ACTIVE, HANDOVER, COMPLETED, WITHDRAWN, REMOVED
}
