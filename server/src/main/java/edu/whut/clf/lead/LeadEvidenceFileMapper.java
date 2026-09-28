package edu.whut.clf.lead;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface LeadEvidenceFileMapper {

    @Insert("INSERT INTO lead_evidence_files (lead_id, file_id) VALUES (#{leadId}, #{fileId})")
    int insert(@Param("leadId") Long leadId, @Param("fileId") Long fileId);

    @Select("SELECT file_id FROM lead_evidence_files WHERE lead_id = #{leadId}")
    List<Long> findFileIds(Long leadId);

    @Select("SELECT lead_id FROM lead_evidence_files WHERE file_id = #{fileId}")
    Long findLeadIdByFileId(Long fileId);
}
