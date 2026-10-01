package edu.whut.clf.claim;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ClaimEvidenceFileMapper {

    @Insert("INSERT INTO claim_evidence_files (claim_id, file_id) VALUES (#{claimId}, #{fileId})")
    int insert(@Param("claimId") Long claimId, @Param("fileId") Long fileId);

    /** 替换语义绑定前清除本申请旧证据（P1-B2；创建流程首次绑定命中 0 行）。 */
    @Delete("DELETE FROM claim_evidence_files WHERE claim_id = #{claimId}")
    int deleteByClaim(Long claimId);

    @Select("SELECT file_id FROM claim_evidence_files WHERE claim_id = #{claimId}")
    List<Long> findFileIds(Long claimId);

    @Select("SELECT claim_id FROM claim_evidence_files WHERE file_id = #{fileId}")
    Long findClaimIdByFileId(Long fileId);
}
