package edu.whut.clf.lead;

import edu.whut.clf.lead.model.LostLead;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface LostLeadMapper {

    @Insert("INSERT INTO lost_leads (lost_post_id, reporter_id, body, status) VALUES (#{lostPostId}, #{reporterId}, #{body}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LostLead lead);

    @Select("SELECT * FROM lost_leads WHERE id = #{id}")
    LostLead findById(Long id);

    @Select("SELECT * FROM lost_leads WHERE lost_post_id = #{postId} ORDER BY created_at DESC")
    List<LostLead> findByPost(Long postId);

    @Select("SELECT * FROM lost_leads WHERE reporter_id = #{userId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<LostLead> findByReporter(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM lost_leads WHERE reporter_id = #{userId}")
    long countByReporter(Long userId);

    @Update("UPDATE lost_leads SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
