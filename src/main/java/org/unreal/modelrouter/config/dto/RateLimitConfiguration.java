package org.unreal.modelrouter.config.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * 限流配置 DTO
 * 充血模型：包含与 Map 的互相转换能力
 */
public record RateLimitConfiguration(
        Integer requestsPerSecond,
        Integer requestsPerMinute,
        Integer requestsPerHour,
        Integer requestsPerDay,
        Integer burstSize,
        Boolean enabled
) {
    /**
     * 创建默认限流配置
     */
    public static RateLimitConfiguration defaultConfig() {
        return new RateLimitConfiguration(
                100,
                1000,
                10000,
                100000,
                10,
                true
        );
    }

    /**
     * 检查是否启用限流
     */
    public boolean isEnabled() {
        return enabled != null && enabled;
    }

    /**
     * 从 Map 转换为 DTO
     */
    @SuppressWarnings("unchecked")
    public static RateLimitConfiguration fromMap(final Map<String, Object> map) {
        if (map == null) {
            return defaultConfig();
        }
        return new RateLimitConfiguration(
                getInteger(map, "requestsPerSecond"),
                getInteger(map, "requestsPerMinute"),
                getInteger(map, "requestsPerHour"),
                getInteger(map, "requestsPerDay"),
                getInteger(map, "burstSize"),
                getBoolean(map, "enabled")
        );
    }

    /**
     * 转换为 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (requestsPerSecond != null) map.put("requestsPerSecond", requestsPerSecond);
        if (requestsPerMinute != null) map.put("requestsPerMinute", requestsPerMinute);
        if (requestsPerHour != null) map.put("requestsPerHour", requestsPerHour);
        if (requestsPerDay != null) map.put("requestsPerDay", requestsPerDay);
        if (burstSize != null) map.put("burstSize", burstSize);
        if (enabled != null) map.put("enabled", enabled);
        return map;
    }

    private static Integer getInteger(final Map<String, Object> map,final String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private static Boolean getBoolean(final Map<String, Object> map,final String key) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }
}
