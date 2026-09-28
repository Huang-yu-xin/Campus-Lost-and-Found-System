package edu.whut.clf.admin;

import edu.whut.clf.admin.model.ModerationAction;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ModerationActionMapper {

    @Insert("""
            INSERT INTO moderation_actions (admin_id, target_type, target_id, action, reason, before_state, after_state)
            VALUES (#{adminId}, #{targetType}, #{targetId}, #{action}, #{reason}, #{beforeState}, #{afterState})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ModerationAction action);

    /** 取某发布最近一次下架前状态（用于恢复参考）。 */
    @Select("""
            SELECT before_state FROM moderation_actions
            WHERE target_type = 'POST' AND target_id = #{postId} AND action = 'REMOVE'
            ORDER BY created_at DESC LIMIT 1
            """)
    String findLastRemovedBeforeState(Long postId);
}
