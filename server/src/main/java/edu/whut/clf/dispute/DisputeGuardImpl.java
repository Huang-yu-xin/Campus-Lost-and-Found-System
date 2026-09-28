package edu.whut.clf.dispute;

import edu.whut.clf.claim.ClaimDisputeGuard;
import org.springframework.stereotype.Component;

/** 向认领模块(M5)提供"是否存在 OPEN 争议"，用于派生交接暂停。 */
@Component
public class DisputeGuardImpl implements ClaimDisputeGuard {

    private final DisputeMapper disputeMapper;

    public DisputeGuardImpl(DisputeMapper disputeMapper) {
        this.disputeMapper = disputeMapper;
    }

    @Override
    public boolean hasOpenDispute(Long claimId) {
        return disputeMapper.countOpenByClaim(claimId) > 0;
    }
}
