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

        // 严格门禁（P1 时间窗候选落地后启用）：
        // 真值拾取时间 = 丢失时间 + 1~7 天，必在 [t-24h, t+30d] 窗口内 → 候选召回必须接近全量；
        // 进入候选后打分（均分 ~0.86）应把真值带进前 20。
        assertTrue(e.candidateRecall() >= 0.95,
                "候选阶段召回 " + e.candidateRecall() + " 低于 0.95 —— 时间窗候选臂失效");
        assertTrue(e.hit20() >= 0.90,
                "Hit@20 " + e.hit20() + " 低于 0.90 —— 打分/阈值回归");

        String extra = """
                ## 说明
                - 数据集：200 组合成正样本对（真值已知）+ 2400 干扰帖 + 40 用户，固定种子 %d。
                - P1 时间窗候选落地：候选召回与 Hit@k 反映事件时间窗选取的真实效果；
                  同类别+同校区的相邻配对构成天然难例，由 T/K 子分区分。
                - 对照基线（P1 前，"最新 100 条"截断）：候选召回 9.0%%、Hit@1 8.5%%、难负误报率 100%%。
                """.formatted(seed);
        harness.writeReport(Path.of("target/match-eval/report-baseline.md"),
                "匹配评估基线（合成配对）", e, extra);
        assertTrue(java.nio.file.Files.exists(Path.of("target/match-eval/report-baseline.md")));
    }
}
