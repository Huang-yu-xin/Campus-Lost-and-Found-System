package edu.whut.clf.dispute;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DisputeEvidenceFileMapper {

    @Insert("INSERT INTO dispute_evidence_files (dispute_id, file_id, uploaded_by) VALUES (#{disputeId}, #{fileId}, #{uploadedBy})")
    int insert(@Param("disputeId") Long disputeId, @Param("fileId") Long fileId, @Param("uploadedBy") Long uploadedBy);

    @Select("SELECT file_id FROM dispute_evidence_files WHERE dispute_id = #{disputeId}")
    List<Long> findFileIds(Long disputeId);

    @Select("SELECT dispute_id FROM dispute_evidence_files WHERE file_id = #{fileId}")
    Long findDisputeIdByFileId(Long fileId);
}
