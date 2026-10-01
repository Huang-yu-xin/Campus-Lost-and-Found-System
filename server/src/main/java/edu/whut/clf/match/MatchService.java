package edu.whut.clf.match;

import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.common.enums.PostStatus;
import edu.whut.clf.common.enums.PostType;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.match.dto.MatchDtos.MatchCandidate;
import edu.whut.clf.post.PostImageMapper;
import edu.whut.clf.post.PostMapper;
import edu.whut.clf.post.model.Post;
import edu.whut.clf.post.model.PostImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 双向候选匹配（FR-MATCH-01/02）。对相反类型的有效发布评分排序。
 * 只读取可公开的相反类型信息；自身/同类型/已关闭/已下架不入选。
 *
 * <p>P1：候选按<b>事件时间窗</b>选取（与 T 子分有效域一致：寻物 t → 拾取 ∈ [t−tol, t+window]；
 * 招领 p → 丢失 ∈ [p−window, p+tol]），anchor 缺失时回退发布时间；event_time 为空的候选由
 * 发布时间兜底臂覆盖。每臂 LIMIT 仅是保险阀，命中即告警（截断可见而非静默）。
 */
@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    private final PostMapper postMapper;
    private final PostImageMapper imageMapper;
    private final MatchScorer scorer;
    private final AppProperties.Match cfg;

    public MatchService(PostMapper postMapper, PostImageMapper imageMapper, MatchScorer scorer,
                        AppProperties props) {
        this.postMapper = postMapper;
        this.imageMapper = imageMapper;
        this.scorer = scorer;
        this.cfg = props.getMatch();
    }

    /** 候选阶段（阈值前）：按事件时间窗 + 同类目宽窗 + 空时间兜底臂选取，Java 侧按 id 去重。 */
    public List<Post> candidatesFor(Post self) {
        String opposite = PostType.LOST.name().equals(self.getType())
                ? PostType.FOUND.name() : PostType.LOST.name();
        LocalDateTime anchor = self.getEventTime() != null ? self.getEventTime() : self.getPublishedAt();
        LocalDateTime winStart;
        LocalDateTime winEnd;
        if (PostType.LOST.name().equals(self.getType())) {
            // 锚=丢失时间：拾取晚于丢失 window 天内有效，容忍早记 tol 小时
            winStart = anchor.minusHours(cfg.getTimeToleranceHours());
            winEnd = anchor.plusDays(cfg.getTimeWindowDays());
        } else {
            // 锚=拾取时间：丢失早于拾取 window 天内有效，晚于拾取 tol 小时内算录入误差
            winStart = anchor.minusDays(cfg.getTimeWindowDays());
            winEnd = anchor.plusHours(cfg.getTimeToleranceHours());
        }
        // P4 同类目臂：窗口更宽（覆盖晚找回），锚点回退与时间臂一致
        LocalDateTime catAnchor = self.getEventTime() != null ? self.getEventTime() : self.getPublishedAt();
        LocalDateTime catStart;
        LocalDateTime catEnd;
        if (PostType.LOST.name().equals(self.getType())) {
            catStart = catAnchor.minusHours(cfg.getTimeToleranceHours());
            catEnd = catAnchor.plusDays(cfg.getCategoryWindowDays());
        } else {
            catStart = catAnchor.minusDays(cfg.getCategoryWindowDays());
            catEnd = catAnchor.plusHours(cfg.getTimeToleranceHours());
        }
        LocalDateTime nullWinStart = LocalDateTime.now(java.time.Clock.systemUTC()).minusDays(cfg.getNullEventWindowDays());
        List<Post> candidates = postMapper.findCandidatesWindowed(opposite, self.getId(),
                winStart, winEnd, self.getCategoryCode(), catStart, catEnd,
                nullWinStart, cfg.getCandidateArmLimit());
        if (candidates.size() >= 3L * cfg.getCandidateArmLimit()) {
            log.warn("match candidate arms hit LIMIT (selfId={}, size={}) — 截断可见：考虑加大 armLimit 或收窄窗口",
                    self.getId(), candidates.size());
        }
        Map<Long, Post> uniq = new LinkedHashMap<>();
        for (Post p : candidates) {
            uniq.putIfAbsent(p.getId(), p);
        }
        return List.copyOf(uniq.values());
    }

    public List<MatchCandidate> matchesFor(Long postId) {
        Post self = postMapper.findById(postId);
        if (self == null) {
            throw BusinessException.of(ErrorCode.POST_NOT_FOUND);
        }
        if (!PostStatus.ACTIVE.name().equals(self.getStatus())) {
            // 非有效发布不产出匹配
            return List.of();
        }
        List<Post> candidates = candidatesFor(self);

        // 先打分/过滤/排序/截断（不查图片），再为最终 ≤maxCandidates 条批量取图，避免 N+1。
        List<Scored> top = candidates.stream()
                .map(c -> new Scored(c, scorer.score(self, c)))
                .filter(sc -> sc.result().score() >= cfg.getMinScore())
                // 同分稳定次序：分数降序，其次 postId 升序
                .sorted(Comparator.comparingDouble((Scored sc) -> sc.result().score()).reversed()
                        .thenComparing(sc -> sc.post().getId()))
                .limit(cfg.getMaxCandidates())
                .toList();

        if (top.isEmpty()) {
            return List.of();
        }
        List<Long> topIds = top.stream().map(sc -> sc.post().getId()).toList();
        Map<Long, List<Long>> imagesByPost = imageMapper.findByPostIds(topIds).stream()
                .collect(Collectors.groupingBy(PostImage::getPostId,
                        Collectors.mapping(PostImage::getFileId, Collectors.toList())));

        return top.stream()
                .map(sc -> new MatchCandidate(sc.post().getId(), sc.post().getType(), sc.post().getTitle(),
                        sc.post().getCategory(), sc.post().getCampus(), sc.post().getEventLocation(),
                        sc.post().getEventTime(), sc.result().score(), sc.result().reasons(),
                        imagesByPost.getOrDefault(sc.post().getId(), List.of())))
                .toList();
    }

    /** 打分中间结果：候选帖 + 评分（图片延迟到截断后批量取）。 */
    private record Scored(Post post, MatchScorer.Result result) {}
}
