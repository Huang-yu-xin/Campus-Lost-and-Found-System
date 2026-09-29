package edu.whut.clf.post;

import edu.whut.clf.post.model.Post;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PostMapper {

    @Insert("""
            INSERT INTO posts (publisher_id, type, title, category, public_description, campus,
                               event_location, event_time, status, version)
            VALUES (#{publisherId}, #{type}, #{title}, #{category}, #{publicDescription}, #{campus},
                    #{eventLocation}, #{eventTime}, #{status}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Post post);

    @Select("SELECT * FROM posts WHERE id = #{id}")
    Post findById(Long id);

    /** 事务内锁定父发布记录（单活跃交接并发控制的关键）。 */
    @Select("SELECT * FROM posts WHERE id = #{id} FOR UPDATE")
    Post lockById(Long id);

    @Update("""
            UPDATE posts SET title = #{title}, category = #{category}, public_description = #{publicDescription},
                   campus = #{campus}, event_location = #{eventLocation}, event_time = #{eventTime},
                   version = version + 1
            WHERE id = #{id}
            """)
    int updateEditable(Post post);

    /** 条件更新状态（乐观并发）：仅当当前状态匹配时才转换。返回受影响行数。 */
    @Update("UPDATE posts SET status = #{to}, version = version + 1 WHERE id = #{id} AND status = #{from}")
    int changeStatus(@Param("id") Long id, @Param("from") String from, @Param("to") String to);

    /** 管理员治理用：无条件设置状态（下架/恢复），前后状态由治理记录留存。 */
    @Update("UPDATE posts SET status = #{to}, version = version + 1 WHERE id = #{id}")
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
    @Select("""
            SELECT * FROM posts
            WHERE status = 'ACTIVE' AND type = #{oppositeType} AND id <> #{selfId}
            ORDER BY published_at DESC
            LIMIT #{limit}
            """)
    List<Post> findCandidates(@Param("oppositeType") String oppositeType,
                              @Param("selfId") Long selfId, @Param("limit") int limit);

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
