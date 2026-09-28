package edu.whut.clf.post;

import edu.whut.clf.post.model.PostImage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PostImageMapper {

    @Insert("INSERT INTO post_images (post_id, file_id, sort_order) VALUES (#{postId}, #{fileId}, #{sortOrder})")
    int insert(PostImage image);

    @Select("SELECT * FROM post_images WHERE post_id = #{postId} ORDER BY sort_order")
    List<PostImage> findByPost(Long postId);
}
