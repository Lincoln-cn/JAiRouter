package org.unreal.modelrouter.config.dto;

import java.util.List;
import java.util.Map;

/**
 * 服务配置 DTO
 * 强类型表示服务配置，替代 Map<String, Object>
 */
public record ServiceConfiguration(
        String adapter,
        List<ModelInstanceConfiguration> instances,
        LoadBalanceConfiguration loadBalance,
        RateLimitConfiguration rateLimit,
        CircuitBreakerConfiguration circuitBreaker,
        FallbackConfiguration fallback
) {
    /**
     * 创建默认配置
     */
    public static ServiceConfiguration defaultConfig() {
        return new ServiceConfiguration(
                "normal",
                List.of(),
                LoadBalanceConfiguration.defaultConfig(),
                RateLimitConfiguration.defaultConfig(),
                CircuitBreakerConfiguration.defaultConfig(),
                FallbackConfiguration.defaultConfig()
        );
    }
}
