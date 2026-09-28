package edu.whut.clf.claim;

/**
 * 模块边界扩展点：由争议模块(M6)实现，让认领模块(M5)判断是否存在 OPEN 争议，
 * 以派生"交接暂停"，避免多个互相矛盾的状态来源。
 */
public interface ClaimDisputeGuard {

    boolean hasOpenDispute(Long claimId);
}
