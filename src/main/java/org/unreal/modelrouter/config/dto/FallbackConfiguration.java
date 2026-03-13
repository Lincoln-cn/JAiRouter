package org.unreal.modelrouter.config.dto;

/**
 * 降级配置 DTO
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
}
