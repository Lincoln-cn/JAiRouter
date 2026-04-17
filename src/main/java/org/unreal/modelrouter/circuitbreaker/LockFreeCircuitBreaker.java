package org.unreal.modelrouter.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 无锁实现的熔断器
 * 使用 AtomicReference 和 AtomicInteger 实现无锁状态管理
 * 提升并发性能，避免 synchronized 阻塞
 */
public class LockFreeCircuitBreaker implements CircuitBreaker {
    
    private static final Logger logger = LoggerFactory.getLogger(LockFreeCircuitBreaker.class);
    private final String instanceId;
    private final int failureThreshold;
    private final long timeout;
    private final int successThreshold;

    // 使用 AtomicReference 存储状态，实现无锁状态切换
    private final AtomicReference<State> stateRef;
    
    // 使用 AtomicInteger 计数器，避免同步
    private final AtomicInteger failureCount;
    private final AtomicInteger successCount;
    
    // 使用 AtomicLong 记录时间戳
    private final AtomicLong lastFailureTime;

    @Autowired(required = false)
    private MetricsCollector metricsCollector;

    public LockFreeCircuitBreaker(String instanceId, int failureThreshold,
                                   long timeout, int successThreshold) {
        this.instanceId = instanceId;
        this.failureThreshold = failureThreshold;
        this.timeout = timeout;
        this.successThreshold = successThreshold;
        this.stateRef = new AtomicReference<>(State.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.lastFailureTime = new AtomicLong(0);
    }

    @Override
    public boolean canExecute() {
        State currentState = stateRef.get();
        
        switch (currentState) {
            case CLOSED:
                return true;
                
            case OPEN:
                // 检查是否可以转为半开状态
                long elapsed = System.currentTimeMillis() - lastFailureTime.get();
                if (elapsed >= timeout) {
                    // CAS 操作尝试转为半开状态
                    if (stateRef.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        successCount.set(0);
                        logger.debug("熔断器转为半开状态：instanceId={}, elapsed={}ms", instanceId, elapsed);
                        return true;
                    }
                    // CAS 失败说明状态已被其他线程修改，递归调用重新检查
                    return canExecute();
                }
                return false;
                
            case HALF_OPEN:
                return true;
                
            default:
                return true;
        }
    }

    @Override
    public void onSuccess() {
        State currentState = stateRef.get();
        
        switch (currentState) {
            case CLOSED:
                failureCount.set(0);
                recordCircuitBreakerEvent("success", "CLOSED");
                break;
                
            case OPEN:
                // 在 OPEN 状态下成功不应该发生，忽略
                break;
                
            case HALF_OPEN:
                int newSuccessCount = successCount.incrementAndGet();
                if (newSuccessCount >= successThreshold) {
                    // CAS 操作尝试转为关闭状态
                    if (stateRef.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                        failureCount.set(0);
                        successCount.set(0);
                        logger.info("熔断器恢复正常：instanceId={}, successCount={}", instanceId, newSuccessCount);
                        recordCircuitBreakerEvent("state_change", "CLOSED");
                    } else {
                        // CAS 失败，重置成功计数
                        successCount.set(0);
                    }
                } else {
                    recordCircuitBreakerEvent("success", "HALF_OPEN");
                }
                break;
        }
    }

    @Override
    public void onFailure() {
        State currentState = stateRef.get();
        
        switch (currentState) {
            case CLOSED:
                int newFailureCount = failureCount.incrementAndGet();
                lastFailureTime.set(System.currentTimeMillis());
                
                if (newFailureCount >= failureThreshold) {
                    // CAS 操作尝试转为打开状态
                    if (stateRef.compareAndSet(State.CLOSED, State.OPEN)) {
                        logger.info("熔断器打开：instanceId={}, failureCount={}, failureThreshold={}",
                                instanceId, newFailureCount, failureThreshold);
                        recordCircuitBreakerEvent("state_change", "OPEN");
                    } else {
                        // CAS 失败，重置失败计数
                        failureCount.set(0);
                    }
                } else {
                    logger.debug("熔断器记录失败 (CLOSED 状态): instanceId={}, failureCount={}, failureThreshold={}",
                            instanceId, newFailureCount, failureThreshold);
                }
                break;
                
            case OPEN:
                lastFailureTime.set(System.currentTimeMillis());
                logger.debug("熔断器记录失败 (OPEN 状态): instanceId={}", instanceId);
                break;
                
            case HALF_OPEN:
                lastFailureTime.set(System.currentTimeMillis());
                // 失败则重新进入打开状态
                if (stateRef.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                    logger.info("熔断器重新打开：instanceId={}, failure in HALF_OPEN state", instanceId);
                    recordCircuitBreakerEvent("state_change", "OPEN");
                }
                break;
        }
    }

    @Override
    public State getState() {
        return stateRef.get();
    }

    /**
     * 重置熔断器到初始状态
     */
    public void reset() {
        stateRef.set(State.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        lastFailureTime.set(0);
        logger.info("熔断器已重置：instanceId={}", instanceId);
        recordCircuitBreakerEvent("reset", "CLOSED");
    }

    /**
     * 获取失败计数（用于测试）
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * 获取成功计数（用于测试）
     */
    public int getSuccessCount() {
        return successCount.get();
    }

    /**
     * 获取最后失败时间（用于测试）
     */
    public long getLastFailureTime() {
        return lastFailureTime.get();
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
