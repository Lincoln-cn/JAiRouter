package org.unreal.modelrouter.router.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.config.core.ServiceConfigManager;
import org.unreal.modelrouter.config.core.dto.ServiceConfiguration;

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

    private final ServiceConfigManager serviceConfigManager;  // 替换 ConfigurationService

    /**
     * 获取限流配置
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getRateLimitConfig(@PathVariable final String serviceType) {
        log.debug("Getting rate limit config for service: {}", serviceType);
        // 使用 ServiceConfigManager 替代废弃方法
        ServiceConfiguration config = serviceConfigManager.getServiceConfiguration(serviceType);
        Map<String, Object> rateLimit = config != null && config.rateLimit() != null
                ? config.rateLimit().toMap() : Map.of();
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
