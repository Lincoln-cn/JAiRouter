package org.unreal.modelrouter.router.model;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.config.core.ConfigMergeService;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.router.fallback.FallbackManager;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancerCallTracker;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancerManager;
import org.unreal.modelrouter.router.loadbalancer.monitor.RoutingMonitorService;
import org.unreal.modelrouter.router.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.router.ratelimit.RateLimitContext;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;
import org.unreal.modelrouter.router.ratelimit.ServiceRateLimitHolder;
import org.unreal.modelrouter.router.pool.PoolSelector;
import org.unreal.modelrouter.router.pool.model.PoolDefinition;
import org.unreal.modelrouter.router.rule.RuleDecision;
import org.unreal.modelrouter.router.rule.RuleEngineService;
import org.unreal.modelrouter.router.rule.RuleStatsService;
import org.unreal.modelrouter.router.rule.model.RuleDefinition;
import org.unreal.modelrouter.monitor.tracing.wrapper.LoadBalancerTracingWrapper;
import org.unreal.modelrouter.router.loadbalancer.AffinityContextHolder;
import org.unreal.modelrouter.router.loadbalancer.AffinityKeyResolver;
import org.unreal.modelrouter.router.loadbalancer.impl.StickyLoadBalancer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 模型服务注册表 - 重构版
 * 负责管理所有模型服务的注册、选择和状态管理
 * 支持动态配置更新和服务发现
 */
