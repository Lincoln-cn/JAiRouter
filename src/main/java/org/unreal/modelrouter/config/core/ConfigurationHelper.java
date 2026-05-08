package org.unreal.modelrouter.config.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.config.core.helper.ConfigValidatorHelper;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.router.ratelimit.RateLimitConfig;

import java.util.List;
import java.util.Map;

/**
 * 配置转换和处理工具类
 * 统一处理配置转换逻辑，消除重复代码
 *
 * <p>v2.13.4重构：所有方法委托到helper类，此类仅作为门面类存在。
 * 所有公共方法已标记@Deprecated，建议直接使用helper类。</p>
 *
 * @deprecated since v2.13.4, 建议使用ServiceTypeResolver、ConfigValidatorHelper、ConfigConverterHelper
 * @see ServiceTypeResolver
 * @see ConfigValidatorHelper
 * @see ConfigConverterHelper
 */
@Component
@Deprecated(since = "v2.13.4", forRemoval = false)
public class ConfigurationHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationHelper.class);

    private ConfigurationValidator configurationValidator;

    // Helper类注入（v2.13.4委托调用）
    private ServiceTypeResolver serviceTypeResolver;
    private ConfigValidatorHelper configValidatorHelper;
    private ConfigConverterHelper configConverterHelper;

    /**
     * Sets the configuration validator for this helper.
     *
     * @param configurationValidator the configuration validator to set
     */
    @Autowired
    public void setConfigurationValidator(final ConfigurationValidator configurationValidator) {
        this.configurationValidator = configurationValidator;
    }

    /**
     * Sets the service type resolver for this helper.
     *
     * @param serviceTypeResolver the service type resolver to set
     */
    @Autowired
    public void setServiceTypeResolver(final ServiceTypeResolver serviceTypeResolver) {
        this.serviceTypeResolver = serviceTypeResolver;
    }

    /**
     * Sets the config validator helper for this helper.
     *
     * @param configValidatorHelper the config validator helper to set
     */
    @Autowired
    public void setConfigValidatorHelper(final ConfigValidatorHelper configValidatorHelper) {
        this.configValidatorHelper = configValidatorHelper;
    }

    /**
     * Sets the config converter helper for this helper.
     *
     * @param configConverterHelper the config converter helper to set
     */
    @Autowired
    public void setConfigConverterHelper(final ConfigConverterHelper configConverterHelper) {
        this.configConverterHelper = configConverterHelper;
    }

    // ===================== ServiceTypeResolver 委托方法 =====================

    /**
     * 服务键到服务类型映射
     * 支持多种格式：连字符、下划线、驼峰等
     *
     * @deprecated since v2.13.4, 请使用 {@link ServiceTypeResolver#parseServiceType(String)}
     * @param serviceKey 服务键
     * @return 服务类型枚举
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public ModelServiceRegistry.ServiceType parseServiceType(final String serviceKey) {
        return serviceTypeResolver.parseServiceType(serviceKey);
    }

    /**
     * 获取服务配置键
     *
     * @deprecated since v2.13.4, 请使用 {@link ServiceTypeResolver#getServiceConfigKey(ModelServiceRegistry.ServiceType)}
     * @param serviceType 服务类型
     * @return 服务配置键
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public String getServiceConfigKey(final ModelServiceRegistry.ServiceType serviceType) {
        return serviceTypeResolver.getServiceConfigKey(serviceType);
    }

    /**
     * 验证服务类型有效性
     *
     * @deprecated since v2.13.4, 请使用 {@link ServiceTypeResolver#isValidServiceType(String)}
     * @param serviceType 服务类型字符串
     * @return 是否有效
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public boolean isValidServiceType(final String serviceType) {
        return serviceTypeResolver.isValidServiceType(serviceType);
    }

    // ===================== ConfigValidatorHelper 委托方法 =====================

    /**
     * 验证限流配置的有效性
     *
     * @deprecated since v2.13.4, 请使用 {@link ConfigValidatorHelper#isValidRateLimitConfig(ModelRouterProperties.RateLimitConfig)}
     * @param config 限流配置
     * @return 配置是否有效
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public boolean isValidRateLimitConfig(final ModelRouterProperties.RateLimitConfig config) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return false;
        }
        RateLimitConfig convertedConfig = configConverterHelper.convertRateLimitConfig(config);
        return configurationValidator.validateRateLimitConfig(convertedConfig);
    }

    /**
     * 验证限流配置参数的合法性
     *
     * @deprecated since v2.13.4, 请使用 {@link ConfigValidatorHelper#validateRateLimitConfig(ModelRouterProperties.RateLimitConfig)}
     * @param config 限流配置
     * @return 配置是否合法
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public boolean validateRateLimitConfig(final ModelRouterProperties.RateLimitConfig config) {
        return configValidatorHelper.validateRateLimitConfig(config);
    }

    /**
     * 验证负载均衡配置参数的合法性
     *
     * @deprecated since v2.13.4, 请使用 {@link ConfigValidatorHelper#validateLoadBalanceConfig(ModelRouterProperties.LoadBalanceConfig)}
     * @param config 负载均衡配置
     * @return 配置是否合法
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public boolean validateLoadBalanceConfig(final ModelRouterProperties.LoadBalanceConfig config) {
        return configValidatorHelper.validateLoadBalanceConfig(config);
    }

    /**
     * 验证服务实例地址的合法性
     *
     * @deprecated since v2.13.4, 请使用 {@link ConfigValidatorHelper#validateServiceAddress(String)}
     * @param address 服务实例地址
     * @return 地址是否合法
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public boolean validateServiceAddress(final String address) {
        return configValidatorHelper.validateServiceAddress(address);
    }

    /**
     * 验证服务配置
     *
     * @deprecated since v2.13.4, 请使用 {@link ConfigValidatorHelper#validateServiceConfig(String, Map, List, List)}
     * @param serviceType 服务类型
     * @param serviceConfig 服务配置
     * @param errors 错误列表
     * @param warnings 警告列表
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public void validateServiceConfig(final String serviceType,
                                      final Map<String, Object> serviceConfig,
                                      final List<String> errors,
                                      final List<String> warnings) {
        configValidatorHelper.validateServiceConfig(serviceType, serviceConfig, errors, warnings);
    }

    // ===================== ConfigConverterHelper 委托方法 =====================

    /**
     * 将ModelRouterProperties.RateLimitConfig转换为RateLimitConfig
     *
     * @deprecated since v2.13.4, 请使用 {@link ConfigConverterHelper#convertRateLimitConfig(ModelRouterProperties.RateLimitConfig)}
     * @param config 限流配置
     * @return RateLimitConfig对象
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public RateLimitConfig convertRateLimitConfig(final ModelRouterProperties.RateLimitConfig config) {
        return configConverterHelper.convertRateLimitConfig(config);
    }

    /**
     * 将ModelRouterProperties转换为Map
     *
     * @deprecated since v2.13.4, 请使用 {@link ConfigConverterHelper#convertModelRouterPropertiesToMap(ModelRouterProperties)}
     * @param modelRouterProperties 配置对象
     * @return 配置Map
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public Map<String, Object> convertModelRouterPropertiesToMap(final ModelRouterProperties modelRouterProperties) {
        LOGGER.debug("开始转换ModelRouterProperties到Map");
        Map<String, Object> result = configConverterHelper.convertModelRouterPropertiesToMap(modelRouterProperties);
        LOGGER.debug("转换完成，结果Map大小: {}", result != null ? result.size() : 0);
        if (result != null && result.containsKey("services")) {
            LOGGER.debug("服务配置存在");
            Object services = result.get("services");
            if (services instanceof Map) {
                LOGGER.debug("服务配置是Map类型，大小: {}", ((Map<?, ?>) services).size());
            }
        }
        return result;
    }

    /**
     * 将ModelInstance对象转换为Map
     *
     * @deprecated since v2.13.4, 请使用 {@link ConfigConverterHelper#convertInstanceToMap(ModelRouterProperties.ModelInstance)}
     * @param instance ModelInstance对象
     * @return 转换后的Map
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public Map<String, Object> convertInstanceToMap(final ModelRouterProperties.ModelInstance instance) {
        return configConverterHelper.convertInstanceToMap(instance);
    }

    /**
     * 将Map转换为ServiceConfig对象
     *
     * @deprecated since v2.13.4, 请使用 {@link ConfigConverterHelper#convertMapToServiceConfig(Map)}
     * @param serviceConfigMap 服务配置Map
     * @return ServiceConfig对象
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public ModelRouterProperties.ServiceConfig convertMapToServiceConfig(final Map<String, Object> serviceConfigMap) {
        return configConverterHelper.convertMapToServiceConfig(serviceConfigMap);
    }

    /**
     * 将Map转换为ModelInstance对象
     *
     * @deprecated since v2.13.4, 请使用 {@link ConfigConverterHelper#convertMapToInstance(Map)}
     * @param instanceMap 实例配置Map
     * @return ModelInstance对象
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public ModelRouterProperties.ModelInstance convertMapToInstance(final Map<String, Object> instanceMap) {
        return configConverterHelper.convertMapToInstance(instanceMap);
    }

    /**
     * 更新限流配置
     *
     * @deprecated since v2.13.4, 请使用 {@link ConfigConverterHelper#updateRateLimitConfig(ModelRouterProperties.RateLimitConfig, Map)}
     * @param rateLimitConfig 限流配置对象
     * @param rateLimitMap 限流配置Map
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public void updateRateLimitConfig(final ModelRouterProperties.RateLimitConfig rateLimitConfig,
                                      final Map<String, Object> rateLimitMap) {
        configConverterHelper.updateRateLimitConfig(rateLimitConfig, rateLimitMap);
    }

    /**
     * 更新熔断器配置
     *
     * @deprecated since v2.13.4, 请使用 {@link ConfigConverterHelper#updateCircuitBreakerConfig(ModelRouterProperties.CircuitBreakerConfig, Map)}
     * @param circuitBreakerConfig 熔断器配置对象
     * @param circuitBreakerMap 熔断器配置Map
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public void updateCircuitBreakerConfig(final ModelRouterProperties.CircuitBreakerConfig circuitBreakerConfig,
                                           final Map<String, Object> circuitBreakerMap) {
        configConverterHelper.updateCircuitBreakerConfig(circuitBreakerConfig, circuitBreakerMap);
    }

    // ===================== 保留方法（未迁移到helper类） =====================

    /**
     * 创建默认负载均衡配置
     *
     * @return 默认负载均衡配置
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public ModelRouterProperties.LoadBalanceConfig createDefaultLoadBalanceConfig() {
        return new ModelRouterProperties.LoadBalanceConfig();
    }

    /**
     * 更新降级配置
     *
     * @param fallbackConfig 降级配置对象
     * @param fallbackMap 降级配置Map
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public void updateFallbackConfig(final ModelRouterProperties.FallbackConfig fallbackConfig,
                                     final Map<String, Object> fallbackMap) {
        if (fallbackConfig == null || fallbackMap == null) {
            return;
        }

        if (fallbackMap.containsKey("enabled")) {
            Object enabledObj = fallbackMap.get("enabled");
            if (enabledObj instanceof Boolean) {
                fallbackConfig.setEnabled((Boolean) enabledObj);
            }
        }
        if (fallbackMap.containsKey("strategy")) {
            fallbackConfig.setStrategy((String) fallbackMap.get("strategy"));
        }
        if (fallbackMap.containsKey("cacheSize")) {
            Object cacheSizeObj = fallbackMap.get("cacheSize");
            if (cacheSizeObj instanceof Number) {
                fallbackConfig.setCacheSize(((Number) cacheSizeObj).intValue());
            }
        }
        if (fallbackMap.containsKey("cacheTtl")) {
            Object cacheTtlObj = fallbackMap.get("cacheTtl");
            if (cacheTtlObj instanceof Number) {
                fallbackConfig.setCacheTtl(((Number) cacheTtlObj).longValue());
            }
        }
    }

    /**
     * 获取有效的负载均衡配置
     *
     * @param serviceConfig 服务配置
     * @param globalConfig 全局配置
     * @return 有效的负载均衡配置
     */
    @Deprecated(since = "v2.13.4", forRemoval = false)
    public ModelRouterProperties.LoadBalanceConfig getEffectiveLoadBalanceConfig(
            final ModelRouterProperties.LoadBalanceConfig serviceConfig,
            final ModelRouterProperties.LoadBalanceConfig globalConfig) {
        if (serviceConfig != null) {
            return serviceConfig;
        }
        if (globalConfig != null) {
            return globalConfig;
        }
        return createDefaultLoadBalanceConfig();
    }
}
