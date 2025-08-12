package org.unreal.modelrouter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.ratelimit.RateLimitConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 配置验证器
 * 提供各种配置参数的合法性检查功能
 */
@Component
public class ConfigurationValidator {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationValidator.class);

    // IP地址正则表达式
    private static final Pattern IP_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    // 域名正则表达式 - 修复 ReDoS 漏洞，使用原子组和限制长度
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)++[a-zA-Z]{2,6}$"
    );

    /**
     * 验证限流配置参数的合法性
     *
     * @param config 限流配置
     * @return 配置是否合法
     */
    public boolean validateRateLimitConfig(RateLimitConfig config) {
        if (config == null) {
            logger.warn("Rate limit config is null");
            return false;
        }

        // 检查算法是否合法
        if (config.getAlgorithm() == null || !isValidRateLimitAlgorithm(config.getAlgorithm())) {
            logger.warn("Invalid rate limit algorithm: {}", config.getAlgorithm());
            return false;
        }

        // 检查容量是否合法
        if (config.getCapacity() <= 0) {
            logger.warn("Invalid rate limit capacity: {}", config.getCapacity());
            return false;
        }

        // 检查速率是否合法
        if (config.getRate() <= 0) {
            logger.warn("Invalid rate limit rate: {}", config.getRate());
            return false;
        }

        // 检查作用域是否合法
        if (config.getScope() == null || !isValidRateLimitScope(config.getScope())) {
            logger.warn("Invalid rate limit scope: {}", config.getScope());
            return false;
        }

        // 对于预热算法，检查预热期是否合法
        if ("warm-up".equalsIgnoreCase(config.getAlgorithm())) {
            if (config.getWarmUpPeriod() <= 0) {
                logger.warn("Invalid warm up period for warm-up algorithm: {}", config.getWarmUpPeriod());
                return false;
            }
        }

        // 检查键值是否合法（如果指定了键）
        if (config.getKey() != null && config.getKey().trim().isEmpty()) {
            logger.warn("Invalid rate limit key: empty string");
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

        // 验证必需字段
        if (!rateLimit.containsKey("algorithm")) {
            errors.add(context + " 限流配置缺少algorithm字段");
            return;
        }

        if (!rateLimit.containsKey("capacity")) {
            errors.add(context + " 限流配置缺少capacity字段");
            return;
        }

        if (!rateLimit.containsKey("rate")) {
            errors.add(context + " 限流配置缺少rate字段");
            return;
        }

        // 验证字段值
        String algorithm = (String) rateLimit.get("algorithm");
        if (!isValidRateLimitAlgorithm(algorithm)) {
            errors.add(context + " 限流算法无效: " + algorithm);
        }

        try {
            long capacity = ((Number) rateLimit.get("capacity")).longValue();
            if (capacity <= 0) {
                errors.add(context + " 限流容量必须大于0");
            }
        } catch (Exception e) {
            errors.add(context + " 限流容量格式错误");
        }

        try {
            long rate = ((Number) rateLimit.get("rate")).longValue();
            if (rate <= 0) {
                errors.add(context + " 限流速率必须大于0");
            }
        } catch (Exception e) {
            errors.add(context + " 限流速率格式错误");
        }

        if (rateLimit.containsKey("scope")) {
            String scope = (String) rateLimit.get("scope");
            if (!isValidRateLimitScope(scope)) {
                errors.add(context + " 限流作用域无效: " + scope);
            }
        }

        // 对于预热算法，验证预热期
        if ("warm-up".equalsIgnoreCase(algorithm)) {
            if (!rateLimit.containsKey("warmUpPeriod")) {
                errors.add(context + " 预热算法必须配置warmUpPeriod字段");
            } else {
                try {
                    long warmUpPeriod = ((Number) rateLimit.get("warmUpPeriod")).longValue();
                    if (warmUpPeriod <= 0) {
                        errors.add(context + " 预热期必须大于0");
                    }
                } catch (Exception e) {
                    errors.add(context + " 预热期格式错误");
                }
            }
        }
    }

    /**
     * 验证URL格式
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /**
     * 验证实例配置
     */
    private void validateInstanceConfig(String serviceType,
                                        int instanceIndex,
                                        Map<String, Object> instance,
                                        Set<String> instanceIds,
                                        List<String> errors,
                                        List<String> warnings) {
        String prefix = "服务 " + serviceType + " 第 " + instanceIndex + " 个实例";

        // 验证必需字段
        if (!instance.containsKey("name") || instance.get("name") == null) {
            errors.add(prefix + " 缺少name字段");
        } else {
            String name = (String) instance.get("name");
            if (name.trim().isEmpty()) {
                errors.add(prefix + " name字段不能为空");
            }
        }

        if (!instance.containsKey("baseUrl") || instance.get("baseUrl") == null) {
            errors.add(prefix + " 缺少baseUrl字段");
        } else {
            String baseUrl = (String) instance.get("baseUrl");
            if (!isValidUrl(baseUrl)) {
                errors.add(prefix + " baseUrl格式错误: " + baseUrl);
            }
        }

        // 验证权重
        if (instance.containsKey("weight")) {
            try {
                int weight = ((Number) instance.get("weight")).intValue();
                if (weight <= 0) {
                    errors.add(prefix + " 权重必须大于0");
                }
            } catch (Exception e) {
                errors.add(prefix + " 权重格式错误");
            }
        }

        // 验证实例ID唯一性
        if (instance.containsKey("name") && instance.get("name") != null &&
                instance.containsKey("baseUrl") && instance.get("baseUrl") != null) {
            String name = (String) instance.get("name");
            String baseUrl = (String) instance.get("baseUrl");
            String instanceId = name + "@" + baseUrl;
            if (instanceIds.contains(instanceId)) {
                errors.add(prefix + " 实例ID重复: " + instanceId);
            } else {
                instanceIds.add(instanceId);
            }
        }

        // 验证实例级限流配置
        if (instance.containsKey("rateLimit")) {
            validateRateLimitConfig(instance.get("rateLimit"), prefix, errors, warnings);
        }
    }

    private void validateSingleServiceConfig(String serviceType,
                                             Map<String, Object> serviceConfig,
                                             List<String> errors,
                                             List<String> warnings) {
        // 验证实例配置
        if (!serviceConfig.containsKey("instances")) {
            warnings.add("服务 " + serviceType + " 未配置实例");
        } else {
            Object instancesObj = serviceConfig.get("instances");
            if (!(instancesObj instanceof List)) {
                errors.add("服务 " + serviceType + " 的实例配置格式错误");
            } else {
                List<Object> instances = (List<Object>) instancesObj;
                if (instances.isEmpty()) {
                    warnings.add("服务 " + serviceType + " 实例列表为空");
                } else {
                    // 验证每个实例
                    Set<String> instanceIds = new HashSet<>();
                    for (int i = 0; i < instances.size(); i++) {
                        Object instanceObj = instances.get(i);
                        if (!(instanceObj instanceof Map)) {
                            errors.add("服务 " + serviceType + " 第 " + (i + 1) + " 个实例配置格式错误");
                            continue;
                        }

                        Map<String, Object> instance = (Map<String, Object>) instanceObj;
                        validateInstanceConfig(serviceType, i + 1, instance, instanceIds, errors, warnings);
                    }
                }
            }
        }

        // 验证负载均衡配置
        if (serviceConfig.containsKey("loadBalance")) {
            validateLoadBalanceConfig(serviceConfig.get("loadBalance"),
                    "服务 " + serviceType, errors, warnings);
        }

        // 验证服务级限流配置
        if (serviceConfig.containsKey("rateLimit")) {
            validateRateLimitConfig(serviceConfig.get("rateLimit"),
                    "服务 " + serviceType, errors, warnings);
        }
    }

    /**
     * 验证基本配置
     */
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
            ModelServiceRegistry.ServiceType.valueOf(serviceType.toLowerCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void validateServiceConfig(String serviceType, Map<String, Object> serviceConfig, List<String> errors, List<String> warnings) {
        if (serviceType == null || serviceType.trim().isEmpty()) {
            errors.add("服务类型不能为空");
            return;
        }

        if (!isValidServiceType(serviceType)) {
            errors.add("不支持的服务类型: " + serviceType);
            return;
        }

        if (serviceConfig == null) {
            errors.add("服务 " + serviceType + " 配置不能为空");
            return;
        }
        validateSingleServiceConfig(serviceType, serviceConfig, errors, warnings);
    }
}
