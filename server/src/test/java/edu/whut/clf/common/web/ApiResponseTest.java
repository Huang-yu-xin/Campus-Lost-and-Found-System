package edu.whut.clf.common.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 纯单元测试（不加载 Spring 上下文，无需数据库），用于 P0 CI 冒烟。
 */
class ApiResponseTest {

    @Test
    void ok_wrapsDataWithOkCode() {
        ApiResponse<String> resp = ApiResponse.ok("payload");
        assertEquals("OK", resp.code());
        assertEquals("payload", resp.data());
        assertNull(resp.requestId());
    }

    @Test
    void error_carriesCodeAndRequestId() {
        ApiResponse<Void> resp = ApiResponse.error("SELF_CLAIM_FORBIDDEN", "不能认领自己的发布", "req-123");
        assertEquals("SELF_CLAIM_FORBIDDEN", resp.code());
        assertEquals("req-123", resp.requestId());
        assertNull(resp.data());
    }
}
