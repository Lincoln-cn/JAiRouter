package org.unreal.modelrouter.fallback.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.fallback.FallbackStrategy;

/**
 * 默认降级策略实现
 */
public class DefaultFallbackStrategy implements FallbackStrategy<ResponseEntity<?>> {
    private static final Logger logger = LoggerFactory.getLogger(DefaultFallbackStrategy.class);

    private final String serviceType;

    public DefaultFallbackStrategy(String serviceType) {
        this.serviceType = serviceType;
    }

    @Override
    public ResponseEntity<?> fallback(Exception cause) {
        logger.warn("Service {} fallback triggered due to: {}", serviceType, cause.getMessage());

        // 返回降级响应
        return ResponseEntity.status(503)
                .header("Content-Type", "application/json")
                .body("Service is currently degraded, please try again later");
    }
}
