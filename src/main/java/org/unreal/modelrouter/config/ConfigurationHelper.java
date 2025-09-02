package org.unreal.modelrouter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.ratelimit.RateLimitConfig;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置转换和处理工具类
 * 统一处理配置转换逻辑，消除重复代码
 */
@Component
public class ConfigurationHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationHelper.class);

    private ConfigurationValidator configurationValidator;

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
     * 服务键到服务类型映射
     * 支持多种格式：连字符、下划线、驼峰等
     */
    public ModelServiceRegistry.ServiceType parseServiceType(final String serviceKey) {
        if (serviceKey == null || serviceKey.trim().isEmpty()) {
            return null;
        }

        try {
            // 标准化处理：转小写，统一格式
            String normalizedKey = serviceKey.toLowerCase(java.util.Locale.ROOT)
                    .replace("-", "_")
                    .trim();

            // 特殊映射处理
            return switch (normalizedKey) {
                case "imggen", "img_gen", "image_generation", "image_gen" -> ModelServiceRegistry.ServiceType.imgGen;
                case "imgedit", "img_edit", "image_edit", "image_editing" -> ModelServiceRegistry.ServiceType.imgEdit;
                case "tts", "text_to_speech", "text2speech" -> ModelServiceRegistry.ServiceType.tts;
                case "stt", "speech_to_text", "speech2text" -> ModelServiceRegistry.ServiceType.stt;
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
    public RateLimitConfig convertRateLimitConfig(final ModelRouterProperties.RateLimitConfig source) {
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
     * 转换降级配置
     * 从Properties配置转换为内部配置对象
     */
    public ModelRouterProperties.FallbackConfig convertFallbackConfig(final ModelRouterProperties.FallbackConfig source) {
        if (source == null || !Boolean.TRUE.equals(source.getEnabled())) {
            return null;
        }

        ModelRouterProperties.FallbackConfig config = new ModelRouterProperties.FallbackConfig();
        config.setEnabled(source.getEnabled());
        config.setStrategy(source.getStrategy() != null ? source.getStrategy() : "default");
        config.setCacheSize(source.getCacheSize() != null ? source.getCacheSize() : 100);
        config.setCacheTtl(source.getCacheTtl() != null ? source.getCacheTtl() : 300000L);
        return config;
    }

    /**
     * 获取有效的负载均衡配置
     * 优先级：服务配置 > 全局配置 > 默认配置
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
    public String getServiceConfigKey(final ModelServiceRegistry.ServiceType serviceType) {
        if (serviceType == null) {
            return null;
        }
        return switch (serviceType) {
            case imgGen -> "img-gen";
            case imgEdit -> "img-edit";
            default -> serviceType.name().toLowerCase().replace("_", "-");
        };
    }

    /**
     * 验证负载均衡配置的有效性
     */
    public boolean isValidLoadBalanceConfig(final ModelRouterProperties.LoadBalanceConfig config) {
        return configurationValidator.validateLoadBalanceConfig(config);
    }

    /**
     * 验证限流配置的有效性
     */
    public boolean isValidRateLimitConfig(final ModelRouterProperties.RateLimitConfig config) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return false;
        }

        RateLimitConfig convertedConfig = convertRateLimitConfig(config);
        return configurationValidator.validateRateLimitConfig(convertedConfig);
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
     * 递归地将对象转换为Map
     *
     * @param obj 待转换的对象
     * @return 转换后的Map
     */
    private Map<String, Object> convertObjectToMap(final Object obj) {
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
                    List<?> originalList = (List<?>) value;
                    resultMap.put(field.getName(), convertListToResultList(originalList));
                } else if (value.getClass().getPackage().getName().equals(clazz.getPackage().getName())) {
                    // 如果是同一个包下的自定义类，递归处理
                    resultMap.put(field.getName(), convertObjectToMap(value));
                } else if (!isJdkInternalClass(value.getClass())) {
                    // 对于非JDK内部类，尝试递归处理
                    resultMap.put(field.getName(), convertObjectToMap(value));
                } else {
                    // 对于其他JDK内部类，使用toString()
                    resultMap.put(field.getName(), value.toString());
                }
            } catch (IllegalAccessException e) {
                LOGGER.warn("无法访问字段: " + field.getName(), e);
            } catch (Exception e) {
                LOGGER.warn("处理字段时出错: " + field.getName(), e);
            }
        }

        return resultMap;
    }

    /**
     * 检查字段是否可以访问
     */
    private boolean isAccessibleField(final Field field) {
        try {
            // 检查是否是JDK内部类的字段
            if (isJdkInternalClass(field.getDeclaringClass())) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查是否为JDK内部类
     */
    private boolean isJdkInternalClass(final Class<?> clazz) {
        String className = clazz.getName();
        return className.startsWith("java.") 
               || className.startsWith("javax.") 
               || className.startsWith("sun.")
               || className.startsWith("com.sun.");
    }

    /**
     * 转换Map为结果Map
     */
    private Map<String, Object> convertMapToResultMap(final Map<?, ?> originalMap) {
        Map<String, Object> convertedMap = new HashMap<>();
        for (Map.Entry<?, ?> entry : originalMap.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            String stringKey = String.valueOf(key);
            
            if (value == null) {
                convertedMap.put(stringKey, null);
            } else if (isPrimitiveOrWrapper(value.getClass()) || value instanceof String) {
                convertedMap.put(stringKey, value);
            } else {
                convertedMap.put(stringKey, convertObjectToMap(value));
            }
        }
        return convertedMap;
    }

    /**
     * 转换List为结果List
     */
    private List<Object> convertListToResultList(final List<?> originalList) {
        List<Object> convertedList = new ArrayList<>();
        for (Object item : originalList) {
            if (item == null) {
                convertedList.add(null);
            } else if (isPrimitiveOrWrapper(item.getClass()) || item instanceof String) {
                convertedList.add(item);
            } else {
                convertedList.add(convertObjectToMap(item));
            }
        }
        return convertedList;
    }

    /**
     * 创建单值Map
     */
    private Map<String, Object> createSingleValueMap(final String key, final Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    /**
     * 判断是否为基本类型或其包装类
     *
     * @param clazz 待判断的类
     * @return 是否为基本类型或其包装类
     */
    private boolean isPrimitiveOrWrapper(final Class<?> clazz) {
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
     * 将ModelInstance对象转换为Map
     *
     * @param instance ModelInstance对象
     * @return 转换后的Map
     */
    public Map<String, Object> convertInstanceToMap(ModelRouterProperties.ModelInstance instance) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", instance.getName());
        map.put("baseUrl", instance.getBaseUrl());
        map.put("path", instance.getPath());
        map.put("weight", instance.getWeight());

        // 添加限流配置
        addRateLimitToMap(instance.getRateLimit(), map);

        // 添加熔断器配置
        addCircuitBreakerToMap(instance.getCircuitBreaker(), map);

        return map;
    }

    /**
     * 将ServiceConfig对象转换为Map
     *
     * @param serviceConfig ServiceConfig对象
     * @return 转换后的Map
     */
    public Map<String, Object> convertServiceConfigToMap(ModelRouterProperties.ServiceConfig serviceConfig) {
        Map<String, Object> map = new HashMap<>();
        if (serviceConfig.getLoadBalance() != null) {
            Map<String, Object> loadBalanceMap = new HashMap<>();
            loadBalanceMap.put("type", serviceConfig.getLoadBalance().getType());
            loadBalanceMap.put("hashAlgorithm", serviceConfig.getLoadBalance().getHashAlgorithm());
            map.put("loadBalance", loadBalanceMap);
        }
        if (serviceConfig.getInstances() != null) {
            List<Map<String, Object>> instances = new ArrayList<>();
            for (ModelRouterProperties.ModelInstance instance : serviceConfig.getInstances()) {
                instances.add(convertInstanceToMap(instance));
            }
            map.put("instances", instances);
        }
        if (serviceConfig.getAdapter() != null) {
            map.put("adapter", serviceConfig.getAdapter());
        }
        
        // 添加限流配置
        addRateLimitToMap(serviceConfig.getRateLimit(), map);
        
        // 添加熔断器配置
        addCircuitBreakerToMap(serviceConfig.getCircuitBreaker(), map);
        
        // 添加降级配置
        addFallbackToMap(serviceConfig.getFallback(), map);

        return map;
    }

    /**
     * 更新限流配置
     * @param rateLimit 目标限流配置对象
     * @param map 配置Map
     */
    @SuppressWarnings("unchecked")
    public void updateRateLimitConfig(ModelRouterProperties.RateLimitConfig rateLimit, Map<String, Object> map) {
        if (map.containsKey("enabled")) {
            rateLimit.setEnabled((Boolean) map.get("enabled"));
        }
        if (map.containsKey("algorithm")) {
            rateLimit.setAlgorithm((String) map.get("algorithm"));
        }
        if (map.containsKey("capacity")) {
            rateLimit.setCapacity(((Number) map.get("capacity")).longValue());
        }
        if (map.containsKey("rate")) {
            rateLimit.setRate(((Number) map.get("rate")).longValue());
        }
        if (map.containsKey("scope")) {
            rateLimit.setScope((String) map.get("scope"));
        }
        if (map.containsKey("key")) {
            rateLimit.setKey((String) map.get("key"));
        }
        if (map.containsKey("clientIpEnable")) {
            rateLimit.setClientIpEnable((Boolean) map.get("clientIpEnable"));
        }
    }

    /**
     * 更新熔断器配置
     * @param circuitBreaker 目标熔断器配置对象
     * @param map 配置Map
     */
    @SuppressWarnings("unchecked")
    public void updateCircuitBreakerConfig(ModelRouterProperties.CircuitBreakerConfig circuitBreaker, Map<String, Object> map) {
        if (map.containsKey("enabled")) {
            circuitBreaker.setEnabled((Boolean) map.get("enabled"));
        }
        if (map.containsKey("failureThreshold")) {
            circuitBreaker.setFailureThreshold((Integer) map.get("failureThreshold"));
        }
        if (map.containsKey("timeout")) {
            circuitBreaker.setTimeout(((Number) map.get("timeout")).longValue());
        }
        if (map.containsKey("successThreshold")) {
            circuitBreaker.setSuccessThreshold((Integer) map.get("successThreshold"));
        }
    }

    /**
     * 更新降级配置
     * @param fallback 目标降级配置对象
     * @param map 配置Map
     */
    @SuppressWarnings("unchecked")
    public void updateFallbackConfig(ModelRouterProperties.FallbackConfig fallback, Map<String, Object> map) {
        if (map.containsKey("enabled")) {
            fallback.setEnabled((Boolean) map.get("enabled"));
        }
        if (map.containsKey("strategy")) {
            fallback.setStrategy((String) map.get("strategy"));
        }
        if (map.containsKey("cacheSize")) {
            fallback.setCacheSize((Integer) map.get("cacheSize"));
        }
        if (map.containsKey("cacheTtl")) {
            fallback.setCacheTtl(((Number) map.get("cacheTtl")).longValue());
        }
    }

    /**
     * 将Map转换为ServiceConfig对象
     * @param map Map对象
     * @return 转换后的ServiceConfig
     */
    @SuppressWarnings("unchecked")
    public ModelRouterProperties.ServiceConfig convertMapToServiceConfig(Map<String, Object> map) {
        ModelRouterProperties.ServiceConfig serviceConfig = new ModelRouterProperties.ServiceConfig();

        if (map.containsKey("loadBalance")) {
            Map<String, Object> loadBalanceMap = (Map<String, Object>) map.get("loadBalance");
            ModelRouterProperties.LoadBalanceConfig loadBalanceConfig = new ModelRouterProperties.LoadBalanceConfig();
            if (loadBalanceMap.containsKey("type")) {
                loadBalanceConfig.setType((String) loadBalanceMap.get("type"));
            }
            if (loadBalanceMap.containsKey("hashAlgorithm")) {
                loadBalanceConfig.setHashAlgorithm((String) loadBalanceMap.get("hashAlgorithm"));
            }
            serviceConfig.setLoadBalance(loadBalanceConfig);
        }

        if (map.containsKey("instances")) {
            List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
            List<Map<String, Object>> instanceList = (List<Map<String, Object>>) map.get("instances");
            for (Map<String, Object> instanceMap : instanceList) {
                instances.add(convertMapToInstance(instanceMap));
            }
            serviceConfig.setInstances(instances);
        }

        if (map.containsKey("adapter")) {
            serviceConfig.setAdapter((String) map.get("adapter"));
        }

        // 设置限流配置
        setRateLimitFromMap(map, serviceConfig);
        
        // 设置熔断器配置
        setCircuitBreakerFromMap(map, serviceConfig);
        
        // 设置降级配置
        setFallbackFromMap(map, serviceConfig);

        return serviceConfig;
    }

    /**
     * 将Map转换为ModelInstance对象
     * @param map Map对象
     * @return 转换后的ModelInstance
     */
    @SuppressWarnings("unchecked")
    public ModelRouterProperties.ModelInstance convertMapToInstance(Map<String, Object> map) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName((String) map.get("name"));
        instance.setBaseUrl((String) map.get("baseUrl"));
        instance.setPath((String) map.get("path"));
        if (map.containsKey("weight")) {
            instance.setWeight(((Number) map.get("weight")).intValue());
        } else {
            instance.setWeight(1);
        }

        // 设置限流配置
        setRateLimitFromMap(map, instance);

        // 设置熔断器配置
        setCircuitBreakerFromMap(map, instance);

        return instance;
    }
    
    // ==================== 共性方法提取 ====================
    
    /**
     * 将限流配置添加到Map中
     */
    private void addRateLimitToMap(ModelRouterProperties.RateLimitConfig rateLimit, Map<String, Object> targetMap) {
        if (rateLimit != null) {
            Map<String, Object> rateLimitMap = new HashMap<>();
            rateLimitMap.put("enabled", rateLimit.getEnabled());
            rateLimitMap.put("algorithm", rateLimit.getAlgorithm());
            rateLimitMap.put("capacity", rateLimit.getCapacity());
            rateLimitMap.put("rate", rateLimit.getRate());
            rateLimitMap.put("scope", rateLimit.getScope());
            rateLimitMap.put("key", rateLimit.getKey());
            rateLimitMap.put("clientIpEnable", rateLimit.getClientIpEnable());
            targetMap.put("rateLimit", rateLimitMap);
        }
    }
    
    /**
     * 从Map中设置限流配置
     */
    private void setRateLimitFromMap(Map<String, Object> sourceMap, ModelRouterProperties.ModelInstance targetInstance) {
        if (sourceMap.containsKey("rateLimit") && sourceMap.get("rateLimit") != null) {
            Map<String, Object> rateLimitMap = (Map<String, Object>) sourceMap.get("rateLimit");
            ModelRouterProperties.RateLimitConfig rateLimitConfig = new ModelRouterProperties.RateLimitConfig();
            if (rateLimitMap.containsKey("enabled")) {
                rateLimitConfig.setEnabled((Boolean) rateLimitMap.get("enabled"));
            }
            if (rateLimitMap.containsKey("algorithm")) {
                rateLimitConfig.setAlgorithm((String) rateLimitMap.get("algorithm"));
            }
            if (rateLimitMap.containsKey("capacity")) {
                rateLimitConfig.setCapacity(((Number) rateLimitMap.get("capacity")).longValue());
            }
            if (rateLimitMap.containsKey("rate")) {
                rateLimitConfig.setRate(((Number) rateLimitMap.get("rate")).longValue());
            }
            if (rateLimitMap.containsKey("scope")) {
                rateLimitConfig.setScope((String) rateLimitMap.get("scope"));
            }
            if (rateLimitMap.containsKey("key")) {
                rateLimitConfig.setKey((String) rateLimitMap.get("key"));
            }
            if (rateLimitMap.containsKey("clientIpEnable")) {
                rateLimitConfig.setClientIpEnable((Boolean) rateLimitMap.get("clientIpEnable"));
            }
            targetInstance.setRateLimit(rateLimitConfig);
        }
    }
    
    /**
     * 从Map中设置限流配置（用于ServiceConfig）
     */
    private void setRateLimitFromMap(Map<String, Object> sourceMap, ModelRouterProperties.ServiceConfig targetConfig) {
        if (sourceMap.containsKey("rateLimit") && sourceMap.get("rateLimit") != null) {
            Map<String, Object> rateLimitMap = (Map<String, Object>) sourceMap.get("rateLimit");
            ModelRouterProperties.RateLimitConfig rateLimitConfig = new ModelRouterProperties.RateLimitConfig();
            if (rateLimitMap.containsKey("enabled")) {
                rateLimitConfig.setEnabled((Boolean) rateLimitMap.get("enabled"));
            }
            if (rateLimitMap.containsKey("algorithm")) {
                rateLimitConfig.setAlgorithm((String) rateLimitMap.get("algorithm"));
            }
            if (rateLimitMap.containsKey("capacity")) {
                rateLimitConfig.setCapacity(((Number) rateLimitMap.get("capacity")).longValue());
            }
            if (rateLimitMap.containsKey("rate")) {
                rateLimitConfig.setRate(((Number) rateLimitMap.get("rate")).longValue());
            }
            if (rateLimitMap.containsKey("scope")) {
                rateLimitConfig.setScope((String) rateLimitMap.get("scope"));
            }
            if (rateLimitMap.containsKey("key")) {
                rateLimitConfig.setKey((String) rateLimitMap.get("key"));
            }
            if (rateLimitMap.containsKey("clientIpEnable")) {
                rateLimitConfig.setClientIpEnable((Boolean) rateLimitMap.get("clientIpEnable"));
            }
            targetConfig.setRateLimit(rateLimitConfig);
        }
    }
    
    /**
     * 将熔断器配置添加到Map中
     */
    private void addCircuitBreakerToMap(ModelRouterProperties.CircuitBreakerConfig circuitBreaker, Map<String, Object> targetMap) {
        if (circuitBreaker != null) {
            Map<String, Object> circuitBreakerMap = new HashMap<>();
            circuitBreakerMap.put("enabled", circuitBreaker.getEnabled());
            circuitBreakerMap.put("failureThreshold", circuitBreaker.getFailureThreshold());
            circuitBreakerMap.put("timeout", circuitBreaker.getTimeout());
            circuitBreakerMap.put("successThreshold", circuitBreaker.getSuccessThreshold());
            targetMap.put("circuitBreaker", circuitBreakerMap);
        }
    }
    
    /**
     * 从Map中设置熔断器配置
     */
    private void setCircuitBreakerFromMap(Map<String, Object> sourceMap, Object target) {
        if (sourceMap.containsKey("circuitBreaker")&& sourceMap.get("circuitBreaker") != null) {
            Map<String, Object> circuitBreakerMap = (Map<String, Object>) sourceMap.get("circuitBreaker");
            ModelRouterProperties.CircuitBreakerConfig circuitBreakerConfig = new ModelRouterProperties.CircuitBreakerConfig();
            if (circuitBreakerMap.containsKey("enabled")) {
                circuitBreakerConfig.setEnabled((Boolean) circuitBreakerMap.get("enabled"));
            }
            if (circuitBreakerMap.containsKey("failureThreshold")) {
                circuitBreakerConfig.setFailureThreshold(((Number) circuitBreakerMap.get("failureThreshold")).intValue());
            }
            if (circuitBreakerMap.containsKey("timeout")) {
                circuitBreakerConfig.setTimeout(((Number) circuitBreakerMap.get("timeout")).longValue());
            }
            if (circuitBreakerMap.containsKey("successThreshold")) {
                circuitBreakerConfig.setSuccessThreshold(((Number) circuitBreakerMap.get("successThreshold")).intValue());
            }
            
            // 根据目标对象类型设置熔断器配置
            if (target instanceof ModelRouterProperties.ModelInstance) {
                ((ModelRouterProperties.ModelInstance) target).setCircuitBreaker(circuitBreakerConfig);
            } else if (target instanceof ModelRouterProperties.ServiceConfig) {
                ((ModelRouterProperties.ServiceConfig) target).setCircuitBreaker(circuitBreakerConfig);
            }
        }
    }
    
    /**
     * 将降级配置添加到Map中
     */
    private void addFallbackToMap(ModelRouterProperties.FallbackConfig fallback, Map<String, Object> targetMap) {
        if (fallback != null) {
            Map<String, Object> fallbackMap = new HashMap<>();
            fallbackMap.put("enabled", fallback.getEnabled());
            fallbackMap.put("strategy", fallback.getStrategy());
            fallbackMap.put("cacheSize", fallback.getCacheSize());
            fallbackMap.put("cacheTtl", fallback.getCacheTtl());
            targetMap.put("fallback", fallbackMap);
        }
    }
    
    /**
     * 从Map中设置降级配置
     */
    private void setFallbackFromMap(Map<String, Object> sourceMap, ModelRouterProperties.ServiceConfig targetConfig) {
        if (sourceMap.containsKey("fallback")&& sourceMap.get("fallback") != null) {
            Map<String, Object> fallbackMap = (Map<String, Object>) sourceMap.get("fallback");
            ModelRouterProperties.FallbackConfig fallbackConfig = new ModelRouterProperties.FallbackConfig();
            if (fallbackMap.containsKey("enabled")) {
                fallbackConfig.setEnabled((Boolean) fallbackMap.get("enabled"));
            }
            if (fallbackMap.containsKey("strategy")) {
                fallbackConfig.setStrategy((String) fallbackMap.get("strategy"));
            }
            if (fallbackMap.containsKey("cacheSize")) {
                fallbackConfig.setCacheSize(((Number) fallbackMap.get("cacheSize")).intValue());
            }
            if (fallbackMap.containsKey("cacheTtl")) {
                fallbackConfig.setCacheTtl(((Number) fallbackMap.get("cacheTtl")).longValue());
            }
            targetConfig.setFallback(fallbackConfig);
        }
    }
}