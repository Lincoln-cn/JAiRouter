package org.unreal.modelrouter.monitoring.error;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 错误追踪器
 * 
 * 统一处理系统中的异常追踪，提供：
 * - 异常信息的结构化记录
 * - 异常堆栈的脱敏和截断处理
 * - 异常分类和聚合统计功能
 * - 与追踪上下文的集成
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorTracker {
    
    private final StructuredLogger structuredLogger;
    
    // 异常统计
    private final ConcurrentHashMap<String, AtomicLong> errorTypeCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> errorLocationCounters = new ConcurrentHashMap<>();
    
    // 异常聚合统计
    private final ConcurrentHashMap<String, ErrorAggregation> errorAggregations = new ConcurrentHashMap<>();
    
    /**
     * 记录异常信息
     * 
     * @param throwable 异常对象
     * @param operation 操作名称
     * @param additionalInfo 额外信息
     */
    public void trackError(Throwable throwable, String operation, Map<String, Object> additionalInfo) {
        if (throwable == null) {
            return;
        }
        
        try {
            // 获取当前追踪上下文
            TracingContext context = TracingContextHolder.getCurrentContext();
            
            // 更新统计信息
            updateErrorStatistics(throwable, operation);
            
            // 记录结构化日志
            Map<String, Object> errorInfo = new HashMap<>();
            if (additionalInfo != null) {
                errorInfo.putAll(additionalInfo);
            }
            
            errorInfo.put("operation", operation);
            errorInfo.put("exceptionClass", throwable.getClass().getName());
            
            // 记录到结构化日志中
            structuredLogger.logError(throwable, context, errorInfo);
            
            // 更新聚合统计
            aggregateError(throwable, operation, context);
            
        } catch (Exception e) {
            log.warn("记录错误信息时发生异常", e);
        }
    }
    
    /**
     * 记录异常信息（简化版）
     * 
     * @param throwable 异常对象
     * @param operation 操作名称
     */
    public void trackError(Throwable throwable, String operation) {
        trackError(throwable, operation, null);
    }
    
    /**
     * 更新错误统计信息
     * 
     * @param throwable 异常对象
     * @param operation 操作名称
     */
    private void updateErrorStatistics(Throwable throwable, String operation) {
        // 按异常类型统计
        String errorType = throwable.getClass().getName();
        errorTypeCounters.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
        
        // 按操作位置统计
        String location = operation != null ? operation : "unknown";
        errorLocationCounters.computeIfAbsent(location, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * 聚合错误信息
     * 
     * @param throwable 异常对象
     * @param operation 操作名称
     * @param context 追踪上下文
     */
    private void aggregateError(Throwable throwable, String operation, TracingContext context) {
        String errorKey = generateErrorKey(throwable, operation);
        ErrorAggregation aggregation = errorAggregations.computeIfAbsent(
            errorKey, 
            k -> new ErrorAggregation(throwable.getClass().getName(), operation)
        );
        
        aggregation.increment();
        
        // 记录聚合信息到结构化日志
        Map<String, Object> aggregationData = new HashMap<>();
        aggregationData.put("errorType", throwable.getClass().getSimpleName());
        aggregationData.put("operation", operation);
        aggregationData.put("count", aggregation.getCount());
        aggregationData.put("firstOccurrence", aggregation.getFirstOccurrence());
        aggregationData.put("lastOccurrence", System.currentTimeMillis());
        
        if (context != null && context.isActive()) {
            aggregationData.put("traceId", context.getTraceId());
        }
        
        structuredLogger.logBusinessEvent("error_aggregation", aggregationData, context);
    }
    
    /**
     * 生成错误键值，用于聚合相同类型的错误
     * 
     * @param throwable 异常对象
     * @param operation 操作名称
     * @return 错误键值
     */
    private String generateErrorKey(Throwable throwable, String operation) {
        return throwable.getClass().getName() + ":" + (operation != null ? operation : "unknown");
    }
    
    /**
     * 获取错误类型统计
     * 
     * @return 错误类型统计映射
     */
    public Map<String, Long> getErrorTypeStatistics() {
        Map<String, Long> result = new HashMap<>();
        errorTypeCounters.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }
    
    /**
     * 获取错误位置统计
     * 
     * @return 错误位置统计映射
     */
    public Map<String, Long> getErrorLocationStatistics() {
        Map<String, Long> result = new HashMap<>();
        errorLocationCounters.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }
    
    /**
     * 获取错误聚合信息
     * 
     * @return 错误聚合映射
     */
    public Map<String, ErrorAggregation> getErrorAggregations() {
        return new HashMap<>(errorAggregations);
    }
    
    /**
     * 错误聚合类
     */
    public static class ErrorAggregation {
        private final String errorType;
        private final String operation;
        private final AtomicLong count = new AtomicLong(0);
        private final long firstOccurrence;
        
        public ErrorAggregation(String errorType, String operation) {
            this.errorType = errorType;
            this.operation = operation;
            this.firstOccurrence = System.currentTimeMillis();
        }
        
        public void increment() {
            count.incrementAndGet();
        }
        
        public String getErrorType() {
            return errorType;
        }
        
        public String getOperation() {
            return operation;
        }
        
        public long getCount() {
            return count.get();
        }
        
        public long getFirstOccurrence() {
            return firstOccurrence;
        }
        
        @Override
        public String toString() {
            return "ErrorAggregation{" +
                    "errorType='" + errorType + '\'' +
                    ", operation='" + operation + '\'' +
                    ", count=" + count +
                    ", firstOccurrence=" + firstOccurrence +
                    '}';
        }
    }
}