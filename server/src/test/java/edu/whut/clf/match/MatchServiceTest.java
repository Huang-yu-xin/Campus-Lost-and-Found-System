package edu.whut.clf.match;

import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.common.enums.PostStatus;
import edu.whut.clf.common.enums.PostType;
import edu.whut.clf.match.dto.MatchDtos.MatchCandidate;
import edu.whut.clf.post.PostImageMapper;
import edu.whut.clf.post.PostMapper;
import edu.whut.clf.post.model.Post;
import edu.whut.clf.post.model.PostImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * P2 回归防护：图片查询必须后置到截断之后并批量执行——
 * 断言逐帖查询 findByPost 零调用，批量查询 findByPostIds 恰好一次且只带最终候选 id。
 */
class MatchServiceTest {

    private PostMapper postMapper;
    private PostImageMapper imageMapper;
    private MatchService service;
    private AppProperties props;

    @BeforeEach
    void setUp() {
        postMapper = mock(PostMapper.class);
        imageMapper = mock(PostImageMapper.class);
        props = new AppProperties();
        service = new MatchService(postMapper, imageMapper,
                new MatchScorer(props.getMatch(), new CategoryDictionary(), new TextTokenizer()), props);
    }

    private Post post(long id, String type, String category, String campus, String loc,
                      LocalDateTime eventTime, String title, String desc) {
        Post p = new Post();
        p.setId(id);
        p.setPublisherId(999L);
        p.setType(type);
        p.setTitle(title);
        p.setCategory(category);
        p.setPublicDescription(desc);
        p.setCampus(campus);
        p.setEventLocation(loc);
        p.setEventTime(eventTime);
        p.setPublishedAt(LocalDateTime.now(java.time.Clock.systemUTC()).minusDays(1));
        p.setStatus(PostStatus.ACTIVE.name());
        p.setVersion(0);
        return p;
    }

    @Test
    void imagesFetchedInSingleBatch_afterTruncation() {
        Post self = post(1, "LOST", "水杯", "南湖校区", "图书馆",
                LocalDateTime.now(java.time.Clock.systemUTC()).minusDays(2), "丢失白色保温杯", "白色保温杯在图书馆遗失");
        when(postMapper.findById(1L)).thenReturn(self);

        // 60 条候选，全部高分（同类同地）：截断后只应保留 20 条
        List<Post> candidates = new java.util.ArrayList<>();
        for (long id = 100; id < 160; id++) {
            candidates.add(post(id, "FOUND", "水杯", "南湖校区", "图书馆",
                    LocalDateTime.now(java.time.Clock.systemUTC()).minusDays(1), "捡到白色保温杯", "捡到白色保温杯在图书馆"));
        }
        when(postMapper.findCandidatesWindowed(anyString(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(candidates);

        // 只有 id=100 的候选有图片
        PostImage img = new PostImage();
        img.setPostId(100L);
        img.setFileId(7L);
        img.setSortOrder(0);
        when(imageMapper.findByPostIds(any())).thenReturn(List.of(img));

        List<MatchCandidate> result = service.matchesFor(1L);

        assertEquals(20, result.size());
        // N+1 防护断言：逐帖查询零调用，批量查询恰好一次
        verify(imageMapper, never()).findByPost(any());
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(imageMapper, times(1)).findByPostIds(captor.capture());
        assertEquals(20, captor.getValue().size());

        // 分数降序 + postId 升序稳定排序
        for (int i = 1; i < result.size(); i++) {
            assertTrue(result.get(i - 1).score() >= result.get(i).score());
        }
        // 图片只挂到有图的候选，其余为空列表
        MatchCandidate withImg = result.stream().filter(c -> c.postId() == 100L).findFirst().orElseThrow();
        assertEquals(List.of(7L), withImg.imageFileIds());
        MatchCandidate withoutImg = result.stream().filter(c -> c.postId() != 100L).findFirst().orElseThrow();
        assertTrue(withoutImg.imageFileIds().isEmpty());
    }

    @Test
    void lowScoreCandidates_getNoImageQuery() {
        Post self = post(1, "LOST", "水杯", "南湖校区", "图书馆",
                LocalDateTime.now(java.time.Clock.systemUTC()).minusDays(2), "丢失白色保温杯", "白色保温杯在图书馆遗失");
        when(postMapper.findById(1L)).thenReturn(self);
        // 全部低分候选（类别不同、地点/文本无重合）→ 过滤后为空 → 不应触发任何图片查询
        List<Post> candidates = List.of(
                post(200, "FOUND", "钥匙", "余家头校区", "操场",
                        LocalDateTime.now(java.time.Clock.systemUTC()).plusDays(100), "捡到一串钥匙", "操场捡到钥匙"));
        when(postMapper.findCandidatesWindowed(anyString(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(candidates);

        List<MatchCandidate> result = service.matchesFor(1L);

        assertTrue(result.isEmpty());
        verify(imageMapper, never()).findByPostIds(any());
        verify(imageMapper, never()).findByPost(any());
    }

    @Test
    void nonActiveSelf_returnsEmpty_withoutCandidateQuery() {
        Post self = post(1, "LOST", "水杯", "南湖校区", "图书馆",
                LocalDateTime.now(java.time.Clock.systemUTC()).minusDays(2), "丢失保温杯", "x");
        self.setStatus(PostStatus.COMPLETED.name());
        when(postMapper.findById(1L)).thenReturn(self);

        List<MatchCandidate> result = service.matchesFor(1L);

        assertTrue(result.isEmpty());
        verify(postMapper, never()).findCandidatesWindowed(anyString(), any(), any(), any(), any(), any(), any(), any(), anyInt());
        verify(imageMapper, never()).findByPostIds(any());
    }
}
