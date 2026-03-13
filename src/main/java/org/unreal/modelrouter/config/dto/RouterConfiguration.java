package org.unreal.modelrouter.config.dto;

import java.util.Map;

/**
 * 路由器全局配置 DTO
 */
public record RouterConfiguration(
        String adapter,
        Map<String, ServiceConfiguration> services,
        LoadBalanceConfiguration loadBalance,
        RateLimitConfiguration rateLimit,
        CircuitBreakerConfiguration circuitBreaker,
        FallbackConfiguration fallback
) {
    /**
     * 创建默认全局配置
     */
    public static RouterConfiguration defaultConfig() {
        return new RouterConfiguration(
                "normal",
                Map.of(),
                LoadBalanceConfiguration.defaultConfig(),
                RateLimitConfiguration.defaultConfig(),
                CircuitBreakerConfiguration.defaultConfig(),
                FallbackConfiguration.defaultConfig()
        );
    }

    /**
     * 获取指定服务类型的配置
     */
    public ServiceConfiguration getServiceConfig(String serviceType) {
        return services != null ? services.get(serviceType) : null;
    }
}
