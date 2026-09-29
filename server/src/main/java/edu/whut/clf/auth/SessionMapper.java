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
            """)
    long countActive(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);
}
