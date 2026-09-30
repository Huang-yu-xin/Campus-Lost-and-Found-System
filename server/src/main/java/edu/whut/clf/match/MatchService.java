package edu.whut.clf.match;

import edu.whut.clf.common.enums.PostStatus;
import edu.whut.clf.common.enums.PostType;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.match.dto.MatchDtos.MatchCandidate;
import edu.whut.clf.post.PostImageMapper;
import edu.whut.clf.post.PostMapper;
import edu.whut.clf.post.model.Post;
import edu.whut.clf.post.model.PostImage;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 双向候选匹配（FR-MATCH-01/02）。对相反类型的有效发布评分排序。
 * 只读取可公开的相反类型信息；自身/同类型/已关闭/已下架不入选。
 */
@Service
public class MatchService {

    private final PostMapper postMapper;
    private final PostImageMapper imageMapper;
    private final MatchScorer scorer;

    public MatchService(PostMapper postMapper, PostImageMapper imageMapper, MatchScorer scorer) {
        this.postMapper = postMapper;
        this.imageMapper = imageMapper;
        this.scorer = scorer;
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
        String opposite = PostType.LOST.name().equals(self.getType())
                ? PostType.FOUND.name() : PostType.LOST.name();
        // 取相反类型的 ACTIVE 候选（上限适当放大后再按分过滤/截断）
        List<Post> candidates = postMapper.findCandidates(opposite, self.getId(), scorer.maxCandidates() * 5);

        // 先打分/过滤/排序/截断（不查图片），再为最终 ≤maxCandidates 条批量取图，避免 N+1。
        List<Scored> top = candidates.stream()
                .map(c -> new Scored(c, scorer.score(self, c)))
                .filter(sc -> sc.result().score() >= scorer.minScore())
                // 同分稳定次序：分数降序，其次 postId 升序
                .sorted(Comparator.comparingDouble((Scored sc) -> sc.result().score()).reversed()
                        .thenComparing(sc -> sc.post().getId()))
                .limit(scorer.maxCandidates())
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
