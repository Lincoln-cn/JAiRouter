package org.unreal.modelrouter.tracing.sampler;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自适应采样策略
 * 
 * 根据系统负载和请求频率动态调整采样率
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
public class AdaptiveSamplingStrategy implements SamplingStrategy {
    
    // 默认采样率
    private final double defaultRatio;
    
    // 最大采样率
    private final double maxRatio;
    
    // 最小采样率
    private final double minRatio;
    
    // 调整阈值
    private final long adjustmentThreshold;
    
    // 每个span名称的计数器
    private final Map<String, AtomicLong> spanCounters = new ConcurrentHashMap<>();
    
    // 上次调整时间
    private volatile long lastAdjustmentTime = System.currentTimeMillis();
    
    // 调整间隔（毫秒）
    private final long adjustmentInterval;
    
    private final String description;
    
    /**
     * 构造自适应采样策略
     * 
     * @param defaultRatio 默认采样率
     * @param maxRatio 最大采样率
     * @param minRatio 最小采样率
     * @param adjustmentThreshold 调整阈值
     * @param adjustmentInterval 调整间隔（秒）
     */
    public AdaptiveSamplingStrategy(
            double defaultRatio,
            double maxRatio,
            double minRatio,
            long adjustmentThreshold,
            long adjustmentInterval) {
        this.defaultRatio = defaultRatio;
        this.maxRatio = maxRatio;
        this.minRatio = minRatio;
        this.adjustmentThreshold = adjustmentThreshold;
        this.adjustmentInterval = adjustmentInterval * 1000; // 转换为毫秒
        this.description = "AdaptiveSampling";
    }
    
    @Override
    public SamplingResult shouldSample(
            Context parentContext,
            String traceId,
            String spanName,
            SpanKind spanKind,
            Map<String, Object> attributes,
            Map<String, String> parentLinks) {
        
        // 获取或创建计数器
        AtomicLong counter = spanCounters.computeIfAbsent(spanName, k -> new AtomicLong(0));
        long count = counter.incrementAndGet();
        
        // 计算当前采样率
        double currentRatio = calculateCurrentRatio(spanName, count);
        
        // 使用基于比例的采样器进行采样决策
        RatioBasedSamplingStrategy ratioSampler = new RatioBasedSamplingStrategy(currentRatio);
        return ratioSampler.shouldSample(parentContext, traceId, spanName, spanKind, attributes, parentLinks);
    }
    
    /**
     * 计算当前采样率
     * 
     * @param spanName span名称
     * @param count 当前计数
     * @return 当前采样率
     */
    private double calculateCurrentRatio(String spanName, long count) {
        long currentTime = System.currentTimeMillis();
        
        // 检查是否需要调整采样率
        if (currentTime - lastAdjustmentTime > adjustmentInterval) {
            synchronized (this) {
                if (currentTime - lastAdjustmentTime > adjustmentInterval) {
                    // 重置计数器
                    spanCounters.clear();
                    lastAdjustmentTime = currentTime;
                }
            }
        }
        
        // 根据计数调整采样率
        if (count > adjustmentThreshold) {
            // 降低采样率
            return Math.max(minRatio, defaultRatio * adjustmentThreshold / count);
        } else {
            // 使用默认采样率或适当增加
            return Math.min(maxRatio, defaultRatio * Math.sqrt(adjustmentThreshold / Math.max(1, count)));
        }
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    /**
     * 获取当前各span的计数信息（用于监控）
     * 
     * @return 计数信息映射
     */
    public Map<String, Long> getSpanCounts() {
        Map<String, Long> counts = new ConcurrentHashMap<>();
        spanCounters.forEach((name, counter) -> counts.put(name, counter.get()));
        return counts;
    }
}