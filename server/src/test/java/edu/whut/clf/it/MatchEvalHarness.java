package edu.whut.clf.it;

import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.match.MatchScorer;
import edu.whut.clf.match.dto.MatchDtos.MatchCandidate;
import edu.whut.clf.match.MatchService;
import edu.whut.clf.post.PostMapper;
import edu.whut.clf.post.model.Post;
import edu.whut.clf.user.UserMapper;
import edu.whut.clf.user.model.User;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 匹配离线评估 harness（Stage 2）。
 * <p>
 * 主评估集：合成正样本对（已知真值，种子固定可复现）+ 干扰帖；正样本 = 同类别/同校区/文本高重合、
 * 事件时间相差 1~7 天的 lost↔found 配对，散布在 90 天发布窗口内（用于度量候选截断对召回的伤害）。
 * 指标：候选阶段召回（阈值前）、Hit@1/5/10/20、MRR、正/难负/随机负的分数分布、τ 处保留率与误报率。
 * <p>
 * 边界：只使用发布公开字段与完成事实，不读取任何认领证明；分数全部由生产 MatchScorer 计算。
 */
public class MatchEvalHarness {

    public static final String USER_PREFIX = "eval-user-";

    private final JdbcTemplate jdbc;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final MatchService matchService;
    private final MatchScorer scorer;
    private final AppProperties.Match cfg;

    public MatchEvalHarness(JdbcTemplate jdbc, PostMapper postMapper, UserMapper userMapper,
                            MatchService matchService, MatchScorer scorer, AppProperties props) {
        this.jdbc = jdbc;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.matchService = matchService;
        this.scorer = scorer;
        this.cfg = props.getMatch();
    }

    // ---------- 内容池（与 tests/performance 生成器一致的校园场景） ----------
    private static final String[] CATEGORIES = {"雨伞", "校园卡", "钥匙", "耳机", "水杯", "手机", "钱包",
            "充电宝", "书本教材", "证件", "手表饰品", "衣物", "笔记本电脑", "眼镜", "其他"};
    private static final String[] CAMPUSES = {"南湖校区", "马房山校区", "余家头校区"};
    private static final Map<String, String[]> LOCATIONS = new HashMap<>();
    private static final Map<String, String[]> ITEMS = new HashMap<>();
    static {
        LOCATIONS.put("南湖校区", new String[]{"南湖图书馆", "南湖教学楼", "南湖食堂", "南苑宿舍楼下", "南湖操场", "南湖快递站"});
        LOCATIONS.put("马房山校区", new String[]{"东院图书馆", "博学广场", "鉴湖教学楼", "东院食堂", "马房山操场", "学子超市"});
        LOCATIONS.put("余家头校区", new String[]{"余家头图书馆", "海运楼", "余家头食堂", "余区宿舍楼下", "余家头操场", "余区快递站"});
        ITEMS.put("雨伞", new String[]{"黑色长柄伞", "折叠晴雨伞", "透明自动伞"});
        ITEMS.put("校园卡", new String[]{"校园卡", "蓝色卡套的校园卡", "皮夹里的校园卡"});
        ITEMS.put("钥匙", new String[]{"一串钥匙", "小熊挂件钥匙", "电动车钥匙"});
        ITEMS.put("耳机", new String[]{"白色蓝牙耳机", "黑色头戴式耳机", "有线耳机"});
        ITEMS.put("水杯", new String[]{"白色保温杯", "透明塑料水杯", "粉色吸管杯"});
        ITEMS.put("手机", new String[]{"黑色智能手机", "透明壳手机", "旧款安卓手机"});
        ITEMS.put("钱包", new String[]{"黑色短款钱包", "帆布卡包", "棕色长款钱包"});
        ITEMS.put("充电宝", new String[]{"白色充电宝", "黑色20000毫安充电宝", "绿色迷你充电宝"});
        ITEMS.put("书本教材", new String[]{"高数教材", "考研英语真题", "一套专业课本"});
        ITEMS.put("证件", new String[]{"身份证", "学生证", "驾驶证"});
        ITEMS.put("手表饰品", new String[]{"电子手表", "银色手链", "黑色皮带手表"});
        ITEMS.put("衣物", new String[]{"黑色卫衣", "灰色围巾", "蓝色棒球帽"});
        ITEMS.put("笔记本电脑", new String[]{"银色笔记本电脑", "游戏本"});
        ITEMS.put("眼镜", new String[]{"黑框眼镜", "隐形眼镜盒"});
        ITEMS.put("其他", new String[]{"保温饭盒", "乒乓球拍", "跳绳"});
    }
    private static final String[] NOISE = {"当时很着急", "希望有好心同学看到", "有酬谢", "对我很重要", "恳请归还"};

