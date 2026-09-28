package edu.whut.clf.common.enums;

/** 争议裁决类型。 */
public enum ResolutionType {
    /** 继续交接 */
    CONTINUE,
    /** 终止本次交接并按规则重开招领 */
    TERMINATE_REOPEN,
    /** 关闭处理 */
    CLOSE
}
