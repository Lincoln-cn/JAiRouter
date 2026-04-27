package org.unreal.modelrouter.config.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * 路由器全局配置 DTO
 * 充血模型：包含与 Map 的互相转换能力
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
    public ServiceConfiguration getServiceConfig(final String serviceType) {
        return services != null ? services.get(serviceType) : null;
    }

    /**
     * 从 Map 转换为 DTO
     */
    @SuppressWarnings("unchecked")
    public static RouterConfiguration fromMap(final Map<String, Object> map) {
        if (map == null) {
            return defaultConfig();
        }
        return new RouterConfiguration(
                getString(map, "adapter"),
                ServiceConfiguration.fromServicesMap(getMap(map, "services")),
                LoadBalanceConfiguration.fromMap(getMap(map, "loadBalance")),
                RateLimitConfiguration.fromMap(getMap(map, "rateLimit")),
                CircuitBreakerConfiguration.fromMap(getMap(map, "circuitBreaker")),
                FallbackConfiguration.fromMap(getMap(map, "fallback"))
        );
    }

    /**
     * 转换为 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (adapter != null) map.put("adapter", adapter);
        if (services != null) map.put("services", ServiceConfiguration.toServicesMap(services));
        if (loadBalance != null) map.put("loadBalance", loadBalance.toMap());
        if (rateLimit != null) map.put("rateLimit", rateLimit.toMap());
        if (circuitBreaker != null) map.put("circuitBreaker", circuitBreaker.toMap());
        if (fallback != null) map.put("fallback", fallback.toMap());
        return map;
    }

    private static String getString(final Map<String, Object> map, final String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(final Map<String, Object> map, final String key) {
        Object value = map.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }
}
