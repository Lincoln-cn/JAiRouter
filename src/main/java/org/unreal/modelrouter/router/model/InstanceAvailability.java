package org.unreal.modelrouter.router.model;

import org.unreal.modelrouter.common.constants.InstanceStatus;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties.ModelInstance;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

/**
 * 实例**可用性**判据的单一事实来源（issue #354）。
 *
 * <p>选择路径（{@code SelectInstanceOptimizer}）与失败原因分类
 * （{@code ServiceInstanceSelector#createAppropriateException}）此前各写了一遍判据：
 * 优化器判「状态 ∧ 健康 ∧ 熔断」，而分类器只判「状态 = active」就下结论，
 * 于是状态一旦不是 {@code active}，客户端会收到**误导性的 404「找不到该模型」**，
 * 而「实例不健康」「熔断打开」两条原因永远不可达。</p>
 *
 * <p>现在两条路径都走这里，语义只有一份。</p>
 *
 * @since v3.3.3
 */
public final class InstanceAvailability {

    private InstanceAvailability() {
    }

    /**
     * 状态判据：只有 {@link InstanceStatus#ACTIVE} 可被路由。
     *
     * @param instance 实例
     * @return 是否可路由
     */
    public static boolean isActive(final ModelInstance instance) {
        return instance != null && InstanceStatus.isActive(instance.getStatus());
    }

    /**
     * 健康判据（走 {@link ServiceStateManager} 的实例级状态）。
     *
     * @param serviceStateManager 实例状态管理器
     * @param serviceType         服务类型
     * @param instance            实例
     * @return 是否健康
     */
    public static boolean isHealthy(final ServiceStateManager serviceStateManager,
                                    final ServiceType serviceType,
                                    final ModelInstance instance) {
        return instance != null
                && serviceStateManager.isInstanceHealthy(serviceType.name(), instance);
    }

    /**
     * 熔断判据：实例显式配置 {@code circuit-breaker.enabled=false} 时跳过检查（视为可执行）。
     *
     * @param circuitBreakerManager 熔断器管理器
     * @param instance             实例
     * @return 是否允许执行
     */
    public static boolean isCircuitClosed(final CircuitBreakerManager circuitBreakerManager,
                                          final ModelInstance instance) {
        if (instance == null) {
            return false;
        }
        final ModelRouterProperties.CircuitBreakerConfig cbConfig = instance.getCircuitBreaker();
        if (cbConfig != null && Boolean.FALSE.equals(cbConfig.getEnabled())) {
            return true;
        }
        return circuitBreakerManager.canExecute(instance.getInstanceId(), instance.getBaseUrl());
    }

    /**
     * 组合判据：状态 ∧ 健康 ∧ 熔断（与实例选择时的过滤条件**同一份**语义）。
     *
     * @param serviceStateManager  实例状态管理器
     * @param circuitBreakerManager 熔断器管理器
     * @param serviceType          服务类型
     * @param instance             实例
     * @return 当前是否可被路由
     */
    public static boolean isRoutable(final ServiceStateManager serviceStateManager,
                                     final CircuitBreakerManager circuitBreakerManager,
                                     final ServiceType serviceType,
                                     final ModelInstance instance) {
        return isActive(instance)
                && isHealthy(serviceStateManager, serviceType, instance)
                && isCircuitClosed(circuitBreakerManager, instance);
    }
}
