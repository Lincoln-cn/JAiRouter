package org.unreal.modelrouter.router.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.ratelimit.RateLimitContext;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 服务实例选择器
 * 负责从可用实例中选择合适的实例，包含限流检查
 */
public class ServiceInstanceSelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceInstanceSelector.class);

    private final ServiceStateManager serviceStateManager;
    private final RateLimitManager rateLimitManager;
    private final CircuitBreakerManager circuitBreakerManager;

    public ServiceInstanceSelector(final ServiceStateManager serviceStateManager,
                                   final RateLimitManager rateLimitManager,
                                   final CircuitBreakerManager circuitBreakerManager) {
        this.serviceStateManager = serviceStateManager;
        this.rateLimitManager = rateLimitManager;
        this.circuitBreakerManager = circuitBreakerManager;
    }

    /**
     * 选择实例并进行实例级限流检查
     *
     * @param availableInstances 可用实例列表
     * @param loadBalancer 负载均衡器
     * @param clientIp 客户端IP
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @return 选中的实例，如果无可用实例则返回null
     */
    public ModelRouterProperties.ModelInstance selectWithRateLimit(
            final List<ModelRouterProperties.ModelInstance> availableInstances,
            final LoadBalancer loadBalancer,
            final String clientIp,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName) {

        List<ModelRouterProperties.ModelInstance> candidateInstances = new ArrayList<>(availableInstances);
        int maxAttempts = Math.min(candidateInstances.size(), 3);

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (candidateInstances.isEmpty()) {
                break;
            }

            ModelRouterProperties.ModelInstance candidate = loadBalancer.selectInstance(
                    candidateInstances, clientIp, serviceType.name().toLowerCase());

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
     * 创建合适的异常信息（区分无实例、不健康、熔断三种情况）
     *
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @param allInstances 所有实例列表
     * @return 对应的异常
     */
    public ResponseStatusException createAppropriateException(
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final List<ModelRouterProperties.ModelInstance> allInstances) {

        // 检查是否有匹配模型名的实例
        List<ModelRouterProperties.ModelInstance> modelInstances = allInstances.stream()
                .filter(instance -> modelName.equals(instance.getName()))
                .filter(instance -> instance.getStatus() != null
                        && "active".equalsIgnoreCase(instance.getStatus()))
                .collect(Collectors.toList());

        if (modelInstances.isEmpty()) {
            return new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No instances found for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        // 检查是否有健康实例
        List<ModelRouterProperties.ModelInstance> healthyInstances = modelInstances.stream()
                .filter(instance -> serviceStateManager.isInstanceHealthy(serviceType.name(), instance))
                .collect(Collectors.toList());

        if (healthyInstances.isEmpty()) {
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No healthy instances found for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        // 剩余情况：全部被熔断
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "No available instances (all in circuit breaker state) for model '" + modelName + "'");
    }
}
