package org.unreal.modelrouter.config.event;

import lombok.Getter;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker.State;
import org.springframework.context.ApplicationEvent;

/**
 * 熔断器状态变化事件
 * 
 * v2.6.13: 新增，用于解耦状态记录逻辑
 */
@Getter
public class CircuitBreakerStateChangeEvent extends ApplicationEvent {

    private final String instanceId;
    private final String instanceName;
    private final String serviceType;
    private final State previousState;
    private final State currentState;
    private final String triggerReason;
    private final Integer failureCount;
    private final Integer successCount;

    /**
     * 构造函数
     */
    public CircuitBreakerStateChangeEvent(
            final Object source,
            final String instanceId,
            final String instanceName,
            final String serviceType,
            final State previousState,
            final State currentState,
            final String triggerReason,
            final Integer failureCount,
            final Integer successCount) {
        super(source);
        this.instanceId = instanceId;
        this.instanceName = instanceName;
        this.serviceType = serviceType;
        this.previousState = previousState;
        this.currentState = currentState;
        this.triggerReason = triggerReason;
        this.failureCount = failureCount;
        this.successCount = successCount;
    }

    /**
     * 创建状态变化事件
     */
    public static CircuitBreakerStateChangeEvent of(
            final Object source,
            final String instanceId,
            final State previousState,
            final State currentState,
            final String triggerReason,
            final Integer failureCount,
            final Integer successCount) {
        return new CircuitBreakerStateChangeEvent(
                source, instanceId, null, null, 
                previousState, currentState, triggerReason, 
                failureCount, successCount);
    }

    /**
     * 创建带实例信息的状态变化事件
     */
    public static CircuitBreakerStateChangeEvent ofWithInstanceInfo(
            final Object source,
            final String instanceId,
            final String instanceName,
            final String serviceType,
            final State previousState,
            final State currentState,
            final String triggerReason,
            final Integer failureCount,
            final Integer successCount) {
        return new CircuitBreakerStateChangeEvent(
                source, instanceId, instanceName, serviceType,
                previousState, currentState, triggerReason,
                failureCount, successCount);
    }
}
