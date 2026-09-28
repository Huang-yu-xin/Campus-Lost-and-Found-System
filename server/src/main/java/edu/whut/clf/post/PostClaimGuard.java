package edu.whut.clf.post;

/**
 * 模块边界扩展点：由认领模块(M5)实现，让发布模块(M3)在不反向依赖 claim 的前提下
 * 判断是否存在有效申请（用于编辑锁定/撤回校验）。
 */
public interface PostClaimGuard {

    /** 该发布是否存在有效(PENDING/WAITING_HANDOVER)申请。 */
    boolean hasActiveClaim(Long postId);
}