@Configuration
@EnableConfigurationProperties(ModelRouterProperties.class)
@org.springframework.context.annotation.DependsOn("jpaDatabaseInitializer")
public class ModelServiceRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelServiceRegistry.class);

    /** v2.9.7: 请求级标签路由 header,值格式 key=value,key2=value2(逗号分隔,容忍空格,空项跳过) */
    private static final String TAGS_HEADER = "X-JAiRouter-Tags";

    /**
     * 获取所有服务类型
     * @return 服务类型集合
     */
    public Set<String> getAllServiceTypes() {
         return Arrays.stream(ServiceType.values()).map(Enum::name).collect(Collectors.toSet());
    }

    public enum ServiceType {
        chat, embedding, rerank, tts, stt, imgGen, imgEdit
    }

    // 依赖组件
    private final LoadBalancerManager loadBalancerManager;
    private final ServiceStateManager serviceStateManager;
    private final RateLimitManager rateLimitManager;
    private final CircuitBreakerManager circuitBreakerManager;
    private final FallbackManager fallbackManager;
    private final ConfigMergeService configMergeService;
    private final ServiceTypeResolver serviceTypeResolver;
    private final ConfigConverterHelper configConverterHelper;

    // v2.7.7: 实例选择优化器
    private final SelectInstanceOptimizer selectInstanceOptimizer;

    // v2.7.20: WebClient缓存管理器
    private final WebClientCacheManager webClientCacheManager;

    // v2.7.28: 辅助组件
    private final ServiceInstanceSelector instanceSelector;
    private final ServiceConfigBuilder configBuilder;

    // v2.7.0: 路由监控服务
    private final RoutingMonitorService routingMonitorService;

    /** #688: 让「选择时加计数」与「完成时减计数」落在同一个 LB 实例上的记账器 */
    private final LoadBalancerCallTracker callTracker = new LoadBalancerCallTracker();

    /**
     * #690: 粘性包装器按「委托 LB」复用。
     *
     * <p>{@link StickyLoadBalancer} 内部用实例字段缓存
     * 哈希环快照（指纹不变就不重建），在请求路径上每请求 new 一个等于缓存永不命中——每个请求都要
     * 全量重建 {@code virtualNodes × 实例数} 次哈希。委托 LB 本身已按服务类型/策略缓存
     * （#649），因此以它作键即可得到稳定复用；键的数量上界是「服务类型数 × 策略数」。</p>
     */
    private final java.util.Map<LoadBalancer, StickyLoadBalancer> stickyLoadBalancers =
            new java.util.concurrent.ConcurrentHashMap<>();

    // v2.8.5: 规则引擎(延迟注入,避免循环依赖)
    private RuleEngineService ruleEngine;

    // v2.8.7: 规则命中统计(延迟注入,避免循环依赖)
    private RuleStatsService ruleStatsService;

    // v2.8.9: 资源池选择器(延迟注入,避免循环依赖)
    private PoolSelector poolSelector;

    // 配置和缓存
    private final ModelRouterProperties originalProperties;
    private volatile Map<String, Object> currentConfig;
    private volatile Map<String, ServiceRuntimeConfig> serviceConfigCache;

    /**
     * v2.8.5: 规则引擎延迟注入
     * required=false:规则引擎不可用时路由行为与之前完全一致
     */
    @Autowired(required = false)
    public void setRuleEngine(final RuleEngineService ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    /**
     * v2.8.7: 规则命中统计延迟注入
     * required=false:统计不可用时路由行为不变
     */
    @Autowired(required = false)
    public void setRuleStatsService(final RuleStatsService ruleStatsService) {
        this.ruleStatsService = ruleStatsService;
    }

    /**
     * v2.8.9: 资源池选择器延迟注入
     * required=false:池不可用时路由行为不变(auto-model 走原逻辑)
     */
    @Autowired(required = false)
    public void setPoolSelector(final PoolSelector poolSelector) {
        this.poolSelector = poolSelector;
    }

    public ModelServiceRegistry(final ModelRouterProperties properties,
                                final ServiceStateManager serviceStateManager,
                                final RateLimitManager rateLimitManager,
                                final LoadBalancerManager loadBalancerManager,
                                final CircuitBreakerManager circuitBreakerManager,
                                final FallbackManager fallbackManager,
                                final ConfigMergeService configMergeService,
                                final ServiceTypeResolver serviceTypeResolver,
                                final ConfigConverterHelper configConverterHelper,
                                final WebClientCacheManager webClientCacheManager,
                                final RoutingMonitorService routingMonitorService) {
        this.originalProperties = properties;
        this.serviceStateManager = serviceStateManager;
        this.rateLimitManager = rateLimitManager;
        this.loadBalancerManager = loadBalancerManager;
        this.circuitBreakerManager = circuitBreakerManager;
        this.fallbackManager = fallbackManager;
        this.configMergeService = configMergeService;
        this.serviceTypeResolver = serviceTypeResolver;
        this.configConverterHelper = configConverterHelper;
        this.webClientCacheManager = webClientCacheManager;
        this.routingMonitorService = routingMonitorService;
        this.serviceConfigCache = new ConcurrentHashMap<>();
        this.selectInstanceOptimizer = new SelectInstanceOptimizer(serviceStateManager, circuitBreakerManager);
        this.instanceSelector = new ServiceInstanceSelector(
                serviceStateManager, rateLimitManager, circuitBreakerManager, routingMonitorService);
        this.configBuilder = new ServiceConfigBuilder(
                configConverterHelper, rateLimitManager, circuitBreakerManager, fallbackManager);
    }

    @PostConstruct
    public void initialize() {
        LOGGER.info("正在初始化ModelServiceRegistry...");

        try {
            LOGGER.debug("开始合并YAML和持久化配置");
            refreshFromMergedConfig();
            LOGGER.debug("配置合并完成，当前配置大小: {}", currentConfig != null ? currentConfig.size() : 0);

            LOGGER.debug("开始初始化各个管理器");
            initializeManagers();
            LOGGER.debug("所有管理器初始化完成");

            LOGGER.info("ModelServiceRegistry初始化完成");
            logCurrentConfiguration();
        } catch (Exception e) {
            LOGGER.error("ModelServiceRegistry初始化失败", e);
            throw new RuntimeException("Failed to initialize ModelServiceRegistry", e);
        }
    }

    public void refreshFromMergedConfig() {
        LOGGER.info("正在刷新运行时配置...");

        try {
            Map<String, Object> mergedConfig = loadMergedConfig();

            this.currentConfig = mergedConfig;
            updateOriginalPropertiesFromConfig(mergedConfig);
            this.serviceConfigCache = configBuilder.rebuildServiceConfigCache(mergedConfig);
            reinitializeLoadBalancers();
            // v2.8.8: 合并配置中的限流配置应用到运行时限流器(重启后持久化配置生效;YAML 无覆盖时不变)
            applyPersistedRateLimits(mergedConfig);

            LOGGER.info("运行时配置刷新完成，当前包含 {} 个服务", serviceConfigCache.size());
        } catch (Exception e) {
            LOGGER.error("刷新运行时配置失败", e);
        }
    }

    /**
     * #707: 读取当前生效的配置（持久化配置优先；没有持久化配置时回落到 YAML 配置）。
     *
     * @return 合并后的配置，永不为 {@code null}
     */
    private Map<String, Object> loadMergedConfig() {
        final Map<String, Object> persistedConfig = configMergeService.getPersistedConfig();
        if (persistedConfig != null && !persistedConfig.isEmpty()) {
            return persistedConfig;
        }

        LOGGER.info("未找到合并配置，使用默认配置");
        if (originalProperties != null) {
            final Map<String, Object> fromYaml =
                    configConverterHelper.convertModelRouterPropertiesToMap(originalProperties);
            if (fromYaml != null) {
                return fromYaml;
            }
        }
        LOGGER.warn("原始配置也为空，使用空配置");
        return new HashMap<>();
    }

    /**
     * #707: 精确刷新单个服务的运行时缓存。
     *
     * <p>{@link #refreshFromMergedConfig()} 会重建整张缓存表；配置写入通常只影响一个服务，用它会让
     * 其它服务的 {@link ServiceRuntimeConfig} 对象被无谓替换。本方法只重建传入的服务。</p>
     *
     * @param serviceKey 服务配置键（{@code chat} / {@code img-gen} 等，支持别名与大小写差异）
     * @return {@code true} 表示该服务的缓存条目已按最新配置重建
     */
    public boolean refreshServiceConfig(final String serviceKey) {
        return !refreshServiceConfigs(Collections.singletonList(serviceKey)).isEmpty();
    }

    /**
     * #707: 精确刷新多个服务的运行时缓存，只重建 {@code serviceKeys} 涉及的条目。
     *
     * <p>刷新范围与全量刷新保持一致（只是缩小到指定服务）：缓存条目、该服务的负载均衡器
     * （有实例时）、该服务的限流器（配置里带 {@code rateLimit} 段时），以及
     * {@link ModelRouterProperties} 里的服务配置副本（粘性会话判定读它）。</p>
     *
     * <p>键在合并后的配置里已经不存在时（例如批量更新把服务删掉了），对应缓存条目会被移除，
     * 避免已删除的服务继续参与路由。</p>
     *
     * @param serviceKeys 服务配置键集合
     * @return 实际被重建的服务键集合（不含被移除的键）
     */
    @SuppressWarnings("unchecked")
    public Set<String> refreshServiceConfigs(final Collection<String> serviceKeys) {
        if (serviceKeys == null || serviceKeys.isEmpty()) {
            return Collections.emptySet();
        }

        final Map<String, Object> mergedConfig = loadMergedConfig();
        final Map<String, Object> services = servicesOf(mergedConfig);
        final Map<String, ServiceRuntimeConfig> newCache = new ConcurrentHashMap<>(
                serviceConfigCache != null ? serviceConfigCache : Collections.emptyMap());
        final Map<String, ModelRouterProperties.ServiceConfig> newPropertiesServices = new HashMap<>();
        if (originalProperties != null && originalProperties.getServices() != null) {
            newPropertiesServices.putAll(originalProperties.getServices());
        }

        final Map<String, Map<String, Object>> refreshedConfigs = new HashMap<>();
        for (String requestedKey : serviceKeys) {
            if (requestedKey == null || requestedKey.isBlank()) {
                continue;
            }
            final String resolvedKey = resolveServiceKey(services, requestedKey);
            if (resolvedKey == null) {
                removeServiceFromCache(newCache, newPropertiesServices, requestedKey);
                continue;
            }
            final Object rawServiceConfig = services.get(resolvedKey);
            if (!(rawServiceConfig instanceof Map)) {
                LOGGER.warn("服务 {} 的配置不是对象，跳过运行时缓存刷新", resolvedKey);
                continue;
            }
            final Map<String, Object> serviceConfigMap = (Map<String, Object>) rawServiceConfig;
            try {
                newCache.put(resolvedKey, configBuilder.buildServiceRuntimeConfig(serviceConfigMap));
                newPropertiesServices.put(resolvedKey,
                        configConverterHelper.convertMapToServiceConfig(serviceConfigMap));
            } catch (RuntimeException e) {
                // 与全量刷新（ServiceConfigBuilder 逐个服务兜底）一致：单个服务配置写坏时保留旧缓存，
                // 不让「已经落库成功」的写入对外报失败，但要留下证据
                LOGGER.warn("重建服务 {} 的运行时配置失败，保留旧缓存: {}", resolvedKey, e.getMessage(), e);
                continue;
            }
            refreshedConfigs.put(resolvedKey, serviceConfigMap);
        }

        this.currentConfig = mergedConfig;
        this.serviceConfigCache = newCache;
        if (originalProperties != null) {
            originalProperties.setServices(newPropertiesServices);
        }
        applyTargetedRuntimeEffects(refreshedConfigs);

        LOGGER.info("运行时缓存已按服务精确刷新，涉及 {} 个服务", refreshedConfigs.size());
        return new HashSet<>(refreshedConfigs.keySet());
    }

    /**
     * #707: 把「配置里已经没有」的服务从缓存与配置副本中摘掉（含别名/大小写同义键）。
     */
    private void removeServiceFromCache(final Map<String, ServiceRuntimeConfig> cache,
                                        final Map<String, ModelRouterProperties.ServiceConfig> propertiesServices,
                                        final String serviceKey) {
        final ServiceType requestedType = serviceTypeResolver.parseServiceType(serviceKey);
        for (String cachedKey : new ArrayList<>(cache.keySet())) {
            final boolean sameKey = cachedKey.equalsIgnoreCase(serviceKey);
            final boolean sameType = requestedType != null
                    && requestedType == serviceTypeResolver.parseServiceType(cachedKey);
            if (!sameKey && !sameType) {
                continue;
            }
            cache.remove(cachedKey);
            propertiesServices.remove(cachedKey);
            LOGGER.info("服务 {} 已不在配置中，移除其运行时缓存（键 {}）", serviceKey, cachedKey);
        }
    }

    /**
     * #707: 对刚重建缓存的服务应用运行时副作用（负载均衡器 + 限流器），范围与全量刷新一致。
     */
    private void applyTargetedRuntimeEffects(final Map<String, Map<String, Object>> refreshedConfigs) {
        for (Map.Entry<String, Map<String, Object>> entry : refreshedConfigs.entrySet()) {
            final String serviceKey = entry.getKey();
            final ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);
            final ServiceType type = serviceTypeResolver.parseServiceType(serviceKey);
            if (runtimeConfig == null || type == null) {
                LOGGER.warn("无法识别的服务键 {}，跳过负载均衡器/限流器刷新", serviceKey);
                continue;
            }
            try {
                if (!runtimeConfig.getInstances().isEmpty()) {
                    loadBalancerManager.reinitializeLoadBalancer(type, runtimeConfig.getLoadBalanceConfig());
                }
                applyPersistedRateLimit(type, entry.getValue());
            } catch (RuntimeException e) {
                LOGGER.warn("服务 {} 的负载均衡/限流配置未能应用到运行时: {}", serviceKey, e.getMessage(), e);
            }
        }
    }

    /**
     * #707: 取出合并配置里的 {@code services} 段（缺失或类型不符时返回空 Map）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> servicesOf(final Map<String, Object> mergedConfig) {
        if (mergedConfig == null || !(mergedConfig.get("services") instanceof Map)) {
            return Collections.emptyMap();
        }
        return (Map<String, Object>) mergedConfig.get("services");
    }

    /**
     * #707: 把调用方给的服务键解析成配置里实际使用的键。
     *
     * <p>顺序：精确匹配 → 别名归一后的标准键（{@code imgGen} / {@code img_gen} → {@code img-gen}）
     * → 忽略大小写匹配。都匹配不上返回 {@code null}。</p>
     */
    private String resolveServiceKey(final Map<String, Object> services, final String serviceKey) {
        if (services.containsKey(serviceKey)) {
            return serviceKey;
        }

        final ServiceType type = serviceTypeResolver.parseServiceType(serviceKey);
        if (type != null) {
            final String canonicalKey = serviceTypeResolver.getServiceConfigKey(type);
            if (canonicalKey != null && services.containsKey(canonicalKey)) {
                return canonicalKey;
            }
        }

        for (String existingKey : services.keySet()) {
            if (existingKey != null && existingKey.equalsIgnoreCase(serviceKey)) {
                return existingKey;
            }
        }
        return null;
    }

    /**
     * 选择服务实例(无请求头版本,内部调用路径)
     */
    public ModelRouterProperties.ModelInstance selectInstance(final ServiceType serviceType,
                                                              final String modelName,
                                                              final String clientIp) {
        return selectInstance(serviceType, modelName, clientIp, null);
    }

    /**
     * 选择服务实例
     * v2.8.5: 支持规则引擎(按 header 等条件路由);无规则/不匹配时行为与之前完全一致
     */
    public ModelRouterProperties.ModelInstance selectInstance(final ServiceType serviceType,
                                                              final String modelName,
                                                              final String clientIp,
                                                              final Map<String, String> headers) {
        if (serviceType == null) {
            throw new IllegalArgumentException("ServiceType cannot be null");
        }
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalArgumentException("ModelName cannot be null or empty");
        }

        String serviceKey = serviceTypeResolver.getServiceConfigKey(serviceType);
        ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);

        if (runtimeConfig == null || runtimeConfig.getInstances().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No instances found for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        // v2.8.5: 规则引擎求值(命中后重写模型名/锁定实例/覆盖LB策略;未命中走原逻辑)
        RuleDecision ruleDecision = ruleEngine != null
                ? ruleEngine.evaluate(serviceType, modelName, clientIp, headers)
                : null;
        // v2.8.7: 命中统计(仅决策生效点计数,resolveRuleAdapterName/dry-run 不计数)
        if (ruleDecision != null && ruleStatsService != null) {
            ruleStatsService.recordHit(ruleDecision.getRule().getId(),
                    ruleDecision.getRule().getAction().getType().name());
        }
        // v2.8.8: RATE_LIMIT 动作 — 仅决策生效点执行(与命中统计同处,resolveRuleAdapterName 侧不执行,防双计)
        if (ruleDecision != null && rateLimitManager != null
                && ruleDecision.getRule().getAction() != null
                && ruleDecision.getRule().getAction().getType() == RuleDefinition.ActionType.RATE_LIMIT) {
            RuleDefinition.Action action = ruleDecision.getRule().getAction();
            RateLimitConfig ruleCfg = new RateLimitConfig();
            ruleCfg.setEnabled(true);
            ruleCfg.setAlgorithm(action.getAlgorithm() != null ? action.getAlgorithm() : "token-bucket");
            ruleCfg.setCapacity(action.getCapacity() != null ? action.getCapacity() : 100L);
            ruleCfg.setRate(action.getRate() != null ? action.getRate() : 10L);
            ruleCfg.setScope(action.getScope() != null ? action.getScope() : "rule");
            if (action.getWarmUpPeriod() != null) {
                ruleCfg.setWarmUpPeriod(action.getWarmUpPeriod());
            }
            RateLimitContext ruleContext = new RateLimitContext(
                    serviceType, modelName, clientIp, 1, null, null, ruleDecision.getRule().getId());
            if (!rateLimitManager.tryAcquireRule(ruleDecision.getRule().getId(), ruleCfg, ruleContext)) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Rate limit rule exceeded: " + ruleDecision.getRule().getName());
            }
        }

        String effectiveModelName = ruleDecision != null && ruleDecision.getTargetModelName() != null
                ? ruleDecision.getTargetModelName() : modelName;

        List<ModelRouterProperties.ModelInstance> candidates = runtimeConfig.getInstances();
        if (ruleDecision != null && ruleDecision.getTargetInstanceId() != null) {
            String targetInstanceId = ruleDecision.getTargetInstanceId();
            candidates = candidates.stream()
                    .filter(i -> targetInstanceId.equals(i.getInstanceId())
                            || targetInstanceId.equals(i.getName()))
                    .collect(Collectors.toList());
            if (candidates.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No instances found for rule target instance '" + targetInstanceId + "'");
            }
            // 锁定实例时按目标实例自身名称匹配,不受原 modelName 限制
            effectiveModelName = candidates.get(0).getName();
        }

        // v2.9.7: 标签过滤 — 规则 TARGET_TAGS 优先,否则解析请求 header X-JAiRouter-Tags;
        // 过滤后空候选与 TARGET_INSTANCE 一致抛 404;池路由(池名显式圈选)不受 tags 影响
        Map<String, String> requiredTags = ruleDecision != null ? ruleDecision.getTargetTags() : null;
        if (requiredTags == null) {
            requiredTags = parseTagsHeader(headers);
        }
        if (requiredTags != null && !requiredTags.isEmpty()) {
            candidates = selectInstanceOptimizer.filterByTags(candidates, requiredTags);
            if (candidates.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No instances found matching tags " + requiredTags
                                + " for model '" + effectiveModelName + "'");
            }
        }

        // v2.8.9: 资源池路由 — effectiveModelName 命中池名 → 池成员候选 + 池策略;
        // 未命中时 auto-model 回退为该服务全部健康实例,其余走原模型名过滤
        List<ModelRouterProperties.ModelInstance> availableInstances;
        LoadBalancer loadBalancer;
        PoolDefinition matchedPool = poolSelector != null
                ? poolSelector.findPool(serviceType, effectiveModelName) : null;
        if (matchedPool != null) {
            availableInstances = poolSelector.resolveCandidates(matchedPool, runtimeConfig.getInstances());
            loadBalancer = loadBalancerManager.getLoadBalancerByStrategy(matchedPool.getStrategy());
            if (loadBalancer == null) {
                loadBalancer = loadBalancerManager.getLoadBalancer(null);
            }
        } else {
            if (PoolSelector.AUTO_MODEL.equals(effectiveModelName) && poolSelector != null) {
                availableInstances = poolSelector.autoFallbackCandidates(
                        runtimeConfig.getInstances(), serviceType);
            } else {
                availableInstances = selectInstanceOptimizer.filterAvailableInstances(
                        candidates, effectiveModelName, serviceType);
            }
            loadBalancer = loadBalancerManager.getLoadBalancer(serviceType);
            if (loadBalancer == null) {
                loadBalancer = loadBalancerManager.getLoadBalancer(null);
            }
            // v2.8.5: 规则指定 LB 策略时覆盖
            if (ruleDecision != null && ruleDecision.getLbStrategy() != null) {
                LoadBalancer ruleLoadBalancer =
                        loadBalancerManager.getLoadBalancerByStrategy(ruleDecision.getLbStrategy());
                if (ruleLoadBalancer != null) {
                    loadBalancer = ruleLoadBalancer;
                }
            }
        }

        if (availableInstances.isEmpty()) {
            throw instanceSelector.createAppropriateException(serviceType, effectiveModelName, candidates);
        }

        // #688: StickyLoadBalancer 的 recordCall/recordCallComplete 都直接委托给 delegate，
        // 真正的在途计数发生在包装之前的那个 LB 上，因此这里先记住它，完成回调时按同一实例减计数。
        final LoadBalancer accountingLoadBalancer = loadBalancer;

        // v2.9.0: 会话粘性路由 — 按 sticky.scope 配置解析亲和性键，用 StickyLoadBalancer 包装原 LB
        String stickyScope = getStickyScope(serviceType);
        String affinityKey = stickyScope != null
                ? AffinityContextHolder.resolveKey(stickyScope)
                : AffinityContextHolder.get();
        if (affinityKey != null && stickyScope != null) {
            // #690: 复用粘性包装器，别每请求 new（内部哈希环快照缓存依赖实例复用）
            final LoadBalancer sticky = stickyLoadBalancerFor(loadBalancer);
            if (sticky != null) {
                loadBalancer = sticky;
            }
        }

        // v2.8.8: 服务级限流检查(每请求恰一次;实例级在 selectWithRateLimit 内;无配置零开销)
        // v2.9.10: 缓存命中提前短路场景——handler 层已预扣服务级限流(见 ServiceRateLimitHolder),
        // 此处跳过以保持恰一次语义;非缓存路径(无 key/disabled)标志未设置,行为与现状完全一致
        if (!ServiceRateLimitHolder.isAcquired()
                && rateLimitManager != null && !rateLimitManager.tryAcquire(
                new RateLimitContext(serviceType, effectiveModelName, clientIp, 1, null, null))) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Service rate limit exceeded for service type '" + serviceType + "'");
        }

        ModelRouterProperties.ModelInstance selectedInstance =
                instanceSelector.selectWithRateLimit(
                        availableInstances, loadBalancer, affinityKey, clientIp, serviceType, effectiveModelName);

        if (selectedInstance == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "All instances rate limited for model '" + effectiveModelName + "'");
        }

        // v2.7.0: 记录路由选择事件
        String strategy = getActualStrategyName(loadBalancer);
        routingMonitorService.recordSelection(
                serviceType.name().toLowerCase(),
                effectiveModelName,
                strategy,
                selectedInstance,
                clientIp,
                availableInstances.size());

        loadBalancer.recordCall(selectedInstance);
        callTracker.track(LoadBalancerCallTracker.key(serviceType.name(), selectedInstance),
                accountingLoadBalancer);
        return selectedInstance;
    }

    /**
     * v2.8.5: 解析规则指定的目标适配器名(供 ServiceRequestHandler 消费 TARGET_ADAPTER 动作)
     * 必须传原始 modelName 与 selectInstance 求值输入一致,保证二次求值确定性
     *
     * @return 规则指定的适配器名;无规则/不匹配/非 TARGET_ADAPTER 动作返回 null
     */
    public String resolveRuleAdapterName(final ServiceType serviceType,
                                         final String modelName,
                                         final String clientIp,
                                         final Map<String, String> headers) {
        if (ruleEngine == null) {
            return null;
        }
        RuleDecision decision = ruleEngine.evaluate(serviceType, modelName, clientIp, headers);
        return decision != null ? decision.getTargetAdapterName() : null;
    }

    public String getModelPath(final ServiceType serviceType, final String modelName) {
        String serviceKey = serviceTypeResolver.getServiceConfigKey(serviceType);
        ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);

        if (runtimeConfig == null || runtimeConfig.getInstances().isEmpty()) {
            return "";
        }

        Optional<ModelRouterProperties.ModelInstance> matchingInstance = runtimeConfig.getInstances().stream()
                .filter(instance -> modelName.equals(instance.getName()))
                .findFirst();

        return matchingInstance.map(ModelRouterProperties.ModelInstance::getPath).orElse("");
    }

    public String getServiceAdapter(final ServiceType serviceType) {
        String serviceKey = serviceTypeResolver.getServiceConfigKey(serviceType);
        ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);
        return runtimeConfig != null ? runtimeConfig.getAdapter() : null;
    }

    public void recordCallComplete(final ServiceType serviceType, final ModelRouterProperties.ModelInstance instance) {
        LoadBalancer loadBalancer = accountingLoadBalancerFor(serviceType, instance);
        if (loadBalancer != null) {
            loadBalancer.recordCallComplete(instance);
        }
        circuitBreakerManager.recordSuccess(instance.getInstanceId(), instance.getBaseUrl());
    }

    public void recordCallFailure(final ServiceType serviceType, final ModelRouterProperties.ModelInstance instance) {
        LoadBalancer loadBalancer = accountingLoadBalancerFor(serviceType, instance);
        if (loadBalancer != null) {
            loadBalancer.recordCallFailure(instance);
        }
        circuitBreakerManager.recordFailure(instance.getInstanceId(), instance.getBaseUrl());
    }

    /**
     * v2.9.3: 记录实例调用完成（带调用时长和成功状态），将 durationMs 传递给负载均衡器钩子
     */
    public void recordCallComplete(final ServiceType serviceType,
                                   final ModelRouterProperties.ModelInstance instance,
                                   final long durationMs,
                                   final boolean success) {
        LoadBalancer loadBalancer = accountingLoadBalancerFor(serviceType, instance);
        if (loadBalancer != null) {
            loadBalancer.recordCallComplete(instance, durationMs, success);
        }
        // v3.5.4（#647）：只有真正成功才重置熔断失败计数。此前无条件 recordSuccess，
        // 导致「完成了但不算成功」的调用（如 4xx 客户端错误）被记成成功，掩盖了实例故障；
        // 同时签名里的 success 形同虚设（#655 第 7 条）。
        if (success) {
            circuitBreakerManager.recordSuccess(instance.getInstanceId(), instance.getBaseUrl());
        }
    }

    /**
     * v2.9.3: 记录实例调用失败（带调用时长和错误码），将 durationMs 传递给负载均衡器钩子
     */
    public void recordCallFailure(final ServiceType serviceType,
                                  final ModelRouterProperties.ModelInstance instance,
                                  final long durationMs,
                                  final String errorCode) {
        LoadBalancer loadBalancer = accountingLoadBalancerFor(serviceType, instance);
        if (loadBalancer != null) {
            loadBalancer.recordCallFailure(instance, durationMs, errorCode);
        }
        circuitBreakerManager.recordFailure(instance.getInstanceId(), instance.getBaseUrl());
    }

    /**
     * #690: 取（必要时创建）委托 LB 对应的粘性包装器。
     *
     * <p>同一个委托 LB 永远拿到同一个包装器实例，因此其中的哈希环快照缓存能真正命中。
     * 包装器本身无状态地按请求参数（affinityKey/instances）工作，复用不引入跨请求串扰。</p>
     *
     * @param delegate 被包装的负载均衡器
     * @return 粘性包装器；{@code delegate} 为 null 时返回 null
     */
    LoadBalancer stickyLoadBalancerFor(final LoadBalancer delegate) {
        if (delegate == null) {
            return null;
        }
        return stickyLoadBalancers.computeIfAbsent(delegate,
                key -> new StickyLoadBalancer(key, serviceStateManager, circuitBreakerManager));
    }

    /**
     * #690: 当前缓存的粘性包装器数量（供测试与排障使用）。
     *
     * @return 缓存条目数
     */
    int getStickyLoadBalancerCacheSize() {
        return stickyLoadBalancers.size();
    }

    /**
     * #688: 取回该实例记账时使用的 LB 并把未完成数减一。
     *
     * <p>没有记账记录时（例如直接调用完成回调的测试或其它入口）退回按服务类型取 LB，
     * 保持与修复前一致的行为。</p>
     *
     * @param serviceType 服务类型
     * @param instance    实例
     * @return 应当执行完成/失败回调的 LB，可能为 null
     */
    private LoadBalancer accountingLoadBalancerFor(final ServiceType serviceType,
                                                   final ModelRouterProperties.ModelInstance instance) {
        // 测试里有 mock(X.class, CALLS_REAL_METHODS) 构造的实例：不跑构造器 ⇒ 字段初始化器不生效，
        // callTracker 会是 null。这里按「没有记账表就退回旧取法」处理，生产路径不受影响。
        final LoadBalancerCallTracker tracker = callTracker;
        if (instance == null || tracker == null) {
            return loadBalancerManager.getLoadBalancer(serviceType);
        }
        final LoadBalancer tracked = tracker.take(
                LoadBalancerCallTracker.key(serviceType.name(), instance));
        return tracked != null ? tracked : loadBalancerManager.getLoadBalancer(serviceType);
    }

    /**
     * #688: 该实例当前未完成的调用数（供测试与排障使用）。
     *
     * @param serviceType 服务类型
     * @param instance    实例
     * @return 未完成调用数
     */
    public int getOutstandingCalls(final ServiceType serviceType,
                                   final ModelRouterProperties.ModelInstance instance) {
        if (instance == null) {
            return 0;
        }
        return callTracker.outstanding(LoadBalancerCallTracker.key(serviceType.name(), instance));
    }

    public CircuitBreaker.State getInstanceCircuitBreakerState(final ModelRouterProperties.ModelInstance instance) {
        return circuitBreakerManager.getState(instance.getInstanceId(), instance.getBaseUrl());
    }

    // ==================== 查询方法 ====================

    public Set<ServiceType> getAvailableServiceTypes() {
        return serviceConfigCache.entrySet().stream()
                .filter(entry -> !entry.getValue().getInstances().isEmpty())
                .map(entry -> serviceTypeResolver.parseServiceType(entry.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Set<String> getAvailableModels(final ServiceType serviceType) {
        String serviceKey = serviceTypeResolver.getServiceConfigKey(serviceType);
        ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);

        if (runtimeConfig == null) {
            return Collections.emptySet();
        }

        return runtimeConfig.getInstances().stream()
                .map(ModelRouterProperties.ModelInstance::getName)
                .collect(Collectors.toSet());
    }

    public Map<ServiceType, List<ModelRouterProperties.ModelInstance>> getAllInstances() {
        Map<ServiceType, List<ModelRouterProperties.ModelInstance>> result = new HashMap<>();

        for (Map.Entry<String, ServiceRuntimeConfig> entry : serviceConfigCache.entrySet()) {
            ServiceType serviceType = serviceTypeResolver.parseServiceType(entry.getKey());
            if (serviceType != null) {
                result.put(serviceType, new ArrayList<>(entry.getValue().getInstances()));
            }
        }

        return result;
    }

    public ModelRouterProperties.ServiceConfig getServiceConfig(final ServiceType serviceType) {
        String serviceKey = serviceTypeResolver.getServiceConfigKey(serviceType);
        ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);

        if (runtimeConfig == null) {
            return null;
        }

        ModelRouterProperties.ServiceConfig serviceConfig = new ModelRouterProperties.ServiceConfig();
        serviceConfig.setInstances(new ArrayList<>(runtimeConfig.getInstances()));
        serviceConfig.setAdapter(runtimeConfig.getAdapter());
        serviceConfig.setLoadBalance(runtimeConfig.getLoadBalanceConfig());
        serviceConfig.setRateLimit(runtimeConfig.getRateLimitConfig());
        serviceConfig.setCircuitBreaker(runtimeConfig.getCircuitBreakerConfig());
        serviceConfig.setFallback(runtimeConfig.getFallbackConfig());

        return serviceConfig;
    }

    public String getLoadBalanceStrategy(final ServiceType serviceType) {
        LoadBalancer loadBalancer = loadBalancerManager.getLoadBalancer(serviceType);
        return loadBalancer != null ? loadBalancer.getClass().getSimpleName() : "Unknown";
    }

    public FallbackManager getFallbackManager() {
        return fallbackManager;
    }

    // ==================== 动态更新方法 ====================

    public void updateServiceInstances(
            final ServiceType serviceType,
            final List<ModelRouterProperties.ModelInstance> instances) {
        String serviceKey = serviceTypeResolver.getServiceConfigKey(serviceType);
        ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);

        if (runtimeConfig != null) {
            runtimeConfig.setInstances(new ArrayList<>(instances));
            LOGGER.info("已更新服务 {} 的实例，共 {} 个实例", serviceType, instances.size());
        }
    }

    public void updateServiceAdapter(final ServiceType serviceType, final String adapter) {
        String serviceKey = serviceTypeResolver.getServiceConfigKey(serviceType);
        ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);

        if (runtimeConfig != null) {
            runtimeConfig.setAdapter(adapter);
            LOGGER.info("已更新服务 {} 的适配器为: {}", serviceType, adapter);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * v2.9.0: 获取指定服务类型的粘性亲和性粒度(scope)。
     * 粘性未启用时返回 null(调用方跳过粘性路由)。
     * scope 值: "tenant_model"(默认) 或 "tenant"。
     */
    private String getStickyScope(final ServiceType serviceType) {
        String serviceKey = serviceTypeResolver.getServiceConfigKey(serviceType);
        ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);
        if (runtimeConfig == null) {
            return null;
        }
        List<ModelRouterProperties.ModelInstance> instances = runtimeConfig.getInstances();
        if (instances == null || instances.size() <= 1) {
            return null;
        }
        if (originalProperties != null && originalProperties.getServices() != null) {
            ModelRouterProperties.ServiceConfig svcConfig =
                    originalProperties.getServices().get(serviceKey);
            if (svcConfig != null && svcConfig.getSticky() != null) {
                if (!Boolean.TRUE.equals(svcConfig.getSticky().getEnabled())) {
                    return null;
                }
                String scope = svcConfig.getSticky().getAffinityKeyScope();
                return scope != null ? scope : AffinityKeyResolver.SCOPE_TENANT_MODEL;
            }
        }
        // 默认: 实例>1时启用，使用 tenant_model 粒度
        return AffinityKeyResolver.SCOPE_TENANT_MODEL;
    }

    /**
     * v2.9.7: 解析请求级标签 header X-JAiRouter-Tags
     * 格式: key=value,key2=value2;逗号分隔、容忍空格、键值等号分隔、空项/空值跳过
     *
     * @return 解析后的标签 Map;无 header 或解析为空返回 null
     */
    private Map<String, String> parseTagsHeader(final Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        String headerValue = headers.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(TAGS_HEADER))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        Map<String, String> tags = new HashMap<>();
        for (String item : headerValue.split(",")) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String trimmed = item.trim();
            int eqIdx = trimmed.indexOf('=');
            if (eqIdx <= 0) {
                continue;
            }
            String key = trimmed.substring(0, eqIdx).trim();
            String value = trimmed.substring(eqIdx + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            tags.put(key, value);
        }
        return tags.isEmpty() ? null : tags;
    }

    /**
     * v2.8.8: 将合并配置中带 rateLimit 的服务应用到运行时限流器
     * 仅处理含 rateLimit 段的配置(YAML 无覆盖的服务保持构造时初始化的限流器不变)
     */
    @SuppressWarnings("unchecked")
    private void applyPersistedRateLimits(final Map<String, Object> mergedConfig) {
        if (mergedConfig == null || !(mergedConfig.get("services") instanceof Map)) {
            return;
        }
        Map<String, Object> services = (Map<String, Object>) mergedConfig.get("services");
        for (Map.Entry<String, Object> entry : services.entrySet()) {
            ServiceType type = serviceTypeResolver.parseServiceType(entry.getKey());
            if (type == null || !(entry.getValue() instanceof Map)) {
                continue;
            }
            applyPersistedRateLimit(type, (Map<String, Object>) entry.getValue());
        }
    }

    /**
     * #707: 把单个服务配置里的 {@code rateLimit} 段应用到运行时限流器（无该段时保持原限流器）。
     */
    @SuppressWarnings("unchecked")
    private void applyPersistedRateLimit(final ServiceType type, final Map<String, Object> serviceConfigMap) {
        Object rateLimit = serviceConfigMap.get("rateLimit");
        if (rateLimit instanceof Map) {
            rateLimitManager.setRateLimiter(type, RateLimitConfig.fromMap((Map<String, Object>) rateLimit));
        }
    }

    private void initializeManagers() {
        circuitBreakerManager.initialize(originalProperties);
        fallbackManager.initialize(originalProperties);
        LOGGER.debug("所有管理器初始化完成");
    }

    private void reinitializeLoadBalancers() {
        try {
            for (Map.Entry<String, ServiceRuntimeConfig> entry : serviceConfigCache.entrySet()) {
                ServiceType serviceType = serviceTypeResolver.parseServiceType(entry.getKey());
                if (serviceType != null && !entry.getValue().getInstances().isEmpty()) {
                    loadBalancerManager.reinitializeLoadBalancer(serviceType, entry.getValue().getLoadBalanceConfig());
                }
            }
            LOGGER.debug("负载均衡器重新初始化完成");
        } catch (Exception e) {
            LOGGER.warn("重新初始化负载均衡器时发生错误: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void updateOriginalPropertiesFromConfig(final Map<String, Object> mergedConfig) {
        if (mergedConfig == null || !mergedConfig.containsKey("services")) {
            return;
        }

        Map<String, Object> servicesMap = (Map<String, Object>) mergedConfig.get("services");
        if (servicesMap == null || originalProperties == null) {
            return;
        }

        Map<String, ModelRouterProperties.ServiceConfig> services = new HashMap<>();
        for (Map.Entry<String, Object> entry : servicesMap.entrySet()) {
            Map<String, Object> serviceConfigMap = (Map<String, Object>) entry.getValue();
            ModelRouterProperties.ServiceConfig serviceConfig =
                    configConverterHelper.convertMapToServiceConfig(serviceConfigMap);
            services.put(entry.getKey(), serviceConfig);
        }

        originalProperties.setServices(services);
    }

    private void logCurrentConfiguration() {
        LOGGER.info("当前服务配置概览:");
        for (Map.Entry<String, ServiceRuntimeConfig> entry : serviceConfigCache.entrySet()) {
            String serviceKey = entry.getKey();
            ServiceRuntimeConfig config = entry.getValue();
            LOGGER.info("  服务 {}: {} 个实例, 适配器={}, 负载均衡={}",
                    serviceKey,
                    config.getInstances().size(),
                    config.getAdapter(),
                    config.getLoadBalanceConfig() != null ? config.getLoadBalanceConfig().getType() : "default");
        }
    }

    /**
     * 获取实际的负载均衡策略名称
     * 如果 LoadBalancer 被 LoadBalancerTracingWrapper 包装，则获取被包装的实际策略名
     *
     * @param loadBalancer 负载均衡器实例
     * @return 实际的策略名称
     */
    private String getActualStrategyName(final LoadBalancer loadBalancer) {
        if (loadBalancer == null) {
            return "Unknown";
        }

        // 检查是否是 TracingWrapper
        if (loadBalancer instanceof LoadBalancerTracingWrapper wrapper) {
            LoadBalancer delegate = wrapper.getDelegate();
            if (delegate != null) {
                return delegate.getClass().getSimpleName();
            }
        }

        return loadBalancer.getClass().getSimpleName();
    }
}
