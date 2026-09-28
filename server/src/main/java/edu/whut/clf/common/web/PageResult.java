package edu.whut.clf.common.web;

import java.util.List;

/**
 * 统一分页载荷：data.items / total / page / pageSize（见 conventions.md）。
 */
public record PageResult<T>(List<T> items, long total, int page, int pageSize) {

    public static <T> PageResult<T> of(List<T> items, long total, int page, int pageSize) {
        return new PageResult<>(items, total, page, pageSize);
    }
}
