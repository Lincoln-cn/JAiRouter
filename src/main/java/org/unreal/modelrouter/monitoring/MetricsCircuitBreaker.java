package org.unreal.modelrouter.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Conditional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 指标收集熔断器
 * 保护指标收集系统，防止因指标处理异常影响主业务流程
 */
@Component
@Conditional(MonitoringEnabledCondition.class)
public class MetricsCircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCircuitBreaker.class);

    // 熔断器状态
    public enum State {
        CLOSED,    // 关闭状态，正常处理
        OPEN,      // 开启状态，拒绝请求
        HALF_OPEN  // 半开状态，尝试恢复
    }

    // 配置参数
    private static final int FAILURE_THRESHOLD = 10;           // 失败阈值
    private static final double FAILURE_RATE_THRESHOLD = 0.5;  // 失败率阈值
    private static final Duration TIMEOUT = Duration.ofSeconds(30); // 超时时间
    private static final int MIN_REQUESTS = 5;                 // 最小请求数

    // 状态管理
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger requestCount = new AtomicInteger(0);

    // 统计窗口
    private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
    private static final Duration WINDOW_SIZE = Duration.ofMinutes(1);

    public MetricsCircuitBreaker() {
        logger.info("MetricsCircuitBreaker initialized with failure threshold: {}, failure rate threshold: {}, timeout: {}",
                   FAILURE_THRESHOLD, FAILURE_RATE_THRESHOLD, TIMEOUT);
    }

    /**
     * 检查是否允许请求
     */
    public boolean allowRequest() {
        State currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                return true;
                
            case OPEN:
                // 检查是否可以尝试恢复
                if (shouldAttemptReset()) {
                    logger.info("Circuit breaker attempting to reset from OPEN to HALF_OPEN");
                    state.set(State.HALF_OPEN);
                    resetCounters();
                    return true;
                }
                return false;
                
            case HALF_OPEN:
                // 半开状态下允许少量请求通过
                return requestCount.get() < MIN_REQUESTS;
                
            default:
                return false;
        }
    }

    /**
     * 记录成功
     */
    public void recordSuccess() {
        resetWindowIfNeeded();
        successCount.incrementAndGet();
        requestCount.incrementAndGet();
        
        State currentState = state.get();
        if (currentState == State.HALF_OPEN) {
            // 半开状态下如果成功，尝试关闭熔断器
            if (successCount.get() >= MIN_REQUESTS) {
                logger.info("Circuit breaker recovering from HALF_OPEN to CLOSED");
                state.set(State.CLOSED);
                resetCounters();
            }
        }
    }

    /**
     * 记录失败
     */
    public void recordFailure() {
        resetWindowIfNeeded();
        failureCount.incrementAndGet();
        requestCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            // 半开状态下失败，立即开启熔断器
            logger.warn("Circuit breaker opening from HALF_OPEN due to failure");
            state.set(State.OPEN);
        } else if (currentState == State.CLOSED) {
            // 检查是否需要开启熔断器
            if (shouldOpenCircuit()) {
                logger.warn("Circuit breaker opening from CLOSED due to failure threshold exceeded");
                state.set(State.OPEN);
            }
        }
    }

    /**
     * 获取当前状态
     */
    public String getState() {
        return state.get().name();
    }

    /**
     * 获取统计信息
     */
    public CircuitBreakerStats getStats() {
        return new CircuitBreakerStats(
            state.get().name(),
            failureCount.get(),
            successCount.get(),
            requestCount.get(),
            getFailureRate()
        );
    }

    /**
     * 检查是否应该开启熔断器
     */
    private boolean shouldOpenCircuit() {
        int requests = requestCount.get();
        int failures = failureCount.get();
        
        // 请求数不足，不开启熔断器
        if (requests < MIN_REQUESTS) {
            return false;
        }
        
        // 失败次数超过阈值
        if (failures >= FAILURE_THRESHOLD) {
            return true;
        }
        
        // 失败率超过阈值
        double failureRate = getFailureRate();
        return failureRate >= FAILURE_RATE_THRESHOLD;
    }

    /**
     * 检查是否应该尝试重置
     */
    private boolean shouldAttemptReset() {
        long lastFailure = lastFailureTime.get();
        return System.currentTimeMillis() - lastFailure >= TIMEOUT.toMillis();
    }

    /**
     * 重置计数器
     */
    private void resetCounters() {
        failureCount.set(0);
        successCount.set(0);
        requestCount.set(0);
        windowStart.set(System.currentTimeMillis());
    }

    /**
     * 如果需要，重置统计窗口
     */
    private void resetWindowIfNeeded() {
        long now = System.currentTimeMillis();
        long windowStartTime = windowStart.get();
        
        if (now - windowStartTime >= WINDOW_SIZE.toMillis()) {
            // 窗口过期，重置计数器
            if (windowStart.compareAndSet(windowStartTime, now)) {
                int oldFailures = failureCount.getAndSet(0);
                int oldSuccesses = successCount.getAndSet(0);
                int oldRequests = requestCount.getAndSet(0);
                
                logger.debug("Reset metrics window - Previous: failures={}, successes={}, requests={}", 
                           oldFailures, oldSuccesses, oldRequests);
            }
        }
    }

    /**
     * 计算失败率
     */
    private double getFailureRate() {
        int requests = requestCount.get();
        if (requests == 0) {
            return 0.0;
        }
        return (double) failureCount.get() / requests;
    }

    /**
     * 强制开启熔断器（用于测试或紧急情况）
     */
    public void forceOpen() {
        logger.warn("Circuit breaker forced to OPEN state");
        state.set(State.OPEN);
        lastFailureTime.set(System.currentTimeMillis());
    }

    /**
     * 强制关闭熔断器（用于测试或恢复）
     */
    public void forceClose() {
        logger.info("Circuit breaker forced to CLOSED state");
        state.set(State.CLOSED);
        resetCounters();
    }

    /**
     * 熔断器统计信息
     */
    public static class CircuitBreakerStats {
        private final String state;
        private final int failureCount;
        private final int successCount;
        private final int requestCount;
        private final double failureRate;

        public CircuitBreakerStats(String state, int failureCount, int successCount, int requestCount, double failureRate) {
            this.state = state;
            this.failureCount = failureCount;
            this.successCount = successCount;
            this.requestCount = requestCount;
            this.failureRate = failureRate;
        }

        public String getState() { return state; }
        public int getFailureCount() { return failureCount; }
        public int getSuccessCount() { return successCount; }
        public int getRequestCount() { return requestCount; }
        public double getFailureRate() { return failureRate; }

        @Override
        public String toString() {
            return String.format("CircuitBreakerStats{state='%s', failures=%d, successes=%d, requests=%d, failureRate=%.2f}",
                               state, failureCount, successCount, requestCount, failureRate);
        }
    }
}