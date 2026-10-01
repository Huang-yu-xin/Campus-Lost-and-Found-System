package edu.whut.clf.post;

import edu.whut.clf.post.model.PostImage;
import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PostImageMapper {

    @Insert("INSERT INTO post_images (post_id, file_id, sort_order) VALUES (#{postId}, #{fileId}, #{sortOrder})")
    int insert(PostImage image);

    /** 替换语义绑定前清除本帖旧图（P1-B2）。 */
    @Delete("DELETE FROM post_images WHERE post_id = #{postId}")
    int deleteByPost(Long postId);

    @Select("SELECT * FROM post_images WHERE post_id = #{postId} ORDER BY sort_order")
    List<PostImage> findByPost(Long postId);

    /** 批量取多张帖子的公开图（匹配候选列表用，替代逐帖 N+1 查询）。 */
    @Select("""
            <script>
            SELECT * FROM post_images
            WHERE post_id IN
            <foreach collection="postIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
            ORDER BY post_id, sort_order
            </script>
            """)
    List<PostImage> findByPostIds(@Param("postIds") Collection<Long> postIds);
}
