package org.unreal.modelrouter.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitoring.config.MonitoringProperties;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 性能跟踪器
 * 提供操作耗时监控、慢操作检测、性能数据统计和聚合功能
 */
@Component
public class PerformanceTracker {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceTracker.class);
    
    private final MeterRegistry meterRegistry;
    private final MonitoringProperties monitoringProperties;
    private final SlowQueryDetector slowQueryDetector;
    
    // 存储操作统计信息
    private final Map<String, OperationStats> operationStats = new ConcurrentHashMap<>();
    
    public PerformanceTracker(MeterRegistry meterRegistry, MonitoringProperties monitoringProperties, SlowQueryDetector slowQueryDetector) {
        this.meterRegistry = meterRegistry;
        this.monitoringProperties = monitoringProperties;
        this.slowQueryDetector = slowQueryDetector;
        logger.info("PerformanceTracker initialized with default thresholds");
    }
    
    /**
     * 开始跟踪操作
     * @param operationName 操作名称
     * @return 跟踪上下文
     */
    public TrackingContext startTracking(String operationName) {
        return new TrackingContext(operationName, System.nanoTime());
    }
    
    /**
     * 结束跟踪操作并记录指标
     * @param context 跟踪上下文
     */
    public void endTracking(TrackingContext context) {
        endTracking(context, null);
    }
    
    /**
     * 结束跟踪操作并记录指标
     * @param context 跟踪上下文
     * @param tags 额外的标签
     */
    public void endTracking(TrackingContext context, Map<String, String> tags) {
        long durationNanos = System.nanoTime() - context.getStartTimeNanos();
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(durationNanos);
        
        // 记录到Micrometer
        Timer.Sample sample = Timer.start(meterRegistry);
        Map<String, String> finalTags = new HashMap<>();
        if (tags != null) {
            finalTags.putAll(tags);
        }
        finalTags.put("operation", context.getOperationName());
        Timer timer = Timer.builder("operation.duration")
                .tags(finalTags.entrySet().stream()
                        .flatMap(e -> Arrays.stream(new String[]{e.getKey(), e.getValue()}))
                        .toArray(String[]::new))
                .register(meterRegistry);
        sample.stop(timer);
        
        // 更新统计数据
        updateStats(context.getOperationName(), durationMillis);
        
        // 检查是否超过阈值并检测慢查询
        checkThreshold(context.getOperationName(), durationMillis, tags);
        
        logger.debug("Operation {} completed in {} ms", context.getOperationName(), durationMillis);
    }
    
    /**
     * 更新操作统计信息
     * @param operationName 操作名称
     * @param durationMillis 耗时(毫秒)
     */
    private void updateStats(String operationName, long durationMillis) {
        operationStats.computeIfAbsent(operationName, k -> new OperationStats())
                .update(durationMillis);
    }
    
    /**
     * 检查操作是否超过性能阈值
     * @param operationName 操作名称
     * @param durationMillis 耗时(毫秒)
     * @param tags 标签信息
     */
    private void checkThreshold(String operationName, long durationMillis, Map<String, String> tags) {
        // 从配置中获取阈值
        Map<String, Long> thresholds = monitoringProperties.getThresholds().getOperationThresholds();
        Long threshold = thresholds.getOrDefault(operationName, 
                monitoringProperties.getThresholds().getDefaultThreshold());
        
        if (durationMillis > threshold) {
            logger.warn("Slow operation detected: {} took {} ms, threshold is {} ms", 
                    operationName, durationMillis, threshold);
            // 可以在这里添加更多慢操作处理逻辑
        }
        
        // 使用SlowQueryDetector检测慢查询
        slowQueryDetector.detectSlowQuery(operationName, durationMillis, tags);
    }
    
    /**
     * 设置操作的性能阈值
     * @param operationName 操作名称
     * @param thresholdMillis 阈值(毫秒)
     */
    public void setPerformanceThreshold(String operationName, long thresholdMillis) {
        monitoringProperties.getThresholds().getOperationThresholds().put(operationName, thresholdMillis);
    }
    
    /**
     * 获取操作统计信息
     * @param operationName 操作名称
     * @return 操作统计信息
     */
    public OperationStats getOperationStats(String operationName) {
        return operationStats.getOrDefault(operationName, new OperationStats());
    }
    
    /**
     * 获取所有操作的统计信息
     * @return 所有操作的统计信息
     */
    public Map<String, OperationStats> getAllOperationStats() {
        return new HashMap<>(operationStats);
    }
    
    /**
     * 获取性能热点(按总耗时排序)
     * @param limit 返回的结果数量限制
     * @return 性能热点列表
     */
    public List<PerformanceHotspot> getPerformanceHotspots(int limit) {
        List<PerformanceHotspot> hotspots = new ArrayList<>();
        
        for (Map.Entry<String, OperationStats> entry : operationStats.entrySet()) {
            String operationName = entry.getKey();
            OperationStats stats = entry.getValue();
            hotspots.add(new PerformanceHotspot(operationName, stats));
        }
        
        // 按总耗时排序
        hotspots.sort((a, b) -> Long.compare(b.getTotalDuration(), a.getTotalDuration()));
        
        // 限制返回数量
        if (hotspots.size() > limit) {
            return hotspots.subList(0, limit);
        }
        
        return hotspots;
    }
    
    /**
     * 清除统计信息
     */
    public void clearStats() {
        operationStats.clear();
    }
    
    /**
     * 跟踪上下文
     */
    public static class TrackingContext {
        private final String operationName;
        private final long startTimeNanos;
        
        public TrackingContext(String operationName, long startTimeNanos) {
            this.operationName = operationName;
            this.startTimeNanos = startTimeNanos;
        }
        
        public String getOperationName() {
            return operationName;
        }
        
        public long getStartTimeNanos() {
            return startTimeNanos;
        }
    }
    
    /**
     * 操作统计信息
     */
    public static class OperationStats {
        private long callCount = 0;
        private long totalDuration = 0;
        private long maxDuration = 0;
        private long minDuration = Long.MAX_VALUE;
        
        public synchronized void update(long durationMillis) {
            callCount++;
            totalDuration += durationMillis;
            maxDuration = Math.max(maxDuration, durationMillis);
            minDuration = Math.min(minDuration, durationMillis);
        }
        
        public long getCallCount() {
            return callCount;
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
            return callCount > 0 ? (double) totalDuration / callCount : 0;
        }
    }
    
    /**
     * 性能热点
     */
    public static class PerformanceHotspot {
        private final String operationName;
        private final OperationStats stats;
        private final long totalDuration;
        
        public PerformanceHotspot(String operationName, OperationStats stats) {
            this.operationName = operationName;
            this.stats = stats;
            this.totalDuration = stats.getTotalDuration();
        }
        
        public String getOperationName() {
            return operationName;
        }
        
        public OperationStats getStats() {
            return stats;
        }
        
        public long getTotalDuration() {
            return totalDuration;
        }
    }
}