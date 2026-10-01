package edu.whut.clf.match;

import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.common.enums.PostType;
import edu.whut.clf.post.model.Post;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** 匹配打分纯单元测试（无需数据库）。验证分数可复算、异常时间降权、缺字段说明。 */
class MatchScorerTest {

    private final CategoryDictionary dictionary = new CategoryDictionary();
    private final TextTokenizer tokenizer = new TextTokenizer();
    private final MatchScorer scorer = new MatchScorer(new AppProperties.Match(), dictionary, tokenizer);

    private Post post(String type, String category, String campus, String location,
                      LocalDateTime eventTime, String title, String desc) {
        Post p = new Post();
        p.setType(type);
        p.setCategory(category);
        p.setCampus(campus);
        p.setEventLocation(location);
        p.setEventTime(eventTime);
        p.setTitle(title);
        p.setPublicDescription(desc);
        return p;
    }

    @Test
    void perfectMatch_scoresHigh() {
        LocalDateTime lost = LocalDateTime.of(2026, 9, 20, 10, 0);
        LocalDateTime found = LocalDateTime.of(2026, 9, 20, 15, 0); // 同日拾取
        Post self = post(PostType.LOST.name(), "钱包", "南湖校区", "图书馆一楼", lost, "黑色钱包", "里面有校园卡");
        Post cand = post(PostType.FOUND.name(), "钱包", "南湖校区", "图书馆一楼大厅", found, "捡到黑色钱包", "有校园卡");

        MatchScorer.Result r = scorer.score(self, cand);
        assertEquals(1.0, r.category(), 0.0001);
        assertTrue(r.location() > 0, "地点应有重合");
        assertTrue(r.time() > 0.9, "同日拾取时间分应接近满分");
        assertTrue(r.keyword() > 0, "关键词应有重合");
        assertTrue(r.score() > 0.6, "综合分应较高: " + r.score());
        // 可复算：加权和一致
        double expected = 0.40 * r.category() + 0.25 * r.location() + 0.20 * r.time() + 0.15 * r.keyword();
        assertEquals(Math.round(expected * 10000) / 10000.0, r.score(), 0.0002);
    }

    @Test
    void foundBeforeLost_timeScoreZero() {
        LocalDateTime lost = LocalDateTime.of(2026, 9, 20, 10, 0);
        LocalDateTime found = LocalDateTime.of(2026, 9, 10, 10, 0); // 拾取早于丢失 10 天，异常
        Post self = post(PostType.LOST.name(), "伞", "南湖", "教1", lost, "雨伞", "蓝色");
        Post cand = post(PostType.FOUND.name(), "伞", "南湖", "教1", found, "雨伞", "蓝色");
        MatchScorer.Result r = scorer.score(self, cand);
        assertEquals(0.0, r.time(), 0.0001);
        assertTrue(r.reasons().stream().anyMatch(s -> s.contains("异常")));
    }

    @Test
    void missingTime_hasExplanation() {
        Post self = post(PostType.LOST.name(), "书", "南湖", "教2", null, "高数课本", "有笔记");
        Post cand = post(PostType.FOUND.name(), "书", "南湖", "教2", null, "高数书", "有笔记");
        MatchScorer.Result r = scorer.score(self, cand);
        assertEquals(0.0, r.time(), 0.0001);
        assertTrue(r.reasons().stream().anyMatch(s -> s.contains("时间缺失")));
    }

    @Test
    void differentCategory_zeroCategoryScore() {
        Post self = post(PostType.LOST.name(), "钱包", "南湖", "教2", null, "钱包", "");
        Post cand = post(PostType.FOUND.name(), "钥匙", "南湖", "教2", null, "钥匙", "");
        MatchScorer.Result r = scorer.score(self, cand);
        assertEquals(0.0, r.category(), 0.0001);
    }

    // ---- P4 类别归一化 ----

    @Test
    void sameChildCode_fullScore_evenWhenRawTextDiffers() {
        Post self = post(PostType.LOST.name(), "蓝牙耳机", "南湖", "图书馆", null, "耳机", "");
        Post cand = post(PostType.FOUND.name(), "有线耳机", "南湖", "图书馆", null, "耳机", "");
        self.setCategoryCode(dictionary.codeFor("蓝牙耳机").orElse(null));
        cand.setCategoryCode(dictionary.codeFor("有线耳机").orElse(null));
        MatchScorer.Result r = scorer.score(self, cand);
        assertEquals(1.0, r.category(), 0.0001);
        assertTrue(r.reasons().stream().anyMatch(s -> s.contains("耳机")));
    }

    @Test
    void sameChildAlias_fullScore() {
        // 饭卡是 campus-card 的别名 → 同子类 → 1.0
        Post self = post(PostType.LOST.name(), "校园卡", "南湖", "食堂", null, "卡", "");
        Post cand = post(PostType.FOUND.name(), "饭卡", "南湖", "食堂", null, "卡", "");
        self.setCategoryCode(dictionary.codeFor("校园卡").orElse(null));
        cand.setCategoryCode(dictionary.codeFor("饭卡").orElse(null));
        assertEquals("campus-card", self.getCategoryCode());
        assertEquals("campus-card", cand.getCategoryCode());
        MatchScorer.Result r = scorer.score(self, cand);
        assertEquals(1.0, r.category(), 0.0001);
    }

