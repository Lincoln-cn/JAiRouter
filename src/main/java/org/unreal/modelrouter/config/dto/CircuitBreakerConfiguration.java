package org.unreal.modelrouter.config.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * 熔断器配置 DTO
 * 充血模型：包含与 Map 的互相转换能力
 */
public record CircuitBreakerConfiguration(
        Integer failureThreshold,
        Long timeout,
        Integer successThreshold,
        Boolean enabled
) {
    /**
     * 创建默认熔断器配置
     */
    public static CircuitBreakerConfiguration defaultConfig() {
        return new CircuitBreakerConfiguration(
                5,
                60000L,
                2,
                true
        );
    }

    /**
     * 检查是否启用熔断器
     */
    public boolean isEnabled() {
        return enabled != null && enabled;
    }

    /**
     * 从 Map 转换为 DTO
     */
    public static CircuitBreakerConfiguration fromMap(Map<String, Object> map) {
        if (map == null) {
            return defaultConfig();
        }
        return new CircuitBreakerConfiguration(
                getInteger(map, "failureThreshold"),
                getLong(map, "timeout"),
                getInteger(map, "successThreshold"),
                getBoolean(map, "enabled")
        );
    }

    /**
     * 转换为 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (failureThreshold != null) map.put("failureThreshold", failureThreshold);
        if (timeout != null) map.put("timeout", timeout);
        if (successThreshold != null) map.put("successThreshold", successThreshold);
        if (enabled != null) map.put("enabled", enabled);
        return map;
    }

    private static Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private static Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private static Boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }
}
