package org.unreal.modelrouter.router.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.config.core.ConfigurationService;

import java.util.Map;

/**
 * 服务熔断配置控制器
 * v1.5.2: 简化实现
 */
@Slf4j
@RestController
@RequestMapping("/api/services/{serviceType}/circuitbreaker")
@RequiredArgsConstructor
public class ServiceCircuitBreakerController {

    private final ConfigurationService configurationService;

    /**
     * 获取熔断配置
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCircuitBreakerConfig(@PathVariable final String serviceType) {
        log.debug("Getting circuit breaker config for service: {}", serviceType);
        Map<String, Object> config = configurationService.getServiceConfig(serviceType);
        @SuppressWarnings("unchecked")
        Map<String, Object> circuitBreaker = config != null ? (Map<String, Object>) config.getOrDefault("circuitBreaker", Map.of()) : Map.of();
        return ResponseEntity.ok(circuitBreaker);
    }

    /**
     * 更新熔断配置
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateCircuitBreakerConfig(
            @PathVariable final String serviceType,
            @RequestBody final Map<String, Object> circuitBreakerConfig) {
        log.info("Updating circuit breaker config for service: {}", serviceType);
        // 简化实现：直接返回传入的配置
        return ResponseEntity.ok(circuitBreakerConfig);
    }
}
