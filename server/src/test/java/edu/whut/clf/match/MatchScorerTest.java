package edu.whut.clf.match;

import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.common.enums.PostType;
import edu.whut.clf.post.model.Post;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** 匹配打分纯单元测试（无需数据库）。验证分数可复算、异常时间降权、缺字段说明。 */
class MatchScorerTest {

    private final MatchScorer scorer = new MatchScorer(new AppProperties.Match());

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
}
