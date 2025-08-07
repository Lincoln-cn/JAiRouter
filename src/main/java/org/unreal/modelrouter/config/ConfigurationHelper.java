package org.unreal.modelrouter.config;

import org.springframework.stereotype.Component;
import org.unreal.modelrouter.ratelimit.RateLimitConfig;

/**
 * 配置转换和处理工具类
 * 统一处理配置转换逻辑，消除重复代码
 */
@Component
public class ConfigurationHelper {

    /**
     * 服务键到服务类型映射
     * 支持多种格式：连字符、下划线、驼峰等
     */
    public ModelServiceRegistry.ServiceType parseServiceType(String serviceKey) {
        if (serviceKey == null || serviceKey.trim().isEmpty()) {
            return null;
        }

        try {
            // 标准化处理：转小写，统一格式
            String normalizedKey = serviceKey.toLowerCase()
                    .replace("-", "_")
                    .trim();

            // 特殊映射处理
            return switch (normalizedKey) {
                case "imggen", "img_gen", "image_generation", "image_gen" ->
                        ModelServiceRegistry.ServiceType.imgGen;
                case "imgedit", "img_edit", "image_edit", "image_editing" ->
                        ModelServiceRegistry.ServiceType.imgEdit;
                case "tts", "text_to_speech", "text2speech" ->
                        ModelServiceRegistry.ServiceType.tts;
                case "stt", "speech_to_text", "speech2text" ->
                        ModelServiceRegistry.ServiceType.stt;
                default -> ModelServiceRegistry.ServiceType.valueOf(normalizedKey);
            };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 转换限流配置
     * 从Properties配置转换为内部配置对象
     */
    public RateLimitConfig convertRateLimitConfig(ModelRouterProperties.RateLimitConfig source) {
        if (source == null || !Boolean.TRUE.equals(source.getEnabled())) {
            return null;
        }

        return new RateLimitConfig(
                source.getAlgorithm() != null ? source.getAlgorithm() : "token-bucket",
                source.getCapacity() != null ? source.getCapacity() : 100L,
                source.getRate() != null ? source.getRate() : 10L,
                source.getScope() != null ? source.getScope() : "service"
        );
    }

    /**
     * 获取有效的负载均衡配置
     * 优先级：服务配置 > 全局配置 > 默认配置
     */
    public ModelRouterProperties.LoadBalanceConfig getEffectiveLoadBalanceConfig(
            ModelRouterProperties.LoadBalanceConfig serviceConfig,
            ModelRouterProperties.LoadBalanceConfig globalConfig) {

        if (serviceConfig != null) {
            return serviceConfig;
        }

        if (globalConfig != null) {
            return globalConfig;
        }

        // 返回默认配置
        return createDefaultLoadBalanceConfig();
    }

    /**
     * 创建默认负载均衡配置
     */
    public ModelRouterProperties.LoadBalanceConfig createDefaultLoadBalanceConfig() {
        ModelRouterProperties.LoadBalanceConfig defaultConfig =
                new ModelRouterProperties.LoadBalanceConfig();
        defaultConfig.setType("random");
        defaultConfig.setHashAlgorithm("md5");
        return defaultConfig;
    }

    /**
     * 获取服务配置键
     * 将ServiceType转换为配置文件中使用的键名
     */
    public String getServiceConfigKey(ModelServiceRegistry.ServiceType serviceType) {
        return switch (serviceType) {
            case imgGen -> "img-gen";
            case imgEdit -> "img-edit";
            default -> serviceType.name().toLowerCase().replace("_", "-");
        };
    }

    /**
     * 验证负载均衡配置的有效性
     */
    public boolean isValidLoadBalanceConfig(ModelRouterProperties.LoadBalanceConfig config) {
        if (config == null || config.getType() == null) {
            return false;
        }

        String type = config.getType().toLowerCase();
        return type.equals("random") ||
                type.equals("round-robin") ||
                type.equals("least-connections") ||
                type.equals("ip-hash");
    }

    /**
     * 验证限流配置的有效性
     */
    public boolean isValidRateLimitConfig(ModelRouterProperties.RateLimitConfig config) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return false;
        }

        return config.getAlgorithm() != null &&
                config.getCapacity() != null && config.getCapacity() > 0 &&
                config.getRate() != null && config.getRate() > 0;
    }
}