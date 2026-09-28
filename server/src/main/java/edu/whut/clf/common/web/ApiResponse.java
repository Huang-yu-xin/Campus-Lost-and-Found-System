package edu.whut.clf.common.web;

/**
 * 统一响应封装：{ "code": "OK", "message": "...", "data": ... }。
 * 失败响应可附 requestId，且不得泄漏堆栈（见 docs/api/conventions.md）。
 *
 * @param <T> data 载荷类型
 */
public record ApiResponse<T>(String code, String message, T data, String requestId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data, null);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>("OK", message, data, null);
    }

    public static <T> ApiResponse<T> error(String code, String message, String requestId) {
        return new ApiResponse<>(code, message, null, requestId);
    }
}
