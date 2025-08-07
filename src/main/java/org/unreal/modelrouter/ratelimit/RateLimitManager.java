package org.unreal.modelrouter.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.ConfigurationHelper;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.factory.ComponentFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增强的限流管理器
 * 支持服务级和实例级两种限流
 */
@Component
public class RateLimitManager {
    private static final Logger logger = LoggerFactory.getLogger(RateLimitManager.class);

    private final ComponentFactory componentFactory;
    private final ConfigurationHelper configHelper;
    private final ModelRouterProperties properties;

    // 全局限流器 - 新增
    private volatile RateLimiter globalLimiter;

    // 服务级限流器
    private final Map<ModelServiceRegistry.ServiceType, RateLimiter> serviceLimiters =
            new ConcurrentHashMap<>();

    // 实例级限流器 - 新增
    private final Map<String, RateLimiter> instanceLimiters = new ConcurrentHashMap<>();

    public RateLimitManager(ComponentFactory componentFactory,
                            ConfigurationHelper configHelper,
                            ModelRouterProperties properties) {
        this.componentFactory = componentFactory;
        this.configHelper = configHelper;
        this.properties = properties;
        initializeRateLimiters();
    }

    /**
     * 初始化所有服务的限流器（包含实例级）
     */
    private void initializeRateLimiters() {
        logger.info("Initializing rate limiters for configured services");

        if (properties.getServices() == null) {
            logger.info("No services configured, skipping rate limiter initialization");
            return;
        }

        ModelRouterProperties.RateLimitConfig globalRateLimit = properties.getRateLimit();
        if (globalRateLimit != null && Boolean.TRUE.equals(globalRateLimit.getEnabled())) {
            RateLimitConfig config = convertInstanceRateLimitConfig(globalRateLimit);
            globalLimiter = componentFactory.createRateLimiter(config);
            logger.info("Initialized global rate limiter with algorithm: {}", config.getAlgorithm());
        }

        int serviceRateLimitersCount = 0;
        int instanceRateLimitersCount = 0;

        for (Map.Entry<String, ModelRouterProperties.ServiceConfig> entry :
                properties.getServices().entrySet()) {
            String serviceKey = entry.getKey();
            ModelRouterProperties.ServiceConfig serviceConfig = entry.getValue();

            try {
                ModelServiceRegistry.ServiceType serviceType = configHelper.parseServiceType(serviceKey);
                if (serviceType == null) {
                    logger.warn("Unknown service type for key: {}, skipping", serviceKey);
                    continue;
                }

                // 初始化服务级限流器
                if (serviceConfig.getRateLimit() != null) {
                    RateLimitConfig config = configHelper.convertRateLimitConfig(serviceConfig.getRateLimit());
                    if (config != null && config.isEnabled()) {
                        RateLimiter limiter = componentFactory.createRateLimiter(config);
                        if (limiter != null) {
                            serviceLimiters.put(serviceType, limiter);
                            serviceRateLimitersCount++;
                            logger.debug("Initialized service rate limiter for: {} with algorithm: {}",
                                    serviceKey, config.getAlgorithm());
                        }
                    }
                }

                // 初始化实例级限流器 - 新增逻辑
                if (serviceConfig.getInstances() != null) {
                    for (ModelRouterProperties.ModelInstance instance : serviceConfig.getInstances()) {
                        if (instance.getRateLimit() != null &&
                                Boolean.TRUE.equals(instance.getRateLimit().getEnabled())) {

                            RateLimitConfig config = convertInstanceRateLimitConfig(instance.getRateLimit());
                            RateLimiter limiter = componentFactory.createRateLimiter(config);

                            if (limiter != null) {
                                String instanceKey = generateInstanceKey(serviceType, instance);
                                instanceLimiters.put(instanceKey, limiter);
                                instanceRateLimitersCount++;
                                logger.debug("Initialized instance rate limiter for: {} with algorithm: {}",
                                        instanceKey, config.getAlgorithm());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to initialize rate limiter for service: {}. Error: {}",
                        serviceKey, e.getMessage());
            }
        }

        logger.info("Rate limiter initialization completed. Service limiters: {}, Instance limiters: {}",
                serviceRateLimitersCount, instanceRateLimitersCount);
    }

    /**
     * 按优先级执行限流：实例 > 服务 > 全局
     */
    public boolean tryAcquireWithPriority(RateLimitContext context) {
        // 1. 实例级
        if (!tryAcquireInstance(context)) {
            logger.warn("Request denied by instance rate limiter");
            return false;
        }

        // 2. 服务级
        if (!tryAcquire(context)) {
            logger.warn("Request denied by service rate limiter");
            return false;
        }

        // 3. 全局级
        if (globalLimiter != null) {
            boolean allowed = globalLimiter.tryAcquire(context);
            if (!allowed) {
                logger.warn("Request denied by global rate limiter");
                return false;
            }
        }

        logger.debug("Request allowed by all levels of rate limiters");
        return true;
    }

    /**
     * 检查服务级限流（原有方法）
     */
    public boolean tryAcquire(RateLimitContext context) {
        if (context == null) {
            logger.warn("RateLimitContext is null, allowing request");
            return true;
        }

        RateLimiter limiter = serviceLimiters.get(context.getServiceType());
        if (limiter == null) {
            logger.debug("No service rate limiter configured for: {}, allowing request",
                    context.getServiceType());
            return true;
        }

        boolean allowed = limiter.tryAcquire(context);

        if (allowed) {
            logger.debug("Request allowed by service rate limiter for: {}, model: {}, client: {}",
                    context.getServiceType(), context.getModelName(), context.getClientIp());
        } else {
            logger.warn("Request denied by service rate limiter for: {}, model: {}, client: {}",
                    context.getServiceType(), context.getModelName(), context.getClientIp());
        }

        return allowed;
    }

    /**
     * 检查实例级限流 - 新增方法
     */
    public boolean tryAcquireInstance(RateLimitContext context) {
        if (context == null || !context.hasInstanceInfo()) {
            return true; // 没有实例信息，跳过实例级限流
        }

        String instanceKey = generateInstanceKey(context.getServiceType(),
                context.getInstanceId(),
                context.getInstanceUrl());
        RateLimiter limiter = instanceLimiters.get(instanceKey);

        if (limiter == null) {
            logger.debug("No instance rate limiter configured for: {}, allowing request", instanceKey);
            return true;
        }

        boolean allowed = limiter.tryAcquire(context);

        if (allowed) {
            logger.debug("Request allowed by instance rate limiter for: {}", instanceKey);
        } else {
            logger.warn("Request denied by instance rate limiter for: {}", instanceKey);
        }

        return allowed;
    }

    /**
     * 综合限流检查 - 新增方法
     * 同时检查服务级和实例级限流
     */
    public boolean tryAcquireBoth(RateLimitContext context) {
        // 先检查服务级限流
        if (!tryAcquire(context)) {
            return false;
        }

        // 再检查实例级限流
        return tryAcquireInstance(context);
    }

    /**
     * 设置实例级限流器 - 新增方法
     */
    public void setInstanceRateLimiter(ModelServiceRegistry.ServiceType serviceType,
                                       ModelRouterProperties.ModelInstance instance,
                                       ModelRouterProperties.RateLimitConfig config) {
        String instanceKey = generateInstanceKey(serviceType, instance);

        try {
            if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
                instanceLimiters.remove(instanceKey);
                logger.info("Removed instance rate limiter for: {}", instanceKey);
                return;
            }

            RateLimitConfig convertedConfig = convertInstanceRateLimitConfig(config);
            RateLimiter limiter = componentFactory.createRateLimiter(convertedConfig);

            if (limiter != null) {
                instanceLimiters.put(instanceKey, limiter);
                logger.info("Set instance rate limiter for: {} with algorithm: {}",
                        instanceKey, convertedConfig.getAlgorithm());
            }
        } catch (Exception e) {
            logger.error("Failed to set instance rate limiter for: {}. Error: {}", instanceKey, e.getMessage());
            throw new RuntimeException("Failed to set instance rate limiter", e);
        }
    }

    /**
     * 移除实例级限流器 - 新增方法
     */
    public void removeInstanceRateLimiter(ModelServiceRegistry.ServiceType serviceType,
                                          ModelRouterProperties.ModelInstance instance) {
        String instanceKey = generateInstanceKey(serviceType, instance);
        RateLimiter removed = instanceLimiters.remove(instanceKey);
        if (removed != null) {
            logger.info("Removed instance rate limiter for: {}", instanceKey);
        } else {
            logger.debug("No instance rate limiter found for: {}", instanceKey);
        }
    }

    /**
     * 初始化实例级限流器 - 新增方法（供外部调用）
     */
    public void initializeInstanceLimiters(Map<ModelServiceRegistry.ServiceType,
            List<ModelRouterProperties.ModelInstance>> allInstances) {
        logger.info("Re-initializing instance-level rate limiters");

        // 清除现有实例限流器
        instanceLimiters.clear();

        int initializedCount = 0;

        for (Map.Entry<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> entry
                : allInstances.entrySet()) {
            ModelServiceRegistry.ServiceType serviceType = entry.getKey();
            List<ModelRouterProperties.ModelInstance> instances = entry.getValue();

            for (ModelRouterProperties.ModelInstance instance : instances) {
                ModelRouterProperties.RateLimitConfig rateLimitConfig = instance.getRateLimit();
                if (rateLimitConfig != null && Boolean.TRUE.equals(rateLimitConfig.getEnabled())) {
                    RateLimitConfig config = convertInstanceRateLimitConfig(rateLimitConfig);
                    RateLimiter limiter = componentFactory.createRateLimiter(config);

                    if (limiter != null) {
                        String instanceKey = generateInstanceKey(serviceType, instance);
                        instanceLimiters.put(instanceKey, limiter);
                        initializedCount++;
                        logger.debug("Re-initialized rate limiter for instance: {} with algorithm: {}",
                                instanceKey, config.getAlgorithm());
                    }
                }
            }
        }

        logger.info("Instance rate limiter re-initialization completed. Total limiters: {}", initializedCount);
    }

    /**
     * 为特定服务设置限流器（原有方法）
     */
    public void setRateLimiter(ModelServiceRegistry.ServiceType serviceType, RateLimitConfig config) {
        try {
            if (config == null || !config.isEnabled()) {
                serviceLimiters.remove(serviceType);
                logger.info("Removed service rate limiter for: {}", serviceType);
                return;
            }

            RateLimiter limiter = componentFactory.createRateLimiter(config);
            if (limiter != null) {
                serviceLimiters.put(serviceType, limiter);
                logger.info("Set service rate limiter for: {} with algorithm: {}",
                        serviceType, config.getAlgorithm());
            } else {
                logger.warn("Failed to create service rate limiter for: {}", serviceType);
            }
        } catch (Exception e) {
            logger.error("Failed to set service rate limiter for: {}. Error: {}", serviceType, e.getMessage());
            throw new RuntimeException("Failed to set service rate limiter", e);
        }
    }

    /**
     * 移除服务的限流器（原有方法）
     */
    public void removeRateLimiter(ModelServiceRegistry.ServiceType serviceType) {
        RateLimiter removed = serviceLimiters.remove(serviceType);
        if (removed != null) {
            logger.info("Removed service rate limiter for: {}", serviceType);
        } else {
            logger.debug("No service rate limiter found for: {}", serviceType);
        }
    }

    public void updateGlobalRateLimiter(ModelRouterProperties.RateLimitConfig config) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            globalLimiter = null;
            logger.info("Removed global rate limiter");
            return;
        }

        RateLimitConfig converted = convertInstanceRateLimitConfig(config);
        globalLimiter = componentFactory.createRateLimiter(converted);
        logger.info("Updated global rate limiter with algorithm: {}", converted.getAlgorithm());
    }

