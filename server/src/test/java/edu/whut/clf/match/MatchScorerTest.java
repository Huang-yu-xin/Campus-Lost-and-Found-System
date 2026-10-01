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
    private final MatchScorer scorer = new MatchScorer(new AppProperties.Match(), dictionary);

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
