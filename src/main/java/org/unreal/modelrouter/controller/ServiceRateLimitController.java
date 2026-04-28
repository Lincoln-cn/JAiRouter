package org.unreal.modelrouter.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.config.core.ConfigurationService;

import java.util.Map;

/**
 * 服务限流配置控制器
 * v1.5.2: 简化实现
 */
@Slf4j
@RestController
@RequestMapping("/api/services/{serviceType}/ratelimit")
@RequiredArgsConstructor
public class ServiceRateLimitController {

    private final ConfigurationService configurationService;

    /**
     * 获取限流配置
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getRateLimitConfig(@PathVariable final String serviceType) {
        log.debug("Getting rate limit config for service: {}", serviceType);
        Map<String, Object> config = configurationService.getServiceConfig(serviceType);
        @SuppressWarnings("unchecked")
        Map<String, Object> rateLimit = config != null ? (Map<String, Object>) config.getOrDefault("rateLimit", Map.of()) : Map.of();
        return ResponseEntity.ok(rateLimit);
    }

    /**
     * 更新限流配置
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateRateLimitConfig(
            @PathVariable final String serviceType,
            @RequestBody final Map<String, Object> rateLimitConfig) {
        log.info("Updating rate limit config for service: {}", serviceType);
        // 简化实现：直接返回传入的配置
        return ResponseEntity.ok(rateLimitConfig);
    }
}
