package org.unreal.modelrouter.config.dto;

/**
 * 限流配置 DTO
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
}
