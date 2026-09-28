package edu.whut.clf.lead;

import edu.whut.clf.common.enums.FilePurpose;
import edu.whut.clf.file.FilePrivateAccessChecker;
import edu.whut.clf.file.model.StoredFile;
import edu.whut.clf.lead.model.LostLead;
import edu.whut.clf.post.PostMapper;
import edu.whut.clf.post.model.Post;
import org.springframework.stereotype.Component;

/** 私密线索证明访问控制：仅线索提交者与寻物发布者可读。 */
@Component
public class LeadEvidenceAccessChecker implements FilePrivateAccessChecker {

    private final LeadEvidenceFileMapper evidenceMapper;
    private final LostLeadMapper leadMapper;
    private final PostMapper postMapper;

    public LeadEvidenceAccessChecker(LeadEvidenceFileMapper evidenceMapper, LostLeadMapper leadMapper, PostMapper postMapper) {
        this.evidenceMapper = evidenceMapper;
        this.leadMapper = leadMapper;
        this.postMapper = postMapper;
    }

    @Override
    public boolean supports(String purpose) {
        return FilePurpose.PRIVATE_LEAD.name().equals(purpose);
    }

    @Override
    public boolean canAccess(Long userId, StoredFile file) {
        Long leadId = evidenceMapper.findLeadIdByFileId(file.getId());
        if (leadId == null) {
            return false;
        }
        LostLead lead = leadMapper.findById(leadId);
        if (lead == null) {
            return false;
        }
        if (lead.getReporterId().equals(userId)) {
            return true;
        }
        Post post = postMapper.findById(lead.getLostPostId());
        return post != null && post.getPublisherId().equals(userId);
    }
}
