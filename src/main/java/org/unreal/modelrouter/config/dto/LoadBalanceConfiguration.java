package org.unreal.modelrouter.config.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * 负载均衡配置 DTO
 * 充血模型：包含与 Map 的互相转换能力
 */
public record LoadBalanceConfiguration(
        String type,
        String hashAlgorithm
) {
    /**
     * 创建默认负载均衡配置
     */
    public static LoadBalanceConfiguration defaultConfig() {
        return new LoadBalanceConfiguration("round_robin", "murmur3");
    }

    /**
     * 支持的负载均衡类型
     */
    public enum Type {
        ROUND_ROBIN("round_robin"),
        RANDOM("random"),
        WEIGHTED("weighted"),
        IP_HASH("ip_hash"),
        LEAST_CONNECTIONS("least_connections");

        private final String value;

        Type(final String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Type fromString(final String type) {
            for (Type t : values()) {
                if (t.value.equalsIgnoreCase(type)) {
                    return t;
                }
            }
            return ROUND_ROBIN;
        }
    }

    /**
     * 从 Map 转换为 DTO
     */
    public static LoadBalanceConfiguration fromMap(final Map<String, Object> map) {
        if (map == null) {
            return defaultConfig();
        }
        return new LoadBalanceConfiguration(
                getString(map, "type"),
                getString(map, "hashAlgorithm")
        );
    }

    /**
     * 转换为 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (type != null) map.put("type", type);
        if (hashAlgorithm != null) map.put("hashAlgorithm", hashAlgorithm);
        return map;
    }

    private static String getString(final Map<String, Object> map, final String key) {
        Object value = map.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }
}
