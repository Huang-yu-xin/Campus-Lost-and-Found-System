package edu.whut.clf.message;

import edu.whut.clf.message.model.ClaimMessage;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ClaimMessageMapper {

    @Insert("INSERT INTO claim_messages (claim_id, sender_id, body) VALUES (#{claimId}, #{senderId}, #{body})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ClaimMessage message);

    @Select("SELECT * FROM claim_messages WHERE claim_id = #{claimId} ORDER BY created_at ASC, id ASC")
    List<ClaimMessage> findByClaim(Long claimId);

    /** 将对方发来的未读消息标记为已读。 */
    @Update("""
            UPDATE claim_messages SET read_at = #{now}
            WHERE claim_id = #{claimId} AND sender_id <> #{readerId} AND read_at IS NULL
            """)
    int markRead(@Param("claimId") Long claimId, @Param("readerId") Long readerId, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM claim_messages WHERE claim_id = #{claimId} AND sender_id <> #{readerId} AND read_at IS NULL")
    long countUnread(@Param("claimId") Long claimId, @Param("readerId") Long readerId);
}
