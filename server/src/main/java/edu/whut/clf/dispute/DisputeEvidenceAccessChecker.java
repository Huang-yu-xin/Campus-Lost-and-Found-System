package edu.whut.clf.dispute;

import edu.whut.clf.claim.ClaimAccessService;
import edu.whut.clf.common.enums.FilePurpose;
import edu.whut.clf.dispute.model.Dispute;
import edu.whut.clf.file.FilePrivateAccessChecker;
import edu.whut.clf.file.model.StoredFile;
import org.springframework.stereotype.Component;

/**
 * 私密争议证据访问控制：仅争议当事人（认领双方）与**受理该争议的管理员**可读。
 * 未获分配的管理员不可读取（NFR-SEC-02 / 任务书 §6.251）。
 */
@Component
public class DisputeEvidenceAccessChecker implements FilePrivateAccessChecker {

    private final DisputeEvidenceFileMapper evidenceMapper;
    private final DisputeMapper disputeMapper;
    private final ClaimAccessService claimAccess;

    public DisputeEvidenceAccessChecker(DisputeEvidenceFileMapper evidenceMapper, DisputeMapper disputeMapper,
                                        ClaimAccessService claimAccess) {
        this.evidenceMapper = evidenceMapper;
        this.disputeMapper = disputeMapper;
        this.claimAccess = claimAccess;
    }

    @Override
    public boolean supports(String purpose) {
        return FilePurpose.PRIVATE_DISPUTE.name().equals(purpose);
    }

    @Override
    public boolean canAccess(Long userId, StoredFile file) {
        Long disputeId = evidenceMapper.findDisputeIdByFileId(file.getId());
        if (disputeId == null) {
            return false;
        }
        Dispute dispute = disputeMapper.findById(disputeId);
        if (dispute == null) {
            return false;
        }
        // 受理管理员
        if (userId.equals(dispute.getAssignedAdminId())) {
            return true;
        }
        // 争议当事人（认领双方）
        try {
            return claimAccess.load(dispute.getClaimId()).isParticipant(userId);
        } catch (Exception e) {
            return false;
        }
    }
}
