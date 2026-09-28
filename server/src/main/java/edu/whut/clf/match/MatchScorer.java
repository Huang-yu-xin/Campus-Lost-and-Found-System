package edu.whut.clf.match;

import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.common.enums.PostType;
import edu.whut.clf.post.model.Post;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 可解释规则匹配打分（FR-MATCH-02）。纯函数式、可单元测试、分数可复算。
 * S = wC*C + wL*L + wT*T + wK*K，各子项 0..1。权重/阈值来自配置（D-05）。
 * 不读取任何私密证明；不证明物品归属。
 */
@Component
public class MatchScorer {

    private final AppProperties.Match cfg;

    @Autowired
    public MatchScorer(AppProperties props) {
        this.cfg = props.getMatch();
    }

    /** 供测试用的显式构造。 */
    public MatchScorer(AppProperties.Match cfg) {
        this.cfg = cfg;
    }

    public Result score(Post self, Post candidate) {
        List<String> reasons = new ArrayList<>();

        double c = categoryScore(self, candidate, reasons);
        double l = locationScore(self, candidate, reasons);
        double t = timeScore(self, candidate, reasons);
        double k = keywordScore(self, candidate, reasons);

        double total = cfg.getWCategory() * c + cfg.getWLocation() * l
                + cfg.getWTime() * t + cfg.getWKeyword() * k;
        total = round(total);
        return new Result(total, reasons, round(c), round(l), round(t), round(k));
    }

    private double categoryScore(Post a, Post b, List<String> reasons) {
        if (a.getCategory() == null || b.getCategory() == null) {
            reasons.add("类别缺失，未计入类别分");
            return 0;
        }
        if (a.getCategory().equalsIgnoreCase(b.getCategory())) {
            reasons.add("类别一致：" + a.getCategory());
            return 1.0;
        }
        reasons.add("类别不同");
        return 0;
    }

    private double locationScore(Post a, Post b, List<String> reasons) {
        Set<String> sa = tokens(join(a.getCampus(), a.getEventLocation()));
        Set<String> sb = tokens(join(b.getCampus(), b.getEventLocation()));
        if (sa.isEmpty() || sb.isEmpty()) {
            reasons.add("地点信息缺失，未计入地点分");
            return 0;
        }
        double j = jaccard(sa, sb);
        if (j > 0) {
            reasons.add("地点相近度 " + pct(j));
        } else {
            reasons.add("地点无重合");
        }
        return j;
    }

    private double timeScore(Post self, Post candidate, List<String> reasons) {
        LocalDateTime lost;
        LocalDateTime found;
        if (PostType.LOST.name().equals(self.getType())) {
            lost = self.getEventTime();
            found = candidate.getEventTime();
        } else {
            found = self.getEventTime();
            lost = candidate.getEventTime();
        }
        if (lost == null || found == null) {
            reasons.add("事件时间缺失，未计入时间分");
            return 0;
        }
        long hours = Duration.between(lost, found).toHours(); // 正=拾取晚于丢失（正常）
        if (hours < -cfg.getTimeToleranceHours()) {
            reasons.add("拾取时间明显早于丢失时间，异常，不计入时间分");
            return 0;
        }
        double days = Math.max(0, hours) / 24.0;
        double score = Math.max(0.0, 1.0 - days / cfg.getTimeWindowDays());
        reasons.add("时间间隔约 " + Math.round(days) + " 天，时间分 " + pct(score));
        return score;
    }

    private double keywordScore(Post a, Post b, List<String> reasons) {
        Set<String> sa = tokens(join(a.getTitle(), a.getPublicDescription()));
        Set<String> sb = tokens(join(b.getTitle(), b.getPublicDescription()));
        if (sa.isEmpty() || sb.isEmpty()) {
            reasons.add("文本信息不足，未计入关键词分");
            return 0;
        }
        double j = jaccard(sa, sb);
        if (j > 0) {
            reasons.add("关键词重合度 " + pct(j));
        }
        return j;
    }

    // ---- 文本工具：ASCII 词 + CJK 单字，去重成集合 ----
    static Set<String> tokens(String text) {
        Set<String> set = new HashSet<>();
        if (text == null) {
            return set;
        }
        String lower = text.toLowerCase();
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z') {
                ascii.append(ch);
            } else {
                if (ascii.length() > 0) {
                    set.add(ascii.toString());
                    ascii.setLength(0);
                }
                if (isCjk(ch)) {
                    set.add(String.valueOf(ch));
                }
            }
        }
        if (ascii.length() > 0) {
            set.add(ascii.toString());
        }
        return set;
    }

    private static boolean isCjk(char ch) {
        return ch >= '一' && ch <= '鿿';
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }

    private static String join(String a, String b) {
        return (a == null ? "" : a) + " " + (b == null ? "" : b);
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    public double minScore() {
        return cfg.getMinScore();
    }

    public int maxCandidates() {
        return cfg.getMaxCandidates();
    }

    /** 打分结果：总分、命中原因、各子项分（可复算）。 */
    public record Result(double score, List<String> reasons, double category, double location, double time, double keyword) {}
}
