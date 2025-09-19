package org.unreal.modelrouter.model;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.config.ConfigMergeService;
import org.unreal.modelrouter.fallback.FallbackManager;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.loadbalancer.LoadBalancerManager;
import org.unreal.modelrouter.ratelimit.RateLimitContext;
import org.unreal.modelrouter.ratelimit.RateLimitManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 模型服务注册表 - 重构版
 * 负责管理所有模型服务的注册、选择和状态管理
 * 支持动态配置更新和服务发现
 */
@Configuration
@EnableConfigurationProperties(ModelRouterProperties.class)
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
    private final org.unreal.modelrouter.config.ConfigurationHelper configurationHelper;

    // 配置和缓存
    private final ModelRouterProperties originalProperties; // 原始YAML配置
    private volatile Map<String, Object> currentConfig; // 当前运行时配置
    private final Map<String, WebClient> webClientCache;

    // 运行时服务配置缓存
    private volatile Map<String, ServiceRuntimeConfig> serviceConfigCache;

    public ModelServiceRegistry(ModelRouterProperties properties,
                                ServiceStateManager serviceStateManager,
                                RateLimitManager rateLimitManager,
                                LoadBalancerManager loadBalancerManager,
                                CircuitBreakerManager circuitBreakerManager,
                                FallbackManager fallbackManager,
                                ConfigMergeService configMergeService,
                                org.unreal.modelrouter.config.ConfigurationHelper configurationHelper) {
        this.originalProperties = properties;
        this.serviceStateManager = serviceStateManager;
        this.rateLimitManager = rateLimitManager;
        this.loadBalancerManager = loadBalancerManager;
        this.circuitBreakerManager = circuitBreakerManager;
        this.fallbackManager = fallbackManager;
        this.configMergeService = configMergeService;
        this.configurationHelper = configurationHelper;

        this.webClientCache = new ConcurrentHashMap<>();
        this.serviceConfigCache = new ConcurrentHashMap<>();
    }

    /**
     * 初始化服务注册表
     * 在Spring容器初始化完成后执行
     */
    @PostConstruct
    public void initialize() {
        LOGGER.info("正在初始化ModelServiceRegistry...");

        try {
            // 1. 合并YAML和持久化配置
            LOGGER.debug("开始合并YAML和持久化配置");
            refreshFromMergedConfig();
            LOGGER.debug("配置合并完成，当前配置大小: {}", currentConfig != null ? currentConfig.size() : 0);

            // 2. 初始化各个管理器
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

    /**
     * 从合并配置中刷新运行时配置
     * 当配置发生变化时调用此方法
     */
    public void refreshFromMergedConfig() {
        LOGGER.info("正在刷新运行时配置...");

        try {
            // 获取合并后的配置
            Map<String, Object> mergedConfig = configMergeService.getPersistedConfig();

            // 调整逻辑：如果有最新的配置文件，则使用最新的，如果没有，则使用默认配置
            if (mergedConfig == null || mergedConfig.isEmpty()) {
                LOGGER.info("未找到合并配置，使用默认配置");
                if (originalProperties != null) {
                    mergedConfig = configurationHelper.convertModelRouterPropertiesToMap(originalProperties);
                } else {
                    LOGGER.warn("原始配置也为空，使用空配置");
                    mergedConfig = new HashMap<>();
                }
            }

            this.currentConfig = mergedConfig;

            // 更新原始Properties对象（用于其他组件访问）
            updateOriginalPropertiesFromConfig(mergedConfig);

            // 重建服务配置缓存
            rebuildServiceConfigCache(mergedConfig);

            // 重新初始化负载均衡器
            reinitializeLoadBalancers();

            LOGGER.info("运行时配置刷新完成，当前包含 {} 个服务", serviceConfigCache.size());
        } catch (Exception e) {
            LOGGER.error("刷新运行时配置失败", e);
        }
    }

    /**
     * 选择服务实例
     */
    public ModelRouterProperties.ModelInstance selectInstance(ServiceType serviceType,
                                                              String modelName,
                                                              String clientIp) {
        if (serviceType == null) {
            throw new IllegalArgumentException("ServiceType cannot be null");
        }
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalArgumentException("ModelName cannot be null or empty");
        }

        String serviceKey = getServiceKey(serviceType);
        ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);

        if (runtimeConfig == null || runtimeConfig.getInstances().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No instances found for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        // 获取指定模型的所有实例
        List<ModelRouterProperties.ModelInstance> modelInstances = runtimeConfig.getInstances().stream()
                .filter(instance -> modelName.equals(instance.getName()))
                // 只选择status为空或者值等于"active"的实例
                .filter(instance -> "active".equals(instance.getStatus()))
                .collect(Collectors.toList());

        if (modelInstances.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No instances found for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        // 健康检查
        List<ModelRouterProperties.ModelInstance> healthyInstances = modelInstances.stream()
                .filter(instance -> serviceStateManager.isInstanceHealthy(serviceType.name(), instance))
                .collect(Collectors.toList());

        if (healthyInstances.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No healthy instances found for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        // 熔断器检查
        List<ModelRouterProperties.ModelInstance> availableInstances = healthyInstances.stream()
                .filter(instance -> circuitBreakerManager.canExecute(instance.getInstanceId(), instance.getBaseUrl()))
                .collect(Collectors.toList());

        if (availableInstances.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No available instances (all in circuit breaker state) for model '" + modelName + "'");
        }

        // 服务级限流检查
        RateLimitContext serviceContext = new RateLimitContext(serviceType, modelName, clientIp, 1);
        if (!rateLimitManager.tryAcquire(serviceContext)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Service rate limit exceeded for model '" + modelName + "'");
        }

        // 负载均衡选择实例
        LoadBalancer loadBalancer = loadBalancerManager.getLoadBalancer(serviceType);
        ModelRouterProperties.ModelInstance selectedInstance = selectInstanceWithRateLimit(
                availableInstances, loadBalancer, clientIp, serviceType, modelName);

        if (selectedInstance == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No available instances found after all checks for model '" + modelName + "'");
        }

        loadBalancer.recordCall(selectedInstance);
        return selectedInstance;
    }

    /**
     * 选择实例并进行实例级限流检查
     */
    private ModelRouterProperties.ModelInstance selectInstanceWithRateLimit(
            List<ModelRouterProperties.ModelInstance> availableInstances,
            LoadBalancer loadBalancer,
            String clientIp,
            ServiceType serviceType,
            String modelName) {

        List<ModelRouterProperties.ModelInstance> candidateInstances = new ArrayList<>(availableInstances);
        int maxAttempts = Math.min(candidateInstances.size(), 3);

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (candidateInstances.isEmpty()) {
                break;
            }

            ModelRouterProperties.ModelInstance candidate = loadBalancer.selectInstance(candidateInstances, clientIp, serviceType.name().toLowerCase());

            // 实例级限流检查
            RateLimitContext instanceContext = new RateLimitContext(
                    serviceType, modelName, clientIp, 1,
                    candidate.getInstanceId(), candidate.getBaseUrl());

            if (!rateLimitManager.tryAcquireInstance(instanceContext)) {
                LOGGER.warn("Instance rate limit exceeded for instance: {}, trying next instance",
                        candidate.getInstanceId());
                candidateInstances.remove(candidate);
                continue;
            }

            return candidate;
        }

        return null;
    }

    /**
     * 获取WebClient
     */
    public WebClient getClient(ServiceType serviceType, String modelName, String clientIp) {
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(serviceType, modelName, clientIp);
        return getWebClient(selectedInstance);
    }

    public WebClient getClient(ServiceType serviceType, String modelName) {
        return getClient(serviceType, modelName, null);
    }

    /**
     * 获取模型路径
     */
    public String getModelPath(ServiceType serviceType, String modelName) {
        String serviceKey = getServiceKey(serviceType);
        ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);

        if (runtimeConfig == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No service found for service type '" + serviceType + "'");
        }

        Optional<ModelRouterProperties.ModelInstance> instance = runtimeConfig.getInstances().stream()
                .filter(inst -> modelName.equals(inst.getName()))
                .findFirst();

        return instance.map(ModelRouterProperties.ModelInstance::getPath)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No model named '" + modelName + "' found for service type '" + serviceType + "'"));
    }

    /**
     * 获取服务适配器
     */
    public String getServiceAdapter(ServiceType serviceType) {
        String serviceKey = getServiceKey(serviceType);
        ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);

        if (runtimeConfig != null && runtimeConfig.getAdapter() != null) {
            return runtimeConfig.getAdapter();
        }

        // 返回全局适配器或默认值
        return Optional.ofNullable(originalProperties.getAdapter()).orElse("normal");
    }

    /**
     * 记录调用完成
     */
    public void recordCallComplete(ServiceType serviceType, ModelRouterProperties.ModelInstance instance) {
        LoadBalancer loadBalancer = loadBalancerManager.getLoadBalancer(serviceType);
        if (loadBalancer != null) {
            loadBalancer.recordCallComplete(instance);
        }
        circuitBreakerManager.recordSuccess(instance.getInstanceId(), instance.getBaseUrl());
    }

    /**
     * 记录调用失败
     */
    public void recordCallFailure(ServiceType serviceType, ModelRouterProperties.ModelInstance instance) {
        LoadBalancer loadBalancer = loadBalancerManager.getLoadBalancer(serviceType);
        if (loadBalancer != null) {
            loadBalancer.recordCallFailure(instance);
        }
        circuitBreakerManager.recordFailure(instance.getInstanceId(), instance.getBaseUrl());
    }

    /**
     * 获取实例的熔断器状态
     */
    public CircuitBreaker.State getInstanceCircuitBreakerState(ModelRouterProperties.ModelInstance instance) {
        return circuitBreakerManager.getState(instance.getInstanceId(), instance.getBaseUrl());
    }

    // ==================== 查询方法 ====================

    /**
     * 获取所有可用的服务类型
     */
    public Set<ServiceType> getAvailableServiceTypes() {
        return serviceConfigCache.entrySet().stream()
                .filter(entry -> !entry.getValue().getInstances().isEmpty())
                .map(entry -> parseServiceType(entry.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 获取指定服务类型的所有可用模型
     */
    public Set<String> getAvailableModels(ServiceType serviceType) {
        String serviceKey = getServiceKey(serviceType);
        ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);

        if (runtimeConfig == null) {
            return Collections.emptySet();
        }

        return runtimeConfig.getInstances().stream()
                .map(ModelRouterProperties.ModelInstance::getName)
                .collect(Collectors.toSet());
    }

    /**
     * 获取所有实例信息
     */
    public Map<ServiceType, List<ModelRouterProperties.ModelInstance>> getAllInstances() {
        Map<ServiceType, List<ModelRouterProperties.ModelInstance>> result = new HashMap<>();

        for (Map.Entry<String, ServiceRuntimeConfig> entry : serviceConfigCache.entrySet()) {
            ServiceType serviceType = parseServiceType(entry.getKey());
            if (serviceType != null) {
                result.put(serviceType, new ArrayList<>(entry.getValue().getInstances()));
            }
        }

        return result;
    }

    /**
     * 获取服务配置
     */
    public ModelRouterProperties.ServiceConfig getServiceConfig(ServiceType serviceType) {
        String serviceKey = getServiceKey(serviceType);
        ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);

        if (runtimeConfig == null) {
            return null;
        }

        // 构造ServiceConfig对象返回
        ModelRouterProperties.ServiceConfig serviceConfig = new ModelRouterProperties.ServiceConfig();
        serviceConfig.setInstances(new ArrayList<>(runtimeConfig.getInstances()));
        serviceConfig.setAdapter(runtimeConfig.getAdapter());
        serviceConfig.setLoadBalance(runtimeConfig.getLoadBalanceConfig());
        serviceConfig.setRateLimit(runtimeConfig.getRateLimitConfig());
        serviceConfig.setCircuitBreaker(runtimeConfig.getCircuitBreakerConfig());
        serviceConfig.setFallback(runtimeConfig.getFallbackConfig());

        return serviceConfig;
    }

    /**
     * 获取负载均衡策略
     */
    public String getLoadBalanceStrategy(ServiceType serviceType) {
        LoadBalancer loadBalancer = loadBalancerManager.getLoadBalancer(serviceType);
        return loadBalancer != null ? loadBalancer.getClass().getSimpleName() : "Unknown";
    }

    public FallbackManager getFallbackManager() {
        return fallbackManager;
    }

    // ==================== 动态更新方法 ====================

    /**
     * 更新服务实例
     */
    public void updateServiceInstances(ServiceType serviceType, List<ModelRouterProperties.ModelInstance> instances) {
        String serviceKey = getServiceKey(serviceType);
        ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);

        if (runtimeConfig != null) {
            runtimeConfig.setInstances(new ArrayList<>(instances));
            LOGGER.info("已更新服务 {} 的实例，共 {} 个实例", serviceType, instances.size());
        }
    }

    /**
     * 更新服务适配器
     */
    public void updateServiceAdapter(ServiceType serviceType, String adapter) {
        String serviceKey = getServiceKey(serviceType);
        ServiceRuntimeConfig runtimeConfig = serviceConfigCache.get(serviceKey);

        if (runtimeConfig != null) {
            runtimeConfig.setAdapter(adapter);
            LOGGER.info("已更新服务 {} 的适配器为: {}", serviceType, adapter);
        }
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 初始化各个管理器
     */
    private void initializeManagers() {
        // 初始化熔断器管理器
        circuitBreakerManager.initialize(originalProperties);

        // 初始化降级管理器
        fallbackManager.initialize(originalProperties);

        LOGGER.debug("所有管理器初始化完成");
    }

    /**
     * 重新初始化负载均衡器
     */
    private void reinitializeLoadBalancers() {
        try {
            for (Map.Entry<String, ServiceRuntimeConfig> entry : serviceConfigCache.entrySet()) {
                ServiceType serviceType = parseServiceType(entry.getKey());
                if (serviceType != null && !entry.getValue().getInstances().isEmpty()) {
                    loadBalancerManager.reinitializeLoadBalancer(serviceType, entry.getValue().getLoadBalanceConfig());
                }
            }
            LOGGER.debug("负载均衡器重新初始化完成");
        } catch (Exception e) {
            LOGGER.warn("重新初始化负载均衡器时发生错误: {}", e.getMessage());
        }
    }

    /**
     * 从合并配置更新原始Properties对象
     */
    @SuppressWarnings("unchecked")
    private void updateOriginalPropertiesFromConfig(Map<String, Object> mergedConfig) {
        try {
            // 更新全局配置
            if (mergedConfig.containsKey("adapter")) {
                originalProperties.setAdapter((String) mergedConfig.get("adapter"));
            }

            // 更新服务配置
            if (mergedConfig.containsKey("services")) {
                Map<String, Object> servicesMap = (Map<String, Object>) mergedConfig.get("services");
                Map<String, ModelRouterProperties.ServiceConfig> serviceConfigs = new HashMap<>();

                // 添加空值检查，防止NullPointerException
                if (servicesMap != null) {
                    for (Map.Entry<String, Object> entry : servicesMap.entrySet()) {
                        String serviceKey = entry.getKey();
                        Map<String, Object> serviceConfigMap = (Map<String, Object>) entry.getValue();
                        ModelRouterProperties.ServiceConfig serviceConfig =
                                configurationHelper.convertMapToServiceConfig(serviceConfigMap);
                        serviceConfigs.put(serviceKey, serviceConfig);
                    }

                    originalProperties.setServices(serviceConfigs);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("更新原始Properties对象时发生错误: {}", e.getMessage());
        }
    }

    /**
     * 重建服务配置缓存
     */
    @SuppressWarnings("unchecked")
    private void rebuildServiceConfigCache(Map<String, Object> mergedConfig) {
        Map<String, ServiceRuntimeConfig> newCache = new ConcurrentHashMap<>();

        if (mergedConfig.containsKey("services")) {
            Map<String, Object> servicesMap = (Map<String, Object>) mergedConfig.get("services");
            
            // 添加空值检查，防止NullPointerException
            if (servicesMap != null) {
                for (Map.Entry<String, Object> entry : servicesMap.entrySet()) {
                    String serviceKey = entry.getKey();
                    Map<String, Object> serviceConfigMap = (Map<String, Object>) entry.getValue();

                    try {
                        ServiceRuntimeConfig runtimeConfig = buildServiceRuntimeConfig(serviceConfigMap);
                        newCache.put(serviceKey, runtimeConfig);
                    } catch (Exception e) {
                        LOGGER.warn("构建服务 {} 的运行时配置失败: {}", serviceKey, e.getMessage());
                    }
                }
            }
        }

        this.serviceConfigCache = newCache;
        LOGGER.debug("服务配置缓存重建完成，包含 {} 个服务", newCache.size());
    }

    /**
     * 构建服务运行时配置
     */
    @SuppressWarnings("unchecked")
    private ServiceRuntimeConfig buildServiceRuntimeConfig(Map<String, Object> serviceConfigMap) {
        ServiceRuntimeConfig runtimeConfig = new ServiceRuntimeConfig();

        // 解析实例列表
        if (serviceConfigMap.containsKey("instances")) {
            List<Map<String, Object>> instanceList = (List<Map<String, Object>>) serviceConfigMap.get("instances");
            List<ModelRouterProperties.ModelInstance> instances = instanceList.stream()
                    .map(configurationHelper::convertMapToInstance)
                    .collect(Collectors.toList());
            runtimeConfig.setInstances(instances);
        } else {
            runtimeConfig.setInstances(new ArrayList<>());
        }

        // 解析适配器
        runtimeConfig.setAdapter((String) serviceConfigMap.get("adapter"));

        // 解析负载均衡配置
        if (serviceConfigMap.containsKey("loadBalance")) {
            Map<String, Object> loadBalanceMap = (Map<String, Object>) serviceConfigMap.get("loadBalance");
            ModelRouterProperties.LoadBalanceConfig loadBalanceConfig = new ModelRouterProperties.LoadBalanceConfig();
            if (loadBalanceMap.containsKey("type")) {
                loadBalanceConfig.setType((String) loadBalanceMap.get("type"));
            }
            if (loadBalanceMap.containsKey("hashAlgorithm")) {
                loadBalanceConfig.setHashAlgorithm((String) loadBalanceMap.get("hashAlgorithm"));
            }
            runtimeConfig.setLoadBalanceConfig(loadBalanceConfig);
        } else {
            runtimeConfig.setLoadBalanceConfig(configurationHelper.createDefaultLoadBalanceConfig());
        }

        // 解析其他配置（限流、熔断器、降级）
        parseAdditionalConfigs(serviceConfigMap, runtimeConfig);

        return runtimeConfig;
    }

    /**
     * 解析额外配置（限流、熔断器、降级）
     */
    @SuppressWarnings("unchecked")
    private void parseAdditionalConfigs(final Map<String, Object> serviceConfigMap,
                                        final ServiceRuntimeConfig runtimeConfig) {
        // 限流配置
        if (serviceConfigMap.containsKey("rateLimit")) {
            Map<String, Object> rateLimitMap = (Map<String, Object>) serviceConfigMap.get("rateLimit");
            if (rateLimitMap != null) {
                ModelRouterProperties.RateLimitConfig rateLimitConfig =
                        new ModelRouterProperties.RateLimitConfig();
                configurationHelper.updateRateLimitConfig(rateLimitConfig, rateLimitMap);
                runtimeConfig.setRateLimitConfig(rateLimitConfig);
            } else {
                runtimeConfig.setRateLimitConfig(rateLimitManager.getDefaultRateLimitConfig());
            }
        }

        // 熔断器配置
        if (serviceConfigMap.containsKey("circuitBreaker")) {
            Map<String, Object> circuitBreakerMap =
                    (Map<String, Object>) serviceConfigMap.get("circuitBreaker");
            if (circuitBreakerMap != null) {
                ModelRouterProperties.CircuitBreakerConfig circuitBreakerConfig =
                        new ModelRouterProperties.CircuitBreakerConfig();
                configurationHelper.updateCircuitBreakerConfig(circuitBreakerConfig, circuitBreakerMap);
                runtimeConfig.setCircuitBreakerConfig(circuitBreakerConfig);
            } else {
                runtimeConfig.setCircuitBreakerConfig(circuitBreakerManager.getDefaultCircuitBreakerConfig());
            }
        }

        // 降级配置
        if (serviceConfigMap.containsKey("fallback")) {
            Map<String, Object> fallbackMap = (Map<String, Object>) serviceConfigMap.get("fallback");
            if (fallbackMap != null) {
                ModelRouterProperties.FallbackConfig fallbackConfig =
                        new ModelRouterProperties.FallbackConfig();
                configurationHelper.updateFallbackConfig(fallbackConfig, fallbackMap);
                runtimeConfig.setFallbackConfig(fallbackConfig);
            } else {
                runtimeConfig.setFallbackConfig(fallbackManager.getDefaultFallbackConfig());
            }
        }
    }


    /**
     * 获取WebClient
     */
    private WebClient getWebClient(final ModelRouterProperties.ModelInstance instance) {
        String key = instance.getBaseUrl();
        return webClientCache.computeIfAbsent(key, url -> {
            // 尝试获取追踪WebClient工厂
            try {
                org.unreal.modelrouter.tracing.client.TracingWebClientFactory tracingFactory = 
                    org.unreal.modelrouter.util.ApplicationContextProvider.getBean(
                        org.unreal.modelrouter.tracing.client.TracingWebClientFactory.class);
                return tracingFactory.createTracingWebClient(url);
            } catch (Exception e) {
                // 如果追踪功能不可用，创建普通WebClient
                return WebClient.builder().baseUrl(url).build();
            }
        });
    }

    /**
     * 获取服务键
     */
    private String getServiceKey(final ServiceType serviceType) {
        return configurationHelper.getServiceConfigKey(serviceType);
    }

    /**
     * 解析服务类型
     */
    private ServiceType parseServiceType(final String serviceKey) {
        return configurationHelper.parseServiceType(serviceKey);
    }

    /**
     * 记录当前配置信息
     */
    private void logCurrentConfiguration() {
        LOGGER.info("当前服务配置概览:");
        for (Map.Entry<String, ServiceRuntimeConfig> entry : serviceConfigCache.entrySet()) {
            String serviceKey = entry.getKey();
            ServiceRuntimeConfig config = entry.getValue();
            LOGGER.info("  服务 {}: {} 个实例, 适配器={}, 负载均衡={}",
                    serviceKey,
                    config.getInstances().size(),
                    config.getAdapter() != null ? config.getAdapter() : "默认",
                    config.getLoadBalanceConfig() != null ? config.getLoadBalanceConfig().getType() : "默认");
        }
    }

}