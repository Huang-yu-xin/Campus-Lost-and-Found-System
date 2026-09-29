package edu.whut.clf.dispute;

import edu.whut.clf.dispute.model.Dispute;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DisputeMapper {

    @Insert("""
            INSERT INTO disputes (claim_id, raised_by, reason, description, status)
            VALUES (#{claimId}, #{raisedBy}, #{reason}, #{description}, 'OPEN')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Dispute dispute);

    @Select("SELECT * FROM disputes WHERE id = #{id}")
    Dispute findById(Long id);

    @Select("SELECT * FROM disputes WHERE claim_id = #{claimId} ORDER BY created_at DESC")
    List<Dispute> findByClaim(Long claimId);

    @Select("SELECT COUNT(*) FROM disputes WHERE claim_id = #{claimId} AND status = 'OPEN'")
    long countOpenByClaim(Long claimId);

    @Select("""
            <script>
            SELECT * FROM disputes
            <where>
              <if test="status != null and status != ''"> status = #{status} </if>
            </where>
            ORDER BY created_at DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<Dispute> adminSearch(@Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM disputes
            <where>
              <if test="status != null and status != ''"> status = #{status} </if>
            </where>
            </script>
            """)
    long adminCount(@Param("status") String status);

    @Update("""
            UPDATE disputes SET status = #{status}, assigned_admin_id = #{adminId},
                   resolution_type = #{resolutionType}, resolution_note = #{note}, resolved_at = #{now}
            WHERE id = #{id} AND status = 'OPEN'
            """)
    int resolve(@Param("id") Long id, @Param("status") String status, @Param("adminId") Long adminId,
                @Param("resolutionType") String resolutionType, @Param("note") String note,
                @Param("now") LocalDateTime now);

    /** 受理：仅 OPEN 可受理，允许改派。返回受影响行数。 */
    @Update("UPDATE disputes SET assigned_admin_id = #{adminId} WHERE id = #{id} AND status = 'OPEN'")
    int assign(@Param("id") Long id, @Param("adminId") Long adminId);
}
