package org.unreal.modelrouter.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指标收集错误处理器
 * 负责处理指标收集过程中的异常，提供降级策略和错误恢复机制
 */
@Component
public class MetricsErrorHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsErrorHandler.class);
    
    private final MeterRegistry meterRegistry;
    private final Counter errorCounter;
    private final Counter degradationCounter;
    private final Timer errorRecoveryTimer;
    
    // 错误统计
    private final ConcurrentHashMap<String, AtomicInteger> errorCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> lastErrorTimes = new ConcurrentHashMap<>();
    
    // 降级配置
    private static final int MAX_ERRORS_PER_MINUTE = 10;
    private static final Duration ERROR_RESET_INTERVAL = Duration.ofMinutes(5);
    private static final Duration DEGRADATION_DURATION = Duration.ofMinutes(2);
    
    // 降级状态
    private final ConcurrentHashMap<String, Instant> degradationStartTimes = new ConcurrentHashMap<>();
    
    public MetricsErrorHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.errorCounter = Counter.builder("jairouter.metrics.errors")
            .description("指标收集错误计数")
            .tag("component", "metrics-handler")
            .register(meterRegistry);
        this.degradationCounter = Counter.builder("jairouter.metrics.degradations")
            .description("指标收集降级计数")
            .tag("component", "metrics-handler")
            .register(meterRegistry);
        this.errorRecoveryTimer = Timer.builder("jairouter.metrics.error_recovery")
            .description("错误恢复时间")
            .tag("component", "metrics-handler")
            .register(meterRegistry);
    }
    
    /**
     * 处理指标收集异常
     */
    public void handleMetricsError(String component, String operation, Throwable error) {
        String errorKey = component + ":" + operation;
        
        // 记录错误
        Counter.builder("jairouter.metrics.errors")
            .description("指标收集错误计数")
            .tag("component", component)
            .tag("operation", operation)
            .tag("error_type", error.getClass().getSimpleName())
            .register(meterRegistry)
            .increment();
        
        // 更新错误计数
        AtomicInteger count = errorCounts.computeIfAbsent(errorKey, k -> new AtomicInteger(0));
        AtomicLong lastTime = lastErrorTimes.computeIfAbsent(errorKey, k -> new AtomicLong(0));
        
        long currentTime = System.currentTimeMillis();
        long lastErrorTime = lastTime.get();
        
        // 如果距离上次错误超过重置间隔，重置计数
        if (currentTime - lastErrorTime > ERROR_RESET_INTERVAL.toMillis()) {
            count.set(0);
        }
        
        count.incrementAndGet();
        lastTime.set(currentTime);
        
        // 检查是否需要降级
        if (count.get() >= MAX_ERRORS_PER_MINUTE) {
            activateDegradation(errorKey);
            logger.warn("指标收集组件 {} 操作 {} 错误过多，启动降级模式", component, operation);
        }
        
        logger.debug("指标收集错误: component={}, operation={}, error={}", 
                    component, operation, error.getMessage());
    }
    
    /**
     * 检查组件是否处于降级状态
     */
    public boolean isDegraded(String component, String operation) {
        String errorKey = component + ":" + operation;
        Instant degradationStart = degradationStartTimes.get(errorKey);
        
        if (degradationStart == null) {
            return false;
        }
        
        // 检查降级是否已过期
        if (Instant.now().isAfter(degradationStart.plus(DEGRADATION_DURATION))) {
            deactivateDegradation(errorKey);
            return false;
        }
        
        return true;
    }
    
    /**
     * 安全执行指标收集操作
     */
    public void safeExecute(String component, String operation, Runnable metricsOperation) {
        // 检查是否处于降级状态
        if (isDegraded(component, operation)) {
            logger.debug("组件 {} 操作 {} 处于降级状态，跳过指标收集", component, operation);
            return;
        }
        
        try {
            metricsOperation.run();
        } catch (Exception e) {
            handleMetricsError(component, operation, e);
        }
    }
    
    /**
     * 安全执行指标收集操作（带返回值）
     */
    public <T> T safeExecute(String component, String operation, 
                           java.util.function.Supplier<T> metricsOperation, T defaultValue) {
        // 检查是否处于降级状态
        if (isDegraded(component, operation)) {
            logger.debug("组件 {} 操作 {} 处于降级状态，返回默认值", component, operation);
            return defaultValue;
        }
        
        try {
            return metricsOperation.get();
        } catch (Exception e) {
            handleMetricsError(component, operation, e);
            return defaultValue;
        }
    }
    
    /**
     * 激活降级模式
     */
    private void activateDegradation(String errorKey) {
        degradationStartTimes.put(errorKey, Instant.now());
        degradationCounter.increment();
    }
    
    /**
     * 停用降级模式
     */
    private void deactivateDegradation(String errorKey) {
        Instant startTime = degradationStartTimes.remove(errorKey);
        if (startTime != null) {
            Duration recoveryTime = Duration.between(startTime, Instant.now());
            errorRecoveryTimer.record(recoveryTime);
            
            // 重置错误计数
            errorCounts.remove(errorKey);
            lastErrorTimes.remove(errorKey);
            
            logger.info("指标收集组件 {} 从降级模式恢复，降级时长: {}ms", 
                       errorKey, recoveryTime.toMillis());
        }
    }
    
    /**
     * 获取错误统计信息
     */
    public MetricsErrorStats getErrorStats() {
        return new MetricsErrorStats(
            errorCounts.size(),
            degradationStartTimes.size(),
            errorCounts.values().stream().mapToInt(AtomicInteger::get).sum()
        );
    }
    
    /**
     * 手动重置错误状态
     */
    public void resetErrorState(String component, String operation) {
        String errorKey = component + ":" + operation;
        errorCounts.remove(errorKey);
        lastErrorTimes.remove(errorKey);
        deactivateDegradation(errorKey);
        
        logger.info("手动重置指标收集错误状态: {}", errorKey);
    }
    
    /**
     * 清理过期的错误记录
     */
    public void cleanupExpiredErrors() {
        long currentTime = System.currentTimeMillis();
        long cleanupThreshold = ERROR_RESET_INTERVAL.toMillis();
        
        lastErrorTimes.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue().get() > cleanupThreshold) {
                errorCounts.remove(entry.getKey());
                return true;
            }
            return false;
        });
        
        // 清理过期的降级状态
        Instant now = Instant.now();
        degradationStartTimes.entrySet().removeIf(entry -> {
            if (now.isAfter(entry.getValue().plus(DEGRADATION_DURATION))) {
                logger.info("清理过期的降级状态: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * 错误统计信息
     */
    public static class MetricsErrorStats {
        private final int activeErrorComponents;
        private final int degradedComponents;
        private final int totalErrors;
        
        public MetricsErrorStats(int activeErrorComponents, int degradedComponents, int totalErrors) {
            this.activeErrorComponents = activeErrorComponents;
            this.degradedComponents = degradedComponents;
            this.totalErrors = totalErrors;
        }
        
        public int getActiveErrorComponents() { return activeErrorComponents; }
        public int getDegradedComponents() { return degradedComponents; }
        public int getTotalErrors() { return totalErrors; }
    }
}