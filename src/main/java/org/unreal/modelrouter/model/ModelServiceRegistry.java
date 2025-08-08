package org.unreal.modelrouter.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.checker.ServerChecker;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.config.ConfigMergeService;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.factory.ComponentFactory;
import org.unreal.modelrouter.fallback.FallbackManager;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.loadbalancer.LoadBalancerManager;
import org.unreal.modelrouter.ratelimit.RateLimitContext;
import org.unreal.modelrouter.ratelimit.RateLimitManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties(ModelRouterProperties.class)
public class ModelServiceRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ModelServiceRegistry.class);

    public enum ServiceType {
        chat, embedding, rerank, tts, stt, imgGen, imgEdit
    }

    private final Map<ServiceType, List<ModelRouterProperties.ModelInstance>> instanceRegistry;
    private final LoadBalancerManager loadBalancerManager;
    private final Map<ServiceType, String> serviceAdapters;
    private final Map<String, WebClient> webClientCache;
    private final String globalAdapter;
    private final ServiceStateManager serviceStateManager;
    private final RateLimitManager rateLimitManager;
    private final CircuitBreakerManager circuitBreakerManager;
    private final ComponentFactory componentFactory;
    private final FallbackManager fallbackManager;
    private final ModelRouterProperties properties;
    private final ConfigMergeService configMergeService;

    public ModelServiceRegistry(ModelRouterProperties properties,
                                ServiceStateManager serviceStateManager,
                                RateLimitManager rateLimitManager,
                                LoadBalancerManager loadBalancerManager,
                                CircuitBreakerManager circuitBreakerManager,
                                ComponentFactory componentFactory,
                                FallbackManager fallbackManager,
                                ConfigMergeService configMergeService) {
        this.properties = properties;
        this.webClientCache = new ConcurrentHashMap<>();
        this.serviceStateManager = serviceStateManager;
        this.rateLimitManager = rateLimitManager;
        this.loadBalancerManager = loadBalancerManager;
        this.circuitBreakerManager = circuitBreakerManager;
        this.componentFactory = componentFactory;
        this.fallbackManager = fallbackManager;
        this.configMergeService = configMergeService;

        // 初始化熔断器管理器
        this.circuitBreakerManager.initialize(properties);
        
        // 初始化降级管理器
        this.fallbackManager.initialize(properties);

        this.globalAdapter = Optional.ofNullable(properties.getAdapter()).orElse("normal");

        // 合并配置
        Map<String, Object> mergedConfig = configMergeService.mergeConfigurations();
        
        // 更新properties中的配置
        updatePropertiesFromMergedConfig(mergedConfig);

        Map<String, ModelRouterProperties.ServiceConfig> services = Optional.ofNullable(properties.getServices())
                .orElse(Map.of());

        // 构建实例注册表、适配器映射
        this.instanceRegistry = new EnumMap<>(ServiceType.class);
        this.serviceAdapters = new EnumMap<>(ServiceType.class);

        for (ServiceType serviceType : ServiceType.values()) {
            String serviceKey = serviceType.name().toLowerCase().replace("_", "-");
            ModelRouterProperties.ServiceConfig serviceConfig = services.get(serviceKey);

            String adapter = Optional.ofNullable(serviceConfig)
                    .map(ModelRouterProperties.ServiceConfig::getAdapter)
                    .orElse(globalAdapter);

            List<ModelRouterProperties.ModelInstance> instances = Optional.ofNullable(serviceConfig)
                    .map(ModelRouterProperties.ServiceConfig::getInstances)
                    .orElse(List.of());

            this.instanceRegistry.put(serviceType, new ArrayList<>(instances));
            this.serviceAdapters.put(serviceType, adapter);
        }
    }

    /**
     * 根据合并后的配置更新ModelRouterProperties
     * @param mergedConfig 合并后的配置
     */
    @SuppressWarnings("unchecked")
    private void updatePropertiesFromMergedConfig(Map<String, Object> mergedConfig) {
        try {
            // 更新全局配置
            if (mergedConfig.containsKey("adapter")) {
                properties.setAdapter((String) mergedConfig.get("adapter"));
            }

            if (mergedConfig.containsKey("loadBalance")) {
                Map<String, Object> loadBalanceMap = (Map<String, Object>) mergedConfig.get("loadBalance");
                ModelRouterProperties.LoadBalanceConfig loadBalanceConfig = properties.getLoadBalance();
                if (loadBalanceMap.containsKey("type")) {
                    loadBalanceConfig.setType((String) loadBalanceMap.get("type"));
                }
                if (loadBalanceMap.containsKey("hashAlgorithm")) {
                    loadBalanceConfig.setHashAlgorithm((String) loadBalanceMap.get("hashAlgorithm"));
                }
            }

            if (mergedConfig.containsKey("rateLimit")) {
                Map<String, Object> rateLimitMap = (Map<String, Object>) mergedConfig.get("rateLimit");
                updateRateLimitConfig(properties.getRateLimit(), rateLimitMap);
            }

            if (mergedConfig.containsKey("circuitBreaker")) {
                Map<String, Object> circuitBreakerMap = (Map<String, Object>) mergedConfig.get("circuitBreaker");
                updateCircuitBreakerConfig(properties.getCircuitBreaker(), circuitBreakerMap);
            }

            if (mergedConfig.containsKey("fallback")) {
                Map<String, Object> fallbackMap = (Map<String, Object>) mergedConfig.get("fallback");
                updateFallbackConfig(properties.getFallback(), fallbackMap);
            }

            // 更新服务配置
            if (mergedConfig.containsKey("services")) {
                Map<String, Object> servicesMap = (Map<String, Object>) mergedConfig.get("services");
                Map<String, ModelRouterProperties.ServiceConfig> serviceConfigs = new HashMap<>();
                
                for (Map.Entry<String, Object> entry : servicesMap.entrySet()) {
                    String serviceKey = entry.getKey();
                    Map<String, Object> serviceConfigMap = (Map<String, Object>) entry.getValue();
                    ModelRouterProperties.ServiceConfig serviceConfig = convertMapToServiceConfig(serviceConfigMap);
                    serviceConfigs.put(serviceKey, serviceConfig);
                }
                
                properties.setServices(serviceConfigs);
            }
        } catch (Exception e) {
            logger.error("更新配置时出错: " + e.getMessage(), e);
        }
    }

    /**
     * 更新限流配置
     * @param rateLimit 目标限流配置对象
     * @param map 配置Map
     */
    @SuppressWarnings("unchecked")
    private void updateRateLimitConfig(ModelRouterProperties.RateLimitConfig rateLimit, Map<String, Object> map) {
        if (map.containsKey("enabled")) {
            rateLimit.setEnabled((Boolean) map.get("enabled"));
        }
        if (map.containsKey("algorithm")) {
            rateLimit.setAlgorithm((String) map.get("algorithm"));
        }
        if (map.containsKey("capacity")) {
            rateLimit.setCapacity(((Number) map.get("capacity")).longValue());
        }
        if (map.containsKey("rate")) {
            rateLimit.setRate(((Number) map.get("rate")).longValue());
        }
        if (map.containsKey("scope")) {
            rateLimit.setScope((String) map.get("scope"));
        }
        if (map.containsKey("key")) {
            rateLimit.setKey((String) map.get("key"));
        }
        if (map.containsKey("clientIpEnable")) {
            rateLimit.setClientIpEnable((Boolean) map.get("clientIpEnable"));
        }
    }

    /**
     * 更新熔断器配置
     * @param circuitBreaker 目标熔断器配置对象
     * @param map 配置Map
     */
    @SuppressWarnings("unchecked")
    private void updateCircuitBreakerConfig(ModelRouterProperties.CircuitBreakerConfig circuitBreaker, Map<String, Object> map) {
        if (map.containsKey("enabled")) {
            circuitBreaker.setEnabled((Boolean) map.get("enabled"));
        }
        if (map.containsKey("failureThreshold")) {
            circuitBreaker.setFailureThreshold((Integer) map.get("failureThreshold"));
        }
        if (map.containsKey("timeout")) {
            circuitBreaker.setTimeout(((Number) map.get("timeout")).longValue());
        }
        if (map.containsKey("successThreshold")) {
            circuitBreaker.setSuccessThreshold((Integer) map.get("successThreshold"));
        }
    }

    /**
     * 更新降级配置
     * @param fallback 目标降级配置对象
     * @param map 配置Map
     */
    @SuppressWarnings("unchecked")
    private void updateFallbackConfig(ModelRouterProperties.FallbackConfig fallback, Map<String, Object> map) {
        if (map.containsKey("enabled")) {
            fallback.setEnabled((Boolean) map.get("enabled"));
        }
        if (map.containsKey("strategy")) {
            fallback.setStrategy((String) map.get("strategy"));
        }
        if (map.containsKey("cacheSize")) {
            fallback.setCacheSize((Integer) map.get("cacheSize"));
        }
        if (map.containsKey("cacheTtl")) {
            fallback.setCacheTtl(((Number) map.get("cacheTtl")).longValue());
        }
    }

    /**
     * 将Map转换为ServiceConfig对象
     * @param map Map对象
     * @return 转换后的ServiceConfig
     */
    @SuppressWarnings("unchecked")
    private ModelRouterProperties.ServiceConfig convertMapToServiceConfig(Map<String, Object> map) {
        ModelRouterProperties.ServiceConfig serviceConfig = new ModelRouterProperties.ServiceConfig();

        if (map.containsKey("loadBalance")) {
            Map<String, Object> loadBalanceMap = (Map<String, Object>) map.get("loadBalance");
            ModelRouterProperties.LoadBalanceConfig loadBalanceConfig = new ModelRouterProperties.LoadBalanceConfig();
            if (loadBalanceMap.containsKey("type")) {
                loadBalanceConfig.setType((String) loadBalanceMap.get("type"));
            }
            if (loadBalanceMap.containsKey("hashAlgorithm")) {
                loadBalanceConfig.setHashAlgorithm((String) loadBalanceMap.get("hashAlgorithm"));
            }
            serviceConfig.setLoadBalance(loadBalanceConfig);
        }

        if (map.containsKey("instances")) {
            List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
            List<Map<String, Object>> instanceList = (List<Map<String, Object>>) map.get("instances");
            for (Map<String, Object> instanceMap : instanceList) {
                instances.add(convertMapToInstance(instanceMap));
            }
            serviceConfig.setInstances(instances);
        }

        if (map.containsKey("adapter")) {
            serviceConfig.setAdapter((String) map.get("adapter"));
        }

        if (map.containsKey("rateLimit")) {
            Map<String, Object> rateLimitMap = (Map<String, Object>) map.get("rateLimit");
            ModelRouterProperties.RateLimitConfig rateLimitConfig = new ModelRouterProperties.RateLimitConfig();
            if (rateLimitMap.containsKey("enabled")) {
                rateLimitConfig.setEnabled((Boolean) rateLimitMap.get("enabled"));
            }
            if (rateLimitMap.containsKey("algorithm")) {
                rateLimitConfig.setAlgorithm((String) rateLimitMap.get("algorithm"));
            }
            if (rateLimitMap.containsKey("capacity")) {
                rateLimitConfig.setCapacity(((Number) rateLimitMap.get("capacity")).longValue());
            }
            if (rateLimitMap.containsKey("rate")) {
                rateLimitConfig.setRate(((Number) rateLimitMap.get("rate")).longValue());
            }
            if (rateLimitMap.containsKey("scope")) {
                rateLimitConfig.setScope((String) rateLimitMap.get("scope"));
            }
            if (rateLimitMap.containsKey("key")) {
                rateLimitConfig.setKey((String) rateLimitMap.get("key"));
            }
            if (rateLimitMap.containsKey("clientIpEnable")) {
                rateLimitConfig.setClientIpEnable((Boolean) rateLimitMap.get("clientIpEnable"));
            }
            serviceConfig.setRateLimit(rateLimitConfig);
        }

        if (map.containsKey("circuitBreaker")) {
            Map<String, Object> circuitBreakerMap = (Map<String, Object>) map.get("circuitBreaker");
            ModelRouterProperties.CircuitBreakerConfig circuitBreakerConfig = new ModelRouterProperties.CircuitBreakerConfig();
            if (circuitBreakerMap.containsKey("enabled")) {
                circuitBreakerConfig.setEnabled((Boolean) circuitBreakerMap.get("enabled"));
            }
            if (circuitBreakerMap.containsKey("failureThreshold")) {
                circuitBreakerConfig.setFailureThreshold(((Number) circuitBreakerMap.get("failureThreshold")).intValue());
            }
            if (circuitBreakerMap.containsKey("timeout")) {
                circuitBreakerConfig.setTimeout(((Number) circuitBreakerMap.get("timeout")).longValue());
            }
            if (circuitBreakerMap.containsKey("successThreshold")) {
                circuitBreakerConfig.setSuccessThreshold(((Number) circuitBreakerMap.get("successThreshold")).intValue());
            }
            serviceConfig.setCircuitBreaker(circuitBreakerConfig);
        }

        if (map.containsKey("fallback")) {
            Map<String, Object> fallbackMap = (Map<String, Object>) map.get("fallback");
            ModelRouterProperties.FallbackConfig fallbackConfig = new ModelRouterProperties.FallbackConfig();
            if (fallbackMap.containsKey("enabled")) {
                fallbackConfig.setEnabled((Boolean) fallbackMap.get("enabled"));
            }
            if (fallbackMap.containsKey("strategy")) {
                fallbackConfig.setStrategy((String) fallbackMap.get("strategy"));
            }
            if (fallbackMap.containsKey("cacheSize")) {
                fallbackConfig.setCacheSize(((Number) fallbackMap.get("cacheSize")).intValue());
            }
            if (fallbackMap.containsKey("cacheTtl")) {
                fallbackConfig.setCacheTtl(((Number) fallbackMap.get("cacheTtl")).longValue());
            }
            serviceConfig.setFallback(fallbackConfig);
        }

        return serviceConfig;
    }

    /**
     * 将Map转换为ModelInstance对象
     * @param map Map对象
     * @return 转换后的ModelInstance
     */
    @SuppressWarnings("unchecked")
    private ModelRouterProperties.ModelInstance convertMapToInstance(Map<String, Object> map) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName((String) map.get("name"));
        instance.setBaseUrl((String) map.get("baseUrl"));
        instance.setPath((String) map.get("path"));
        if (map.containsKey("weight")) {
            instance.setWeight(((Number) map.get("weight")).intValue());
        } else {
            instance.setWeight(1);
        }

        // 设置限流配置
        if (map.containsKey("rateLimit")) {
            Map<String, Object> rateLimitMap = (Map<String, Object>) map.get("rateLimit");
            ModelRouterProperties.RateLimitConfig rateLimitConfig = new ModelRouterProperties.RateLimitConfig();
            if (rateLimitMap.containsKey("enabled")) {
                rateLimitConfig.setEnabled((Boolean) rateLimitMap.get("enabled"));
            }
            if (rateLimitMap.containsKey("algorithm")) {
                rateLimitConfig.setAlgorithm((String) rateLimitMap.get("algorithm"));
            }
            if (rateLimitMap.containsKey("capacity")) {
                rateLimitConfig.setCapacity(((Number) rateLimitMap.get("capacity")).longValue());
            }
            if (rateLimitMap.containsKey("rate")) {
                rateLimitConfig.setRate(((Number) rateLimitMap.get("rate")).longValue());
            }
            if (rateLimitMap.containsKey("scope")) {
                rateLimitConfig.setScope((String) rateLimitMap.get("scope"));
            }
            if (rateLimitMap.containsKey("key")) {
                rateLimitConfig.setKey((String) rateLimitMap.get("key"));
            }
            if (rateLimitMap.containsKey("clientIpEnable")) {
                rateLimitConfig.setClientIpEnable((Boolean) rateLimitMap.get("clientIpEnable"));
            }
            instance.setRateLimit(rateLimitConfig);
        }

        // 设置熔断器配置
        if (map.containsKey("circuitBreaker")) {
            Map<String, Object> circuitBreakerMap = (Map<String, Object>) map.get("circuitBreaker");
            ModelRouterProperties.CircuitBreakerConfig circuitBreakerConfig = new ModelRouterProperties.CircuitBreakerConfig();
            if (circuitBreakerMap.containsKey("enabled")) {
                circuitBreakerConfig.setEnabled((Boolean) circuitBreakerMap.get("enabled"));
            }
            if (circuitBreakerMap.containsKey("failureThreshold")) {
                circuitBreakerConfig.setFailureThreshold(((Number) circuitBreakerMap.get("failureThreshold")).intValue());
            }
            if (circuitBreakerMap.containsKey("timeout")) {
                circuitBreakerConfig.setTimeout(((Number) circuitBreakerMap.get("timeout")).longValue());
            }
            if (circuitBreakerMap.containsKey("successThreshold")) {
                circuitBreakerConfig.setSuccessThreshold(((Number) circuitBreakerMap.get("successThreshold")).intValue());
            }
            instance.setCircuitBreaker(circuitBreakerConfig);
        }

        return instance;
    }

    // ... 其余原有方法保持不变
    public ModelRouterProperties.ModelInstance selectInstance(ServiceType serviceType,
                                                              String modelName,
                                                              String clientIp) {
        List<ModelRouterProperties.ModelInstance> allInstances = getInstancesByServiceAndModel(serviceType, modelName);
        if (allInstances.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No instances found for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        // 健康检查
        List<ModelRouterProperties.ModelInstance> healthyInstances = allInstances.stream()
                .filter(instance -> serviceStateManager.isInstanceHealthy(serviceType.name(), instance))
                .collect(Collectors.toList());

        if (healthyInstances.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No healthy instances found for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        // 熔断器检查 - 过滤掉处于熔断状态的实例
        List<ModelRouterProperties.ModelInstance> availableInstances = healthyInstances.stream()
                .filter(instance -> circuitBreakerManager.canExecute(instance.getInstanceId(), instance.getBaseUrl()))
                .collect(Collectors.toList());

        if (availableInstances.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No available instances (all in circuit breaker state) for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        // 1. 服务级限流检查
        RateLimitContext serviceContext = new RateLimitContext(serviceType, modelName, clientIp, 1);
        if (!rateLimitManager.tryAcquire(serviceContext)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Service rate limit exceeded for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        LoadBalancer loadBalancer = loadBalancerManager.getLoadBalancer(serviceType);

        // 尝试选择实例，包含实例级限流检查
        ModelRouterProperties.ModelInstance selectedInstance = null;
        List<ModelRouterProperties.ModelInstance> candidateInstances = new ArrayList<>(availableInstances);
        int maxAttempts = Math.min(candidateInstances.size(), 3); // 最多尝试3次或所有健康实例

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (candidateInstances.isEmpty()) {
                break;
            }

            // 负载均衡选择实例
            ModelRouterProperties.ModelInstance candidate = loadBalancer.selectInstance(candidateInstances, clientIp);

            // 健康检查
            if (!serviceStateManager.isInstanceHealthy(serviceType.name(), candidate)) {
                candidateInstances.remove(candidate);
                continue;
            }

            // 熔断器检查
            if (!circuitBreakerManager.canExecute(candidate.getInstanceId(), candidate.getBaseUrl())) {
                logger.warn("Instance {} is in circuit breaker state, trying next instance",
                        candidate.getBaseUrl());
                candidateInstances.remove(candidate);
                continue;
            }

            // 2. 实例级限流检查
            RateLimitContext instanceContext = new RateLimitContext(
                    serviceType,
                    modelName,
                    clientIp,
                    1,
                    candidate.getInstanceId(),
                    candidate.getBaseUrl()
            );

            if (!rateLimitManager.tryAcquireInstance(instanceContext)) {
                logger.warn("Instance rate limit exceeded for instance: {}, trying next instance",
                        candidate.getInstanceId());
                candidateInstances.remove(candidate);
                continue;
            }

            // 找到可用实例
            selectedInstance = candidate;
            break;
        }

        if (selectedInstance == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No available instances found for model '" + modelName + "' after rate limit, health and circuit breaker checks");
        }

        loadBalancer.recordCall(selectedInstance);
        return selectedInstance;
    }

    public WebClient getClient(ServiceType serviceType, String modelName, String clientIp) {
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(serviceType, modelName, clientIp);
        return getWebClient(selectedInstance);
    }

    public WebClient getClient(ServiceType serviceType, String modelName) {
        return getClient(serviceType, modelName, null);
    }

    public String getModelPath(ServiceType serviceType, String modelName) {
        List<ModelRouterProperties.ModelInstance> instances = getInstancesByServiceAndModel(serviceType, modelName);
        if (instances.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No model named '" + modelName + "' found for service type '" + serviceType + "'");
        }
        return instances.get(0).getPath();
    }

    public void recordCallComplete(ServiceType serviceType, ModelRouterProperties.ModelInstance instance) {
        LoadBalancer loadBalancer = loadBalancerManager.getLoadBalancer(serviceType);
        if (loadBalancer != null) {
            loadBalancer.recordCallComplete(instance);
        }
        // 记录熔断器成功
        circuitBreakerManager.recordSuccess(instance.getInstanceId(), instance.getBaseUrl());
    }

    /**
     * 获取实例的熔断器状态
     *
     * @param instance 实例
     * @return 熔断器状态
     */
    public CircuitBreaker.State getInstanceCircuitBreakerState(ModelRouterProperties.ModelInstance instance) {
        return circuitBreakerManager.getState(instance.getInstanceId(), instance.getBaseUrl());
    }

    /**
     * 记录调用失败
     *
     * @param serviceType 服务类型
     * @param instance    实例
     */
    public void recordCallFailure(ServiceType serviceType, ModelRouterProperties.ModelInstance instance) {
        LoadBalancer loadBalancer = loadBalancerManager.getLoadBalancer(serviceType);
        if (loadBalancer != null) {
            loadBalancer.recordCallFailure(instance);
        }

        // 记录熔断器失败
        circuitBreakerManager.recordFailure(instance.getInstanceId(), instance.getBaseUrl());
    }

    public String getServiceAdapter(ServiceType serviceType) {
        return serviceAdapters.get(serviceType);
    }

    private List<ModelRouterProperties.ModelInstance> getInstancesByServiceAndModel(ServiceType serviceType, String modelName) {
        return Optional.ofNullable(instanceRegistry.get(serviceType))
                .orElse(Collections.emptyList())
                .stream()
                .filter(instance -> modelName.equals(instance.getName()))
                .collect(Collectors.toList());
    }

    private WebClient getWebClient(ModelRouterProperties.ModelInstance instance) {
        String key = instance.getBaseUrl();
        return webClientCache.computeIfAbsent(key, url ->
                WebClient.builder().baseUrl(url).build());
    }

    public Set<ServiceType> getAvailableServiceTypes() {
        return instanceRegistry.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public Set<String> getAvailableModels(ServiceType serviceType) {
        return Optional.ofNullable(instanceRegistry.get(serviceType))
                .orElse(Collections.emptyList())
                .stream()
                .map(ModelRouterProperties.ModelInstance::getName)
                .collect(Collectors.toSet());
    }

    public String getLoadBalanceStrategy(ServiceType serviceType) {
        LoadBalancer loadBalancer = loadBalancerManager.getLoadBalancer(serviceType);
        return loadBalancer != null ? loadBalancer.getClass().getSimpleName() : "Unknown";
    }

    public Map<ServiceType, List<ModelRouterProperties.ModelInstance>> getAllInstances() {
        return new HashMap<>(instanceRegistry);
    }

    /**
     * 更新服务实例注册表
     * @param serviceType 服务类型
     * @param instances 实例列表
     */
    public void updateServiceInstances(ServiceType serviceType, List<ModelRouterProperties.ModelInstance> instances) {
        instanceRegistry.put(serviceType, new ArrayList<>(instances));
    }

    /**
     * 更新服务适配器
     * @param serviceType 服务类型
     * @param adapter 适配器名称
     */
    public void updateServiceAdapter(ServiceType serviceType, String adapter) {
        serviceAdapters.put(serviceType, adapter);
    }

    /**
     * 获取服务配置
     * @param serviceType 服务类型
     * @return 服务配置
     */
    public ModelRouterProperties.ServiceConfig getServiceConfig(ServiceType serviceType) {
        String serviceKey = serviceType.name().toLowerCase().replace("_", "-");
        if (properties.getServices() != null) {
            return properties.getServices().get(serviceKey);
        }
        return null;
    }

    public FallbackManager getFallbackManager() {
        return fallbackManager;
    }
}
