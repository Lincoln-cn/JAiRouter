package org.unreal.modelrouter.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitoring.config.MonitoringProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 慢查询检测器
 * 检测系统中的慢操作并提供告警和统计功能
 */
@Component
public class SlowQueryDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(SlowQueryDetector.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final MonitoringProperties monitoringProperties;

    // 存储慢查询统计信息
    private final Map<String, SlowQueryStats> slowQueryStats = new ConcurrentHashMap<>();
    
    // 慢查询计数器
    private final AtomicLong slowQueryCount = new AtomicLong(0);
    
    public SlowQueryDetector(MonitoringProperties monitoringProperties) {
        this.monitoringProperties = monitoringProperties;
        logger.info("SlowQueryDetector initialized");
    }
    
    /**
     * 检测操作是否为慢查询
     * @param operationName 操作名称
     * @param durationMillis 操作耗时(毫秒)
     * @param context 操作上下文信息
     */
    public void detectSlowQuery(String operationName, long durationMillis, Map<String, String> context) {
        // 从配置中获取阈值
        Map<String, Long> slowQueryThresholds = monitoringProperties.getThresholds().getSlowQueryThresholds();
        Long threshold = slowQueryThresholds.getOrDefault(operationName,
                monitoringProperties.getThresholds().getSlowQueryThreshold());
        
        // 如果操作耗时超过阈值，则标记为慢查询
        if (durationMillis > threshold) {
            long count = slowQueryCount.incrementAndGet();
            
            // 记录慢查询日志
            logSlowQuery(operationName, durationMillis, threshold, context);
            
            // 更新统计信息
            updateSlowQueryStats(operationName, durationMillis);
            
            logger.debug("Slow query detected: {} took {} ms, threshold is {} ms, total slow queries: {}",
                    operationName, durationMillis, threshold, count);
        }
    }
    
    /**
     * 记录慢查询详细日志
     * @param operationName 操作名称
     * @param durationMillis 操作耗时(毫秒)
     * @param threshold 阈值(毫秒)
     * @param context 操作上下文信息
     */
    private void logSlowQuery(String operationName, long durationMillis, long threshold, Map<String, String> context) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("Slow Query Detected - Operation: ").append(operationName)
                .append(", Duration: ").append(durationMillis).append(" ms")
                .append(", Threshold: ").append(threshold).append(" ms")
                .append(", Time: ").append(LocalDateTime.now().format(DATE_FORMATTER));
        
        if (context != null && !context.isEmpty()) {
            logMessage.append(", Context: ").append(context);
        }
        
        logger.warn(logMessage.toString());
    }
    
    /**
     * 更新慢查询统计信息
     * @param operationName 操作名称
     * @param durationMillis 操作耗时(毫秒)
     */
    private void updateSlowQueryStats(String operationName, long durationMillis) {
        slowQueryStats.computeIfAbsent(operationName, k -> new SlowQueryStats())
                .update(durationMillis);
    }
    

    /**
     * 获取慢查询统计信息
     * @param operationName 操作名称
     * @return 慢查询统计信息
     */
    public SlowQueryStats getSlowQueryStats(String operationName) {
        return slowQueryStats.getOrDefault(operationName, new SlowQueryStats());
    }
    
    /**
     * 获取所有慢查询统计信息
     * @return 所有慢查询统计信息
     */
    public Map<String, SlowQueryStats> getAllSlowQueryStats() {
        return new ConcurrentHashMap<>(slowQueryStats);
    }
    
    /**
     * 获取慢查询总数
     * @return 慢查询总数
     */
    public long getTotalSlowQueryCount() {
        return slowQueryCount.get();
    }
    
    /**
     * 重置统计信息
     */
    public void resetStats() {
        slowQueryStats.clear();
        slowQueryCount.set(0);
    }
    
    /**
     * 慢查询统计信息类
     */
    public static class SlowQueryStats {
        private long count = 0;
        private long totalDuration = 0;
        private long maxDuration = 0;
        private long minDuration = Long.MAX_VALUE;
        
        public synchronized void update(long durationMillis) {
            count++;
            totalDuration += durationMillis;
            maxDuration = Math.max(maxDuration, durationMillis);
            minDuration = Math.min(minDuration, durationMillis);
        }
        
        public long getCount() {
            return count;
        }
        
        public long getTotalDuration() {
            return totalDuration;
        }
        
        public long getMaxDuration() {
            return maxDuration;
        }
        
        public long getMinDuration() {
            return minDuration == Long.MAX_VALUE ? 0 : minDuration;
        }
        
        public double getAverageDuration() {
            return count > 0 ? (double) totalDuration / count : 0;
        }
    }
}