    /** 一次评估的快照：种子时的配对与干扰帖。 */
    public record Seed(List<long[]> pairs, List<Long> distractorIds, int users) {}

    /** 评估结果。 */
    public record EvalResult(
            int pairs, double candidateRecall, double hit1, double hit5, double hit10, double hit20,
            double mrr, double posMeanScore, double hardNegFpr, double randomNegFpr,
            double retentionAtTau, int hardNegSampled, int randomNegSampled,
            double[] posScores, double[] hardNegScores) {}

    // ---------- 种子 ----------
    public Seed seed(long seed, int nPairs, int nDistractors, int nUsers) {
        cleanup();
        Random r = new Random(seed);
        LocalDateTime now = LocalDateTime.now().withNano(0);

        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < nUsers; i++) {
            User u = new User();
            u.setNickname(USER_PREFIX + i);
            u.setStatus("ACTIVE");
            u.setCampusVerificationStatus("UNVERIFIED");
            userMapper.insert(u);
            userIds.add(u.getId());
        }

        List<long[]> pairs = new ArrayList<>();
        List<Post> all = new ArrayList<>();
        for (int i = 0; i < nPairs; i++) {
            String cat = CATEGORIES[i % CATEGORIES.length];
            String campus = CAMPUSES[i % CAMPUSES.length];
            String[] locs = LOCATIONS.get(campus);
            String loc = locs[i % locs.length];
            String item = ITEMS.get(cat)[i % ITEMS.get(cat).length];
            // 90 天窗口内散布：让"最新 100 条"截断只覆盖尾部
            int daysAgo = 90 - (int) Math.round(i * 90.0 / nPairs);
            LocalDateTime lostEvent = now.minusDays(daysAgo).withHour(8 + (i % 12)).withMinute((i * 7) % 60);
            int gapDays = 1 + (i % 7);
            LocalDateTime foundEvent = lostEvent.plusDays(gapDays).plusHours(i % 10);
            boolean hardText = i % 3 == 0; // 1/3 的对：文本更不相似
            Post lost = post(userIds.get(i % nUsers), "LOST", cat, campus, loc, lostEvent,
                    "丢失" + item,
                    "在" + campus + loc + "遗失" + item + "。（" + NOISE[i % NOISE.length] + "）"
                            + (hardText ? "特征当面核实。" : "物品特征明显，可描述细节。"),
                    lostEvent.plusHours(2 + (i % 10)));
            Post found = post(userIds.get((i + 1) % nUsers), "FOUND", cat, campus, loc, foundEvent,
                    (hardText ? "捡到" + item.substring(item.length() - Math.max(2, item.length() - 3))
                              : "捡到" + item),
                    "在" + loc + "捡到物品，保管中。为保护失主隐私，未公开唯一性细节。"
                            + (hardText ? "请通过申请说明特征。" : "外观完好。"),
                    foundEvent.plusHours(2 + (i % 13)));
            long lostId = postMapper.insert(lost) > 0 ? lost.getId() : 0;
            long foundId = postMapper.insert(found) > 0 ? found.getId() : 0;
            pairs.add(new long[]{lostId, foundId});
            all.add(lost);
            all.add(found);
        }
        List<Long> distractorIds = new ArrayList<>();
        for (int i = 0; i < nDistractors; i++) {
            String cat = CATEGORIES[r.nextInt(CATEGORIES.length)];
            String campus = CAMPUSES[r.nextInt(CAMPUSES.length)];
            String[] locs = LOCATIONS.get(campus);
            String loc = locs[r.nextInt(locs.length)];
            String item = ITEMS.get(cat)[r.nextInt(ITEMS.get(cat).length)];
            LocalDateTime event = now.minusDays(r.nextInt(90)).withHour(7 + r.nextInt(14)).withMinute(r.nextInt(60));
            String type = r.nextBoolean() ? "LOST" : "FOUND";
            Post d = post(userIds.get(r.nextInt(nUsers)), type, cat, campus, loc, event,
                    (type.equals("LOST") ? "丢失" : "捡到") + item,
                    "在" + campus + loc + (type.equals("LOST") ? "遗失" : "捡到") + item + "。",
                    event.plusHours(1 + r.nextInt(48)));
            postMapper.insert(d);
            distractorIds.add(d.getId());
            all.add(d);
        }
        return new Seed(pairs, distractorIds, nUsers);
    }

    private Post post(Long publisher, String type, String category, String campus, String loc,
                      LocalDateTime eventTime, String title, String desc, LocalDateTime publishedAt) {
        Post p = new Post();
        p.setPublisherId(publisher);
        p.setType(type);
        p.setTitle(title);
        p.setCategory(category);
        p.setPublicDescription(desc);
        p.setCampus(campus);
        p.setEventLocation(loc);
        p.setEventTime(eventTime);
        p.setPublishedAt(publishedAt);
        p.setStatus("ACTIVE");
        p.setVersion(0);
        return p;
    }

    /** 清理上一次评估种子（按 eval 用户前缀）。 */
    public void cleanup() {
        jdbc.update("DELETE FROM posts WHERE publisher_id IN (SELECT id FROM users WHERE nickname LIKE ?)",
                USER_PREFIX + "%");
        jdbc.update("DELETE FROM auth_identities WHERE user_id IN (SELECT id FROM users WHERE nickname LIKE ?)",
                USER_PREFIX + "%");
        jdbc.update("DELETE FROM sessions WHERE user_id IN (SELECT id FROM users WHERE nickname LIKE ?)",
                USER_PREFIX + "%");
        jdbc.update("DELETE FROM users WHERE nickname LIKE ?", USER_PREFIX + "%");
    }

    // ---------- 评估 ----------
    public EvalResult evaluate(Seed seed, double tau) {
        int hit1 = 0, hit5 = 0, hit10 = 0, hit20 = 0, inCandidates = 0;
        double rrSum = 0, posScoreSum = 0;
        List<Double> posScores = new ArrayList<>();
        List<Double> hardNeg = new ArrayList<>();
        List<Double> randomNeg = new ArrayList<>();
        Random r = new Random(42);

        // 预加载干扰帖，避免逐条 findById（万级查询）
        Map<Long, Post> distractorPosts = new HashMap<>();
        for (Long did : seed.distractorIds()) {
            Post d = postMapper.findById(did);
            if (d != null) distractorPosts.put(did, d);
        }
        List<Post> distractorList = List.copyOf(distractorPosts.values());

        for (long[] pair : seed.pairs()) {
            Post lost = postMapper.findById(pair[0]);
            Post found = postMapper.findById(pair[1]);
            // 候选阶段召回（阈值前）：真值是否进入候选查询结果
            List<Post> candidates = postMapper.findCandidates(
                    "FOUND", lost.getId(), cfg.getMaxCandidates() * 5);
            boolean inCand = candidates.stream().anyMatch(p -> p.getId().equals(found.getId()));
            if (inCand) inCandidates++;

            // 端到端排名（生产路径，含阈值/截断）
            List<MatchCandidate> res = matchService.matchesFor(lost.getId());
            int rank = -1;
            for (int i = 0; i < res.size(); i++) {
                if (res.get(i).postId().equals(found.getId())) { rank = i + 1; break; }
            }
            if (rank == 1) hit1++;
            if (rank > 0 && rank <= 5) hit5++;
            if (rank > 0 && rank <= 10) hit10++;
            if (rank > 0 && rank <= 20) hit20++;
            if (rank > 0) rrSum += 1.0 / rank;

            double s = scorer.score(lost, found).score();
            posScores.add(s);
            posScoreSum += s;

            // 难负样本：同类别+同校区、事件时间 ±7 天内的干扰帖（最多 10 个/正样本）
            int hard = 0;
            for (Post d : distractorList) {
                if (hard >= 10) break;
                if (!d.getCategory().equals(lost.getCategory())
                        || !d.getCampus().equals(lost.getCampus())) continue;
                if (d.getEventTime() == null || lost.getEventTime() == null) continue;
                long dt = Math.abs(java.time.Duration.between(lost.getEventTime(), d.getEventTime()).toDays());
                if (dt <= 7) {
                    hardNeg.add(scorer.score(lost, d).score());
                    hard++;
                }
            }
            // 随机负样本：10 个/正样本
            for (int k = 0; k < 10 && !distractorList.isEmpty(); k++) {
                Post d = distractorList.get(r.nextInt(distractorList.size()));
                randomNeg.add(scorer.score(lost, d).score());
            }
        }

        int n = seed.pairs().size();
        double hardFpr = hardNeg.stream().filter(s -> s >= tau).count() / (double) Math.max(1, hardNeg.size());
        double randFpr = randomNeg.stream().filter(s -> s >= tau).count() / (double) Math.max(1, randomNeg.size());
        double retention = posScores.stream().filter(s -> s >= tau).count() / (double) n;

        return new EvalResult(n, inCandidates / (double) n,
                hit1 / (double) n, hit5 / (double) n, hit10 / (double) n, hit20 / (double) n,
                rrSum / n, posScoreSum / n, hardFpr, randFpr, retention,
                hardNeg.size(), randomNeg.size(),
                posScores.stream().mapToDouble(Double::doubleValue).toArray(),
                hardNeg.stream().mapToDouble(Double::doubleValue).toArray());
    }

    // ---------- 工具 ----------
    public String configHash() {
        try {
            String raw = cfg.getWCategory() + "|" + cfg.getWLocation() + "|" + cfg.getWTime() + "|"
                    + cfg.getWKeyword() + "|" + cfg.getTimeWindowDays() + "|" + cfg.getTimeToleranceHours()
                    + "|" + cfg.getMaxCandidates() + "|" + cfg.getMinScore();
            byte[] h = MessageDigest.getInstance("SHA-256").digest(raw.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.substring(0, 12);
        } catch (Exception e) {
            return "n/a";
        }
    }

    public void writeReport(Path out, String title, EvalResult e, String extra) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("- 生成时间：").append(LocalDateTime.now().withNano(0)).append("\n");
        sb.append("- 配置哈希：").append(configHash())
          .append("（wC=").append(cfg.getWCategory()).append(" wL=").append(cfg.getWLocation())
          .append(" wT=").append(cfg.getWTime()).append(" wK=").append(cfg.getWKeyword())
          .append(" window=").append(cfg.getTimeWindowDays()).append("d tol=")
          .append(cfg.getTimeToleranceHours()).append("h tau=").append(cfg.getMinScore()).append("）\n\n");
        sb.append("| 指标 | 值 |\n|---|---|\n");
        sb.append("| 正样本对数 | ").append(e.pairs()).append(" |\n");
        sb.append("| 候选阶段召回（阈值前） | ").append(pct(e.candidateRecall())).append(" |\n");
        sb.append("| Hit@1 / @5 / @10 / @20 | ").append(pct(e.hit1())).append(" / ").append(pct(e.hit5()))
          .append(" / ").append(pct(e.hit10())).append(" / ").append(pct(e.hit20())).append(" |\n");
        sb.append("| MRR | ").append(String.format("%.3f", e.mrr())).append(" |\n");
        sb.append("| 正样本均分 | ").append(String.format("%.3f", e.posMeanScore())).append(" |\n");
        sb.append("| τ 处正样本保留率 | ").append(pct(e.retentionAtTau())).append(" |\n");
        sb.append("| 难负样本误报率（n=").append(e.hardNegSampled()).append("） | ").append(pct(e.hardNegFpr())).append(" |\n");
        sb.append("| 随机负样本误报率（n=").append(e.randomNegSampled()).append("） | ").append(pct(e.randomNegFpr())).append(" |\n");
        if (extra != null && !extra.isBlank()) {
            sb.append("\n").append(extra).append("\n");
        }
        sb.append("\n> 边界声明：评估只使用公开字段与完成事实，不读取认领证明；匹配结果不证明归属。\n");
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, sb.toString());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String pct(double v) {
        return String.format("%.1f%%", v * 100);
    }
}
