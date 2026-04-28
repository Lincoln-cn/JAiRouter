package org.unreal.modelrouter.router.circuitbreaker.state;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker.State;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 熔断器状态上下文
 * 
 * 保存熔断器的状态数据，供各个状态类使用。
 */
@Getter
@RequiredArgsConstructor
public class CircuitStateContext {

    private static final Logger logger = LoggerFactory.getLogger(CircuitStateContext.class);

    // 配置参数
    private final String instanceId;
    private final int failureThreshold;
    private final long timeout;
    private final int successThreshold;
    private final MetricsCollector metricsCollector;

    // 状态数据
    private CircuitState currentState;
    private int failureCount = 0;
    private int successCount = 0;
    private long lastFailureTime = 0;

    /**
     * 创建上下文并设置初始状态
     */
    public CircuitStateContext(final String aInstanceId, final int aFailureThreshold, 
                                final long aTimeout, final int aSuccessThreshold,
                                final MetricsCollector aMetricsCollector,
                                final CircuitState aInitialState) {
        this.instanceId = aInstanceId;
        this.failureThreshold = aFailureThreshold;
        this.timeout = aTimeout;
        this.successThreshold = aSuccessThreshold;
        this.metricsCollector = aMetricsCollector;
        this.currentState = aInitialState;
    }

    /**
     * 设置当前状态
     */
    public void setCurrentState(final CircuitState newState) {
        CircuitState oldState = this.currentState;
        this.currentState = newState;
        
        if (oldState != newState) {
            logger.info("熔断器状态变化：{} -> {}, instanceId={}", 
                oldState.getStateName(), newState.getStateName(), instanceId);
            recordEvent("state_change", newState.getStateName());
        }
    }

    /**
     * 获取当前枚举状态
     */
    public State getState() {
        return currentState.getState();
    }

    /**
     * 增加失败计数
     */
    public void incrementFailureCount() {
        this.failureCount++;
        this.lastFailureTime = System.currentTimeMillis();
    }

    /**
     * 重置失败计数
     */
    public void resetFailureCount() {
        this.failureCount = 0;
    }

    /**
     * 增加成功计数
     */
    public void incrementSuccessCount() {
        this.successCount++;
    }

    /**
     * 重置成功计数
     */
    public void resetSuccessCount() {
        this.successCount = 0;
    }

    /**
     * 检查是否达到失败阈值
     */
    public boolean isFailureThresholdReached() {
        return failureCount >= failureThreshold;
    }

    /**
     * 检查是否达到成功阈值
     */
    public boolean isSuccessThresholdReached() {
        return successCount >= successThreshold;
    }

    /**
     * 检查超时是否已过
     */
    public boolean isTimeoutElapsed() {
        return System.currentTimeMillis() - lastFailureTime >= timeout;
    }

    /**
     * 记录熔断器事件
     */
    public void recordEvent(final String eventType, final String state) {
        if (metricsCollector != null) {
            try {
                metricsCollector.recordCircuitBreaker(instanceId, state, eventType);
            } catch (Exception e) {
                logger.warn("记录熔断器指标失败：{}", e.getMessage());
            }
        }
    }
}
