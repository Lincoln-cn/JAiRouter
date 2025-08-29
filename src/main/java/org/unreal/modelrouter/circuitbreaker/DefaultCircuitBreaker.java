package org.unreal.modelrouter.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;

public class DefaultCircuitBreaker implements CircuitBreaker {
    private static final Logger logger = LoggerFactory.getLogger(DefaultCircuitBreaker.class);
    private final String instanceId;
    private final int failureThreshold;
    private final long timeout;
    private final int successThreshold;

    private State state = State.CLOSED;
    private int failureCount = 0;
    private int successCount = 0;
    private long lastFailureTime = 0;
    
    @Autowired(required = false)
    private MetricsCollector metricsCollector;

    public DefaultCircuitBreaker(String instanceId, int failureThreshold,
                                 long timeout, int successThreshold) {
        this.instanceId = instanceId;
        this.failureThreshold = failureThreshold;
        this.timeout = timeout;
        this.successThreshold = successThreshold;
    }

    @Override
    public synchronized boolean canExecute() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                // 检查是否可以转为半开状态
                if (System.currentTimeMillis() - lastFailureTime >= timeout) {
                    state = State.HALF_OPEN;
                    successCount = 0;
                    return true;
                }
                return false;
            case HALF_OPEN:
                return true;
            default:
                return true;
        }
    }

    @Override
    public synchronized void onSuccess() {
        State previousState = state;
        switch (state) {
            case CLOSED:
                failureCount = 0;
                recordCircuitBreakerEvent("success", state.name());
                break;
            case OPEN:
                break;
            case HALF_OPEN:
                successCount++;
                if (successCount >= successThreshold) {
                    // 恢复正常状态
                    state = State.CLOSED;
                    failureCount = 0;
                    recordCircuitBreakerEvent("state_change", state.name());
                }
                recordCircuitBreakerEvent("success", state.name());
                break;
        }
    }

    @Override
    public synchronized void onFailure() {
        State previousState = state;
        failureCount++;
        lastFailureTime = System.currentTimeMillis();

        switch (state) {
            case CLOSED:
                if (failureCount >= failureThreshold) {
                    state = State.OPEN;
                    logger.info("熔断器打开: instanceId={}, failureCount={}, failureThreshold={}", 
                        instanceId, failureCount, failureThreshold);
                    recordCircuitBreakerEvent("state_change", state.name());
                } else {
                    logger.debug("熔断器记录失败 (CLOSED状态): instanceId={}, failureCount={}, failureThreshold={}", 
                        instanceId, failureCount, failureThreshold);
                }
                break;
            case OPEN:
                logger.debug("熔断器记录失败 (OPEN状态): instanceId={}", instanceId);
                break;
            case HALF_OPEN:
                // 失败则重新进入熔断状态
                state = State.OPEN;
                logger.info("熔断器重新打开: instanceId={}, failure in HALF_OPEN state", instanceId);
                recordCircuitBreakerEvent("state_change", state.name());
                break;
        }
        
        recordCircuitBreakerEvent("failure", state.name());
    }

    @Override
    public State getState() {
        return state;
    }

    /**
     * 记录熔断器指标
     */
    private void recordCircuitBreakerEvent(String event, String currentState) {
        if (metricsCollector != null) {
            try {
                metricsCollector.recordCircuitBreaker(instanceId, currentState, event);
            } catch (Exception e) {
                logger.warn("Failed to record circuit breaker metrics: {}", e.getMessage());
            }
        }
    }
}
