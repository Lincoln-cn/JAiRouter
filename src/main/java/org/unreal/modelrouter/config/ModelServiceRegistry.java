package org.unreal.modelrouter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.checker.ServerChecker;
import org.unreal.modelrouter.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.circuitbreaker.CircuitBreakerManager;
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
    private final ServerChecker serverChecker;
    private final RateLimitManager rateLimitManager;
    private final CircuitBreakerManager circuitBreakerManager;

    public ModelServiceRegistry(ModelRouterProperties properties,
                                ServerChecker serverChecker,
                                RateLimitManager rateLimitManager,
                                LoadBalancerManager loadBalancerManager,
                                CircuitBreakerManager circuitBreakerManager) {
        this.webClientCache = new ConcurrentHashMap<>();
        this.serverChecker = serverChecker;
        this.rateLimitManager = rateLimitManager;
        this.loadBalancerManager = loadBalancerManager;
        this.circuitBreakerManager = circuitBreakerManager;

        // 初始化熔断器管理器
        this.circuitBreakerManager.initialize(properties);

        this.globalAdapter = Optional.ofNullable(properties.getAdapter()).orElse("normal");

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
                .filter(instance -> serverChecker.isInstanceHealthy(serviceType.name(), instance))
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
            if (!serverChecker.isInstanceHealthy(serviceType.name(), candidate)) {
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
}
