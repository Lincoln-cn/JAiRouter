package org.unreal.modelrouter.config.dto;

/**
 * 熔断器配置 DTO
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
}
