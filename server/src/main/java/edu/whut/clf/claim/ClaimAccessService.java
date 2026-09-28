package edu.whut.clf.claim;

import edu.whut.clf.claim.model.Claim;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.post.PostMapper;
import edu.whut.clf.post.model.Post;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 认领参与方查询与鉴权（供 M5/M6 复用）。 */
@Service
public class ClaimAccessService {

    private final ClaimMapper claimMapper;
    private final PostMapper postMapper;

    public ClaimAccessService(ClaimMapper claimMapper, PostMapper postMapper) {
        this.claimMapper = claimMapper;
        this.postMapper = postMapper;
    }

    public record Participants(Long claimId, Long applicantId, Long publisherId, String claimStatus) {
        public boolean isParticipant(Long userId) {
            return Objects.equals(applicantId, userId) || Objects.equals(publisherId, userId);
        }
    }

    public Participants load(Long claimId) {
        Claim claim = claimMapper.findById(claimId);
        if (claim == null) {
            throw BusinessException.of(ErrorCode.CLAIM_NOT_FOUND);
        }
        Post post = postMapper.findById(claim.getPostId());
        if (post == null) {
            throw BusinessException.of(ErrorCode.POST_NOT_FOUND);
        }
        return new Participants(claimId, claim.getApplicantId(), post.getPublisherId(), claim.getStatus());
    }

    /** 要求为参与方，否则按 404 避免枚举。 */
    public Participants requireParticipant(Long claimId, Long userId) {
        Participants p = load(claimId);
        if (!p.isParticipant(userId)) {
            throw BusinessException.of(ErrorCode.CLAIM_NOT_FOUND);
        }
        return p;
    }
}
