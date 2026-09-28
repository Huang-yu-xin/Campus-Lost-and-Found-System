package edu.whut.clf.claim;

import edu.whut.clf.post.PostClaimGuard;
import org.springframework.stereotype.Component;

/** 向发布模块(M3)提供"是否存在有效申请"的判断（用于编辑锁定/撤回校验）。 */
@Component
public class ClaimGuardImpl implements PostClaimGuard {

    private final ClaimMapper claimMapper;

    public ClaimGuardImpl(ClaimMapper claimMapper) {
        this.claimMapper = claimMapper;
    }

    @Override
    public boolean hasActiveClaim(Long postId) {
        return claimMapper.countActiveByPost(postId) > 0;
    }
}
