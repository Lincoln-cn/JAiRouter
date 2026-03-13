package org.unreal.modelrouter.config.dto;

import java.util.Map;

/**
 * 模型实例配置 DTO
 */
public record ModelInstanceConfiguration(
        String name,
        String baseUrl,
        String path,
        String adapter,
        Integer weight,
        String status,
        Map<String, Object> rateLimit,
        Map<String, Object> circuitBreaker,
        Map<String, Object> fallback,
        Map<String, String> headers,
        String instanceId
) {
    /**
     * 创建默认实例配置
     */
    public static ModelInstanceConfiguration defaultConfig(String name, String baseUrl) {
        return new ModelInstanceConfiguration(
                name,
                baseUrl,
                "/v1/chat/completions",
                null,
                1,
                "active",
                Map.of(),
                Map.of(),
                Map.of(),
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
}
