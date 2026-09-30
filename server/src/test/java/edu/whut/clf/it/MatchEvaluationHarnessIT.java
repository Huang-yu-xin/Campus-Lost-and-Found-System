package edu.whut.clf.it;

import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.match.MatchScorer;
import edu.whut.clf.match.MatchService;
import edu.whut.clf.post.PostMapper;
import edu.whut.clf.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 2 评估基线（合成配对主集，固定种子可复现）。
 * CI 门禁：CLF_IT=true 时随 mvn verify 运行，报告写 target/match-eval/report-baseline.md。
 * 严格阈值门禁（候选召回/Hit@k 下限）随 P1 落地后启用——v0 阶段先记录基线。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CLF_IT", matches = "true")
class MatchEvaluationHarnessIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired PostMapper postMapper;
    @Autowired UserMapper userMapper;
    @Autowired MatchService matchService;
    @Autowired MatchScorer scorer;
    @Autowired AppProperties props;

    @Test
    void evaluateBaselineAndWriteReport() {
        MatchEvalHarness harness = new MatchEvalHarness(jdbc, postMapper, userMapper, matchService, scorer, props);
        long seed = 20260929L;
        MatchEvalHarness.Seed s = harness.seed(seed, 200, 2400, 40);
        assertEquals(200, s.pairs().size());
        assertEquals(2400, s.distractorIds().size());

        MatchEvalHarness.EvalResult e = harness.evaluate(s, props.getMatch().getMinScore());

        // 评估本身的自检：全部对都完成了打分与排名
        assertEquals(200, e.posScores().length);
        assertTrue(e.candidateRecall() >= 0 && e.candidateRecall() <= 1);
        assertTrue(e.hit20() <= e.candidateRecall() + 1e-9);

        String extra = """
                ## 说明
                - 数据集：200 组合成正样本对（真值已知）+ 2400 干扰帖 + 40 用户，固定种子 %d。
                - v0 基线结论（P1 未落地）：候选查询按发布时间取最新 100 条，正样本对散布 90 天窗口，
                  因此候选阶段召回与 Hit@k 反映的是"截断伤害"，作为 P1 落地后的对照基线。
                - 严格门禁（候选召回 ≥ 95%%）在 P1 合入后启用。
                """.formatted(seed);
        harness.writeReport(Path.of("target/match-eval/report-baseline.md"),
                "匹配评估基线（合成配对）", e, extra);
        assertTrue(java.nio.file.Files.exists(Path.of("target/match-eval/report-baseline.md")));
    }
}
