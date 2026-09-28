package edu.whut.clf.handover;

import org.apache.ibatis.annotations.*;

@Mapper
public interface HandoverConfirmationMapper {

    /** 幂等插入：唯一键 (claim_id, confirmed_by)。冲突返回 0（已确认过）。 */
    @Insert("""
            INSERT IGNORE INTO handover_confirmations (claim_id, confirmed_by)
            VALUES (#{claimId}, #{confirmedBy})
            """)
    int insertIgnore(@Param("claimId") Long claimId, @Param("confirmedBy") Long confirmedBy);

    @Select("SELECT COUNT(*) FROM handover_confirmations WHERE claim_id = #{claimId} AND confirmed_by = #{userId}")
    long exists(@Param("claimId") Long claimId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM handover_confirmations WHERE claim_id = #{claimId}")
    long countByClaim(Long claimId);
}
