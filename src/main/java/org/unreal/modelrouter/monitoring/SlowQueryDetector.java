package org.unreal.modelrouter.monitoring;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitoring.alert.SlowQueryAlertService;
import org.unreal.modelrouter.monitoring.config.MonitoringProperties;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;

/**
 * 慢查询检测器
 * 
 * 监控系统中的慢操作，根据配置的阈值检测和记录慢查询信息。
 * 提供慢查询统计和分析功能。
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Component
public class SlowQueryDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(SlowQueryDetector.class);
    
    private final MonitoringProperties monitoringProperties;
    
    // 存储每个操作的慢查询统计信息
    private final Map<String, SlowQueryStats> slowQueryStatsMap = new ConcurrentHashMap<>();
    
    // 总慢查询计数
    private final AtomicLong totalSlowQueryCount = new AtomicLong(0);
    
    // 慢查询告警服务（延迟注入避免循环依赖）
    @Autowired(required = false)
    private SlowQueryAlertService slowQueryAlertService;
    
    public SlowQueryDetector(MonitoringProperties monitoringProperties) {
        this.monitoringProperties = monitoringProperties;
    }
    
    /**
     * 检测慢查询
     * 
     * @param operationName 操作名称
     * @param durationMillis 操作耗时（毫秒）
     * @param context 上下文信息
     */
    public void detectSlowQuery(String operationName, long durationMillis, Map<String, String> context) {
        detectSlowQuery(operationName, durationMillis, context, null);
    }
    
    /**
     * 检测慢查询（带追踪上下文）
     * 
     * @param operationName 操作名称
     * @param durationMillis 操作耗时（毫秒）
     * @param context 上下文信息
     * @param tracingContext 追踪上下文
     */
    public void detectSlowQuery(String operationName, long durationMillis, Map<String, String> context, TracingContext tracingContext) {
        // 获取慢查询阈值，如果没有配置则使用默认值1000ms
        Map<String, Long> slowQueryThresholds = monitoringProperties.getThresholds().getSlowQueryThresholds();
        Long threshold = slowQueryThresholds != null ? 
            slowQueryThresholds.getOrDefault(operationName, slowQueryThresholds.getOrDefault("default", 1000L)) : 
            1000L;
        
        // 如果操作耗时超过阈值，则记录为慢查询
        if (durationMillis >= threshold) {
            totalSlowQueryCount.incrementAndGet();
            
            // 更新该操作的慢查询统计信息
            slowQueryStatsMap.computeIfAbsent(operationName, k -> new SlowQueryStats())
                            .recordSlowQuery(durationMillis);
            
            // 记录慢查询日志
            logger.warn("Slow query detected - Operation: {}, Duration: {}ms, Threshold: {}ms, Context: {}", 
                       operationName, durationMillis, threshold, context);
            
            // 触发告警检查
            triggerSlowQueryAlert(operationName, durationMillis, threshold, context, tracingContext);
        }
    }
    
    /**
     * 触发慢查询告警
     */
    private void triggerSlowQueryAlert(String operationName, long durationMillis, long threshold, 
                                      Map<String, String> context, TracingContext tracingContext) {
        if (slowQueryAlertService == null) {
            return; // 告警服务未配置
        }
        
        try {
            // 获取当前追踪上下文（如果没有提供）
            TracingContext currentContext = tracingContext != null ? 
                    tracingContext : TracingContextHolder.getCurrentContext();
            
            // 构建额外信息
            Map<String, Object> additionalInfo = new HashMap<>();
            if (context != null) {
                additionalInfo.putAll(context);
            }
            
            // 检查并发送告警
            slowQueryAlertService.checkAndAlert(operationName, durationMillis, threshold, 
                    currentContext, additionalInfo);
            
        } catch (Exception e) {
            logger.debug("触发慢查询告警时发生错误：operation={}, duration={}ms", operationName, durationMillis, e);
        }
    }
    
    /**
     * 获取指定操作的慢查询统计信息
     * 
     * @param operationName 操作名称
     * @return 慢查询统计信息
     */
    public SlowQueryStats getSlowQueryStats(String operationName) {
        return slowQueryStatsMap.getOrDefault(operationName, new SlowQueryStats());
    }
    
    /**
     * 获取所有操作的慢查询统计信息
     * 
     * @return 所有慢查询统计信息的映射
     */
    public Map<String, SlowQueryStats> getAllSlowQueryStats() {
        return new ConcurrentHashMap<>(slowQueryStatsMap);
    }
    
    /**
     * 获取总慢查询计数
     * 
     * @return 总慢查询计数
     */
    public long getTotalSlowQueryCount() {
        return totalSlowQueryCount.get();
    }
    
    /**
     * 重置统计信息
     */
    public void resetStats() {
        slowQueryStatsMap.clear();
        totalSlowQueryCount.set(0);
    }
    
    /**
     * 慢查询统计信息类
     */
    public static class SlowQueryStats {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalDuration = new AtomicLong(0);
        private final AtomicLong maxDuration = new AtomicLong(0);
        private final AtomicLong minDuration = new AtomicLong(Long.MAX_VALUE);
        
        /**
         * 记录一次慢查询
         * 
         * @param durationMillis 慢查询耗时（毫秒）
         */
        public void recordSlowQuery(long durationMillis) {
            count.incrementAndGet();
            totalDuration.addAndGet(durationMillis);
            
            // 更新最大耗时
            maxDuration.accumulateAndGet(durationMillis, Math::max);
            
            // 更新最小耗时
            minDuration.accumulateAndGet(durationMillis, Math::min);
        }
        
        /**
         * 获取慢查询次数
         * 
         * @return 慢查询次数
         */
        public long getCount() {
            return count.get();
        }
        
        /**
         * 获取总耗时
         * 
         * @return 总耗时（毫秒）
         */
        public long getTotalDuration() {
            return totalDuration.get();
        }
        
        /**
         * 获取最大耗时
         * 
         * @return 最大耗时（毫秒）
         */
        public long getMaxDuration() {
            long max = maxDuration.get();
            return max == 0 ? 0 : max; // 如果没有记录，则返回0
        }
        
        /**
         * 获取最小耗时
         * 
         * @return 最小耗时（毫秒）
         */
        public long getMinDuration() {
            long min = minDuration.get();
            return min == Long.MAX_VALUE ? 0 : min; // 如果没有记录，则返回0
        }
        
        /**
         * 获取平均耗时
         * 
         * @return 平均耗时（毫秒）
         */
        public double getAverageDuration() {
            long cnt = count.get();
            return cnt == 0 ? 0.0 : (double) totalDuration.get() / cnt;
        }
    }
}