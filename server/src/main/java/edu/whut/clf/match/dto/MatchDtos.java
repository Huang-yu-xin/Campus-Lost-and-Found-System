package edu.whut.clf.match.dto;

import java.time.LocalDateTime;
import java.util.List;

public class MatchDtos {

    /** 匹配候选：公开预览 + 分数 + 命中规则原因。 */
    public record MatchCandidate(
            Long postId, String type, String title, String category, String campus,
            String eventLocation, LocalDateTime eventTime,
            double score, List<String> reasons, List<Long> imageFileIds) {}
}
