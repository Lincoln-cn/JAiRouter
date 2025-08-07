package org.unreal.modelrouter.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.checker.ServerChecker;
import org.unreal.modelrouter.loadbalancer.*;
import org.unreal.modelrouter.loadbalancer.impl.IpHashLoadBalancer;
import org.unreal.modelrouter.loadbalancer.impl.LeastConnectionsLoadBalancer;
import org.unreal.modelrouter.loadbalancer.impl.RandomLoadBalancer;
import org.unreal.modelrouter.loadbalancer.impl.RoundRobinLoadBalancer;
import org.unreal.modelrouter.ratelimit.RateLimitContext;
import org.unreal.modelrouter.ratelimit.RateLimitManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties(ModelRouterProperties.class)
public class ModelServiceRegistry {

    public enum ServiceType {
        chat, embedding, rerank, tts, stt , imgGen ,imgEdit
    }

    private final Map<ServiceType, List<ModelRouterProperties.ModelInstance>> instanceRegistry;
    private final Map<ServiceType, LoadBalancer> loadBalancers;
    private final Map<ServiceType, String> serviceAdapters;
    private final Map<String, WebClient> webClientCache;
    private final ModelRouterProperties.LoadBalanceConfig globalLoadBalanceConfig;
    private final String globalAdapter;
    private final ServerChecker serverChecker;
    private final RateLimitManager rateLimitManager;

    public ModelServiceRegistry(ModelRouterProperties properties, ServerChecker serverChecker,RateLimitManager rateLimitManager) {
        this.webClientCache = new ConcurrentHashMap<>();
        this.serverChecker = serverChecker;
        this.rateLimitManager = rateLimitManager;

        // 使用全局配置
        this.globalLoadBalanceConfig = Optional.ofNullable(properties.getLoadBalance())
                .orElse(createDefaultLoadBalanceConfig());
        this.globalAdapter = Optional.ofNullable(properties.getAdapter()).orElse("normal");

        Map<String, ModelRouterProperties.ServiceConfig> services = Optional.ofNullable(properties.getServices())
                .orElse(Map.of());

        // 构建实例注册表、负载均衡器、适配器映射
        this.instanceRegistry = new EnumMap<>(ServiceType.class);
        this.loadBalancers = new EnumMap<>(ServiceType.class);
        this.serviceAdapters = new EnumMap<>(ServiceType.class);

        for (ServiceType serviceType : ServiceType.values()) {
            String serviceKey = serviceType.name().toLowerCase().replace("_", "-");
            ModelRouterProperties.ServiceConfig serviceConfig = services.get(serviceKey);

            // 使用服务级配置，没有则用全局
            ModelRouterProperties.LoadBalanceConfig lbConfig = Optional.ofNullable(serviceConfig)
                    .map(ModelRouterProperties.ServiceConfig::getLoadBalance)
                    .orElse(globalLoadBalanceConfig);

            String adapter = Optional.ofNullable(serviceConfig)
                    .map(ModelRouterProperties.ServiceConfig::getAdapter)
                    .orElse(globalAdapter);

            List<ModelRouterProperties.ModelInstance> instances = Optional.ofNullable(serviceConfig)
                    .map(ModelRouterProperties.ServiceConfig::getInstances)
                    .orElse(List.of());

            this.instanceRegistry.put(serviceType, new ArrayList<>(instances));
            this.loadBalancers.put(serviceType, createLoadBalancer(lbConfig));
            this.serviceAdapters.put(serviceType, adapter);
        }
    }

    private ModelRouterProperties.LoadBalanceConfig createDefaultLoadBalanceConfig() {
        ModelRouterProperties.LoadBalanceConfig config = new ModelRouterProperties.LoadBalanceConfig();
        config.setType("random");
        config.setHashAlgorithm("md5");
        return config;
    }

    private LoadBalancer createLoadBalancer(ModelRouterProperties.LoadBalanceConfig config) {
        String type = config.getType().toLowerCase();
        return switch (type) {
            case "random" -> new RandomLoadBalancer();
            case "round-robin" -> new RoundRobinLoadBalancer();
            case "least-connections" -> new LeastConnectionsLoadBalancer();
            case "ip-hash" -> new IpHashLoadBalancer(config.getHashAlgorithm());
            default -> throw new IllegalArgumentException("Unsupported load balance type: " + type);
        };
    }

    public WebClient getClient(ServiceType serviceType, String modelName, String clientIp) {
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(serviceType, modelName, clientIp);
        return getWebClient(selectedInstance);
    }

    public WebClient getClient(ServiceType serviceType, String modelName) {
        return getClient(serviceType, modelName, null);
    }

    public ModelRouterProperties.ModelInstance selectInstance(ServiceType serviceType, String modelName, String clientIp) {
        List<ModelRouterProperties.ModelInstance> allInstances = getInstancesByServiceAndModel(serviceType, modelName);
        if (allInstances.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No instances found for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        List<ModelRouterProperties.ModelInstance> healthyInstances = allInstances.stream()
                .filter(instance -> serverChecker.isInstanceHealthy(serviceType.name(), instance))
                .collect(Collectors.toList());

        if (healthyInstances.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No healthy instances found for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        LoadBalancer loadBalancer = loadBalancers.get(serviceType);
        // 检查限流
        if (!rateLimitManager.tryAcquire(new RateLimitContext(serviceType, modelName, clientIp, 1))) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        ModelRouterProperties.ModelInstance selectedInstance = loadBalancer.selectInstance(healthyInstances, clientIp);

        int attempts = 0;
        while (attempts < 3 && !serverChecker.isInstanceHealthy(serviceType.name(), selectedInstance)) {
            ModelRouterProperties.ModelInstance finalSelectedInstance = selectedInstance;
            healthyInstances = healthyInstances.stream()
                    .filter(instance -> !instance.equals(finalSelectedInstance))
                    .collect(Collectors.toList());
            if (healthyInstances.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "No healthy instances available for model '" + modelName + "' in service type '" + serviceType + "'");
            }
            selectedInstance = loadBalancer.selectInstance(healthyInstances, clientIp);
            attempts++;
        }

        loadBalancer.recordCall(selectedInstance);
        return selectedInstance;
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
        LoadBalancer loadBalancer = loadBalancers.get(serviceType);
        if (loadBalancer != null) {
            loadBalancer.recordCallComplete(instance);
        }
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
        LoadBalancer loadBalancer = loadBalancers.get(serviceType);
        return loadBalancer != null ? loadBalancer.getClass().getSimpleName() : "Unknown";
    }

    public Map<ServiceType, List<ModelRouterProperties.ModelInstance>> getAllInstances() {
        return new HashMap<>(instanceRegistry);
    }
}