package edu.whut.clf.it;

import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.match.MatchScorer;
import edu.whut.clf.post.PostMapper;
import edu.whut.clf.post.model.Post;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 事实链接对评估（V4）。
 * <p>正样本不再靠"认领人+同类别+文本重合"启发式推导，而是直接读失主确认的事实链接
 * {@code posts.resolved_by_claim_id}：lost ← claim → found。每对都是失主亲手确认的真值，无标签噪声。
 * <p>候选池按<b>点时重建</b>：取两帖都仍"开放"的最后时刻 t = min(lost.closed_at, found.closed_at)，
 * 用 {@code published_at <= t AND (closed_at IS NULL OR closed_at >= t)} 还原彼时的候选宇宙
 * （closed_at 由 V4 提供，替代旧的 status!='REMOVED' 近似），再套用与生产一致的事件时间窗/同类目臂
 * 做候选，交由生产 {@link MatchScorer} 打分排序，度量候选召回与 Hit@K。
 * <p>运行：{@code CLF_EVAL_REAL=true} 且 DB_NAME 指向含事实链接的库（如 campus_lost_found）时手动执行，
 * 报告写 target/match-eval/report-real-pairs.md。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CLF_EVAL_REAL", matches = "true")
class MatchRealPairsIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired PostMapper postMapper;
    @Autowired MatchScorer scorer;
    @Autowired AppProperties props;

    @Test
    void evaluateFactLinkedPairs() {
        AppProperties.Match cfg = props.getMatch();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT l.id AS lost_id, f.id AS found_id
                FROM posts l
                JOIN claims c ON c.id = l.resolved_by_claim_id
                JOIN posts f ON f.id = c.post_id
                WHERE l.type = 'LOST' AND f.type = 'FOUND'
                """);

        int pairs = 0, inCand = 0, hit1 = 0, hit5 = 0, hit20 = 0;
        List<String> detail = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long lostId = ((Number) row.get("lost_id")).longValue();
            long foundId = ((Number) row.get("found_id")).longValue();
            Post lost = postMapper.findById(lostId);
            Post found = postMapper.findById(foundId);
            if (lost == null || found == null) continue;
            pairs++;

            // 点时 t：两帖都仍开放的最后时刻（通常 = found 交接完成时间，早于 lost 关联时间）
            LocalDateTime t = earliest(lost.getClosedAt(), found.getClosedAt());
            if (t == null) t = LocalDateTime.now(java.time.Clock.systemUTC());

            List<Post> pool = reconstructPoolAt(lost, t, cfg);
            boolean recalled = pool.stream().anyMatch(p -> p.getId().equals(found.getId()));
            if (recalled) inCand++;

            // 端到端排名：生产打分 + minScore 阈值 + 同分稳定序 + maxCandidates 截断
            List<Long> ranked = pool.stream()
                    .map(c -> Map.entry(c, scorer.score(lost, c).score()))
                    .filter(e -> e.getValue() >= cfg.getMinScore())
                    .sorted(Comparator.comparingDouble((Map.Entry<Post, Double> e) -> e.getValue()).reversed()
                            .thenComparing(e -> e.getKey().getId()))
                    .limit(cfg.getMaxCandidates())
                    .map(e -> e.getKey().getId())
                    .toList();
            int rank = ranked.indexOf(foundId) + 1; // 0 → 缺席
            if (rank == 1) hit1++;
            if (rank > 0 && rank <= 5) hit5++;
            if (rank > 0 && rank <= 20) hit20++;

            double pairScore = scorer.score(lost, found).score();
            detail.add(lostId + "->" + foundId
                    + " rank=" + (rank > 0 ? rank : "缺席")
                    + " recalled=" + (recalled ? "是" : "否")
                    + " score=" + String.format("%.2f", pairScore)
                    + " t=" + t);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# 事实链接对评估\n\n");
        sb.append("- 生成时间：").append(LocalDateTime.now(java.time.Clock.systemUTC()).withNano(0)).append("\n");
        sb.append("- 事实链接对数：").append(pairs).append("（来自 posts.resolved_by_claim_id）\n");
        if (pairs == 0) {
            sb.append("\n> 事实对为 0——请先重新生成模拟数据（S5：tests/performance/gen_mock_data.py "
                    + "的事实链接回填段），或在开发库走通一次 resolve-lost 后再跑。\n");
        } else {
            sb.append("- 候选阶段召回（点时重建，closed_at 版）：")
              .append(inCand * 100 / pairs).append("%（").append(inCand).append("/").append(pairs).append("）\n");
            sb.append("- Hit@1 / @5 / @20：").append(hit1 * 100 / pairs).append("% / ")
              .append(hit5 * 100 / pairs).append("% / ").append(hit20 * 100 / pairs).append("%\n\n");
            sb.append("## 逐对明细\n");
            detail.forEach(d -> sb.append("- ").append(d).append("\n"));
            sb.append("\n> 正样本来自失主确认的 resolved_by_claim_id 事实链接（无标签噪声）；"
                    + "候选池按 t=min(lost.closed_at, found.closed_at) 点时重建，分数由生产 MatchScorer 计算。\n");
        }
        try {
            Files.createDirectories(Path.of("target/match-eval"));
            Files.writeString(Path.of("target/match-eval/report-real-pairs.md"), sb.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println("fact pairs: " + pairs + ", inCandidate: " + inCand
                + ", hit1/hit5/hit20: " + hit1 + "/" + hit5 + "/" + hit20);
    }

    /**
     * 点时重建 LOST 帖在 t 时刻的 FOUND 候选池：在 t 仍开放的帖子 ×（事件时间窗 ∪ 同类目宽窗 ∪ 空时间兜底臂），
     * 窗口口径与生产 MatchService.candidatesFor 对 LOST 锚点的处理一致。
     */
    private List<Post> reconstructPoolAt(Post lost, LocalDateTime t, AppProperties.Match cfg) {
        LocalDateTime anchor = lost.getEventTime() != null ? lost.getEventTime() : lost.getPublishedAt();
        LocalDateTime winStart = anchor.minusHours(cfg.getTimeToleranceHours());
        LocalDateTime winEnd = anchor.plusDays(cfg.getTimeWindowDays());
        LocalDateTime catStart = anchor.minusHours(cfg.getTimeToleranceHours());
        LocalDateTime catEnd = anchor.plusDays(cfg.getCategoryWindowDays());
        LocalDateTime nullWinStart = t.minusDays(cfg.getNullEventWindowDays());
        String selfCode = lost.getCategoryCode();
        return jdbc.query("""
                SELECT * FROM posts
                WHERE type = 'FOUND' AND id <> ?
                  AND published_at <= ? AND (closed_at IS NULL OR closed_at >= ?)
                  AND (
                        (event_time >= ? AND event_time <= ?)
                     OR (? IS NOT NULL AND category_code = ? AND event_time >= ? AND event_time <= ?)
                     OR (event_time IS NULL AND published_at >= ?)
                  )
                """,
                (rs, i) -> mapPost(rs),
                lost.getId(), t, t,
                winStart, winEnd,
                selfCode, selfCode, catStart, catEnd,
                nullWinStart);
    }

    private static LocalDateTime earliest(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    /** 仅映射打分与排序所需字段。 */
    private static Post mapPost(ResultSet rs) throws SQLException {
        Post p = new Post();
        p.setId(rs.getLong("id"));
        p.setType(rs.getString("type"));
        p.setTitle(rs.getString("title"));
        p.setCategory(rs.getString("category"));
        p.setCategoryCode(rs.getString("category_code"));
        p.setPublicDescription(rs.getString("public_description"));
        p.setCampus(rs.getString("campus"));
        p.setEventLocation(rs.getString("event_location"));
        p.setEventTime(toLdt(rs.getTimestamp("event_time")));
        p.setPublishedAt(toLdt(rs.getTimestamp("published_at")));
        return p;
    }

    private static LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
