package edu.whut.clf.lead;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface LeadEvidenceFileMapper {

    @Insert("INSERT INTO lead_evidence_files (lead_id, file_id) VALUES (#{leadId}, #{fileId})")
    int insert(@Param("leadId") Long leadId, @Param("fileId") Long fileId);

    /** 替换语义绑定前清除本线索旧证据（P1-B2；创建流程首次绑定命中 0 行）。 */
    @Delete("DELETE FROM lead_evidence_files WHERE lead_id = #{leadId}")
    int deleteByLead(Long leadId);

    @Select("SELECT file_id FROM lead_evidence_files WHERE lead_id = #{leadId}")
    List<Long> findFileIds(Long leadId);

    @Select("SELECT lead_id FROM lead_evidence_files WHERE file_id = #{fileId}")
    Long findLeadIdByFileId(Long fileId);
}
