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
 * 统一限流管理器
 * 支持：实例级 > 服务级 > 全局级 三层限流
 * 使用 ComponentFactory 创建限流器，彻底消除递归问题
 */
@Component
public class RateLimitManager {
    private static final Logger logger = LoggerFactory.getLogger(RateLimitManager.class);

    private final ComponentFactory componentFactory;
    private final ConfigurationHelper configHelper;
    private final ModelRouterProperties properties;

    /* ---------------- 三级限流器 ---------------- */
    private volatile RateLimiter globalLimiter;                     // 全局
    private final Map<ModelServiceRegistry.ServiceType, RateLimiter> serviceLimiters =
            new EnumMap<>(ModelServiceRegistry.ServiceType.class);  // 服务级
    private final Map<String, RateLimiter> instanceLimiters =
            new ConcurrentHashMap<>();                               // 实例级

    public RateLimitManager(ComponentFactory componentFactory,
                            ConfigurationHelper configHelper,
                            ModelRouterProperties properties) {
        this.componentFactory = componentFactory;
        this.configHelper = configHelper;
        this.properties = properties;
        initializeRateLimiters();
    }

    /* ===================== 初始化 ===================== */

    private void initializeRateLimiters() {
        logger.info("Initializing rate limiters");

        if (properties.getServices() == null) {
            logger.info("No services configured, skipping");
            return;
        }

        /* 全局 */
        ModelRouterProperties.RateLimitConfig globalCfg = properties.getRateLimit();
        if (globalCfg != null && Boolean.TRUE.equals(globalCfg.getEnabled())) {
            globalLimiter = componentFactory.createScopedRateLimiter(convert(globalCfg));
            logger.info("Global limiter initialized");
        }

        int svcCnt = 0, instCnt = 0;

        /* 服务级 & 实例级 */
        for (var entry : properties.getServices().entrySet()) {
            String serviceKey = entry.getKey();
            ModelRouterProperties.ServiceConfig svcCfg = entry.getValue();

            ModelServiceRegistry.ServiceType type = configHelper.parseServiceType(serviceKey);
            if (type == null) {
                logger.warn("Unknown service type: {}", serviceKey);
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

        logger.info("Initialization complete. Service: {}, Instance: {}", svcCnt, instCnt);
    }

    /* ===================== 优先级限流 ===================== */

    /**
     * 按优先级执行限流：实例 > 服务 > 全局
     */
    public boolean tryAcquireWithPriority(RateLimitContext context) {
        /* 1. 实例级 */
        if (!tryAcquireInstance(context)) {
            logger.warn("Instance limit denied");
            return false;
        }

        /* 2. 服务级 */
        if (!tryAcquire(context)) {
            logger.warn("Service limit denied");
            return false;
        }

        /* 3. 全局级 */
        if (globalLimiter != null && !globalLimiter.tryAcquire(context)) {
            logger.warn("Global limit denied");
            return false;
        }

        logger.debug("All levels passed");
        return true;
    }

    /* ---------------- 单一级别限流 ---------------- */

    public boolean tryAcquire(RateLimitContext context) {
        if (context == null) return true;
        RateLimiter limiter = serviceLimiters.get(context.getServiceType());
        return limiter == null || limiter.tryAcquire(context);
    }

    public boolean tryAcquireInstance(RateLimitContext context) {
        if (context == null || !context.hasInstanceInfo()) return true;
        String key = generateInstanceKey(context.getServiceType(), context.getInstanceId(), context.getInstanceUrl());
        RateLimiter limiter = instanceLimiters.get(key);
        return limiter == null || limiter.tryAcquire(context);
    }

    /* ===================== 动态管理 ===================== */

    public void updateGlobalRateLimiter(ModelRouterProperties.RateLimitConfig cfg) {
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) {
            globalLimiter = null;
            logger.info("Global limiter removed");
            return;
        }
        globalLimiter = componentFactory.createScopedRateLimiter(convert(cfg));
        logger.info("Global limiter updated");
    }

    public void setRateLimiter(ModelServiceRegistry.ServiceType type, RateLimitConfig cfg) {
        if (cfg == null || !cfg.isEnabled()) {
            serviceLimiters.remove(type);
            logger.info("Service limiter removed for {}", type);
            return;
        }
        serviceLimiters.put(type, componentFactory.createScopedRateLimiter(cfg));
        logger.info("Service limiter set for {}", type);
    }

    public void setInstanceRateLimiter(ModelServiceRegistry.ServiceType type,
                                       ModelRouterProperties.ModelInstance instance,
                                       ModelRouterProperties.RateLimitConfig cfg) {
        String key = generateInstanceKey(type, instance);
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) {
            instanceLimiters.remove(key);
            logger.info("Instance limiter removed for {}", key);
            return;
        }
        instanceLimiters.put(key, componentFactory.createScopedRateLimiter(convert(cfg)));
        logger.info("Instance limiter set for {}", key);
    }

    public void removeRateLimiter(ModelServiceRegistry.ServiceType type) {
        serviceLimiters.remove(type);
        logger.info("Service limiter removed for {}", type);
    }

    public void removeInstanceRateLimiter(ModelServiceRegistry.ServiceType type,
                                          ModelRouterProperties.ModelInstance instance) {
        String key = generateInstanceKey(type, instance);
        instanceLimiters.remove(key);
        logger.info("Instance limiter removed for {}", key);
    }

    /* ===================== 配置重载 ===================== */

    public synchronized void updateConfiguration() {
        logger.info("Reloading rate limiters");
        var oldSvc = new EnumMap<>(serviceLimiters);
        var oldInst = new ConcurrentHashMap<>(instanceLimiters);

        try {
            serviceLimiters.clear();
            instanceLimiters.clear();
            initializeRateLimiters();
            logger.info("Reloaded successfully");
        } catch (Exception e) {
            serviceLimiters.putAll(oldSvc);
            instanceLimiters.putAll(oldInst);
            logger.error("Reload failed, rollback. Error: {}", e.getMessage());
            throw new RuntimeException("Rate limit reload failed", e);
        }
    }

    /* ===================== 状态查询 ===================== */

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

    private String describe(RateLimitConfig cfg) {
        return String.format("%s(capacity=%d,rate=%d,scope=%s)",
                cfg.getAlgorithm(), cfg.getCapacity(), cfg.getRate(), cfg.getScope());
    }

    /* ===================== 工具方法 ===================== */

    private RateLimitConfig convert(ModelRouterProperties.RateLimitConfig src) {
        if (src == null) return null;
        RateLimitConfig dst = new RateLimitConfig();
        dst.setEnabled(Boolean.TRUE.equals(src.getEnabled()));
        dst.setAlgorithm(src.getAlgorithm() != null ? src.getAlgorithm() : "token-bucket");
        dst.setCapacity(src.getCapacity() != null ? src.getCapacity() : 100L);
        dst.setRate(src.getRate() != null ? src.getRate() : 10L);
        dst.setScope(src.getScope() != null ? src.getScope() : "service");
        dst.setKey(src.getKey());
        return dst;
    }

    private String generateInstanceKey(ModelServiceRegistry.ServiceType type,
                                       ModelRouterProperties.ModelInstance inst) {
        return generateInstanceKey(type, inst.getInstanceId(), inst.getBaseUrl());
    }

    private String generateInstanceKey(ModelServiceRegistry.ServiceType type,
                                       String id, String url) {
        return type.name() + ":" + id;
    }
}