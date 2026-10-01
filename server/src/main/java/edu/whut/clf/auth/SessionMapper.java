package edu.whut.clf.auth;

import edu.whut.clf.auth.model.Session;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface SessionMapper {

    @Insert("""
            INSERT INTO sessions (user_id, token_hash, expires_at)
            VALUES (#{userId}, #{tokenHash}, #{expiresAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Session session);

    @Update("UPDATE sessions SET revoked_at = #{now} WHERE token_hash = #{tokenHash} AND revoked_at IS NULL")
    int revokeByHash(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(*) FROM sessions
            WHERE token_hash = #{tokenHash} AND revoked_at IS NULL AND expires_at > #{now}
              AND EXISTS (SELECT 1 FROM users u WHERE u.id = sessions.user_id AND u.status != 'DISABLED')
            """)
    long countActive(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    /** D14/P2-13：删除创建超过 cutoff 且已失效（已撤销或已过期）的会话行。返回删除行数。 */
    @Delete("""
            DELETE FROM sessions
            WHERE created_at < #{cutoff} AND (revoked_at IS NOT NULL OR expires_at < #{now})
            """)
    int deleteStale(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);
}
