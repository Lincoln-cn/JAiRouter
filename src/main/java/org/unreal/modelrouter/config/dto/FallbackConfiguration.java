package org.unreal.modelrouter.config.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * 降级配置 DTO
 * 充血模型：包含与 Map 的互相转换能力
 */
public record FallbackConfiguration(
        Boolean enabled,
        String fallbackUrl,
        Integer maxRetries,
        Long retryInterval,
        Boolean returnDefaultResponse
) {
    /**
     * 创建默认降级配置
     */
    public static FallbackConfiguration defaultConfig() {
        return new FallbackConfiguration(
                false,
                null,
                3,
                1000L,
                true
        );
    }

    /**
     * 检查是否启用降级
     */
    public boolean isEnabled() {
        return enabled != null && enabled;
    }

    /**
     * 从 Map 转换为 DTO
     */
    public static FallbackConfiguration fromMap(final Map<String, Object> map) {
        if (map == null) {
            return defaultConfig();
        }
        return new FallbackConfiguration(
                getBoolean(map, "enabled"),
                getString(map, "fallbackUrl"),
                getInteger(map, "maxRetries"),
                getLong(map, "retryInterval"),
                getBoolean(map, "returnDefaultResponse")
        );
    }

    /**
     * 转换为 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (enabled != null) map.put("enabled", enabled);
        if (fallbackUrl != null) map.put("fallbackUrl", fallbackUrl);
        if (maxRetries != null) map.put("maxRetries", maxRetries);
        if (retryInterval != null) map.put("retryInterval", retryInterval);
        if (returnDefaultResponse != null) map.put("returnDefaultResponse", returnDefaultResponse);
        return map;
    }

    private static Integer getInteger(final Map<String, Object> map,final String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private static Long getLong(final Map<String, Object> map,final String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
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

    private static String getString(final Map<String, Object> map,final String key) {
        Object value = map.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }
}
