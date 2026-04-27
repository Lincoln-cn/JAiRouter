package org.unreal.modelrouter.config.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模型实例配置 DTO
 * 充血模型：包含与 Map 的互相转换能力
 */
public record ModelInstanceConfiguration(
        String name,
        String baseUrl,
        String path,
        String adapter,
        Integer weight,
        String status,
        RateLimitConfiguration rateLimit,
        CircuitBreakerConfiguration circuitBreaker,
        FallbackConfiguration fallback,
        Map<String, String> headers,
        String instanceId
) {
    /**
     * 创建默认实例配置
     */
    public static ModelInstanceConfiguration defaultConfig(final String name,final String baseUrl) {
        return new ModelInstanceConfiguration(
                name,
                baseUrl,
                "/v1/chat/completions",
                null,
                1,
                "active",
                RateLimitConfiguration.defaultConfig(),
                CircuitBreakerConfiguration.defaultConfig(),
                FallbackConfiguration.defaultConfig(),
                Map.of(),
                null
        );
    }

    /**
     * 检查实例是否活跃
     */
    public boolean isActive() {
        return "active".equals(status) || status == null;
    }

    /**
     * 从 Map 转换为 DTO
     */
    @SuppressWarnings("unchecked")
    public static ModelInstanceConfiguration fromMap(final Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        return new ModelInstanceConfiguration(
                getString(map, "name"),
                getString(map, "baseUrl"),
                getString(map, "path"),
                getString(map, "adapter"),
                getInteger(map, "weight"),
                getString(map, "status"),
                RateLimitConfiguration.fromMap(getMap(map, "rateLimit")),
                CircuitBreakerConfiguration.fromMap(getMap(map, "circuitBreaker")),
                FallbackConfiguration.fromMap(getMap(map, "fallback")),
                getStringMap(map, "headers"),
                getString(map, "instanceId")
        );
    }

    /**
     * 转换为 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (name != null) map.put("name", name);
        if (baseUrl != null) map.put("baseUrl", baseUrl);
        if (path != null) map.put("path", path);
        if (adapter != null) map.put("adapter", adapter);
        if (weight != null) map.put("weight", weight);
        if (status != null) map.put("status", status);
        if (rateLimit != null) map.put("rateLimit", rateLimit.toMap());
        if (circuitBreaker != null) map.put("circuitBreaker", circuitBreaker.toMap());
        if (fallback != null) map.put("fallback", fallback.toMap());
        if (headers != null) map.put("headers", headers);
        if (instanceId != null) map.put("instanceId", instanceId);
        return map;
    }

    /**
     * 从 Map 列表转换为 DTO 列表
     */
    @SuppressWarnings("unchecked")
    public static List<ModelInstanceConfiguration> fromMapList(final List<Map<String, Object>> maps) {
        if (maps == null) {
            return new ArrayList<>();
        }
        return maps.stream()
                .map(ModelInstanceConfiguration::fromMap)
                .collect(Collectors.toList());
    }

    /**
     * 转换为 Map 列表
     */
    public static List<Map<String, Object>> toMapList(final List<ModelInstanceConfiguration> instances) {
        if (instances == null) {
            return new ArrayList<>();
        }
        return instances.stream()
                .map(ModelInstanceConfiguration::toMap)
                .collect(Collectors.toList());
    }

    private static String getString(final Map<String, Object> map,final String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : null;
    }

    private static Integer getInteger(final Map<String, Object> map,final String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(final Map<String, Object> map,final String key) {
        Object value = map.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> getStringMap(final Map<String, Object> map,final String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            Map<String, Object> rawMap = (Map<String, Object>) value;
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                if (entry.getValue() instanceof String) {
                    result.put(entry.getKey(), (String) entry.getValue());
                }
            }
            return result;
        }
        return Map.of();
    }
}
