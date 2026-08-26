package org.unreal.modelrouter.router.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.config.sync.repository.StoreConfigRepository;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;

import java.util.Map;

/**
 * 服务限流配置控制器
 * v1.5.2: 简化实现
 * v2.8.8: PUT 做实 — 持久化(StoreManager)+ 热生效(RateLimitManager),GET/PUT 统一 canonical 格式
 */
@Slf4j
@RestController
@RequestMapping("/api/services/{serviceType}/ratelimit")
@RequiredArgsConstructor
public class ServiceRateLimitController {

    private final StoreConfigRepository storeConfigRepository;
    private final RateLimitManager rateLimitManager;
    private final ServiceTypeResolver serviceTypeResolver;

    /**
     * 获取限流配置(canonical 格式)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getRateLimitConfig(@PathVariable final String serviceType) {
        return ResponseEntity.ok(storeConfigRepository.findRateLimitRaw(serviceType).orElse(Map.of()));
    }

    /**
     * 更新限流配置:持久化 + 热生效
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateRateLimitConfig(
            @PathVariable final String serviceType,
            @RequestBody final Map<String, Object> rateLimitConfig) {
        log.info("Updating rate limit config for service: {}", serviceType);

        ModelServiceRegistry.ServiceType type = serviceTypeResolver.parseServiceType(serviceType);
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown service type: " + serviceType);
        }
        if (Boolean.TRUE.equals(rateLimitConfig.get("enabled"))) {
            long capacity = numberValue(rateLimitConfig.get("capacity"));
            long rate = numberValue(rateLimitConfig.get("rate"));
            if (capacity <= 0 || rate <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "capacity and rate must be > 0 when enabled");
            }
        }

        storeConfigRepository.saveRateLimitRaw(serviceType, rateLimitConfig);

        RateLimitConfig cfg = RateLimitConfig.fromMap(rateLimitConfig);
        rateLimitManager.setRateLimiter(type, cfg);
        log.info("Rate limit config saved and applied for service: {}", serviceType);

        return ResponseEntity.ok(rateLimitConfig);
    }

    private long numberValue(final Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
