package edu.whut.clf.it;

import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.match.MatchScorer;
import edu.whut.clf.match.MatchService;
import edu.whut.clf.match.dto.MatchDtos.MatchCandidate;
import edu.whut.clf.post.PostMapper;
import edu.whut.clf.post.model.Post;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 真实推导对补充评估（合成主集之外的补充证据）。
 * 正样本推导：COMPLETED 认领的 FOUND 帖 × 认领人本人同类别 LOST 帖（标题 token 重合最高者）。
 * 局限：数据模型没有 lost↔found 直接链接，推导对含少量标签噪声，n 小，仅作方向性参考。
 * 运行：CLF_EVAL_REAL=true 且 DB_NAME 指向真实库（如 campus_lost_found）时手动执行，
 * 报告写 target/match-eval/report-real-pairs.md。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CLF_EVAL_REAL", matches = "true")
class MatchRealPairsIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired PostMapper postMapper;
    @Autowired MatchService matchService;
    @Autowired MatchScorer scorer;
    @Autowired AppProperties props;

    private record Pair(long lostId, long foundId, double overlap) {}

    @Test
    void evaluateRealDerivedPairs() {
        List<Map<String, Object>> completions = jdbc.queryForList("""
                SELECT c.id AS claim_id, c.post_id AS found_id, c.applicant_id, f.category
                FROM claims c JOIN posts f ON f.id = c.post_id
                WHERE c.status = 'COMPLETED' AND f.type = 'FOUND' AND f.status IN ('COMPLETED','ACTIVE')
                """);

        List<Pair> pairs = new ArrayList<>();
        for (Map<String, Object> row : completions) {
            long foundId = ((Number) row.get("found_id")).longValue();
            long applicant = ((Number) row.get("applicant_id")).longValue();
            String category = (String) row.get("category");
            Post found = postMapper.findById(foundId);
            if (found == null) continue;
            Set<String> ft = tokens(found.getTitle() + " " + found.getPublicDescription());
            List<Map<String, Object>> candidates = jdbc.queryForList("""
                    SELECT id, title, public_description FROM posts
                    WHERE publisher_id = ? AND type = 'LOST' AND category = ?
                      AND status IN ('ACTIVE','COMPLETED') AND id <> ?
                    """, applicant, category, foundId);
            Pair best = null;
            for (Map<String, Object> c : candidates) {
                long lostId = ((Number) c.get("id")).longValue();
                Post lost = postMapper.findById(lostId);
                if (lost == null) continue;
                Set<String> lt = tokens(lost.getTitle() + " " + lost.getPublicDescription());
                Set<String> inter = new HashSet<>(ft);
                inter.retainAll(lt);
                Set<String> union = new HashSet<>(ft);
                union.addAll(lt);
                double ov = union.isEmpty() ? 0 : (double) inter.size() / union.size();
                if (ov > 0 && (best == null || ov > best.overlap())) {
                    best = new Pair(lostId, foundId, ov);
                }
            }
            if (best != null) pairs.add(best);
        }

        int hit1 = 0, hit5 = 0, hit20 = 0, inCand = 0;
        List<String> ranks = new ArrayList<>();
        for (Pair p : pairs) {
            Post lost = postMapper.findById(p.lostId());
            if (lost == null) continue;
            List<Post> cand = matchService.candidatesFor(lost);
            if (cand.stream().anyMatch(x -> x.getId() == p.foundId())) inCand++;
            List<MatchCandidate> res = matchService.matchesFor(p.lostId());
            int rank = -1;
            for (int i = 0; i < res.size(); i++) {
                if (res.get(i).postId() == p.foundId()) { rank = i + 1; break; }
            }
            if (rank == 1) hit1++;
            if (rank > 0 && rank <= 5) hit5++;
            if (rank > 0 && rank <= 20) hit20++;
            ranks.add(p.lostId() + "->" + p.foundId() + " rank=" + (rank > 0 ? rank : "缺席")
                    + " overlap=" + String.format("%.2f", p.overlap()));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# 真实推导对补充评估\n\n");
        sb.append("- 生成时间：").append(LocalDateTime.now().withNano(0)).append("\n");
        sb.append("- 推导出的正样本对数：").append(pairs.size())
          .append("（完成认领 ").append(completions.size()).append(" 条中）\n");
        sb.append("- 候选阶段召回：").append(pairs.isEmpty() ? "n/a" : (inCand * 100 / pairs.size()) + "%").append("\n");
        sb.append("- Hit@1 / @5 / @20：").append(pairs.isEmpty() ? "n/a"
                : (hit1 * 100 / pairs.size()) + "% / " + (hit5 * 100 / pairs.size()) + "% / "
                  + (hit20 * 100 / pairs.size()) + "%").append("\n\n");
        sb.append("## 逐对明细\n");
        ranks.forEach(r -> sb.append("- ").append(r).append("\n"));
        sb.append("\n> 局限：无 lost↔found 直接链接，按认领人+同类别+文本重合推导，含标签噪声；n 小，仅作方向性参考。\n");
        try {
            Files.createDirectories(Path.of("target/match-eval"));
            Files.writeString(Path.of("target/match-eval/report-real-pairs.md"), sb.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println("real pairs: " + pairs.size() + ", inCandidate: " + inCand
                + ", hit1/hit5/hit20: " + hit1 + "/" + hit5 + "/" + hit20);
    }

    private static Set<String> tokens(String text) {
        Set<String> set = new HashSet<>();
        if (text == null) return set;
        for (char ch : text.toLowerCase().toCharArray()) {
            if (ch >= '一' && ch <= '鿿') set.add(String.valueOf(ch));
            else if (ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z') set.add(String.valueOf(ch));
        }
        return set;
    }
}