    @Test
    void sameParentDifferentChild_partialScore() {
        // 校园卡(campus-card) 与 身份证(id-docs) 同属 cards 父类 → 0.5
        Post self = post(PostType.LOST.name(), "校园卡", "南湖", "食堂", null, "卡", "");
        Post cand = post(PostType.FOUND.name(), "身份证", "南湖", "食堂", null, "证", "");
        self.setCategoryCode(dictionary.codeFor("校园卡").orElse(null));
        cand.setCategoryCode(dictionary.codeFor("身份证").orElse(null));
        assertEquals("cards", dictionary.parentOf(self.getCategoryCode()));
        assertEquals("cards", dictionary.parentOf(cand.getCategoryCode()));
        MatchScorer.Result r = scorer.score(self, cand);
        assertEquals(0.5, r.category(), 0.0001);
        assertTrue(r.reasons().stream().anyMatch(s -> s.contains("同父类")));
    }

    @Test
    void exclusionBlocksContainment() {
        // "耳机套" 被排除词拦下，不允许包含匹配 → 未映射
        assertTrue(dictionary.codeFor("耳机套").isEmpty());
        // 包含匹配：长别名优先
        assertEquals("earphones", dictionary.codeFor("黑色头戴式耳机").orElse(null));
        assertEquals("cup", dictionary.codeFor("保温杯").orElse(null));
        assertEquals("campus-card", dictionary.codeFor("饭卡").orElse(null));
        // "图书馆" 不应因含"书"被映射到书本教材（裸单字不在别名表）
        assertTrue(dictionary.codeFor("图书馆").isEmpty());
    }

    // ---- P3 token 升级 / P6 地点门控 ----

    @Test
    void synonymCanonicalization_liftsRelatedItemOverlap() {
        // 旧单字 Jaccard：水杯 vs 保温杯 = 1/4 = 0.25；规整后两者都变成"杯子" → 显著提升
        double k = scorer.score(
                post(PostType.LOST.name(), "水杯", "南湖校区", "图书馆", null, "丢失水杯", "蓝色水杯"),
                post(PostType.FOUND.name(), "保温杯", "南湖校区", "图书馆", null, "捡到保温杯", "蓝色保温杯")
        ).keyword();
        assertTrue(k >= 0.5, "同义词规整后 K 应显著提升: " + k);
    }

    @Test
    void stopwordOnlyOverlap_scoresNearZero() {
        // 两段文本仅共享停用字（的/在/丢失/物品 等样板），实词完全不重合 → K ≈ 0
        double k = scorer.score(
                post(PostType.LOST.name(), "钥匙", "南湖校区", "操场", null, "丢失钥匙串", "在操场丢失的物品"),
                post(PostType.FOUND.name(), "眼镜", "余家头校区", "教学楼", null, "捡到黑框眼镜", "描述详情请核验")
        ).keyword();
        assertTrue(k < 0.05, "停用字不应贡献关键词分: " + k);
    }

    @Test
    void campusGate_reducesCrossCampusLocationScore() {
        Post self = post(PostType.LOST.name(), "雨伞", "南湖校区", "图书馆", null, "雨伞", "");
        Post sameCampus = post(PostType.FOUND.name(), "雨伞", "南湖校区", "图书馆", null, "雨伞", "");
        Post otherCampus = post(PostType.FOUND.name(), "雨伞", "余家头校区", "图书馆", null, "雨伞", "");
        double lSame = scorer.score(self, sameCampus).location();
        double lCross = scorer.score(self, otherCampus).location();
        // 同名楼：单字重合相同，但跨校区被门控乘 0.3
        assertEquals(lSame * 0.3, lCross, 0.0001);
        assertTrue(lCross < 0.35, "跨校区同名楼不应得到高地点分: " + lCross);
    }

    @Test
    void unmappedFallsBackToRawEquality() {
        Post self = post(PostType.LOST.name(), "滑板", "南湖", "广场", null, "滑板", "");
        Post cand = post(PostType.FOUND.name(), "滑板", "南湖", "广场", null, "滑板", "");
        MatchScorer.Result r = scorer.score(self, cand);
        assertEquals(1.0, r.category(), 0.0001);
        // 不同且未映射 → 回退原文精确比对，且 reason 带未映射标记
        Post other = post(PostType.FOUND.name(), "轮滑鞋", "南湖", "广场", null, "轮滑鞋", "");
        MatchScorer.Result r2 = scorer.score(self, other);
        assertEquals(0.0, r2.category(), 0.0001);
        assertTrue(r2.reasons().stream().anyMatch(s -> s.contains("未映射")));
    }
}