    /**
     * 获取所有限流器的状态信息（增强版）
     */
    public Map<String, Object> getAllRateLimiterStatus() {
        Map<String, Object> allStatus = new java.util.HashMap<>();
        // 全局级限流状态
        if (globalLimiter != null) {
            RateLimitConfig config = globalLimiter.getConfig();
            allStatus.put("global", String.format("%s(capacity=%d,rate=%d,scope=%s)",
                    config.getAlgorithm(), config.getCapacity(), config.getRate(), config.getScope()));
        }
        // 服务级限流状态
        Map<ModelServiceRegistry.ServiceType, String> serviceStatus = new ConcurrentHashMap<>();
        serviceLimiters.forEach((serviceType, limiter) -> {
            RateLimitConfig config = limiter.getConfig();
            String statusInfo = String.format("%s(capacity=%d,rate=%d,scope=%s)",
                    config.getAlgorithm(), config.getCapacity(), config.getRate(), config.getScope());
            serviceStatus.put(serviceType, statusInfo);
        });
        allStatus.put("service", serviceStatus);

        // 实例级限流状态
        Map<String, String> instanceStatus = new ConcurrentHashMap<>();
        instanceLimiters.forEach((instanceKey, limiter) -> {
            RateLimitConfig config = limiter.getConfig();
            String statusInfo = String.format("%s(capacity=%d,rate=%d,scope=%s)",
                    config.getAlgorithm(), config.getCapacity(), config.getRate(), config.getScope());
            instanceStatus.put(instanceKey, statusInfo);
        });
        allStatus.put("instance", instanceStatus);

        // 统计信息
        Map<String, Integer> stats = new java.util.HashMap<>();
        stats.put("serviceRateLimiters", serviceLimiters.size());
        stats.put("instanceRateLimiters", instanceLimiters.size());
        allStatus.put("stats", stats);

        return allStatus;
    }

