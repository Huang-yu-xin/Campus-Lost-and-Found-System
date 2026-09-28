package edu.whut.clf.audit;

import edu.whut.clf.audit.model.AuditLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AuditLogMapper {

    @Insert("""
            INSERT INTO audit_logs (actor_id, actor_type, action, target_type, target_id, request_id, result, metadata)
            VALUES (#{actorId}, #{actorType}, #{action}, #{targetType}, #{targetId}, #{requestId}, #{result}, #{metadata})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuditLog log);

    @Select("""
            <script>
            SELECT * FROM audit_logs
            <where>
              <if test="action != null and action != ''"> action = #{action} </if>
              <if test="targetType != null and targetType != ''"> AND target_type = #{targetType} </if>
            </where>
            ORDER BY created_at DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<AuditLog> search(@Param("action") String action, @Param("targetType") String targetType,
                          @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM audit_logs
            <where>
              <if test="action != null and action != ''"> action = #{action} </if>
              <if test="targetType != null and targetType != ''"> AND target_type = #{targetType} </if>
            </where>
            </script>
            """)
    long count(@Param("action") String action, @Param("targetType") String targetType);
}
