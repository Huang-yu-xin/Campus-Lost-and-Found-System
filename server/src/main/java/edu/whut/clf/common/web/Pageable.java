package edu.whut.clf.common.web;

import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;

/**
 * 分页入参归一化（D1/P2-1）：页码下界 1、上限 {@link #MAX_PAGE}（超出 400），每页 1..100（非法回落 20），
 * offset 用 long 计算防止 (page-1)*size 的 int 溢出产生负 LIMIT。
 * 所有分页服务统一走本类，避免每处重复 Math.max/上限判断。
 */
public record Pageable(int page, int size, long offset) {

    public static final int MAX_PAGE = 100000;

    public static Pageable of(int page, int pageSize) {
        int p = Math.max(1, page);
        if (p > MAX_PAGE) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "page 超出上限 " + MAX_PAGE);
        }
        int size = pageSize <= 0 || pageSize > 100 ? 20 : pageSize;
        return new Pageable(p, size, (long) (p - 1) * size);
    }
}