    /**
     * 获取服务级限流器状态（原有方法，保持兼容性）
     */
    public Map<ModelServiceRegistry.ServiceType, String> getRateLimiterStatus() {
        Map<ModelServiceRegistry.ServiceType, String> status = new ConcurrentHashMap<>();

        serviceLimiters.forEach((serviceType, limiter) -> {
            RateLimitConfig config = limiter.getConfig();
            String statusInfo = String.format("%s(capacity=%d,rate=%d,scope=%s)",
                    config.getAlgorithm(), config.getCapacity(), config.getRate(), config.getScope());
            status.put(serviceType, statusInfo);
        });

        return status;
    }

    /**
     * 获取实例级限流器状态 - 新增方法
     */
    public Map<String, String> getInstanceRateLimiterStatus() {
        Map<String, String> status = new ConcurrentHashMap<>();

        instanceLimiters.forEach((instanceKey, limiter) -> {
            RateLimitConfig config = limiter.getConfig();
            String statusInfo = String.format("%s(capacity=%d,rate=%d,scope=%s)",
                    config.getAlgorithm(), config.getCapacity(), config.getRate(), config.getScope());
            status.put(instanceKey, statusInfo);
        });

        return status;
    }

    /**
     * 检查是否有指定服务类型的限流器（原有方法）
     */
    public boolean hasRateLimiter(ModelServiceRegistry.ServiceType serviceType) {
        return serviceLimiters.containsKey(serviceType);
    }

