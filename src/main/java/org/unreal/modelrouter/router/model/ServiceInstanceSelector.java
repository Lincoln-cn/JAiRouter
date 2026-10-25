package org.unreal.modelrouter.router.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.router.loadbalancer.monitor.RoutingMonitorService;
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
    private final RoutingMonitorService routingMonitorService;

    public ServiceInstanceSelector(final ServiceStateManager serviceStateManager,
                                   final RateLimitManager rateLimitManager,
                                   final CircuitBreakerManager circuitBreakerManager,
                                   final RoutingMonitorService routingMonitorService) {
        this.serviceStateManager = serviceStateManager;
        this.rateLimitManager = rateLimitManager;
        this.circuitBreakerManager = circuitBreakerManager;
        this.routingMonitorService = routingMonitorService;
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
        return selectWithRateLimit(availableInstances, loadBalancer, null, clientIp, serviceType, modelName);
    }

    /**
     * v2.9.0: 选择实例并进行实例级限流检查(带会话亲和性)
     *
     * @param availableInstances 可用实例列表
     * @param loadBalancer 负载均衡器
     * @param affinityKey 会话亲和性键(可为null)
     * @param clientIp 客户端IP
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @return 选中的实例，如果无可用实例则返回null
     */
    public ModelRouterProperties.ModelInstance selectWithRateLimit(
            final List<ModelRouterProperties.ModelInstance> availableInstances,
            final LoadBalancer loadBalancer,
            final String affinityKey,
            final String clientIp,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName) {

        List<ModelRouterProperties.ModelInstance> candidateInstances = new ArrayList<>(availableInstances);
        // #654: 候选被限流拒绝就从列表里移除，因此上限取候选总数即可遍历全部实例；
        // 原先硬编码 min(size, 3) 会在前 3 个实例都被限流时直接返回 null，
        // 让后面仍在配额内的实例永远选不到（表现为「All instances rate limited」的假告警）。
        int maxAttempts = candidateInstances.size();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (candidateInstances.isEmpty()) {
                break;
            }

            ModelRouterProperties.ModelInstance candidate;
            if (affinityKey != null && !affinityKey.isBlank()) {
                candidate = loadBalancer.selectInstance(
                        candidateInstances, affinityKey, clientIp, serviceType.name().toLowerCase());
            } else {
                candidate = loadBalancer.selectInstance(
                        candidateInstances, clientIp, serviceType.name().toLowerCase());
            }

            if (candidate == null) {
                LOGGER.warn("Load balancer returned no instance for service: {}, candidates left: {}",
                        serviceType, candidateInstances.size());
                break;
            }

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
     * 创建合适的异常信息（区分**没有该模型的实例**、**有实例但不可路由**，后者再细分状态/健康/熔断）。
     *
     * <p>issue #354 修复：原实现先按 {@code status=active} 过滤，再判「是否为空」⇒
     * 状态一旦不是 {@code active}（例如被写坏成 {@code healthy}），这里会返回
     * **404「No instances found for model ...」**，把排查方向指向「模型不存在」，
     * 而下面「不健康 / 熔断」两条原因永远不可达。现在先按**模型名**分流，
     * 再按 {@link InstanceAvailability} 的同一份判据（与选择路径一致）给出真实原因。</p>
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

        // 1) 连「同名实例」都没有 —— 这才是 404（原因真的是模型/实例不存在）
        List<ModelRouterProperties.ModelInstance> modelInstances = allInstances.stream()
                .filter(instance -> modelName.equals(instance.getName()))
                .collect(Collectors.toList());

        if (modelInstances.isEmpty()) {
            return new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No instances found for model '" + modelName + "' in service type '" + serviceType + "'");
        }

        // 2) 有同名实例：按同一份可用性判据细分「为什么一个都用不了」
        List<ModelRouterProperties.ModelInstance> routable = modelInstances.stream()
                .filter(instance -> InstanceAvailability.isRoutable(
                        serviceStateManager, circuitBreakerManager, serviceType, instance))
                .collect(Collectors.toList());

        if (!routable.isEmpty()) {
            // 走到这里说明「选择」与「分类」之间发生了竞态（例如刚被限流/熔断）——保留兜底语义
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No available instances for model '" + modelName + "' in service type '"
                            + serviceType + "' at this moment (all instances busy or rate limited)");
        }

        long notActive = modelInstances.stream()
                .filter(instance -> !InstanceAvailability.isActive(instance))
                .count();
        long unhealthy = modelInstances.stream()
                .filter(InstanceAvailability::isActive)
                .filter(instance -> !InstanceAvailability.isHealthy(
                        serviceStateManager, serviceType, instance))
                .count();
        long circuitOpen = modelInstances.stream()
                .filter(InstanceAvailability::isActive)
                .filter(instance -> InstanceAvailability.isHealthy(
                        serviceStateManager, serviceType, instance))
                .filter(instance -> !InstanceAvailability.isCircuitClosed(circuitBreakerManager, instance))
                .count();

        // 状态不合法是最容易被忽略的一种（写入侧此前不校验）：文案必须点名 status 与合法取值
        if (notActive > 0 && unhealthy == 0 && circuitOpen == 0) {
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Model '" + modelName + "' has " + modelInstances.size() + " instance(s) in service type '"
                            + serviceType + "' but none is routable: " + notActive
                            + " instance(s) have a status other than 'active' (lifecycle status must be active;"
                            + " health belongs to healthStatus)");
        }

        if (unhealthy > 0 && circuitOpen == 0) {
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No healthy instances found for model '" + modelName + "' in service type '"
                            + serviceType + "' (" + unhealthy + " instance(s) unhealthy, "
                            + notActive + " not active)");
        }

        if (circuitOpen > 0) {
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No available instances (all in circuit breaker state) for model '" + modelName
                            + "' (circuit open: " + circuitOpen + ", unhealthy: " + unhealthy
                            + ", not active: " + notActive + ")");
        }

        // 兜底：理论上不可达（上面三种计数之和应等于 modelInstances.size()）
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "No routable instances for model '" + modelName + "' in service type '" + serviceType + "'");
    }
}
