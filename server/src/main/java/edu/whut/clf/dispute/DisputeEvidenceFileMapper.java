package edu.whut.clf.dispute;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DisputeEvidenceFileMapper {

    @Insert("INSERT INTO dispute_evidence_files (dispute_id, file_id, uploaded_by) VALUES (#{disputeId}, #{fileId}, #{uploadedBy})")
    int insert(@Param("disputeId") Long disputeId, @Param("fileId") Long fileId, @Param("uploadedBy") Long uploadedBy);

    /** 替换语义绑定前清除本争议旧证据（P1-B2；创建流程首次绑定命中 0 行）。 */
    @Delete("DELETE FROM dispute_evidence_files WHERE dispute_id = #{disputeId}")
    int deleteByDispute(Long disputeId);

    @Select("SELECT file_id FROM dispute_evidence_files WHERE dispute_id = #{disputeId}")
    List<Long> findFileIds(Long disputeId);

    @Select("SELECT dispute_id FROM dispute_evidence_files WHERE file_id = #{fileId}")
    Long findDisputeIdByFileId(Long fileId);
}
