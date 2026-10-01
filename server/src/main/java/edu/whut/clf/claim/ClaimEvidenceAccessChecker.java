package edu.whut.clf.claim;

import edu.whut.clf.common.enums.FilePurpose;
import edu.whut.clf.file.FilePrivateAccessChecker;
import edu.whut.clf.file.model.StoredFile;
import edu.whut.clf.post.PostMapper;
import edu.whut.clf.post.model.Post;
import edu.whut.clf.claim.model.Claim;
import org.springframework.stereotype.Component;

/** 私密认领证明访问控制：仅申请人与对应发布者可读（争议管理员由争议模块单独授权）。 */
@Component
public class ClaimEvidenceAccessChecker implements FilePrivateAccessChecker {

    private final ClaimEvidenceFileMapper evidenceMapper;
    private final ClaimMapper claimMapper;
    private final PostMapper postMapper;
    private final edu.whut.clf.dispute.DisputeMapper disputeMapper;

    public ClaimEvidenceAccessChecker(ClaimEvidenceFileMapper evidenceMapper, ClaimMapper claimMapper, PostMapper postMapper, edu.whut.clf.dispute.DisputeMapper disputeMapper) {
        this.evidenceMapper = evidenceMapper;
        this.claimMapper = claimMapper;
        this.postMapper = postMapper;
        this.disputeMapper = disputeMapper;
    }

    @Override
    public boolean supports(String purpose) {
        return FilePurpose.PRIVATE_CLAIM.name().equals(purpose);
    }

    @Override
    public boolean canAccess(Long userId, StoredFile file) {
        Long claimId = evidenceMapper.findClaimIdByFileId(file.getId());
        if (claimId == null) {
            return false;
        }
        Claim claim = claimMapper.findById(claimId);
        if (claim == null) {
            return false;
        }
        if (claim.getApplicantId().equals(userId)) {
            return true;
        }
        if (edu.whut.clf.common.security.AuthContext.current() != null
                && edu.whut.clf.common.security.AuthContext.current().isAdmin()
                && disputeMapper.countAssignedByClaim(claimId, userId) > 0) return true;
        Post post = postMapper.findById(claim.getPostId());
        return post != null && post.getPublisherId().equals(userId);
    }
}
