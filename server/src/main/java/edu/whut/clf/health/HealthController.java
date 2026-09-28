package edu.whut.clf.health;

import edu.whut.clf.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查端点，用于 P0 脚手架自检与 CI 冒烟。
 * 路径前缀 /api/v1 由 spring.mvc.servlet.path / server.servlet.context-path 配置提供。
 */
@RestController
@Tag(name = "Health", description = "服务健康检查")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "返回服务状态与时间戳（UTC）")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "campus-lost-found-server");
        data.put("time", Instant.now().toString());
        return ApiResponse.ok(data);
    }
}
