package org.unreal.modelrouter.monitoring.error;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.ErrorTrackerProperties;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 错误指标收集器
 * 
 * 负责收集和暴露错误相关的Micrometer指标。
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ErrorMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final ErrorTrackerProperties properties;
    
    // 指标缓存
    private final ConcurrentHashMap<String, Counter> errorCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> errorTimers = new ConcurrentHashMap<>();
    
    public ErrorMetricsCollector(MeterRegistry meterRegistry, 
                                ErrorTrackerProperties properties) {
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        
        // 注册基础错误指标
        registerBaseErrorMetrics();
    }
    
    /**
     * 注册基础错误指标
     */
    private void registerBaseErrorMetrics() {
        if (!properties.getMetrics().isEnabled()) {
            return;
        }
        
        try {
            // 总错误计数
            Counter.builder(properties.getMetrics().getCounterPrefix() + ".total")
                .description("Total number of errors")
                .register(meterRegistry);
                
            // 按错误类型分组的错误计数
            if (properties.getMetrics().isGroupByErrorType()) {
                // 这些指标会在记录错误时动态创建
                log.debug("启用按错误类型分组的错误指标");
            }
            
            // 按操作分组的错误计数
            if (properties.getMetrics().isGroupByOperation()) {
                // 这些指标会在记录错误时动态创建
                log.debug("启用按操作分组的错误指标");
            }
            
        } catch (Exception e) {
            log.warn("注册基础错误指标失败", e);
        }
    }
    
    /**
     * 记录错误指标
     * 
     * @param errorType 错误类型
     * @param operation 操作名称
     * @param duration 错误处理耗时
     */
    public void recordError(String errorType, String operation, Duration duration) {
        if (!properties.getMetrics().isEnabled()) {
            return;
        }
        
        try {
            // 记录总错误计数
            getOrCreateErrorCounter("total", "all", "all").increment();
            
            // 按错误类型记录
            if (properties.getMetrics().isGroupByErrorType()) {
                getOrCreateErrorCounter("by_type", errorType, "all").increment();
            }
            
            // 按操作记录
            if (properties.getMetrics().isGroupByOperation()) {
                getOrCreateErrorCounter("by_operation", "all", operation).increment();
            }
            
            // 记录错误处理耗时
            if (properties.getMetrics().isRecordDuration() && duration != null) {
                getOrCreateErrorTimer(errorType, operation).record(duration);
            }
            
        } catch (Exception e) {
            log.debug("记录错误指标失败: errorType={}, operation={}", errorType, operation, e);
        }
    }
    
    /**
     * 记录错误指标（简化版）
     * 
     * @param errorType 错误类型
     * @param operation 操作名称
     */
    public void recordError(String errorType, String operation) {
        recordError(errorType, operation, null);
    }
    
    /**
     * 获取或创建错误计数器
     * 
     * @param category 分类
     * @param errorType 错误类型
     * @param operation 操作名称
     * @return 错误计数器
     */
    private Counter getOrCreateErrorCounter(String category, String errorType, String operation) {
        String key = String.format("%s:%s:%s", category, errorType, operation);
        
        return errorCounters.computeIfAbsent(key, k -> {
            Counter.Builder builder = Counter.builder(properties.getMetrics().getCounterPrefix() + "." + category)
                .description("Error count by " + category);
                
            if (!"all".equals(errorType)) {
                builder.tag("error_type", errorType);
            }
            if (!"all".equals(operation)) {
                builder.tag("operation", operation);
            }
            
            return builder.register(meterRegistry);
        });
    }
    
    /**
     * 获取或创建错误计时器
     * 
     * @param errorType 错误类型
     * @param operation 操作名称
     * @return 错误计时器
     */
    private Timer getOrCreateErrorTimer(String errorType, String operation) {
        String key = String.format("duration:%s:%s", errorType, operation);
        
        return errorTimers.computeIfAbsent(key, k -> {
            Timer.Builder builder = Timer.builder(properties.getMetrics().getCounterPrefix() + ".duration")
                .description("Error handling duration");
                
            if (errorType != null && !"all".equals(errorType)) {
                builder.tag("error_type", errorType);
            }
            if (operation != null && !"all".equals(operation)) {
                builder.tag("operation", operation);
            }
            
            // 设置直方图桶
            if (properties.getMetrics().getHistogramBuckets() != null) {
                Duration[] buckets = java.util.Arrays.stream(properties.getMetrics().getHistogramBuckets())
                    .mapToObj(seconds -> Duration.ofMillis((long) (seconds * 1000)))
                    .toArray(Duration[]::new);
                builder.serviceLevelObjectives(buckets);
            }
            
            return builder.register(meterRegistry);
        });
    }
    
    /**
     * 获取错误指标统计信息
     *
     * @return 错误指标统计信息
     */
    public ErrorMetricsStats getErrorMetricsStats() {
        return ErrorMetricsStats.builder()
                .totalErrorCounters(errorCounters.size())
                .totalErrorTimers(errorTimers.size())
                .build();
    }
    
    /**
     * 错误指标统计信息
     */
    @lombok.Data
    @lombok.Builder
    public static class ErrorMetricsStats {
        private int totalErrorCounters;
        private int totalErrorTimers;
        
        /**
         * 获取错误类型统计信息
         *
         * @return 错误类型统计信息
         */
        public Map<String, Counter> getErrorTypeStats() {
            // 这里应该返回按错误类型分组的统计信息
            // 为了简化，我们返回一个空的映射
            return Map.of();
        }
        
        /**
         * 获取错误位置统计信息
         *
         * @return 错误位置统计信息
         */
        public Map<String, Counter> getErrorLocationStats() {
            // 这里应该返回按错误位置分组的统计信息
            // 为了简化，我们返回一个空的映射
            return Map.of();
        }
    }
}