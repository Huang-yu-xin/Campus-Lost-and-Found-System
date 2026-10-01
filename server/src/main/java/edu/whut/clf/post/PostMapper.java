package edu.whut.clf.post;

import edu.whut.clf.post.model.Post;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PostMapper {

    @Insert("""
            INSERT INTO posts (publisher_id, type, title, category, category_code, public_description, campus,
                               event_location, event_time, published_at, status, version)
            VALUES (#{publisherId}, #{type}, #{title}, #{category}, #{categoryCode}, #{publicDescription}, #{campus},
                    #{eventLocation}, #{eventTime}, #{publishedAt}, #{status}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Post post);

    @Select("SELECT * FROM posts WHERE id = #{id}")
    Post findById(Long id);

    /** 事务内锁定父发布记录（单活跃交接并发控制的关键）。 */
    @Select("SELECT * FROM posts WHERE id = #{id} FOR UPDATE")
    Post lockById(Long id);

    @Update("""
            UPDATE posts SET title = #{title}, category = #{category}, category_code = #{categoryCode},
                   public_description = #{publicDescription}, campus = #{campus},
                   event_location = #{eventLocation}, event_time = #{eventTime},
                   version = version + 1
            WHERE id = #{id}
            """)
    int updateEditable(Post post);

    // V4 闭环：closed_at 的单一权威维护规则——进入终态（COMPLETED/WITHDRAWN/REMOVED）
    // 写 NOW()，回到 ACTIVE（restore / 争议 TERMINATE_REOPEN）清 NULL。规则内嵌在下面两条
    // 状态转换 SQL 的 CASE 中，覆盖全部调用点（withdraw / mark-found / 交接完成 / 取消交接 /
    // 争议裁决 / 治理下架·恢复），因此无需改动任何 Java 调用方逻辑。

    /** 条件更新状态（乐观并发）：仅当当前状态匹配时才转换。返回受影响行数。 */
    @Update("""
            UPDATE posts
               SET status = #{to}, version = version + 1,
                   closed_at = CASE WHEN #{to} IN ('COMPLETED','WITHDRAWN','REMOVED') THEN NOW() ELSE NULL END
             WHERE id = #{id} AND status = #{from}
            """)
    int changeStatus(@Param("id") Long id, @Param("from") String from, @Param("to") String to);

    /** 管理员治理用：无条件设置状态（下架/恢复），前后状态由治理记录留存。 */
    @Update("""
            UPDATE posts
               SET status = #{to}, version = version + 1,
                   closed_at = CASE WHEN #{to} IN ('COMPLETED','WITHDRAWN','REMOVED') THEN NOW() ELSE NULL END
             WHERE id = #{id}
            """)
    int forceStatus(@Param("id") Long id, @Param("to") String to);

    // ---- 公开列表 / 搜索（M3 FR-POST-03 / M4 FR-SEARCH-01）----
    @Select("""
            <script>
            SELECT * FROM posts
            <where>
              status = 'ACTIVE'
              <if test="type != null"> AND type = #{type} </if>
              <if test="category != null and category != ''"> AND category = #{category} </if>
              <if test="campus != null and campus != ''"> AND campus = #{campus} </if>
              <if test="keyword != null and keyword != ''">
                AND (title LIKE CONCAT('%', #{keyword}, '%') OR public_description LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="eventFrom != null"> AND event_time &gt;= #{eventFrom} </if>
              <if test="eventTo != null"> AND event_time &lt;= #{eventTo} </if>
            </where>
            ORDER BY published_at DESC, id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<Post> searchPublic(@Param("keyword") String keyword, @Param("type") String type,
                            @Param("category") String category, @Param("campus") String campus,
                            @Param("eventFrom") LocalDateTime eventFrom, @Param("eventTo") LocalDateTime eventTo,
                            @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM posts
            <where>
              status = 'ACTIVE'
              <if test="type != null"> AND type = #{type} </if>
              <if test="category != null and category != ''"> AND category = #{category} </if>
              <if test="campus != null and campus != ''"> AND campus = #{campus} </if>
              <if test="keyword != null and keyword != ''">
                AND (title LIKE CONCAT('%', #{keyword}, '%') OR public_description LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="eventFrom != null"> AND event_time &gt;= #{eventFrom} </if>
              <if test="eventTo != null"> AND event_time &lt;= #{eventTo} </if>
            </where>
            </script>
            """)
    long countPublic(@Param("keyword") String keyword, @Param("type") String type,
                     @Param("category") String category, @Param("campus") String campus,
                     @Param("eventFrom") LocalDateTime eventFrom, @Param("eventTo") LocalDateTime eventTo);

    // ---- 我的发布（FR-POST-04）----
    @Select("SELECT * FROM posts WHERE publisher_id = #{userId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<Post> findByPublisher(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM posts WHERE publisher_id = #{userId}")
    long countByPublisher(Long userId);

    // ---- 匹配候选（相反类型、ACTIVE）(FR-MATCH-01) ----
    // P1：时间窗多臂候选。窗口与 T 子分的有效域一致（窗外候选 T=0，本就无价值）；
    // P4：同类目臂（category_code 相同、窗口更宽），覆盖"晚找回"场景；
    // 兜底臂覆盖 event_time 为空的候选（按发布时间回看 nullEventWindowDays 天）。
    // 每臂 LIMIT 仅作保险阀；臂间可能重叠，由 Java 侧按 id 去重兜底。
    @Select("""
            <script>
            (SELECT * FROM posts
              WHERE status = 'ACTIVE' AND type = #{oppositeType} AND id != #{selfId}
                AND event_time &gt;= #{winStart} AND event_time &lt;= #{winEnd}
              LIMIT #{armLimit})
            <if test="selfCode != null and selfCode != ''">
            UNION ALL
            (SELECT * FROM posts
              WHERE status = 'ACTIVE' AND type = #{oppositeType} AND id != #{selfId}
                AND category_code = #{selfCode}
                AND event_time &gt;= #{catStart} AND event_time &lt;= #{catEnd}
              LIMIT #{armLimit})
            </if>
            UNION ALL
            (SELECT * FROM posts
              WHERE status = 'ACTIVE' AND type = #{oppositeType} AND id != #{selfId}
                AND event_time IS NULL AND published_at &gt;= #{nullWinStart}
              LIMIT #{armLimit})
            </script>
            """)
    List<Post> findCandidatesWindowed(@Param("oppositeType") String oppositeType,
                                      @Param("selfId") Long selfId,
                                      @Param("winStart") LocalDateTime winStart,
                                      @Param("winEnd") LocalDateTime winEnd,
                                      @Param("selfCode") String selfCode,
                                      @Param("catStart") LocalDateTime catStart,
                                      @Param("catEnd") LocalDateTime catEnd,
                                      @Param("nullWinStart") LocalDateTime nullWinStart,
                                      @Param("armLimit") int armLimit);

    // ---- 治理检索（FR-ADMIN-01）----
    @Select("""
            <script>
            SELECT * FROM posts
            <where>
              <if test="status != null and status != ''"> status = #{status} </if>
              <if test="type != null and type != ''"> AND type = #{type} </if>
            </where>
            ORDER BY created_at DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<Post> adminSearch(@Param("status") String status, @Param("type") String type,
                           @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM posts
            <where>
              <if test="status != null and status != ''"> status = #{status} </if>
              <if test="type != null and type != ''"> AND type = #{type} </if>
            </where>
            </script>
            """)
    long adminSearchCount(@Param("status") String status, @Param("type") String type);
}
