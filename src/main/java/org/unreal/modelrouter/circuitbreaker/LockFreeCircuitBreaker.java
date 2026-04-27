package org.unreal.modelrouter.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;

import java.util.HashMap;
import java.util.Map;
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

    public LockFreeCircuitBreaker(final String instanceId,final int failureThreshold,
                                   final long timeout,final int successThreshold) {
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
    private void recordCircuitBreakerEvent(final String event,final String currentState) {
        if (metricsCollector != null) {
            try {
                metricsCollector.recordCircuitBreaker(instanceId, currentState, event);
            } catch (Exception e) {
                logger.warn("Failed to record circuit breaker metrics: {}", e.getMessage());
            }
        }
    }

    // ==================== v2.0.0 新增功能 ====================

    /**
     * 获取熔断器详细状态信息
     *
     * @return 包含所有状态字段的 Map
     */
    public Map<String, Object> getStateDetail() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("instanceId", instanceId);
        detail.put("state", stateRef.get().name());
        detail.put("failureCount", failureCount.get());
        detail.put("successCount", successCount.get());
        detail.put("lastFailureTime", lastFailureTime.get());
        detail.put("failureThreshold", failureThreshold);
        detail.put("successThreshold", successThreshold);
        detail.put("timeout", timeout);
        detail.put("elapsedTime", System.currentTimeMillis() - lastFailureTime.get());
        return detail;
    }

    /**
     * 获取熔断器性能指标
     *
     * @return 包含性能指标的 Map
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("instanceId", instanceId);
        metrics.put("currentState", stateRef.get().name());
        metrics.put("totalFailures", failureCount.get());
        metrics.put("totalSuccesses", successCount.get());
        metrics.put("failureRate", calculateFailureRate());
        metrics.put("avgResponseTime", 0); // 待后续实现
        metrics.put("stateChangedCount", 0); // 待后续实现
        metrics.put("lastStateChangeTime", lastFailureTime.get());
        return metrics;
    }

    /**
     * 计算失败率
     *
     * @return 失败率 (0.0-1.0)
     */
    private double calculateFailureRate() {
        int failures = failureCount.get();
        int successes = successCount.get();
        int total = failures + successes;
        return total > 0 ? (double) failures / total : 0.0;
    }

    /**
     * 持久化熔断器状态到 Map（用于存储到 H2/Redis）
     *
     * @return 包含状态信息的 Map
     */
    public Map<String, Object> persistState() {
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("instanceId", instanceId);
        stateData.put("state", stateRef.get().name());
        stateData.put("failureCount", failureCount.get());
        stateData.put("successCount", successCount.get());
        stateData.put("lastFailureTime", lastFailureTime.get());
        stateData.put("timestamp", System.currentTimeMillis());
        logger.debug("熔断器状态已持久化：instanceId={}, state={}", instanceId, stateRef.get().name());
        return stateData;
    }

    /**
     * 从 Map 恢复熔断器状态
     *
     * @param stateData 包含状态信息的 Map
     */
    public void restoreState(final Map<String, Object> stateData) {
        if (stateData == null || !stateData.containsKey("instanceId")) {
            logger.warn("无效的状态数据，无法恢复熔断器状态");
            return;
        }

        String savedInstanceId = (String) stateData.get("instanceId");
        if (!savedInstanceId.equals(this.instanceId)) {
            logger.warn("实例 ID 不匹配，无法恢复状态：期望={}, 实际={}", this.instanceId, savedInstanceId);
            return;
        }

        try {
            // 恢复状态
            String stateName = (String) stateData.get("state");
            State savedState = State.valueOf(stateName);
            stateRef.set(savedState);

            // 恢复计数器
            if (stateData.containsKey("failureCount")) {
                failureCount.set(((Number) stateData.get("failureCount")).intValue());
            }
            if (stateData.containsKey("successCount")) {
                successCount.set(((Number) stateData.get("successCount")).intValue());
            }
            if (stateData.containsKey("lastFailureTime")) {
                lastFailureTime.set(((Number) stateData.get("lastFailureTime")).longValue());
            }

            logger.info("熔断器状态已恢复：instanceId={}, state={}, failureCount={}, successCount={}",
                    instanceId, savedState, failureCount.get(), successCount.get());
        } catch (Exception e) {
            logger.warn("恢复熔断器状态失败：{}", e.getMessage());
        }
    }
}
