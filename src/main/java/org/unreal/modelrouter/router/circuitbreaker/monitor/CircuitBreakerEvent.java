package org.unreal.modelrouter.router.circuitbreaker.monitor;

import java.time.Instant;

/**
 * 熔断器事件记录
 * 记录熔断器状态变化和成功/失败事件
 *
 * @param timestamp       事件时间戳
 * @param instanceId      实例ID
 * @param instanceName    实例名称
 * @param serviceType     服务类型
 * @param eventType       事件类型 (STATE_CHANGE, SUCCESS, FAILURE)
 * @param previousState   前一状态 (仅 STATE_CHANGE 事件)
 * @param currentState    当前状态
 * @param failureCount    失败次数
 * @param successCount    成功次数
 * @param triggerReason   触发原因
 *
 * @author JAiRouter Team
 * @since 2.7.0
 */
public record CircuitBreakerEvent(
    Instant timestamp,
    String instanceId,
    String instanceName,
    String serviceType,
    EventType eventType,
    String previousState,
    String currentState,
    int failureCount,
    int successCount,
    String triggerReason
) {
    /**
     * 事件类型枚举
     */
    public enum EventType {
        STATE_CHANGE,
        SUCCESS,
        FAILURE
    }

    /**
     * 创建状态变化事件
     */
    public static CircuitBreakerEvent stateChange(
            String instanceId,
            String instanceName,
            String serviceType,
            String previousState,
            String currentState,
            int failureCount,
            int successCount,
            String triggerReason) {
        return new CircuitBreakerEvent(
            Instant.now(),
            instanceId,
            instanceName,
            serviceType,
            EventType.STATE_CHANGE,
            previousState,
            currentState,
            failureCount,
            successCount,
            triggerReason
        );
    }

    /**
     * 创建成功事件
     */
    public static CircuitBreakerEvent success(
            String instanceId,
            String instanceName,
            String serviceType,
            int failureCount,
            int successCount) {
        return new CircuitBreakerEvent(
            Instant.now(),
            instanceId,
            instanceName,
            serviceType,
            EventType.SUCCESS,
            null,
            null,
            failureCount,
            successCount,
            null
        );
    }

    /**
     * 创建失败事件
     */
    public static CircuitBreakerEvent failure(
            String instanceId,
            String instanceName,
            String serviceType,
            int failureCount,
            int successCount) {
        return new CircuitBreakerEvent(
            Instant.now(),
            instanceId,
            instanceName,
            serviceType,
            EventType.FAILURE,
            null,
            null,
            failureCount,
            successCount,
            null
        );
    }

    /**
     * 转换为紧凑的字符串表示（用于日志）
     */
    public String toCompactString() {
        if (eventType == EventType.STATE_CHANGE) {
            return String.format("[%s] %s: %s -> %s (%s)",
                instanceId, eventType, previousState, currentState, triggerReason);
        } else {
            return String.format("[%s] %s (failures=%d, successes=%d)",
                instanceId, eventType, failureCount, successCount);
        }
    }
}
