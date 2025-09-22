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
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * 配置转换和处理工具类
 * 统一处理配置转换逻辑，消除重复代码
 */
@Component
public class ConfigurationHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationHelper.class);
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationHelper.class);
    
    // IP地址正则表达式
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );
    
    // 域名正则表达式
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
    );

    private ConfigurationValidator configurationValidator;
    private ConfigurationHelper configurationHelper;

    /**
     * Sets the configuration validator for this helper.
     * 
     * @param configurationValidator the configuration validator to set
     */
    @Autowired
    public void setConfigurationValidator(final ConfigurationValidator configurationValidator) {
        this.configurationValidator = configurationValidator;
    }
    
    @Autowired
    public void setConfigurationHelper(final ConfigurationHelper configurationHelper) {
        this.configurationHelper = configurationHelper;
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
                    .replaceAll("[\\s_-]+", ""); // 移除空格、下划线和连字符
            
            // 尝试直接匹配枚举值
            return ModelServiceRegistry.ServiceType.valueOf(normalizedKey);
        } catch (IllegalArgumentException e) {
            // 处理常见的别名映射
            switch (serviceKey.toLowerCase(java.util.Locale.ROOT)) {
                case "chat":
                case "chat-completion":
                case "chat-completions":
                    return ModelServiceRegistry.ServiceType.chat;
                case "embedding":
                case "embeddings":
                    return ModelServiceRegistry.ServiceType.embedding;
                case "rerank":
                case "re-rank":
                    return ModelServiceRegistry.ServiceType.rerank;
                case "tts":
                case "text-to-speech":
                    return ModelServiceRegistry.ServiceType.tts;
                case "stt":
                case "speech-to-text":
                    return ModelServiceRegistry.ServiceType.stt;
                case "imggen":
                case "image-generation":
                case "image-generate":
                    return ModelServiceRegistry.ServiceType.imgGen;
                case "imgedit":
                case "image-edit":
                case "image-editing":
                    return ModelServiceRegistry.ServiceType.imgEdit;
                default:
                    LOGGER.warn("无法解析服务类型: {}", serviceKey);
                    return null;
            }
        }
    }

    /**
     * 获取服务配置键
     * @param serviceType 服务类型
     * @return 服务配置键
     */
    public String getServiceConfigKey(ModelServiceRegistry.ServiceType serviceType) {
        if (serviceType == null) {
            return null;
        }
        
        // 使用连字符格式作为标准键名
        return serviceType.name().replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
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
     * 将ModelRouterProperties.RateLimitConfig转换为RateLimitConfig
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
     * 验证限流配置参数的合法性
     */
    public boolean validateRateLimitConfig(final ModelRouterProperties.RateLimitConfig config) {
        if (config == null) {
            LOGGER.warn("Rate limit config is null");
            return false;
        }

        // 如果未启用，则无需验证
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            LOGGER.debug("Rate limit config is disabled, validation skipped");
            return true;
        }

        // 验证算法
        if (config.getAlgorithm() == null || !isValidRateLimitAlgorithm(config.getAlgorithm())) {
            LOGGER.warn("Invalid rate limit algorithm: {}", config.getAlgorithm());
            return false;
        }

        // 验证容量和速率
        if (config.getCapacity() == null || config.getCapacity() <= 0) {
            LOGGER.warn("Invalid rate limit capacity: {}", config.getCapacity());
            return false;
        }

        if (config.getRate() == null || config.getRate() <= 0) {
            LOGGER.warn("Invalid rate limit rate: {}", config.getRate());
            return false;
        }

        // 验证作用域
        if (config.getScope() == null || !isValidRateLimitScope(config.getScope())) {
            LOGGER.warn("Invalid rate limit scope: {}", config.getScope());
            return false;
        }

        // 验证客户端IP启用标志
        if (config.getClientIpEnable() == null) {
            LOGGER.warn("Rate limit clientIpEnable is null");
            return false;
        }

        logger.debug("Rate limit config validation passed");
        return true;
    }

    /**
     * 验证负载均衡配置参数的合法性
     *
     * @param config 负载均衡配置
     * @return 配置是否合法
     */
    public boolean validateLoadBalanceConfig(ModelRouterProperties.LoadBalanceConfig config) {
        if (config == null) {
            logger.warn("Load balance config is null");
            return false;
        }

        // 检查类型是否合法
        if (config.getType() == null || !isValidLoadBalanceType(config.getType())) {
            logger.warn("Invalid load balance type: {}", config.getType());
            return false;
        }

        // 对于IP哈希算法，检查哈希算法是否合法
        if ("ip-hash".equalsIgnoreCase(config.getType())) {
            if (config.getHashAlgorithm() == null || !isValidHashAlgorithm(config.getHashAlgorithm())) {
                logger.warn("Invalid hash algorithm for IP hash load balancer: {}", config.getHashAlgorithm());
                return false;
            }
        }

        logger.debug("Load balance config validation passed");
        return true;
    }

    /**
     * 验证服务实例地址的合法性
     *
     * @param address 服务实例地址
     * @return 地址是否合法
     */
    public boolean validateServiceAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            logger.warn("Invalid service address: null or empty");
            return false;
        }

        // 移除协议部分（http:// 或 https://）
        String cleanAddress = address;
        if (address.startsWith("http://")) {
            cleanAddress = address.substring(7);
        } else if (address.startsWith("https://")) {
            cleanAddress = address.substring(8);
        }

        // 分离主机和端口
        String host;
        String portStr = null;

        int colonIndex = cleanAddress.lastIndexOf(':');
        if (colonIndex > 0) {
            host = cleanAddress.substring(0, colonIndex);
            portStr = cleanAddress.substring(colonIndex + 1);
        } else {
            host = cleanAddress;
        }

        // 验证主机部分（IP或域名）
        if (!IP_PATTERN.matcher(host).matches() && !DOMAIN_PATTERN.matcher(host).matches()) {
            logger.warn("Invalid service address host: {}", host);
            return false;
        }

        // 验证端口部分（如果存在）
        if (portStr != null) {
            try {
                int port = Integer.parseInt(portStr);
                if (port <= 0 || port > 65535) {
                    logger.warn("Invalid service address port: {}", port);
                    return false;
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid service address port format: {}", portStr);
                return false;
            }
        }

        logger.debug("Service address validation passed: {}", address);
        return true;
    }

    /**
     * 检查限流算法是否合法
     *
     * @param algorithm 算法名称
     * @return 算法是否合法
     */
    private boolean isValidRateLimitAlgorithm(String algorithm) {
        if (algorithm == null) {
            return false;
        }
        String normalizedAlgorithm = algorithm.toLowerCase(java.util.Locale.ROOT);
        return "token-bucket".equals(normalizedAlgorithm) ||
                "leaky-bucket".equals(normalizedAlgorithm) ||
                "sliding-window".equals(normalizedAlgorithm) ||
                "warm-up".equals(normalizedAlgorithm);
    }

    /**
     * 检查限流作用域是否合法
     *
     * @param scope 作用域
     * @return 作用域是否合法
     */
    private boolean isValidRateLimitScope(String scope) {
        if (scope == null) {
            return false;
        }
        String normalizedScope = scope.toLowerCase(java.util.Locale.ROOT);
        return "service".equals(normalizedScope) ||
                "model".equals(normalizedScope) ||
                "client-ip".equals(normalizedScope) ||
                "instance".equals(normalizedScope);
    }

    /**
     * 检查负载均衡类型是否合法
     *
     * @param type 负载均衡类型
     * @return 类型是否合法
     */
    private boolean isValidLoadBalanceType(String type) {
        if (type == null) {
            return false;
        }
        String normalizedType = type.toLowerCase(java.util.Locale.ROOT);
        return "random".equals(normalizedType) ||
                "round-robin".equals(normalizedType) ||
                "least-connections".equals(normalizedType) ||
                "ip-hash".equals(normalizedType);
    }

    /**
     * 检查哈希算法是否合法
     *
     * @param algorithm 哈希算法
     * @return 算法是否合法
     */
    private boolean isValidHashAlgorithm(String algorithm) {
        if (algorithm == null) {
            return false;
        }
        String normalizedAlgorithm = algorithm.toLowerCase(java.util.Locale.ROOT);
        return "md5".equals(normalizedAlgorithm) ||
                "sha1".equals(normalizedAlgorithm) ||
                "sha256".equals(normalizedAlgorithm);
    }

    /**
     * 验证负载均衡配置
     */
    @SuppressWarnings("unchecked")
    private void validateLoadBalanceConfig(Object loadBalanceObj,
                                           String context,
                                           List<String> errors,
                                           List<String> warnings) {
        if (!(loadBalanceObj instanceof Map)) {
            errors.add(context + " 负载均衡配置格式错误");
            return;
        }

        Map<String, Object> loadBalance = (Map<String, Object>) loadBalanceObj;

        if (loadBalance.containsKey("type")) {
            String type = (String) loadBalance.get("type");
            if (!isValidLoadBalanceType(type)) {
                errors.add(context + " 负载均衡类型无效: " + type);
            }
        }
    }

    /**
     * 验证限流配置
     */
    @SuppressWarnings("unchecked")
    private void validateRateLimitConfig(Object rateLimitObj,
                                         String context,
                                         List<String> errors,
                                         List<String> warnings) {
        if (!(rateLimitObj instanceof Map)) {
            errors.add(context + " 限流配置格式错误");
            return;
        }

        Map<String, Object> rateLimit = (Map<String, Object>) rateLimitObj;

        // 如果未启用，则无需验证
        if (rateLimit.containsKey("enabled") &&
                (Boolean.FALSE.equals(rateLimit.get("enabled")) || "false".equalsIgnoreCase(rateLimit.get("enabled").toString()))) {
            return;
        }

        if (rateLimit.containsKey("algorithm")) {
            String algorithm = (String) rateLimit.get("algorithm");
            if (!isValidRateLimitAlgorithm(algorithm)) {
                errors.add(context + " 限流算法无效: " + algorithm);
            }
        }

        if (rateLimit.containsKey("capacity")) {
            try {
                long capacity = ((Number) rateLimit.get("capacity")).longValue();
                if (capacity <= 0) {
                    errors.add(context + " 限流容量必须大于0: " + capacity);
                }
            } catch (Exception e) {
                errors.add(context + " 限流容量格式错误: " + rateLimit.get("capacity"));
            }
        }

        if (rateLimit.containsKey("rate")) {
            try {
                long rate = ((Number) rateLimit.get("rate")).longValue();
                if (rate <= 0) {
                    errors.add(context + " 限流速率必须大于0: " + rate);
                }
            } catch (Exception e) {
                errors.add(context + " 限流速率格式错误: " + rateLimit.get("rate"));
            }
        }

        if (rateLimit.containsKey("scope")) {
            String scope = (String) rateLimit.get("scope");
            if (!isValidRateLimitScope(scope)) {
                errors.add(context + " 限流作用域无效: " + scope);
            }
        }
    }

    /**
     * 验证熔断器配置
     */
    @SuppressWarnings("unchecked")
    private void validateCircuitBreakerConfig(Object circuitBreakerObj,
                                              String context,
                                              List<String> errors,
                                              List<String> warnings) {
        if (!(circuitBreakerObj instanceof Map)) {
            errors.add(context + " 熔断器配置格式错误");
            return;
        }

        Map<String, Object> circuitBreaker = (Map<String, Object>) circuitBreakerObj;

        // 如果未启用，则无需验证
        if (circuitBreaker.containsKey("enabled") &&
                (Boolean.FALSE.equals(circuitBreaker.get("enabled")) || "false".equalsIgnoreCase(circuitBreaker.get("enabled").toString()))) {
            return;
        }

        if (circuitBreaker.containsKey("failureThreshold")) {
            try {
                int failureThreshold = ((Number) circuitBreaker.get("failureThreshold")).intValue();
                if (failureThreshold <= 0) {
                    errors.add(context + " 熔断器失败阈值必须大于0: " + failureThreshold);
                }
            } catch (Exception e) {
                errors.add(context + " 熔断器失败阈值格式错误: " + circuitBreaker.get("failureThreshold"));
            }
        }

        if (circuitBreaker.containsKey("timeout")) {
            try {
                long timeout = ((Number) circuitBreaker.get("timeout")).longValue();
                if (timeout <= 0) {
                    errors.add(context + " 熔断器超时时间必须大于0: " + timeout);
                }
            } catch (Exception e) {
                errors.add(context + " 熔断器超时时间格式错误: " + circuitBreaker.get("timeout"));
            }
        }

        if (circuitBreaker.containsKey("successThreshold")) {
            try {
                int successThreshold = ((Number) circuitBreaker.get("successThreshold")).intValue();
                if (successThreshold <= 0) {
                    errors.add(context + " 熔断器成功阈值必须大于0: " + successThreshold);
                }
            } catch (Exception e) {
                errors.add(context + " 熔断器成功阈值格式错误: " + circuitBreaker.get("successThreshold"));
            }
        }
    }

    /**
     * 验证服务配置
     */
    public void validateServiceConfig(String serviceType,
                                      Map<String, Object> serviceConfig,
                                      List<String> errors,
                                      List<String> warnings) {
        if (serviceConfig == null) {
            errors.add("服务配置不能为空");
            return;
        }

        // 验证基本配置
        validateBasicConfiguration(serviceConfig, errors, warnings);

        // 验证负载均衡配置
        if (serviceConfig.containsKey("loadBalance")) {
            validateLoadBalanceConfig(serviceConfig.get("loadBalance"), serviceType, errors, warnings);
        }

        // 验证限流配置
        if (serviceConfig.containsKey("rateLimit")) {
            validateRateLimitConfig(serviceConfig.get("rateLimit"), serviceType, errors, warnings);
        }

        // 验证熔断器配置
        if (serviceConfig.containsKey("circuitBreaker")) {
            validateCircuitBreakerConfig(serviceConfig.get("circuitBreaker"), serviceType, errors, warnings);
        }

        // 验证实例配置
        if (serviceConfig.containsKey("instances")) {
            validateInstancesConfig(serviceConfig.get("instances"), serviceType, errors, warnings);
        }
    }

    /**
     * 验证实例配置
     */
    @SuppressWarnings("unchecked")
    private void validateInstancesConfig(Object instancesObj,
                                         String serviceType,
                                         List<String> errors,
                                         List<String> warnings) {
        if (!(instancesObj instanceof List)) {
            errors.add(serviceType + " 实例配置格式错误");
            return;
        }

        List<Map<String, Object>> instances = (List<Map<String, Object>>) instancesObj;
        Set<String> instanceIds = new HashSet<>();

        for (int i = 0; i < instances.size(); i++) {
            Map<String, Object> instance = instances.get(i);
            String context = serviceType + " 实例[" + i + "]";

            // 验证必需字段
            if (!instance.containsKey("name") || instance.get("name") == null) {
                errors.add(context + " 名称不能为空");
            }

            if (!instance.containsKey("baseUrl") || instance.get("baseUrl") == null) {
                errors.add(context + " 基础URL不能为空");
            } else {
                String baseUrl = (String) instance.get("baseUrl");
                if (!validateServiceAddress(baseUrl)) {
                    errors.add(context + " 基础URL格式不正确: " + baseUrl);
                }
            }

            // 验证权重
            if (instance.containsKey("weight")) {
                try {
                    int weight = ((Number) instance.get("weight")).intValue();
                    if (weight <= 0) {
                        errors.add(context + " 权重必须大于0: " + weight);
                    }
                } catch (Exception e) {
                    errors.add(context + " 权重格式错误: " + instance.get("weight"));
                }
            }

            // 验证限流配置
            if (instance.containsKey("rateLimit")) {
                validateRateLimitConfig(instance.get("rateLimit"), context, errors, warnings);
            }

            // 验证熔断器配置
            if (instance.containsKey("circuitBreaker")) {
                validateCircuitBreakerConfig(instance.get("circuitBreaker"), context, errors, warnings);
            }

            // 检查实例ID唯一性
            if (instance.containsKey("instanceId")) {
                String instanceId = (String) instance.get("instanceId");
                if (instanceIds.contains(instanceId)) {
                    errors.add(context + " 实例ID重复: " + instanceId);
                } else {
                    instanceIds.add(instanceId);
                }
            }
        }
    }

    /**
     * 验证基础配置
     */
    @SuppressWarnings("unchecked")
    private void validateBasicConfiguration(Map<String, Object> config,
                                            List<String> errors,
                                            List<String> warnings) {
        // 验证全局适配器
        if (config.containsKey("adapter")) {
            String adapter = (String) config.get("adapter");
            if (adapter == null || adapter.trim().isEmpty()) {
                warnings.add("全局适配器配置为空");
            }
        }

        // 验证全局负载均衡配置
        if (config.containsKey("loadBalance")) {
            validateLoadBalanceConfig(config.get("loadBalance"), "全局", errors, warnings);
        }

        // 验证全局限流配置
        if (config.containsKey("rateLimit")) {
            validateRateLimitConfig(config.get("rateLimit"), "全局", errors, warnings);
        }
    }

    public boolean isValidServiceType(String serviceType) {
        if (serviceType == null) {
            return false;
        }
        
        try {
            // 使用ConfigurationHelper来解析服务类型，支持更多格式
            return configurationHelper.parseServiceType(serviceType) != null;
        } catch (Exception e) {
            return false;
        }
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
                    resultMap.put(field.getName(), convertListToResultList((List<?>) value));
                } else {
                    // 递归处理嵌套对象
                    resultMap.put(field.getName(), convertObjectToMap(value));
                }
            } catch (IllegalAccessException e) {
                LOGGER.warn("无法访问字段: {}", field.getName(), e);
            } catch (Exception e) {
                LOGGER.warn("处理字段时发生错误: {}", field.getName(), e);
            }
        }

        return resultMap;
    }

    /**
     * 检查字段是否可以访问
     */
    private boolean isAccessibleField(Field field) {
        // 检查是否是静态字段
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            return false;
        }
        return true;
    }

    /**
     * 检查类是否是JDK内部类
     */
    private boolean isJdkInternalClass(Class<?> clazz) {
        Package pkg = clazz.getPackage();
        if (pkg == null) {
            return false;
        }
        String pkgName = pkg.getName();
        return pkgName.startsWith("java.") || 
               pkgName.startsWith("javax.") || 
               pkgName.startsWith("sun.") || 
               pkgName.startsWith("com.sun.");
    }

    /**
     * 检查是否是基本类型或其包装类
     */
    private boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() ||
               clazz == Boolean.class ||
               clazz == Character.class ||
               clazz == Byte.class ||
               clazz == Short.class ||
               clazz == Integer.class ||
               clazz == Long.class ||
               clazz == Float.class ||
               clazz == Double.class;
    }

    /**
     * 创建单值Map
     */
    private Map<String, Object> createSingleValueMap(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    /**
     * 转换Map为结果Map
     */
    private Map<String, Object> convertMapToResultMap(Map<?, ?> originalMap) {
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
     */
    private List<Object> convertListToResultList(List<?> originalList) {
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
     * 将ModelInstance对象转换为Map
     *
     * @param instance ModelInstance对象
     * @return 转换后的Map
     */
    public Map<String, Object> convertInstanceToMap(ModelRouterProperties.ModelInstance instance) {
        if (instance == null) {
            return null;
        }
        return convertObjectToMap(instance);
    }
    
    /**
     * 将Map转换为ServiceConfig对象
     *
     * @param serviceConfigMap 服务配置Map
     * @return ServiceConfig对象
     */
    public ModelRouterProperties.ServiceConfig convertMapToServiceConfig(Map<String, Object> serviceConfigMap) {
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
    public ModelRouterProperties.ModelInstance convertMapToInstance(Map<String, Object> instanceMap) {
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
     * 创建默认负载均衡配置
     *
     * @return 默认负载均衡配置
     */
    public ModelRouterProperties.LoadBalanceConfig createDefaultLoadBalanceConfig() {
        return new ModelRouterProperties.LoadBalanceConfig();
    }
    
    /**
     * 更新限流配置
     *
     * @param rateLimitConfig 限流配置对象
     * @param rateLimitMap 限流配置Map
     */
    public void updateRateLimitConfig(ModelRouterProperties.RateLimitConfig rateLimitConfig, Map<String, Object> rateLimitMap) {
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
    public void updateCircuitBreakerConfig(ModelRouterProperties.CircuitBreakerConfig circuitBreakerConfig, Map<String, Object> circuitBreakerMap) {
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
     * 更新降级配置
     *
     * @param fallbackConfig 降级配置对象
     * @param fallbackMap 降级配置Map
     */
    public void updateFallbackConfig(ModelRouterProperties.FallbackConfig fallbackConfig, Map<String, Object> fallbackMap) {
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
            ModelRouterProperties.LoadBalanceConfig serviceConfig,
            ModelRouterProperties.LoadBalanceConfig globalConfig) {
        if (serviceConfig != null) {
            return serviceConfig;
        }
        if (globalConfig != null) {
            return globalConfig;
        }
        return createDefaultLoadBalanceConfig();
    }
}
