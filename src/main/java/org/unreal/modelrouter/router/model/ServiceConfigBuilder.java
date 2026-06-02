package org.unreal.modelrouter.router.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.config.core.ConfigurationHelper;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.fallback.FallbackManager;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 服务配置构建器
 * 负责从配置Map构建运行时配置对象
 */
public class ServiceConfigBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceConfigBuilder.class);

    private final ConfigurationHelper configurationHelper;
    private final RateLimitManager rateLimitManager;
    private final CircuitBreakerManager circuitBreakerManager;
    private final FallbackManager fallbackManager;

    public ServiceConfigBuilder(final ConfigurationHelper configurationHelper,
                                final RateLimitManager rateLimitManager,
                                final CircuitBreakerManager circuitBreakerManager,
                                final FallbackManager fallbackManager) {
        this.configurationHelper = configurationHelper;
        this.rateLimitManager = rateLimitManager;
        this.circuitBreakerManager = circuitBreakerManager;
        this.fallbackManager = fallbackManager;
    }

    /**
     * 重建服务配置缓存
     *
     * @param mergedConfig 合并后的配置
     * @return 服务运行时配置缓存
     */
    @SuppressWarnings("unchecked")
    public Map<String, ServiceRuntimeConfig> rebuildServiceConfigCache(final Map<String, Object> mergedConfig) {
        Map<String, ServiceRuntimeConfig> newCache = new ConcurrentHashMap<>();

        if (mergedConfig.containsKey("services")) {
            Map<String, Object> servicesMap = (Map<String, Object>) mergedConfig.get("services");

            if (servicesMap != null) {
                for (Map.Entry<String, Object> entry : servicesMap.entrySet()) {
                    String serviceKey = entry.getKey();
                    Map<String, Object> serviceConfigMap = (Map<String, Object>) entry.getValue();

                    try {
                        ServiceRuntimeConfig runtimeConfig = buildServiceRuntimeConfig(serviceConfigMap);
                        newCache.put(serviceKey, runtimeConfig);
                    } catch (Exception e) {
                        LOGGER.warn("构建服务 {} 的运行时配置失败: {}", serviceKey, e.getMessage());
                    }
                }
            }
        }

        LOGGER.debug("服务配置缓存重建完成，包含 {} 个服务", newCache.size());
        return newCache;
    }

    /**
     * 构建服务运行时配置
     *
     * @param serviceConfigMap 服务配置Map
     * @return 运行时配置对象
     */
    @SuppressWarnings("unchecked")
    public ServiceRuntimeConfig buildServiceRuntimeConfig(final Map<String, Object> serviceConfigMap) {
        ServiceRuntimeConfig runtimeConfig = new ServiceRuntimeConfig();

        // 解析实例列表
        if (serviceConfigMap.containsKey("instances")) {
            List<Map<String, Object>> instanceList = (List<Map<String, Object>>) serviceConfigMap.get("instances");
            List<ModelRouterProperties.ModelInstance> instances = instanceList.stream()
                    .map(configurationHelper::convertMapToInstance)
                    .collect(Collectors.toList());
            runtimeConfig.setInstances(instances);
        } else {
            runtimeConfig.setInstances(new ArrayList<>());
        }

        // 解析适配器
        runtimeConfig.setAdapter((String) serviceConfigMap.get("adapter"));

        // 解析负载均衡配置
        if (serviceConfigMap.containsKey("loadBalance")) {
            Map<String, Object> loadBalanceMap = (Map<String, Object>) serviceConfigMap.get("loadBalance");
            ModelRouterProperties.LoadBalanceConfig loadBalanceConfig = new ModelRouterProperties.LoadBalanceConfig();
            if (loadBalanceMap.containsKey("type")) {
                loadBalanceConfig.setType((String) loadBalanceMap.get("type"));
            }
            if (loadBalanceMap.containsKey("hashAlgorithm")) {
                loadBalanceConfig.setHashAlgorithm((String) loadBalanceMap.get("hashAlgorithm"));
            }
            runtimeConfig.setLoadBalanceConfig(loadBalanceConfig);
        } else {
            runtimeConfig.setLoadBalanceConfig(configurationHelper.createDefaultLoadBalanceConfig());
        }

        // 解析其他配置（限流、熔断器、降级）
        parseAdditionalConfigs(serviceConfigMap, runtimeConfig);

        return runtimeConfig;
    }

    /**
     * 解析额外配置（限流、熔断器、降级）
     */
    @SuppressWarnings("unchecked")
    private void parseAdditionalConfigs(final Map<String, Object> serviceConfigMap,
                                        final ServiceRuntimeConfig runtimeConfig) {
        // 限流配置
        if (serviceConfigMap.containsKey("rateLimit")) {
            Map<String, Object> rateLimitMap = (Map<String, Object>) serviceConfigMap.get("rateLimit");
            if (rateLimitMap != null) {
                ModelRouterProperties.RateLimitConfig rateLimitConfig =
                        new ModelRouterProperties.RateLimitConfig();
                configurationHelper.updateRateLimitConfig(rateLimitConfig, rateLimitMap);
                runtimeConfig.setRateLimitConfig(rateLimitConfig);
            } else {
                runtimeConfig.setRateLimitConfig(rateLimitManager.getDefaultRateLimitConfig());
            }
        }

        // 熔断器配置
        if (serviceConfigMap.containsKey("circuitBreaker")) {
            Map<String, Object> circuitBreakerMap =
                    (Map<String, Object>) serviceConfigMap.get("circuitBreaker");
            if (circuitBreakerMap != null) {
                ModelRouterProperties.CircuitBreakerConfig circuitBreakerConfig =
                        new ModelRouterProperties.CircuitBreakerConfig();
                configurationHelper.updateCircuitBreakerConfig(circuitBreakerConfig, circuitBreakerMap);
                runtimeConfig.setCircuitBreakerConfig(circuitBreakerConfig);
            } else {
                runtimeConfig.setCircuitBreakerConfig(circuitBreakerManager.getDefaultCircuitBreakerConfig());
            }
        }

        // 降级配置
        if (serviceConfigMap.containsKey("fallback")) {
            Map<String, Object> fallbackMap = (Map<String, Object>) serviceConfigMap.get("fallback");
            if (fallbackMap != null) {
                ModelRouterProperties.FallbackConfig fallbackConfig =
                        new ModelRouterProperties.FallbackConfig();
                configurationHelper.updateFallbackConfig(fallbackConfig, fallbackMap);
                runtimeConfig.setFallbackConfig(fallbackConfig);
            } else {
                runtimeConfig.setFallbackConfig(fallbackManager.getDefaultFallbackConfig());
            }
        }
    }
}
