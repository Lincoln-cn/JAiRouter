package org.unreal.modelrouter.config.dto;

/**
 * 负载均衡配置 DTO
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

        Type(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Type fromString(String type) {
            for (Type t : values()) {
                if (t.value.equalsIgnoreCase(type)) {
                    return t;
                }
            }
            return ROUND_ROBIN;
        }
    }
}
