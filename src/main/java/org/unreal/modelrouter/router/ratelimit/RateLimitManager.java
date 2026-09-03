package org.unreal.modelrouter.router.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.router.factory.ComponentFactory;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一限流管理器
 * 支持：实例级 > 服务级 > 全局级 三层限流
 * 使用 ComponentFactory 创建限流器，彻底消除递归问题
 *
 * v2.7.10 优化：使用 Caffeine 缓存替代 ConcurrentHashMap 存储客户端 IP 限流器
 */
@Component
public class RateLimitManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitManager.class);

    private final ComponentFactory componentFactory;
    private final ServiceTypeResolver serviceTypeResolver;
    private final ConfigConverterHelper configConverterHelper;
    private final ModelRouterProperties properties;

    /* ---------------- 三级限流器 ---------------- */
    private volatile RateLimiter globalLimiter;                     // 全局
    private final Map<ModelServiceRegistry.ServiceType, RateLimiter> serviceLimiters =
            new EnumMap<>(ModelServiceRegistry.ServiceType.class);  // 服务级
    private final Map<String, RateLimiter> instanceLimiters =
            new ConcurrentHashMap<>();                               // 实例级
    private final Map<String, RateLimiter> ruleLimiters =
            new ConcurrentHashMap<>();                               // v2.8.8: 规则级(按 ruleId keyed)

    // v2.7.10: 使用 Caffeine 缓存替代 ConcurrentHashMap，解决内存泄漏风险
    private final ClientIpRateLimiterCache clientIpRateLimiterCache = new ClientIpRateLimiterCache();

    public RateLimitManager(final ComponentFactory componentFactory,
                            final ServiceTypeResolver serviceTypeResolver,
                            final ConfigConverterHelper configConverterHelper,
                            final ModelRouterProperties properties) {
        this.componentFactory = componentFactory;
        this.serviceTypeResolver = serviceTypeResolver;
        this.configConverterHelper = configConverterHelper;
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

            ModelServiceRegistry.ServiceType type = serviceTypeResolver.parseServiceType(serviceKey);
            if (type == null) {
                LOGGER.warn("Unknown service type: {}", serviceKey);
                continue;
            }

            /* 服务级 */
            if (svcCfg.getRateLimit() != null && Boolean.TRUE.equals(svcCfg.getRateLimit().getEnabled())) {
                serviceLimiters.put(type, componentFactory.createScopedRateLimiter(
                        configConverterHelper.convertRateLimitConfig(svcCfg.getRateLimit())));
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
     * v2.9.10: handler 层服务级限流预扣（缓存命中提前短路场景）.
     *
     * <p>内部仅按 serviceType 取服务级限流桶（与 tryAcquire 语义一致），
     * modelName 仅用于指标 tag。handler 在缓存读前调用此方法实现恰一次预扣，
     * selectInstance 内部通过 {@link ServiceRateLimitHolder} 跳过重复扣减。
     *
     * @param serviceType 服务类型
     * @param clientIp 客户端 IP
     * @param modelName 模型名称（仅指标 tag）
     * @return true=通过限流检查；false=超限
     */
    public boolean tryAcquireService(final ModelServiceRegistry.ServiceType serviceType,
                                     final String clientIp,
                                     final String modelName) {
        return tryAcquire(new RateLimitContext(serviceType, modelName, clientIp, 1, null, null));
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
     * v2.8.8: 尝试获取规则级限流令牌(按 ruleId keyed,限流器按规则动作参数惰性创建)
     * 配置无效/禁用时不限流;删除规则须调 removeRuleRateLimiter 防泄漏
     * @param ruleId 规则 ID
     * @param cfg 规则限流配置
     * @param context 限流上下文
     * @return 是否通过限流检查
     */
    public boolean tryAcquireRule(final String ruleId, final RateLimitConfig cfg, final RateLimitContext context) {
        if (ruleId == null || cfg == null || !cfg.isEnabled() || !cfg.isValid()) {
            return true;
        }
        RateLimiter limiter = ruleLimiters.computeIfAbsent(ruleId,
                k -> componentFactory.createScopedRateLimiter(cfg));
        return limiter.tryAcquire(context);
    }

    /**
     * v2.8.8: 移除规则级限流器(规则删除/动作变更时调用,防泄漏)
     * @param ruleId 规则 ID
     */
    public void removeRuleRateLimiter(final String ruleId) {
        if (ruleId == null) {
            return;
        }
        ruleLimiters.remove(ruleId);
        LOGGER.info("Rule limiter removed for {}", ruleId);
    }

    /**
     * 客户端IP限流检查
     *
     * v2.7.10 优化：使用 Caffeine 缓存，自动过期清理，解决内存泄漏风险
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
            // v2.7.10: 使用 Caffeine 缓存获取限流器
            RateLimiter ipLimiter = clientIpRateLimiterCache.get(serviceType, clientIp, () -> {
                RateLimitConfig config = configConverterHelper.convertRateLimitConfig(serviceConfig.getRateLimit());
                return componentFactory.createScopedRateLimiter(config);
            });
            return ipLimiter != null && ipLimiter.tryAcquire(context);
        }

        // 检查全局配置是否启用了客户端IP限流
        ModelRouterProperties.RateLimitConfig globalRateLimit = properties.getRateLimit();
        if (globalRateLimit != null && Boolean.TRUE.equals(globalRateLimit.getClientIpEnable())) {
            // v2.7.10: 使用 Caffeine 缓存获取限流器
            RateLimiter ipLimiter = clientIpRateLimiterCache.get(serviceType, clientIp, () -> {
                RateLimitConfig config = configConverterHelper.convertRateLimitConfig(globalRateLimit);
                return componentFactory.createScopedRateLimiter(config);
            });
            return ipLimiter != null && ipLimiter.tryAcquire(context);
        }

        return true; // 未启用客户端IP限流
    }

    /**
     * v2.7.10: 获取客户端 IP 限流器缓存统计信息
     */
    public ClientIpRateLimiterCache.CacheStats getClientIpCacheStats() {
        return clientIpRateLimiterCache.getStats();
    }

    /**
     * v2.7.10: 清理不活跃的客户端 IP 限流器
     * Caffeine 缓存会自动过期，此方法为兼容性保留
     */
    public void cleanupInactiveClientIpLimiters() {
        // Caffeine cache auto-expires entries, no manual cleanup needed
        ClientIpRateLimiterCache.CacheStats stats = clientIpRateLimiterCache.getStats();
        LOGGER.debug("Client IP limiter cache stats: size={}, evictions={}", 
                stats.getSize(), stats.getEvictionCount());
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
        // v2.7.10: clientIpRateLimiterCache has built-in cleanup, no need to backup/restore

        try {
            serviceLimiters.clear();
            instanceLimiters.clear();
            // v2.7.10: Caffeine cache auto-expires, no explicit clear needed
            initializeRateLimiters();
            LOGGER.info("Reloaded successfully");
        } catch (Exception e) {
            serviceLimiters.putAll(oldSvc);
            instanceLimiters.putAll(oldInst);
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

        Map<String, String> rules = new ConcurrentHashMap<>();
        ruleLimiters.forEach((k, l) -> rules.put(k, describe(l.getConfig())));
        status.put("rule", rules);

        status.put("stats", Map.of(
                "serviceRateLimiters", serviceLimiters.size(),
                "instanceRateLimiters", instanceLimiters.size(),
                "ruleRateLimiters", ruleLimiters.size()
        ));
        return status;
    }

    /**
     * 获取限流器详细指标信息
     * 包括剩余容量和使用率
     * @return 指标信息列表
     */
    public List<RateLimiterMetrics> getRateLimiterMetrics() {
        List<RateLimiterMetrics> metrics = new java.util.ArrayList<>();

        // 全局限流器
        if (globalLimiter != null) {
            metrics.add(new RateLimiterMetrics(
                    "global", "global", "global",
                    globalLimiter.getConfig().getAlgorithm(),
                    globalLimiter.getRemainingCapacity(),
                    globalLimiter.getUsageRatio(),
                    globalLimiter.getConfig().getCapacity(),
                    globalLimiter.getConfig().getRate()
            ));
        }

        // 服务级限流器
        serviceLimiters.forEach((type, limiter) -> {
            metrics.add(new RateLimiterMetrics(
                    type.name().toLowerCase(), "service", type.name().toLowerCase(),
                    limiter.getConfig().getAlgorithm(),
                    limiter.getRemainingCapacity(),
                    limiter.getUsageRatio(),
                    limiter.getConfig().getCapacity(),
                    limiter.getConfig().getRate()
            ));
        });

        // 实例级限流器
        instanceLimiters.forEach((key, limiter) -> {
            // v2.9.7: 用首个 ':' 切分,兼容 key 含 ':' 的 name/baseUrl 回退值(如 qwen3:4b)
            int idx = key.indexOf(':');
            String serviceName = idx > 0 ? key.substring(0, idx).toLowerCase() : "unknown";
            String instanceId = idx >= 0 ? key.substring(idx + 1) : key;
            metrics.add(new RateLimiterMetrics(
                    serviceName, "instance", instanceId,
                    limiter.getConfig().getAlgorithm(),
                    limiter.getRemainingCapacity(),
                    limiter.getUsageRatio(),
                    limiter.getConfig().getCapacity(),
                    limiter.getConfig().getRate()
            ));
        });

        // v2.8.8: 规则级限流器
        ruleLimiters.forEach((ruleId, limiter) -> {
            metrics.add(new RateLimiterMetrics(
                    "rule", "rule", ruleId,
                    limiter.getConfig().getAlgorithm(),
                    limiter.getRemainingCapacity(),
                    limiter.getUsageRatio(),
                    limiter.getConfig().getCapacity(),
                    limiter.getConfig().getRate()
            ));
        });

        return metrics;
    }

    /**
     * 限流器指标数据结构
     */
    public static class RateLimiterMetrics {
        private final String service;
        private final String scope;
        private final String identifier;
        private final String algorithm;
        private final long remainingCapacity;
        private final double usageRatio;
        private final long capacity;
        private final long rate;

        public RateLimiterMetrics(final String service, final String scope, final String identifier,
                                   final String algorithm, final long remainingCapacity, final double usageRatio,
                                   final long capacity, final long rate) {
            this.service = service;
            this.scope = scope;
            this.identifier = identifier;
            this.algorithm = algorithm;
            this.remainingCapacity = remainingCapacity;
            this.usageRatio = usageRatio;
            this.capacity = capacity;
            this.rate = rate;
        }

        public String getService() { return service; }
        public String getScope() { return scope; }
        public String getIdentifier() { return identifier; }
        public String getAlgorithm() { return algorithm; }
        public long getRemainingCapacity() { return remainingCapacity; }
        public double getUsageRatio() { return usageRatio; }
        public long getCapacity() { return capacity; }
        public long getRate() { return rate; }
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

    /**
     * 生成实例级限流器 key
     * v2.9.7: instanceId 缺失(如 YAML 实例未配置 instance-id)时依次回退 name -> baseUrl,
     * 避免 key 冲突与监控页 identifier 显示 "null"
     */
    private String generateInstanceKey(final ModelServiceRegistry.ServiceType type,
                                       final ModelRouterProperties.ModelInstance inst) {
        return generateInstanceKey(type, inst.getInstanceId(), inst.getName(), inst.getBaseUrl());
    }

    private String generateInstanceKey(final ModelServiceRegistry.ServiceType type,
                                       final String id, final String url) {
        return generateInstanceKey(type, id, null, url);
    }

    private String generateInstanceKey(final ModelServiceRegistry.ServiceType type,
                                       final String id, final String name, final String url) {
        String keyPart = id;
        if (keyPart == null || keyPart.isBlank()) {
            keyPart = name;
        }
        if (keyPart == null || keyPart.isBlank()) {
            keyPart = url;
        }
        return type.name() + ":" + keyPart;
    }

    /**
     * 获取默认限流配置
     * @return 默认限流配置
     */
    public ModelRouterProperties.RateLimitConfig getDefaultRateLimitConfig() {
        return new  ModelRouterProperties.RateLimitConfig();
    }
}