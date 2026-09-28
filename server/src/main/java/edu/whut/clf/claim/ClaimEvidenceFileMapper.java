package edu.whut.clf.claim;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ClaimEvidenceFileMapper {

    @Insert("INSERT INTO claim_evidence_files (claim_id, file_id) VALUES (#{claimId}, #{fileId})")
    int insert(@Param("claimId") Long claimId, @Param("fileId") Long fileId);

    @Select("SELECT file_id FROM claim_evidence_files WHERE claim_id = #{claimId}")
    List<Long> findFileIds(Long claimId);

    @Select("SELECT claim_id FROM claim_evidence_files WHERE file_id = #{fileId}")
    Long findClaimIdByFileId(Long fileId);
}
