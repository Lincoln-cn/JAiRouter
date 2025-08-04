package org.unreal.modelrouter.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.checker.ServerChecker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties(ModelRouterProperties.class)
public class ModelServiceRegistry {

    public enum ServiceType {
        chat, embedding, rerank, tts, stt
    }

    private final Map<ServiceType, List<ModelRouterProperties.ModelInstance>> instanceRegistry;
    private final Map<ServiceType, LoadBalancer> loadBalancers;
    private final Map<String, WebClient> webClientCache;
    private final ModelRouterProperties.LoadBalanceConfig globalLoadBalanceConfig;
    private final ServerChecker serverChecker;

    public ModelServiceRegistry(ModelRouterProperties properties, WebClient.Builder webClientBuilder, ServerChecker serverChecker) {
        this.webClientCache = new ConcurrentHashMap<>();
        this.serverChecker = serverChecker;

        // 获取全局负载均衡配置
        this.globalLoadBalanceConfig = Optional.ofNullable(properties.getServices())
                .map(services -> services.get("load-balance"))
                .map(ModelRouterProperties.ServiceConfig::getLoadBalance)
                .orElse(createDefaultLoadBalanceConfig());

        Map<String, ModelRouterProperties.ServiceConfig> services = Optional.ofNullable(properties.getServices())
                .orElse(Map.of());

        // 构建实例注册表
        this.instanceRegistry = new EnumMap<>(ServiceType.class);
        for (ServiceType serviceType : ServiceType.values()) {
            String serviceKey = serviceType.name().toLowerCase().replace("_", "-");
            ModelRouterProperties.ServiceConfig serviceConfig = services.get(serviceKey);

            if (serviceConfig != null && serviceConfig.getInstances() != null) {
                this.instanceRegistry.put(serviceType, new ArrayList<>(serviceConfig.getInstances()));
            } else {
                this.instanceRegistry.put(serviceType, new ArrayList<>());
            }
        }

        // 构建负载均衡器
        this.loadBalancers = new EnumMap<>(ServiceType.class);
        for (ServiceType serviceType : ServiceType.values()) {
            String serviceKey = serviceType.name().toLowerCase().replace("_", "-");
            ModelRouterProperties.ServiceConfig serviceConfig = services.get(serviceKey);

            // 使用服务特定的负载均衡配置，如果没有则使用全局配置
            ModelRouterProperties.LoadBalanceConfig lbConfig = Optional.ofNullable(serviceConfig)
                    .map(ModelRouterProperties.ServiceConfig::getLoadBalance)
                    .orElse(globalLoadBalanceConfig);

            this.loadBalancers.put(serviceType, createLoadBalancer(lbConfig));
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
            case "random" -> new LoadBalancer.RandomLoadBalancer();
            case "round-robin" -> new LoadBalancer.RoundRobinLoadBalancer();
            case "least-connections" -> new LoadBalancer.LeastConnectionsLoadBalancer();
            case "ip-hash" -> new LoadBalancer.IpHashLoadBalancer(config.getHashAlgorithm());
            default -> throw new IllegalArgumentException("Unsupported load balance type: " + type);
        };
    }

    /**
     * 根据负载均衡策略选择实例并获取WebClient
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @param clientIp 客户端IP (用于IP Hash策略)
     * @return WebClient实例
     */
    public WebClient getClient(ServiceType serviceType, String modelName, String clientIp) {
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(serviceType, modelName, clientIp);
        return getWebClient(selectedInstance);
    }

    /**
     * 兼容性方法，不使用负载均衡，直接返回第一个匹配的实例
     */
    public WebClient getClient(ServiceType serviceType, String modelName) {
        return getClient(serviceType, modelName, null);
    }

    /**
     * 根据负载均衡策略选择实例
     */
    public ModelRouterProperties.ModelInstance selectInstance(ServiceType serviceType, String modelName, String clientIp) {
        List<ModelRouterProperties.ModelInstance> allInstances = getInstancesByServiceAndModel(serviceType, modelName);

        if (allInstances.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No instances found for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        // 过滤出通过健康检查的实例
        List<ModelRouterProperties.ModelInstance> healthyInstances = allInstances.stream()
                .filter(instance -> serverChecker.isInstanceHealthy(serviceType.name(), instance))
                .collect(Collectors.toList());

        if (healthyInstances.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No healthy instances found for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        LoadBalancer loadBalancer = loadBalancers.get(serviceType);
        ModelRouterProperties.ModelInstance selectedInstance = loadBalancer.selectInstance(healthyInstances, clientIp);

        // 如果选中的实例不健康，则尝试重新选择（最多尝试3次）
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

        // 记录调用
        loadBalancer.recordCall(selectedInstance);

        return selectedInstance;
    }

    /**
     * 获取模型路径
     */
    public String getModelPath(ServiceType serviceType, String modelName) {
        List<ModelRouterProperties.ModelInstance> instances = getInstancesByServiceAndModel(serviceType, modelName);

        if (instances.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No model named '" + modelName + "' found for service type '" + serviceType + "'");
        }

        // 返回第一个实例的路径（假设同名模型的路径都相同）
        return instances.get(0).getPath();
    }

    /**
     * 记录调用完成（用于最少连接策略）
     */
    public void recordCallComplete(ServiceType serviceType, ModelRouterProperties.ModelInstance instance) {
        LoadBalancer loadBalancer = loadBalancers.get(serviceType);
        if (loadBalancer != null) {
            loadBalancer.recordCallComplete(instance);
        }
    }

    /**
     * 获取指定服务类型和模型名称的所有实例
     */
    private List<ModelRouterProperties.ModelInstance> getInstancesByServiceAndModel(ServiceType serviceType, String modelName) {
        return Optional.ofNullable(instanceRegistry.get(serviceType))
                .orElse(Collections.emptyList())
                .stream()
                .filter(instance -> modelName.equals(instance.getName()))
                .collect(Collectors.toList());
    }

    /**
     * 获取或创建WebClient
     */
    private WebClient getWebClient(ModelRouterProperties.ModelInstance instance) {
        String key = instance.getBaseUrl();
        return webClientCache.computeIfAbsent(key, url ->
                WebClient.builder().baseUrl(url).build());
    }

    /**
     * 获取所有可用的服务类型
     */
    public Set<ServiceType> getAvailableServiceTypes() {
        return instanceRegistry.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * 获取指定服务类型的所有可用模型名称
     */
    public Set<String> getAvailableModels(ServiceType serviceType) {
        return Optional.ofNullable(instanceRegistry.get(serviceType))
                .orElse(Collections.emptyList())
                .stream()
                .map(ModelRouterProperties.ModelInstance::getName)
                .collect(Collectors.toSet());
    }

    /**
     * 获取指定服务类型的负载均衡策略信息
     */
    public String getLoadBalanceStrategy(ServiceType serviceType) {
        LoadBalancer loadBalancer = loadBalancers.get(serviceType);
        if (loadBalancer != null) {
            return loadBalancer.getClass().getSimpleName();
        }
        return "Unknown";
    }

    /**
     * 健康检查 - 获取所有实例的状态
     */
    public Map<ServiceType, List<ModelRouterProperties.ModelInstance>> getAllInstances() {
        return new HashMap<>(instanceRegistry);
    }
}