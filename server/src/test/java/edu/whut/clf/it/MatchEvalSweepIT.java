package edu.whut.clf.it;

import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.match.CategoryDictionary;
import edu.whut.clf.match.MatchScorer;
import edu.whut.clf.match.MatchService;
import edu.whut.clf.post.PostMapper;
import edu.whut.clf.post.model.Post;
import edu.whut.clf.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 匹配调参 sweep（Stage 2 → 调参依据）。CLF_SWEEP=true 时手动运行。
 * <p>
 * 方法：合成集（与基线同一固定种子）上，对每对正样本缓存候选池内全部成员的
 * C/L/K 子分与 T 的"间隔天数"（权重无关量，只算一次）；随后在权重单纯形
 * （步长 0.05）× 时间窗 {14,21,30,45} 网格上离线重排，时间切分——旧 60% 配对调参、
 * 新 40% 配对报告；τ 按"训练集正样本保留率 ≥95%"的最大值选取并报告测试集误报率。
 * 报告写 target/match-eval/report-sweep.md。运行期打分公式保持线性可解释，不引入模型。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CLF_SWEEP", matches = "true")
class MatchEvalSweepIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired PostMapper postMapper;
    @Autowired UserMapper userMapper;
    @Autowired MatchService matchService;
    @Autowired MatchScorer scorer;
    @Autowired CategoryDictionary dictionary;
    @Autowired AppProperties props;

    private static final double[] WINDOWS = {14, 21, 30, 45};
    private static final double STEP = 0.05;

    private record Cached(Post lost, Post found, long foundId, List<PoolMember> pool) {}
    private record PoolMember(long id, double c, double l, double gapDays, double k, boolean hardNeg) {}
    private record Cfg(double wc, double wl, double wt, double wk, double window) {}

    @Test
    void sweep() throws Exception {
        MatchEvalHarness harness = new MatchEvalHarness(jdbc, postMapper, userMapper,
                matchService, scorer, dictionary, props);
        MatchEvalHarness.Seed seed = harness.seed(20260929L, 200, 2400, 40);

        int trainSize = (int) (seed.pairs().size() * 0.6);
        double tolHours = props.getMatch().getTimeToleranceHours();

        // 1) 缓存每对的候选池子分（权重无关）
        List<Cached> train = new ArrayList<>();
        List<Cached> test = new ArrayList<>();
        for (int i = 0; i < seed.pairs().size(); i++) {
            long[] pair = seed.pairs().get(i);
            Post lost = postMapper.findById(pair[0]);
            Post found = postMapper.findById(pair[1]);
            List<PoolMember> pool = new ArrayList<>();
            for (Post m : matchService.candidatesFor(lost)) {
                if (m.getId().equals(found.getId())) continue;
                MatchScorer.Result r = scorer.score(lost, m);
                double gapDays = gapDays(lost, m, tolHours);
                boolean hard = m.getCategoryCode() != null && m.getCategoryCode().equals(lost.getCategoryCode())
                        && m.getCampus() != null && m.getCampus().equals(lost.getCampus())
                        && m.getEventTime() != null && lost.getEventTime() != null
                        && Math.abs(Duration.between(lost.getEventTime(), m.getEventTime()).toDays()) <= 7;
                pool.add(new PoolMember(m.getId(), r.category(), r.location(), gapDays, r.keyword(), hard));
            }
            MatchScorer.Result rf = scorer.score(lost, found);
            double gapF = gapDays(lost, found, tolHours);
            List<PoolMember> poolWithSelf = new ArrayList<>(pool);
            poolWithSelf.add(new PoolMember(found.getId(), rf.category(), rf.location(), gapF, rf.keyword(), false));
            Cached cached = new Cached(lost, found, found.getId(), poolWithSelf);
            (i < trainSize ? train : test).add(cached);
        }

        // 2) 网格：权重单纯形（步长 0.05，范围约束）× 时间窗
        List<Cfg> cfgs = new ArrayList<>();
        for (double wc = 0.30; wc <= 0.501; wc += STEP)
            for (double wl = 0.15; wl <= 0.351; wl += STEP)
                for (double wt = 0.10; wt <= 0.301; wt += STEP)
                    for (double wk = 0.05; wk <= 0.251; wk += STEP)
                        for (double win : WINDOWS)
                            if (Math.abs(wc + wl + wt + wk - 1.0) < 1e-9)
                                cfgs.add(new Cfg(round(wc), round(wl), round(wt), round(wk), win));

        // 3) 训练集选优：Hit@1 为主、Hit@5 次之
        Cfg best = null;
        double bestHit1 = -1, bestHit5 = -1;
        List<String> top5 = new ArrayList<>();
        for (Cfg c : cfgs) {
            double hit1 = hitK(train, c);
            double hit5 = hitK5(train, c);
            if (hit1 > bestHit1 + 1e-12 || (Math.abs(hit1 - bestHit1) < 1e-12 && hit5 > bestHit5)) {
                bestHit1 = hit1; bestHit5 = hit5; best = c;
            }
        }
        // 记录训练集前 5（用于报告稳健性观察）
        record ScoredCfg(Cfg c, double h1, double h5) {}
        List<ScoredCfg> ranked = new ArrayList<>();
        for (Cfg c : cfgs) ranked.add(new ScoredCfg(c, hitK(train, c), hitK5(train, c)));
        ranked.sort((a, b) -> b.h1() != a.h1() ? Double.compare(b.h1(), a.h1()) : Double.compare(b.h5(), a.h5()));
        for (int i = 0; i < Math.min(5, ranked.size()); i++) {
            ScoredCfg s = ranked.get(i);
            top5.add(String.format("| %.2f / %.2f / %.2f / %.2f | %.0f | %.1f%% | %.1f%% |",
                    s.c().wc(), s.c().wl(), s.c().wt(), s.c().wk(), s.c().window(),
                    s.h1() * 100, s.h5() * 100));
        }

        // 4) τ：训练集保留率 ≥95% 的最大阈值（步长 0.05），报告测试集误报率
        final Cfg bestF = best;
        double tau = props.getMatch().getMinScore();
        for (double cand = 0.30; cand <= 0.801; cand += STEP) {
            final double t = cand;
            double retention = train.stream().filter(p -> score(p, bestF) >= t).count() / (double) train.size();
            if (retention >= 0.95) tau = cand;
        }
        final double tauF = tau;
        double testRetention = test.stream().filter(p -> score(p, bestF) >= tauF).count() / (double) test.size();
        long hardNeg = test.stream().mapToLong(p -> p.pool().stream().filter(m -> m.hardNeg()
                && scoreOf(bestF, m) >= tauF).count()).sum();
        long hardNegTotal = test.stream().mapToLong(p -> p.pool().stream().filter(PoolMember::hardNeg).count()).sum();

        // 5) 报告
        double testHit1 = hitK(test, best), testHit5 = hitK5(test, best);
        StringBuilder sb = new StringBuilder();
        sb.append("# 匹配调参 sweep 报告\n\n");
        sb.append("- 生成时间：").append(LocalDateTime.now().withNano(0)).append("\n");
        sb.append("- 网格：权重单纯形步长 0.05 × 时间窗 {14,21,30,45}，共 ").append(cfgs.size()).append(" 组；时间切分 训练/测试 = 120/80 对\n");
        sb.append("- 现行默认：wC=0.40 wL=0.25 wT=0.20 wK=0.15 window=30 → 测试集 Hit@1=")
          .append(String.format("%.1f%%", hitK(test, new Cfg(0.40, 0.25, 0.20, 0.15, 30)) * 100)).append("\n\n");
        sb.append("## 训练集 Top5（Hit@1 / Hit@5）\n\n| wC / wL / wT / wK | 窗口(天) | Hit@1 | Hit@5 |\n|---|---|---|---|\n");
        top5.forEach(l -> sb.append(l).append("\n"));

        sb.append("\n## 训练集 Top5 在测试集上的 Hit@1\n\n| wC / wL / wT / wK | 窗口 | 测试 Hit@1 |\n|---|---|---|\n");
        for (int i2 = 0; i2 < Math.min(5, ranked.size()); i2++) {
            ScoredCfg s2 = ranked.get(i2);
            sb.append(String.format("| %.2f / %.2f / %.2f / %.2f | %.0f | %.1f%% |%n",
                    s2.c().wc(), s2.c().wl(), s2.c().wt(), s2.c().wk(), s2.c().window(), hitK(test, s2.c()) * 100));
        }
        sb.append("\n## 选定配置的 τ 档位表（测试集）\n\n| τ | 保留率 | 难负误报 |\n|---|---|---|\n");
        for (double tv : new double[]{0.40, 0.50, 0.60, 0.70, 0.80}) {
            final double tt = tv;
            double ret = test.stream().filter(p -> score(p, bestF) >= tt).count() / (double) test.size();
            long hn = test.stream().mapToLong(p -> p.pool().stream().filter(m -> m.hardNeg()
                    && scoreOf(bestF, m) >= tt).count()).sum();
            long hnt = test.stream().mapToLong(p -> p.pool().stream().filter(PoolMember::hardNeg).count()).sum();
            sb.append(String.format("| %.2f | %.1f%% | %d/%d |%n", tv, ret * 100, hn, hnt));
        }

        sb.append("\n## 选定配置在测试集上的表现\n\n");
        sb.append("- 配置：wC=").append(best.wc()).append(" wL=").append(best.wl())
          .append(" wT=").append(best.wt()).append(" wK=").append(best.wk())
          .append(" window=").append((int) best.window()).append("d\n");
        sb.append("- Hit@1 / Hit@5：").append(String.format("%.1f%% / %.1f%%", testHit1 * 100, testHit5 * 100)).append("\n");
        sb.append("- 选定 τ=").append(tau).append("（训练集保留率≥95% 的最大值）→ 测试集保留率 ")
          .append(String.format("%.1f%%", testRetention * 100))
          .append("，难负误报 ").append(hardNeg).append("/").append(hardNegTotal).append("\n");
        sb.append("\n> 方法说明：子分 C/L/K 与间隔天数缓存后离线重排，与生产线性公式一致；τ 优先保召回（结果是人看的 Top-20 列表）。\n");
        Files.createDirectories(Path.of("target/match-eval"));
        Files.writeString(Path.of("target/match-eval/report-sweep.md"), sb.toString());
        System.out.println("SWEEP DONE: best=" + bestF + " testHit1=" + testHit1 + " tau=" + tau);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double gapDays(Post lost, Post cand, double tolHours) {
        if (lost.getEventTime() == null || cand.getEventTime() == null) return -1;
        long hours = Duration.between(lost.getEventTime(), cand.getEventTime()).toHours();
        if (hours < -tolHours) return Double.NEGATIVE_INFINITY; // 异常 → T=0
        return Math.max(0, hours) / 24.0;
    }

    private static double tScore(double gapDays, double window) {
        return gapDays == Double.NEGATIVE_INFINITY ? 0 : Math.max(0, 1.0 - gapDays / window);
    }

    private static double scoreOf(Cfg c, PoolMember m) {
        return c.wc() * m.c() + c.wl() * m.l() + c.wt() * tScore(m.gapDays(), c.window()) + c.wk() * m.k();
    }

    private static double score(Cached p, Cfg c) {
        return p.pool().stream().filter(m -> m.id() == p.foundId()).findFirst()
                .map(m -> scoreOf(c, m)).orElse(0.0);
    }

    private double hitK(List<Cached> ps, Cfg c) {
        return rankShare(ps, c, 1);
    }

    private double hitK5(List<Cached> ps, Cfg c) {
        return rankShare(ps, c, 5);
    }

    private double rankShare(List<Cached> ps, Cfg c, int k) {
        long hit = ps.stream().filter(p -> {
            double sSelf = score(p, c);
            if (sSelf < props.getMatch().getMinScore()) return false; // 生产路径含阈值
            long better = p.pool().stream().filter(m -> scoreOf(c, m) > sSelf + 1e-12).count();
            return better < k; // rank ≤ k（并列按 postId 升序，真值 id 未知时保守不计并列优势）
        }).count();
        return hit / (double) ps.size();
    }
}
