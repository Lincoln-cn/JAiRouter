package org.unreal.modelrouter.config.core.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.ratelimit.RateLimitConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 配置验证工具类
 * 从 ConfigurationHelper 提取的验证逻辑
 */
@Component
public class ConfigValidatorHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigValidatorHelper.class);

    /**
     * IP地址正则表达式
     */
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    /**
     * 域名正则表达式
     */
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
    );

    /**
     * 验证限流配置的有效性
     *
     * @param config 限流配置
     * @return 配置是否有效
     */
    public boolean isValidRateLimitConfig(final ModelRouterProperties.RateLimitConfig config) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return false;
        }

        // 验证算法
        if (config.getAlgorithm() == null || !isValidRateLimitAlgorithm(config.getAlgorithm())) {
            return false;
        }

        // 验证容量和速率
        if (config.getCapacity() == null || config.getCapacity() <= 0) {
            return false;
        }

        if (config.getRate() == null || config.getRate() <= 0) {
            return false;
        }

        // 验证作用域
        if (config.getScope() == null || !isValidRateLimitScope(config.getScope())) {
            return false;
        }

        return true;
    }

    /**
     * 验证限流配置参数的合法性
     *
     * @param config 限流配置
     * @return 配置是否合法
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

        LOGGER.debug("Rate limit config validation passed");
        return true;
    }

    /**
     * 验证限流配置（Map格式）
     *
     * @param rateLimitObj 限流配置对象
     * @param context 上下文信息
     * @param errors 错误列表
     * @param warnings 警告列表
     */
    @SuppressWarnings("unchecked")
    public void validateRateLimitConfig(final Object rateLimitObj,
                                        final String context,
                                        final List<String> errors,
                                        final List<String> warnings) {
        if (!(rateLimitObj instanceof Map)) {
            errors.add(context + " 限流配置格式错误");
            return;
        }

        Map<String, Object> rateLimit = (Map<String, Object>) rateLimitObj;

        // 如果未启用，则无需验证
        if (rateLimit.containsKey("enabled")
                && (Boolean.FALSE.equals(rateLimit.get("enabled"))
                    || "false".equalsIgnoreCase(rateLimit.get("enabled").toString()))) {
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
     * 验证负载均衡配置参数的合法性
     *
     * @param config 负载均衡配置
     * @return 配置是否合法
     */
    public boolean validateLoadBalanceConfig(final ModelRouterProperties.LoadBalanceConfig config) {
        if (config == null) {
            LOGGER.warn("Load balance config is null");
            return false;
        }

        // 检查类型是否合法
        if (config.getType() == null || !isValidLoadBalanceType(config.getType())) {
            LOGGER.warn("Invalid load balance type: {}", config.getType());
            return false;
        }

        // 对于IP哈希算法，检查哈希算法是否合法
        if ("ip-hash".equalsIgnoreCase(config.getType())) {
            if (config.getHashAlgorithm() == null || !isValidHashAlgorithm(config.getHashAlgorithm())) {
                LOGGER.warn("Invalid hash algorithm for IP hash load balancer: {}", config.getHashAlgorithm());
                return false;
            }
        }

        LOGGER.debug("Load balance config validation passed");
        return true;
    }

    /**
     * 验证负载均衡配置（Map格式）
     *
     * @param loadBalanceObj 负载均衡配置对象
     * @param context 上下文信息
     * @param errors 错误列表
     * @param warnings 警告列表
     */
    @SuppressWarnings("unchecked")
    public void validateLoadBalanceConfig(final Object loadBalanceObj,
                                           final String context,
                                           final List<String> errors,
                                           final List<String> warnings) {
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
     * 验证服务实例地址的合法性
     *
     * @param address 服务实例地址
     * @return 地址是否合法
     */
    public boolean validateServiceAddress(final String address) {
        if (address == null || address.trim().isEmpty()) {
            LOGGER.warn("Invalid service address: null or empty");
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
            LOGGER.warn("Invalid service address host: {}", host);
            return false;
        }

        // 验证端口部分（如果存在）
        if (portStr != null) {
            try {
                int port = Integer.parseInt(portStr);
                if (port <= 0 || port > 65535) {
                    LOGGER.warn("Invalid service address port: {}", port);
                    return false;
                }
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid service address port format: {}", portStr);
                return false;
            }
        }

        LOGGER.debug("Service address validation passed: {}", address);
        return true;
    }

    /**
     * 验证服务配置
     *
     * @param serviceType 服务类型
     * @param serviceConfig 服务配置
     * @param errors 错误列表
     * @param warnings 警告列表
     */
    public void validateServiceConfig(final String serviceType,
                                       final Map<String, Object> serviceConfig,
                                       final List<String> errors,
                                       final List<String> warnings) {
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
     *
     * @param instancesObj 实例配置对象
     * @param serviceType 服务类型
     * @param errors 错误列表
     * @param warnings 警告列表
     */
    @SuppressWarnings("unchecked")
    public void validateInstancesConfig(final Object instancesObj,
                                         final String serviceType,
                                         final List<String> errors,
                                         final List<String> warnings) {
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
     * 验证熔断器配置
     *
     * @param circuitBreakerObj 熔断器配置对象
     * @param context 上下文信息
     * @param errors 错误列表
     * @param warnings 警告列表
     */
    @SuppressWarnings("unchecked")
    public void validateCircuitBreakerConfig(final Object circuitBreakerObj,
                                              final String context,
                                              final List<String> errors,
                                              final List<String> warnings) {
        if (!(circuitBreakerObj instanceof Map)) {
            errors.add(context + " 熔断器配置格式错误");
            return;
        }

        Map<String, Object> circuitBreaker = (Map<String, Object>) circuitBreakerObj;

        // 如果未启用，则无需验证
        if (circuitBreaker.containsKey("enabled")
                && (Boolean.FALSE.equals(circuitBreaker.get("enabled"))
                    || "false".equalsIgnoreCase(circuitBreaker.get("enabled").toString()))) {
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
     * 验证基础配置
     *
     * @param config 配置Map
     * @param errors 错误列表
     * @param warnings 警告列表
     */
    @SuppressWarnings("unchecked")
    public void validateBasicConfiguration(final Map<String, Object> config,
                                           final List<String> errors,
                                           final List<String> warnings) {
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

    /**
     * 检查限流算法是否合法
     *
     * @param algorithm 算法名称
     * @return 算法是否合法
     */
    public boolean isValidRateLimitAlgorithm(final String algorithm) {
        if (algorithm == null) {
            return false;
        }
        String normalizedAlgorithm = algorithm.toLowerCase(java.util.Locale.ROOT);
        return "token-bucket".equals(normalizedAlgorithm)
                || "leaky-bucket".equals(normalizedAlgorithm)
                || "sliding-window".equals(normalizedAlgorithm)
                || "warm-up".equals(normalizedAlgorithm);
    }

    /**
     * 检查限流作用域是否合法
     *
     * @param scope 作用域
     * @return 作用域是否合法
     */
    public boolean isValidRateLimitScope(final String scope) {
        if (scope == null) {
            return false;
        }
        String normalizedScope = scope.toLowerCase(java.util.Locale.ROOT);
        return "service".equals(normalizedScope)
                || "model".equals(normalizedScope)
                || "client-ip".equals(normalizedScope)
                || "instance".equals(normalizedScope);
    }

    /**
     * 检查负载均衡类型是否合法
     *
     * @param type 负载均衡类型
     * @return 类型是否合法
     */
    public boolean isValidLoadBalanceType(final String type) {
        if (type == null) {
            return false;
        }
        String normalizedType = type.toLowerCase(java.util.Locale.ROOT);
        return "random".equals(normalizedType)
                || "round-robin".equals(normalizedType)
                || "least-connections".equals(normalizedType)
                || "ip-hash".equals(normalizedType);
    }

    /**
     * 检查哈希算法是否合法
     *
     * @param algorithm 哈希算法
     * @return 算法是否合法
     */
    public boolean isValidHashAlgorithm(final String algorithm) {
        if (algorithm == null) {
            return false;
        }
        String normalizedAlgorithm = algorithm.toLowerCase(java.util.Locale.ROOT);
        return "md5".equals(normalizedAlgorithm)
                || "sha1".equals(normalizedAlgorithm)
                || "sha256".equals(normalizedAlgorithm);
    }

    /**
     * 将ModelRouterProperties.RateLimitConfig转换为RateLimitConfig
     *
     * @param config 限流配置
     * @return RateLimitConfig对象
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
}