    /**
     * 检查是否有指定实例的限流器 - 新增方法
     */
    public boolean hasInstanceRateLimiter(ModelServiceRegistry.ServiceType serviceType,
                                          ModelRouterProperties.ModelInstance instance) {
        String instanceKey = generateInstanceKey(serviceType, instance);
        return instanceLimiters.containsKey(instanceKey);
    }

    /**
     * 获取服务级限流器数量（原有方法）
     */
    public int getRateLimiterCount() {
        return serviceLimiters.size();
    }

    /**
     * 获取实例级限流器数量 - 新增方法
     */
    public int getInstanceRateLimiterCount() {
        return instanceLimiters.size();
    }

    /**
     * 获取指定服务的限流配置（原有方法）
     */
    public RateLimitConfig getRateLimitConfig(ModelServiceRegistry.ServiceType serviceType) {
        RateLimiter limiter = serviceLimiters.get(serviceType);
        return limiter != null ? limiter.getConfig() : null;
    }

    /**
     * 获取指定实例的限流配置 - 新增方法
     */
    public RateLimitConfig getInstanceRateLimitConfig(ModelServiceRegistry.ServiceType serviceType,
                                                      ModelRouterProperties.ModelInstance instance) {
        String instanceKey = generateInstanceKey(serviceType, instance);
        RateLimiter limiter = instanceLimiters.get(instanceKey);
        return limiter != null ? limiter.getConfig() : null;
    }

    /**
     * 动态更新单个服务的限流配置（原有方法）
     */
    public void updateServiceRateLimit(ModelServiceRegistry.ServiceType serviceType,
                                       RateLimitConfig config) {
        try {
            if (config == null || !config.isEnabled()) {
                removeRateLimiter(serviceType);
                return;
            }

            RateLimiter newLimiter = componentFactory.createRateLimiter(config);
            if (newLimiter != null) {
                RateLimiter oldLimiter = serviceLimiters.put(serviceType, newLimiter);
                logger.info("Updated service rate limiter for: {} from {} to {}",
                        serviceType,
                        oldLimiter != null ? oldLimiter.getClass().getSimpleName() : "null",
                        newLimiter.getClass().getSimpleName());
            }
        } catch (Exception e) {
            logger.error("Failed to update service rate limiter for: {}. Error: {}", serviceType, e.getMessage());
            throw new RuntimeException("Failed to update service rate limiter", e);
        }
    }

    /**
     * 动态更新单个实例的限流配置 - 新增方法
     */
    public void updateInstanceRateLimit(ModelServiceRegistry.ServiceType serviceType,
                                        ModelRouterProperties.ModelInstance instance,
                                        ModelRouterProperties.RateLimitConfig config) {
        setInstanceRateLimiter(serviceType, instance, config);
    }

