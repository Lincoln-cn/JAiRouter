package org.unreal.modelrouter.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.ConfigurationHelper;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.factory.ComponentFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一限流管理器
 * 支持：实例级 > 服务级 > 全局级 三层限流
 * 使用 ComponentFactory 创建限流器，彻底消除递归问题
 */
@Component
public class RateLimitManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitManager.class);

    private final ComponentFactory componentFactory;
    private final ConfigurationHelper configHelper;
    private final ModelRouterProperties properties;

    /* ---------------- 三级限流器 ---------------- */
    private volatile RateLimiter globalLimiter;                     // 全局
    private final Map<ModelServiceRegistry.ServiceType, RateLimiter> serviceLimiters =
            new EnumMap<>(ModelServiceRegistry.ServiceType.class);  // 服务级
    private final Map<String, RateLimiter> instanceLimiters =
            new ConcurrentHashMap<>();                               // 实例级
    // 客户端IP限流器：serviceType -> (clientIp -> RateLimiter)
    private final Map<ModelServiceRegistry.ServiceType, Map<String, RateLimiter>> clientIpLimiters =
            new ConcurrentHashMap<>();

    public RateLimitManager(final ComponentFactory componentFactory,
                            final ConfigurationHelper configHelper,
                            final ModelRouterProperties properties) {
        this.componentFactory = componentFactory;
        this.configHelper = configHelper;
        this.properties = properties;
        initializeRateLimiters();
    }

    /* ===================== 初始化 ===================== */

    private void initializeRateLimiters() {
        LOGGER.info("Initializing rate limiters");

        if (properties.getServices() == null) {
            LOGGER.info("No services configured, skipping");
            return;
        }

        /* 全局 */
        ModelRouterProperties.RateLimitConfig globalCfg = properties.getRateLimit();
        if (globalCfg != null && Boolean.TRUE.equals(globalCfg.getEnabled())) {
            globalLimiter = componentFactory.createScopedRateLimiter(convert(globalCfg));
            LOGGER.info("Global limiter initialized");
        }

        int svcCnt = 0, instCnt = 0;

        /* 服务级 & 实例级 */
        for (var entry : properties.getServices().entrySet()) {
            String serviceKey = entry.getKey();
            ModelRouterProperties.ServiceConfig svcCfg = entry.getValue();

            ModelServiceRegistry.ServiceType type = configHelper.parseServiceType(serviceKey);
            if (type == null) {
                LOGGER.warn("Unknown service type: {}", serviceKey);
                continue;
            }

            /* 服务级 */
            if (svcCfg.getRateLimit() != null && Boolean.TRUE.equals(svcCfg.getRateLimit().getEnabled())) {
                serviceLimiters.put(type, componentFactory.createScopedRateLimiter(
                        configHelper.convertRateLimitConfig(svcCfg.getRateLimit())));
                svcCnt++;
            }

            /* 实例级 */
            if (svcCfg.getInstances() != null) {
                for (var inst : svcCfg.getInstances()) {
                    if (inst.getRateLimit() != null && Boolean.TRUE.equals(inst.getRateLimit().getEnabled())) {
                        instanceLimiters.put(
                                generateInstanceKey(type, inst),
                                componentFactory.createScopedRateLimiter(
                                        convert(inst.getRateLimit())));
                        instCnt++;
                    }
                }
            }
        }

        LOGGER.info("Initialization complete. Service: {}, Instance: {}", svcCnt, instCnt);
    }

    /* ===================== 优先级限流 ===================== */

    /**
     * 按优先级执行限流：实例 > 服务 > 全局 > 客户端IP
     * @param context 限流上下文
     * @return 是否通过限流检查
     */
    public boolean tryAcquireWithPriority(final RateLimitContext context) {
        /* 1. 实例级 */
        if (!tryAcquireInstance(context)) {
            LOGGER.warn("Instance limit denied");
            return false;
        }

        /* 2. 服务级 */
        if (!tryAcquire(context)) {
            LOGGER.warn("Service limit denied");
            return false;
        }

        /* 3. 全局级 */
        if (globalLimiter != null && !globalLimiter.tryAcquire(context)) {
            LOGGER.warn("Global limit denied");
            return false;
        }

        /* 4. 客户端IP级 */
        if (!tryAcquireClientIp(context)) {
            LOGGER.warn("Client IP limit denied for IP: {}", context.getClientIp());
            return false;
        }

        LOGGER.debug("All levels passed");
        return true;
    }

    /* ---------------- 单一级别限流 ---------------- */

    /**
     * 尝试获取服务级限流令牌
     * @param context 限流上下文
     * @return 是否获取成功
     */
    public boolean tryAcquire(final RateLimitContext context) {
        if (context == null) {
            return true;
        }
        RateLimiter limiter = serviceLimiters.get(context.getServiceType());
        return limiter == null || limiter.tryAcquire(context);
    }

    /**
     * 尝试获取实例级限流令牌
     * @param context 限流上下文
     * @return 是否获取成功
     */
    public boolean tryAcquireInstance(final RateLimitContext context) {
        if (context == null || !context.hasInstanceInfo()) {
            return true;
        }
        String key = generateInstanceKey(context.getServiceType(), context.getInstanceId(), context.getInstanceUrl());
        RateLimiter limiter = instanceLimiters.get(key);
        return limiter == null || limiter.tryAcquire(context);
    }

    /**
     * 客户端IP限流检查
     * @param context 限流上下文
     * @return 是否通过限流检查
     */
    public boolean tryAcquireClientIp(final RateLimitContext context) {
        if (context == null || context.getClientIp() == null) {
            return true;
        }

        ModelServiceRegistry.ServiceType serviceType = context.getServiceType();
        String clientIp = context.getClientIp();

        // 检查服务级配置是否启用了客户端IP限流
        ModelRouterProperties.ServiceConfig serviceConfig = getServiceConfig(serviceType);
        if (serviceConfig != null 
                && serviceConfig.getRateLimit() != null
                && Boolean.TRUE.equals(serviceConfig.getRateLimit().getClientIpEnable())) {
            // 获取或创建针对该服务类型和客户端IP的限流器
            Map<String, RateLimiter> ipLimiters = clientIpLimiters.computeIfAbsent(
                    serviceType, k -> new ConcurrentHashMap<>());

            RateLimiter ipLimiter = ipLimiters.computeIfAbsent(clientIp, k -> {
                RateLimitConfig config = configHelper.convertRateLimitConfig(serviceConfig.getRateLimit());
                return componentFactory.createScopedRateLimiter(config);
            });

            return ipLimiter.tryAcquire(context);
        }

        // 检查全局配置是否启用了客户端IP限流
        ModelRouterProperties.RateLimitConfig globalRateLimit = properties.getRateLimit();
        if (globalRateLimit != null && Boolean.TRUE.equals(globalRateLimit.getClientIpEnable())) {
            // 获取或创建针对该服务类型和客户端IP的限流器
            Map<String, RateLimiter> ipLimiters = clientIpLimiters.computeIfAbsent(
                    serviceType, k -> new ConcurrentHashMap<>());

            RateLimiter ipLimiter = ipLimiters.computeIfAbsent(clientIp, k -> {
                RateLimitConfig config = configHelper.convertRateLimitConfig(globalRateLimit);
                return componentFactory.createScopedRateLimiter(config);
            });

            return ipLimiter.tryAcquire(context);
        }

        return true; // 未启用客户端IP限流
    }

    private ModelRouterProperties.ServiceConfig getServiceConfig(final ModelServiceRegistry.ServiceType serviceType) {
        if (properties.getServices() == null) {
            return null;
        }

        String serviceName = serviceType.name().toLowerCase();
        for (Map.Entry<String, ModelRouterProperties.ServiceConfig> entry : properties.getServices().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(serviceName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /* ===================== 动态管理 ===================== */

    /**
     * 更新全局限流器配置
     * @param cfg 限流配置
     */
    public void updateGlobalRateLimiter(final ModelRouterProperties.RateLimitConfig cfg) {
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) {
            globalLimiter = null;
            LOGGER.info("Global limiter removed");
            return;
        }
        globalLimiter = componentFactory.createScopedRateLimiter(convert(cfg));
        LOGGER.info("Global limiter updated");
    }

    /**
     * 设置服务级限流器
     * @param type 服务类型
     * @param cfg 限流配置
     */
    public void setRateLimiter(final ModelServiceRegistry.ServiceType type, final RateLimitConfig cfg) {
        if (cfg == null || !cfg.isEnabled()) {
            serviceLimiters.remove(type);
            LOGGER.info("Service limiter removed for {}", type);
            return;
        }
        serviceLimiters.put(type, componentFactory.createScopedRateLimiter(cfg));
        LOGGER.info("Service limiter set for {}", type);
    }

    /**
     * 设置实例级限流器
     * @param type 服务类型
     * @param instance 模型实例
     * @param cfg 限流配置
     */
    public void setInstanceRateLimiter(final ModelServiceRegistry.ServiceType type,
                                       final ModelRouterProperties.ModelInstance instance,
                                       final ModelRouterProperties.RateLimitConfig cfg) {
        String key = generateInstanceKey(type, instance);
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) {
            instanceLimiters.remove(key);
            LOGGER.info("Instance limiter removed for {}", key);
            return;
        }
        instanceLimiters.put(key, componentFactory.createScopedRateLimiter(convert(cfg)));
        LOGGER.info("Instance limiter set for {}", key);
    }

    /**
     * 移除服务级限流器
     * @param type 服务类型
     */
    public void removeRateLimiter(final ModelServiceRegistry.ServiceType type) {
        serviceLimiters.remove(type);
        LOGGER.info("Service limiter removed for {}", type);
    }

    /**
     * 移除实例级限流器
     * @param type 服务类型
     * @param instance 模型实例
     */
    public void removeInstanceRateLimiter(final ModelServiceRegistry.ServiceType type,
                                          final ModelRouterProperties.ModelInstance instance) {
        String key = generateInstanceKey(type, instance);
        instanceLimiters.remove(key);
        LOGGER.info("Instance limiter removed for {}", key);
    }

    /* ===================== 配置重载 ===================== */

    /**
     * 更新配置
     */
    public synchronized void updateConfiguration() {
        LOGGER.info("Reloading rate limiters");
        var oldSvc = new EnumMap<>(serviceLimiters);
        var oldInst = new ConcurrentHashMap<>(instanceLimiters);
        var oldClientIp = new ConcurrentHashMap<>(clientIpLimiters);

        try {
            serviceLimiters.clear();
            instanceLimiters.clear();
            clientIpLimiters.clear();
            initializeRateLimiters();
            LOGGER.info("Reloaded successfully");
        } catch (Exception e) {
            serviceLimiters.putAll(oldSvc);
            instanceLimiters.putAll(oldInst);
            clientIpLimiters.putAll(oldClientIp);
            LOGGER.error("Reload failed, rollback. Error: {}", e.getMessage());
            throw new RuntimeException("Rate limit reload failed", e);
        }
    }

    /* ===================== 状态查询 ===================== */

    /**
     * 获取所有限流器状态
     * @return 状态信息Map
     */
    public Map<String, Object> getAllRateLimiterStatus() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        if (globalLimiter != null) {
            status.put("global", describe(globalLimiter.getConfig()));
        }
        Map<ModelServiceRegistry.ServiceType, String> svc = new EnumMap<>(ModelServiceRegistry.ServiceType.class);
        serviceLimiters.forEach((t, l) -> svc.put(t, describe(l.getConfig())));
        status.put("service", svc);

        Map<String, String> inst = new ConcurrentHashMap<>();
        instanceLimiters.forEach((k, l) -> inst.put(k, describe(l.getConfig())));
        status.put("instance", inst);

        status.put("stats", Map.of(
                "serviceRateLimiters", serviceLimiters.size(),
                "instanceRateLimiters", instanceLimiters.size()
        ));
        return status;
    }

    private String describe(final RateLimitConfig cfg) {
        return String.format("%s(capacity=%d,rate=%d,scope=%s)",
                cfg.getAlgorithm(), cfg.getCapacity(), cfg.getRate(), cfg.getScope());
    }

    /* ===================== 工具方法 ===================== */

    private RateLimitConfig convert(final ModelRouterProperties.RateLimitConfig src) {
        if (src == null) {
            return null;
        }
        RateLimitConfig dst = new RateLimitConfig();
        dst.setEnabled(Boolean.TRUE.equals(src.getEnabled()));
        dst.setAlgorithm(src.getAlgorithm() != null ? src.getAlgorithm() : "token-bucket");
        dst.setCapacity(src.getCapacity() != null ? src.getCapacity() : 100L);
        dst.setRate(src.getRate() != null ? src.getRate() : 10L);
        dst.setScope(src.getScope() != null ? src.getScope() : "service");
        dst.setKey(src.getKey());
        return dst;
    }

    private String generateInstanceKey(final ModelServiceRegistry.ServiceType type,
                                       final ModelRouterProperties.ModelInstance inst) {
        return generateInstanceKey(type, inst.getInstanceId(), inst.getBaseUrl());
    }

    private String generateInstanceKey(final ModelServiceRegistry.ServiceType type,
                                       final String id, final String url) {
        return type.name() + ":" + id;
    }

    /**
     * 获取默认限流配置
     * @return 默认限流配置
     */
    public ModelRouterProperties.RateLimitConfig getDefaultRateLimitConfig() {
        return new  ModelRouterProperties.RateLimitConfig();
    }
}