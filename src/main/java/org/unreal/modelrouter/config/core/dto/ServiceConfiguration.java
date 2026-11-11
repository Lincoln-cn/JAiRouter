package org.unreal.modelrouter.config.core.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务配置 DTO
 * 强类型表示服务配置，替代 Map<String, Object>
 * 充血模型：包含与 Map 的互相转换能力
 */
public record ServiceConfiguration(
        String adapter,
        List<ModelInstanceConfiguration> instances,
        LoadBalanceConfiguration loadBalance,
        RateLimitConfiguration rateLimit,
        CircuitBreakerConfiguration circuitBreaker,
        FallbackConfiguration fallback,
        StickyConfiguration sticky
) {
    /**
     * 向后兼容的构造器：不传 sticky 时视为「未配置」，与加这个字段之前的行为一致。
     */
    public ServiceConfiguration(
            final String adapter,
            final List<ModelInstanceConfiguration> instances,
            final LoadBalanceConfiguration loadBalance,
            final RateLimitConfiguration rateLimit,
            final CircuitBreakerConfiguration circuitBreaker,
            final FallbackConfiguration fallback) {
        this(adapter, instances, loadBalance, rateLimit, circuitBreaker, fallback, null);
    }

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
                FallbackConfiguration.defaultConfig(),
                StickyConfiguration.defaultConfig()
        );
    }

    /**
     * 从 Map 转换为 DTO
     */
    @SuppressWarnings("unchecked")
    public static ServiceConfiguration fromMap(final Map<String, Object> map) {
        if (map == null) {
            return defaultConfig();
        }
        return new ServiceConfiguration(
                getString(map, "adapter"),
                ModelInstanceConfiguration.fromMapList(getList(map, "instances")),
                LoadBalanceConfiguration.fromMap(getMap(map, "loadBalance")),
                RateLimitConfiguration.fromMap(getMap(map, "rateLimit")),
                CircuitBreakerConfiguration.fromMap(getMap(map, "circuitBreaker")),
                FallbackConfiguration.fromMap(getMap(map, "fallback")),
                StickyConfiguration.fromMap(getMap(map, "sticky"))
        );
    }

    /**
     * 转换为 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (adapter != null) {
            map.put("adapter", adapter);
        }
        if (instances != null) {
            map.put("instances", ModelInstanceConfiguration.toMapList(instances));
        }
        if (loadBalance != null) {
            map.put("loadBalance", loadBalance.toMap());
        }
        if (rateLimit != null) {
            map.put("rateLimit", rateLimit.toMap());
        }
        if (circuitBreaker != null) {
            map.put("circuitBreaker", circuitBreaker.toMap());
        }
        if (fallback != null) {
            map.put("fallback", fallback.toMap());
        }
        // issue #1095：此前这里不输出 sticky，导致「写进去的粘性开关」在读面消失，
        // 运维看不到当前状态、工具链也无法回读确认关闭是否生效。
        if (sticky != null) {
            map.put("sticky", sticky.toMap());
        }
        return map;
    }

    /**
     * 从 services Map 转换为 ServiceConfiguration Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, ServiceConfiguration> fromServicesMap(final Map<String, Object> servicesMap) {
        if (servicesMap == null) {
            return Map.of();
        }
        Map<String, ServiceConfiguration> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : servicesMap.entrySet()) {
            if (entry.getValue() instanceof Map) {
                result.put(entry.getKey(), fromMap((Map<String, Object>) entry.getValue()));
            }
        }
        return result;
    }

    /**
     * 转换为 services Map
     */
    public static Map<String, Object> toServicesMap(final Map<String, ServiceConfiguration> services) {
        if (services == null) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, ServiceConfiguration> entry : services.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toMap());
        }
        return result;
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getList(final Map<String, Object> map, final String key) {
        Object value = map.get(key);
        return value instanceof List ? (List<Map<String, Object>>) value : List.of();
    }
}
