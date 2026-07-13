package org.unreal.modelrouter.config.core.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.ratelimit.RateLimitConfig;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置转换辅助类
 * 从 ConfigurationHelper 提取的转换逻辑，负责配置对象的转换操作
 */
@Component
public class ConfigConverterHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigConverterHelper.class);

    /**
     * 将ModelRouterProperties.RateLimitConfig转换为RateLimitConfig
     *
     * @param config 配置对象
     * @return 转换后的限流配置
     */
    public RateLimitConfig convertRateLimitConfig(final ModelRouterProperties.RateLimitConfig config) {
        if (config == null) {
            return null;
        }

        return RateLimitConfig.builder()
                .algorithm(config.getAlgorithm())
                .capacity(config.getCapacity())
                .rate(config.getRate())
                .scope(config.getScope())
                .key(config.getKey())
                .build();
    }

    /**
     * 将ModelRouterProperties转换为Map
     *
     * @param modelRouterProperties 配置对象
     * @return 配置Map
     */
    public Map<String, Object> convertModelRouterPropertiesToMap(final ModelRouterProperties modelRouterProperties) {
        LOGGER.debug("开始转换ModelRouterProperties到Map");
        // 使用反射机制将ModelRouterProperties对象转换为Map，避免硬编码
        Map<String, Object> result = convertObjectToMap(modelRouterProperties);
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
     * 将Map转换为ServiceConfig对象
     *
     * @param serviceConfigMap 服务配置Map
     * @return ServiceConfig对象
     */
    @SuppressWarnings("unchecked")
    public ModelRouterProperties.ServiceConfig convertMapToServiceConfig(final Map<String, Object> serviceConfigMap) {
        if (serviceConfigMap == null) {
            return null;
        }

        ModelRouterProperties.ServiceConfig serviceConfig = new ModelRouterProperties.ServiceConfig();

        // 设置负载均衡配置
        if (serviceConfigMap.containsKey("loadBalance") && serviceConfigMap.get("loadBalance") instanceof Map) {
            Map<String, Object> loadBalanceMap = (Map<String, Object>) serviceConfigMap.get("loadBalance");
            ModelRouterProperties.LoadBalanceConfig loadBalanceConfig = new ModelRouterProperties.LoadBalanceConfig();
            if (loadBalanceMap.containsKey("type")) {
                loadBalanceConfig.setType((String) loadBalanceMap.get("type"));
            }
            if (loadBalanceMap.containsKey("hashAlgorithm")) {
                loadBalanceConfig.setHashAlgorithm((String) loadBalanceMap.get("hashAlgorithm"));
            }
            serviceConfig.setLoadBalance(loadBalanceConfig);
        }

        // 设置适配器
        if (serviceConfigMap.containsKey("adapter")) {
            serviceConfig.setAdapter((String) serviceConfigMap.get("adapter"));
        }

        // 设置实例列表
        if (serviceConfigMap.containsKey("instances") && serviceConfigMap.get("instances") instanceof List) {
            List<Map<String, Object>> instanceList = (List<Map<String, Object>>) serviceConfigMap.get("instances");
            List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
            for (Map<String, Object> instanceMap : instanceList) {
                ModelRouterProperties.ModelInstance instance = convertMapToInstance(instanceMap);
                if (instance != null) {
                    instances.add(instance);
                }
            }
            serviceConfig.setInstances(instances);
        }

        return serviceConfig;
    }

    /**
     * 将Map转换为ModelInstance对象
     *
     * @param instanceMap 实例配置Map
     * @return ModelInstance对象
     */
    @SuppressWarnings("unchecked")
    public ModelRouterProperties.ModelInstance convertMapToInstance(final Map<String, Object> instanceMap) {
        if (instanceMap == null) {
            return null;
        }

        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();

        // 设置基本属性
        if (instanceMap.containsKey("name")) {
            instance.setName((String) instanceMap.get("name"));
        }
        if (instanceMap.containsKey("baseUrl")) {
            instance.setBaseUrl((String) instanceMap.get("baseUrl"));
        }
        if (instanceMap.containsKey("path")) {
            instance.setPath((String) instanceMap.get("path"));
        }
        if (instanceMap.containsKey("weight")) {
            Object weightObj = instanceMap.get("weight");
            if (weightObj instanceof Number) {
                instance.setWeight(((Number) weightObj).intValue());
            }
        }
        if (instanceMap.containsKey("status")) {
            instance.setStatus((String) instanceMap.get("status"));
        }
        if (instanceMap.containsKey("instanceId")) {
            instance.setInstanceId((String) instanceMap.get("instanceId"));
        }
        if (instanceMap.containsKey("adapter")) {
            instance.setAdapter((String) instanceMap.get("adapter"));
        }

        // 设置请求头配置
        if (instanceMap.containsKey("headers") && instanceMap.get("headers") instanceof Map) {
            Map<String, String> headers = (Map<String, String>) instanceMap.get("headers");
            instance.setHeaders(headers);
        }

        // 设置限流配置
        if (instanceMap.containsKey("rateLimit") && instanceMap.get("rateLimit") instanceof Map) {
            Map<String, Object> rateLimitMap = (Map<String, Object>) instanceMap.get("rateLimit");
            ModelRouterProperties.RateLimitConfig rateLimitConfig = new ModelRouterProperties.RateLimitConfig();
            updateRateLimitConfig(rateLimitConfig, rateLimitMap);
            instance.setRateLimit(rateLimitConfig);
        }

        // 设置熔断器配置
        if (instanceMap.containsKey("circuitBreaker") && instanceMap.get("circuitBreaker") instanceof Map) {
            Map<String, Object> circuitBreakerMap = (Map<String, Object>) instanceMap.get("circuitBreaker");
            ModelRouterProperties.CircuitBreakerConfig circuitBreakerConfig = new ModelRouterProperties.CircuitBreakerConfig();
            updateCircuitBreakerConfig(circuitBreakerConfig, circuitBreakerMap);
            instance.setCircuitBreaker(circuitBreakerConfig);
        }

        return instance;
    }

    /**
     * 将ModelInstance对象转换为Map
     *
     * @param instance ModelInstance对象
     * @return 转换后的Map
     */
    public Map<String, Object> convertInstanceToMap(final ModelRouterProperties.ModelInstance instance) {
        if (instance == null) {
            return null;
        }
        return convertObjectToMap(instance);
    }

    /**
     * 递归地将对象转换为Map
     *
     * @param obj 待转换的对象
     * @return 转换后的Map
     */
    public Map<String, Object> convertObjectToMap(final Object obj) {
        if (obj == null) {
            return null;
        }

        // 对于基本类型和字符串，直接返回
        if (isPrimitiveOrWrapper(obj.getClass()) || obj instanceof String) {
            return createSingleValueMap("value", obj);
        }

        // 对于Map类型，特殊处理
        if (obj instanceof Map) {
            return convertMapToResultMap((Map<?, ?>) obj);
        }

        // 对于List类型，特殊处理
        if (obj instanceof List) {
            return createSingleValueMap("value", convertListToResultList((List<?>) obj));
        }

        Map<String, Object> resultMap = new HashMap<>();
        Class<?> clazz = obj.getClass();

        // 检查是否是JDK内部类，如果是则直接返回其字符串表示
        if (isJdkInternalClass(clazz)) {
            resultMap.put("value", obj.toString());
            return resultMap;
        }

        // 获取所有字段并处理
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            try {
                // 检查是否可以访问该字段
                if (!isAccessibleField(field)) {
                    LOGGER.debug("跳过无法访问的字段: {}", field.getName());
                    continue;
                }

                field.setAccessible(true);
                Object value = field.get(obj);

                if (value == null) {
                    resultMap.put(field.getName(), null);
                } else if (isPrimitiveOrWrapper(value.getClass()) || value instanceof String) {
                    resultMap.put(field.getName(), value);
                } else if (value instanceof Map) {
                    Map<?, ?> originalMap = (Map<?, ?>) value;
                    resultMap.put(field.getName(), convertMapToResultMap(originalMap));
                } else if (value instanceof List) {
                    resultMap.put(field.getName(), convertListToResultList((List<?>) value));
                } else {
                    // 递归处理嵌套对象
                    resultMap.put(field.getName(), convertObjectToMap(value));
                }
            } catch (IllegalAccessException e) {
                LOGGER.warn("无法访问字段: {}", field.getName(), e);
            } catch (Exception e) {
                LOGGER.warn("处理字段时发生异常: {}", field.getName(), e);
            }
        }

        return resultMap;
    }

    /**
     * 转换Map为结果Map
     *
     * @param originalMap 原始Map
     * @return 转换后的Map
     */
    public Map<String, Object> convertMapToResultMap(final Map<?, ?> originalMap) {
        Map<String, Object> resultMap = new HashMap<>();
        for (Map.Entry<?, ?> entry : originalMap.entrySet()) {
            String key = entry.getKey() != null ? entry.getKey().toString() : "null";
            Object value = entry.getValue();
            if (value == null) {
                resultMap.put(key, null);
            } else if (isPrimitiveOrWrapper(value.getClass()) || value instanceof String) {
                resultMap.put(key, value);
            } else {
                resultMap.put(key, convertObjectToMap(value));
            }
        }
        return resultMap;
    }

    /**
     * 转换List为结果List
     *
     * @param originalList 原始List
     * @return 转换后的List
     */
    public List<Object> convertListToResultList(final List<?> originalList) {
        List<Object> resultList = new ArrayList<>();
        for (Object item : originalList) {
            if (item == null) {
                resultList.add(null);
            } else if (isPrimitiveOrWrapper(item.getClass()) || item instanceof String) {
                resultList.add(item);
            } else {
                resultList.add(convertObjectToMap(item));
            }
        }
        return resultList;
    }

    /**
     * 创建单值Map
     *
     * @param key 键
     * @param value 值
     * @return 单值Map
     */
    public Map<String, Object> createSingleValueMap(final String key, final Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    /**
     * 检查字段是否可以访问
     *
     * @param field 字段
     * @return 是否可访问
     */
    public boolean isAccessibleField(final Field field) {
        // 检查是否是静态字段
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            return false;
        }
        return true;
    }

    /**
     * 检查类是否是JDK内部类
     *
     * @param clazz 类
     * @return 是否是JDK内部类
     */
    public boolean isJdkInternalClass(final Class<?> clazz) {
        Package pkg = clazz.getPackage();
        if (pkg == null) {
            return false;
        }
        String pkgName = pkg.getName();
        return pkgName.startsWith("java.")
               || pkgName.startsWith("javax.")
               || pkgName.startsWith("sun.")
               || pkgName.startsWith("com.sun.");
    }

    /**
     * 检查是否是基本类型或其包装类
     *
     * @param clazz 类
     * @return 是否是基本类型或包装类
     */
    public boolean isPrimitiveOrWrapper(final Class<?> clazz) {
        return clazz.isPrimitive()
               || clazz == Boolean.class
               || clazz == Character.class
               || clazz == Byte.class
               || clazz == Short.class
               || clazz == Integer.class
               || clazz == Long.class
               || clazz == Float.class
               || clazz == Double.class;
    }

    /**
     * 更新限流配置
     *
     * @param rateLimitConfig 限流配置对象
     * @param rateLimitMap 限流配置Map
     */
    public void updateRateLimitConfig(final ModelRouterProperties.RateLimitConfig rateLimitConfig, final Map<String, Object> rateLimitMap) {
        if (rateLimitConfig == null || rateLimitMap == null) {
            return;
        }

        if (rateLimitMap.containsKey("enabled")) {
            Object enabledObj = rateLimitMap.get("enabled");
            if (enabledObj instanceof Boolean) {
                rateLimitConfig.setEnabled((Boolean) enabledObj);
            }
        }
        if (rateLimitMap.containsKey("algorithm")) {
            rateLimitConfig.setAlgorithm((String) rateLimitMap.get("algorithm"));
        }
        if (rateLimitMap.containsKey("capacity")) {
            Object capacityObj = rateLimitMap.get("capacity");
            if (capacityObj instanceof Number) {
                rateLimitConfig.setCapacity(((Number) capacityObj).longValue());
            }
        }
        if (rateLimitMap.containsKey("rate")) {
            Object rateObj = rateLimitMap.get("rate");
            if (rateObj instanceof Number) {
                rateLimitConfig.setRate(((Number) rateObj).longValue());
            }
        }
        if (rateLimitMap.containsKey("scope")) {
            rateLimitConfig.setScope((String) rateLimitMap.get("scope"));
        }
        if (rateLimitMap.containsKey("key")) {
            rateLimitConfig.setKey((String) rateLimitMap.get("key"));
        }
        if (rateLimitMap.containsKey("clientIpEnable")) {
            Object clientIpEnableObj = rateLimitMap.get("clientIpEnable");
            if (clientIpEnableObj instanceof Boolean) {
                rateLimitConfig.setClientIpEnable((Boolean) clientIpEnableObj);
            }
        }
    }

    /**
     * 更新熔断器配置
     *
     * @param circuitBreakerConfig 熔断器配置对象
     * @param circuitBreakerMap 熔断器配置Map
     */
    public void updateCircuitBreakerConfig(final ModelRouterProperties.CircuitBreakerConfig circuitBreakerConfig, final Map<String, Object> circuitBreakerMap) {
        if (circuitBreakerConfig == null || circuitBreakerMap == null) {
            return;
        }

        if (circuitBreakerMap.containsKey("enabled")) {
            Object enabledObj = circuitBreakerMap.get("enabled");
            if (enabledObj instanceof Boolean) {
                circuitBreakerConfig.setEnabled((Boolean) enabledObj);
            }
        }
        if (circuitBreakerMap.containsKey("failureThreshold")) {
            Object failureThresholdObj = circuitBreakerMap.get("failureThreshold");
            if (failureThresholdObj instanceof Number) {
                circuitBreakerConfig.setFailureThreshold(((Number) failureThresholdObj).intValue());
            }
        }
        if (circuitBreakerMap.containsKey("timeout")) {
            Object timeoutObj = circuitBreakerMap.get("timeout");
            if (timeoutObj instanceof Number) {
                circuitBreakerConfig.setTimeout(((Number) timeoutObj).longValue());
            }
        }
        if (circuitBreakerMap.containsKey("successThreshold")) {
            Object successThresholdObj = circuitBreakerMap.get("successThreshold");
            if (successThresholdObj instanceof Number) {
                circuitBreakerConfig.setSuccessThreshold(((Number) successThresholdObj).intValue());
            }
        }
    }

    /**
     * 创建默认负载均衡配置
     *
     * @return 默认负载均衡配置
     */
    public ModelRouterProperties.LoadBalanceConfig createDefaultLoadBalanceConfig() {
        return new ModelRouterProperties.LoadBalanceConfig();
    }

    /**
     * 更新降级配置
     *
     * @param fallbackConfig 降级配置对象
     * @param fallbackMap 降级配置Map
     */
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