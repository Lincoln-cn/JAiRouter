package org.unreal.modelrouter.router.model;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.config.core.ConfigMergeService;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.router.fallback.FallbackManager;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancerManager;
import org.unreal.modelrouter.router.loadbalancer.monitor.RoutingMonitorService;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;
import org.unreal.modelrouter.monitor.tracing.wrapper.LoadBalancerTracingWrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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

    // 配置和缓存
    private final ModelRouterProperties originalProperties;
    private volatile Map<String, Object> currentConfig;
    private volatile Map<String, ServiceRuntimeConfig> serviceConfigCache;

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
        this.configBuilder = new ServiceConfigBuilder(configConverterHelper, rateLimitManager, circuitBreakerManager, fallbackManager);
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
            Map<String, Object> mergedConfig = configMergeService.getPersistedConfig();

            if (mergedConfig == null || mergedConfig.isEmpty()) {
                LOGGER.info("未找到合并配置，使用默认配置");
                if (originalProperties != null) {
                    mergedConfig = configConverterHelper.convertModelRouterPropertiesToMap(originalProperties);
                } else {
                    LOGGER.warn("原始配置也为空，使用空配置");
                    mergedConfig = new HashMap<>();
                }
            }

            this.currentConfig = mergedConfig;
            updateOriginalPropertiesFromConfig(mergedConfig);
            this.serviceConfigCache = configBuilder.rebuildServiceConfigCache(mergedConfig);
            reinitializeLoadBalancers();

            LOGGER.info("运行时配置刷新完成，当前包含 {} 个服务", serviceConfigCache.size());
        } catch (Exception e) {
            LOGGER.error("刷新运行时配置失败", e);
        }
    }

    /**
     * 选择服务实例
     */
    public ModelRouterProperties.ModelInstance selectInstance(final ServiceType serviceType,
                                                              final String modelName,
                                                              final String clientIp) {
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

        List<ModelRouterProperties.ModelInstance> availableInstances =
                selectInstanceOptimizer.filterAvailableInstances(
                        runtimeConfig.getInstances(), modelName, serviceType);

        if (availableInstances.isEmpty()) {
            throw instanceSelector.createAppropriateException(serviceType, modelName, runtimeConfig.getInstances());
        }

        LoadBalancer loadBalancer = loadBalancerManager.getLoadBalancer(serviceType);
        if (loadBalancer == null) {
            loadBalancer = loadBalancerManager.getLoadBalancer(null);
        }

        ModelRouterProperties.ModelInstance selectedInstance =
                instanceSelector.selectWithRateLimit(availableInstances, loadBalancer, clientIp, serviceType, modelName);

        if (selectedInstance == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "All instances rate limited for model '" + modelName + "'");
        }

        // v2.7.0: 记录路由选择事件
        String strategy = getActualStrategyName(loadBalancer);
        routingMonitorService.recordSelection(
                serviceType.name().toLowerCase(),
                modelName,
                strategy,
                selectedInstance,
                clientIp,
                availableInstances.size());

        loadBalancer.recordCall(selectedInstance);
        return selectedInstance;
    }

    public WebClient getClient(final ServiceType serviceType, final String modelName, final String clientIp) {
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(serviceType, modelName, clientIp);
        return webClientCacheManager.getOrCreate(selectedInstance.getBaseUrl());
    }

    public WebClient getClient(final ServiceType serviceType, final String modelName) {
        return getClient(serviceType, modelName, null);
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
        LoadBalancer loadBalancer = loadBalancerManager.getLoadBalancer(serviceType);
        if (loadBalancer != null) {
            loadBalancer.recordCallComplete(instance);
        }
        circuitBreakerManager.recordSuccess(instance.getInstanceId(), instance.getBaseUrl());
    }

    public void recordCallFailure(final ServiceType serviceType, final ModelRouterProperties.ModelInstance instance) {
        LoadBalancer loadBalancer = loadBalancerManager.getLoadBalancer(serviceType);
        if (loadBalancer != null) {
            loadBalancer.recordCallFailure(instance);
        }
        circuitBreakerManager.recordFailure(instance.getInstanceId(), instance.getBaseUrl());
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

    public void updateServiceInstances(final ServiceType serviceType, final List<ModelRouterProperties.ModelInstance> instances) {
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
            ModelRouterProperties.ServiceConfig serviceConfig = configConverterHelper.convertMapToServiceConfig(serviceConfigMap);
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