    /**
     * 重新加载所有配置（增强版）
     */
    public void updateConfiguration() {
        logger.info("Updating rate limiter configuration");

        // 保存当前状态用于回滚
        Map<ModelServiceRegistry.ServiceType, RateLimiter> oldServiceLimiters = null;
        Map<String, RateLimiter> oldInstanceLimiters = null;

        synchronized (this) {
            // 重新加载全局限流器
            ModelRouterProperties.RateLimitConfig newGlobal = properties.getRateLimit();
            if (newGlobal != null && Boolean.TRUE.equals(newGlobal.getEnabled())) {
                try {
                    RateLimitConfig config = convertInstanceRateLimitConfig(newGlobal);
                    globalLimiter = componentFactory.createRateLimiter(config);
                    logger.info("Updated global rate limiter with algorithm: {}", config.getAlgorithm());
                } catch (Exception e) {
                    logger.error("Failed to convert or create global rate limiter, error: {}", e.getMessage());
                    throw new RuntimeException("Invalid global rate limiter configuration", e);
                }
            } else {
                globalLimiter = null;
                logger.info("Disabled global rate limiter");
            }

            // 准备回滚数据（延迟初始化）
            oldServiceLimiters = new ConcurrentHashMap<>(serviceLimiters);
            oldInstanceLimiters = new ConcurrentHashMap<>(instanceLimiters);

            try {
                serviceLimiters.clear();
                instanceLimiters.clear();
                initializeRateLimiters();

                logger.info("Rate limiter configuration updated successfully");
            } catch (Exception e) {
                logger.error("Failed to update rate limiter configuration, rolling back. Error: {}", e.getMessage());

                // 回滚到旧配置
                synchronized (this) {
                    serviceLimiters.clear();
                    instanceLimiters.clear();
                    serviceLimiters.putAll(oldServiceLimiters);
                    instanceLimiters.putAll(oldInstanceLimiters);
                }

                throw new RuntimeException("Failed to update configuration", e);
            }
        }
    }


    /**
     * 验证所有限流器配置（原有方法）
     */
    public boolean validateConfiguration() {
        if (properties.getServices() == null) {
            return true;
        }

        for (Map.Entry<String, ModelRouterProperties.ServiceConfig> entry :
                properties.getServices().entrySet()) {

            String serviceKey = entry.getKey();
            ModelRouterProperties.ServiceConfig serviceConfig = entry.getValue();

            // 验证服务级限流配置
            if (serviceConfig.getRateLimit() != null) {
                RateLimitConfig config = configHelper.convertRateLimitConfig(serviceConfig.getRateLimit());
                if (config != null && !componentFactory.validateRateLimitConfig(config)) {
                    logger.warn("Invalid service rate limit configuration for: {}", serviceKey);
                    return false;
                }
            }

            // 验证实例级限流配置
            if (serviceConfig.getInstances() != null) {
                for (ModelRouterProperties.ModelInstance instance : serviceConfig.getInstances()) {
                    if (instance.getRateLimit() != null) {
                        RateLimitConfig config = convertInstanceRateLimitConfig(instance.getRateLimit());
                        if (!componentFactory.validateRateLimitConfig(config)) {
                            logger.warn("Invalid instance rate limit configuration for: {}", instance.getInstanceId());
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    /**
     * 清理所有限流器（增强版）
     */
    public void clearAll() {
        int serviceCount = serviceLimiters.size();
        int instanceCount = instanceLimiters.size();

        serviceLimiters.clear();
        instanceLimiters.clear();

        logger.info("Cleared all rate limiters. Service limiters: {}, Instance limiters: {}",
                serviceCount, instanceCount);
    }

    /**
     * 转换实例级限流配置格式 - 私有辅助方法
     */
    private RateLimitConfig convertInstanceRateLimitConfig(ModelRouterProperties.RateLimitConfig source) {
        if (source == null) {
            return null;
        }

        RateLimitConfig target = new RateLimitConfig();
        target.setEnabled(Boolean.TRUE.equals(source.getEnabled()));
        target.setAlgorithm(source.getAlgorithm() != null ? source.getAlgorithm() : "token-bucket");
        target.setCapacity(source.getCapacity() != null ? source.getCapacity() : 100L);
        target.setRate(source.getRate() != null ? source.getRate() : 10L);
        target.setScope(source.getScope() != null ? source.getScope() : "instance");
        target.setKey(source.getKey());

        return target;
    }

    /**
     * 生成实例级限流键 - 私有辅助方法
     */
    private String generateInstanceKey(ModelServiceRegistry.ServiceType serviceType,
                                       ModelRouterProperties.ModelInstance instance) {
        return generateInstanceKey(serviceType, instance.getInstanceId(), instance.getBaseUrl());
    }

    private String generateInstanceKey(ModelServiceRegistry.ServiceType serviceType,
                                       String instanceId,
                                       String instanceUrl) {
        return serviceType.name() + ":" + instanceId;
    }

}