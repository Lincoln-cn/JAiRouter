package org.unreal.modelrouter.monitoring.circuitbreaker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 指标收集降级策略
 * 在系统负载过高或错误频发时，自动降级指标收集功能
 */
@Component
public class MetricsDegradationStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsDegradationStrategy.class);
    
    private static final SecureRandom secureRandom = new SecureRandom();
    
    private final MeterRegistry meterRegistry;
    private final Counter degradationActivations;
    private final Counter degradationDeactivations;
    private final AtomicInteger currentDegradationLevel = new AtomicInteger(0);
    
    // 降级级别定义
    public enum DegradationLevel {
        NONE(0, 1.0, "正常模式"),
        LIGHT(1, 0.5, "轻度降级 - 50%采样"),
        MODERATE(2, 0.2, "中度降级 - 20%采样"),
        HEAVY(3, 0.05, "重度降级 - 5%采样"),
        EMERGENCY(4, 0.0, "紧急降级 - 停止收集");
        
        private final int level;
        private final double samplingRate;
        private final String description;
        
        DegradationLevel(int level, double samplingRate, String description) {
            this.level = level;
            this.samplingRate = samplingRate;
            this.description = description;
        }
        
        public int getLevel() { return level; }
        public double getSamplingRate() { return samplingRate; }
        public String getDescription() { return description; }
        
        public static DegradationLevel fromLevel(int level) {
            for (DegradationLevel dl : values()) {
                if (dl.level == level) return dl;
            }
            return NONE;
        }
    }
    
    // 降级触发条件
    private static final int ERROR_THRESHOLD_LIGHT = 5;
    private static final int ERROR_THRESHOLD_MODERATE = 15;
    private static final int ERROR_THRESHOLD_HEAVY = 30;
    private static final int ERROR_THRESHOLD_EMERGENCY = 50;
    
    private static final double MEMORY_THRESHOLD_LIGHT = 0.7;
    private static final double MEMORY_THRESHOLD_MODERATE = 0.8;
    private static final double MEMORY_THRESHOLD_HEAVY = 0.9;
    private static final double MEMORY_THRESHOLD_EMERGENCY = 0.95;
    
    // 状态跟踪
    private final AtomicBoolean autoModeEnabled = new AtomicBoolean(true);
    private final ConcurrentHashMap<String, Integer> componentErrorCounts = new ConcurrentHashMap<>();
    private Instant lastDegradationChange = Instant.now();
    
    public MetricsDegradationStrategy(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.degradationActivations = Counter.builder("jairouter.metrics.degradation.activations")
            .description("降级策略激活次数")
            .register(meterRegistry);
        this.degradationDeactivations = Counter.builder("jairouter.metrics.degradation.deactivations")
            .description("降级策略停用次数")
            .register(meterRegistry);
        
        // 注册降级级别指标
        Gauge.builder("jairouter.metrics.degradation.level", currentDegradationLevel, AtomicInteger::doubleValue)
            .description("当前降级级别")
            .register(meterRegistry);
    }
    
    /**
     * 根据系统状态自动调整降级级别
     */
    public void evaluateAndAdjustDegradation(double memoryUsageRatio, int totalErrors) {
        if (!autoModeEnabled.get()) {
            return;
        }
        
        DegradationLevel newLevel = calculateOptimalDegradationLevel(memoryUsageRatio, totalErrors);
        DegradationLevel currentLevel = getCurrentDegradationLevel();
        
        if (newLevel != currentLevel) {
            setDegradationLevel(newLevel);
            logger.info("自动调整降级级别: {} -> {} (内存使用率: {:.1%}, 错误数: {})", 
                       currentLevel.getDescription(), newLevel.getDescription(), 
                       memoryUsageRatio, totalErrors);
        }
    }
    
    /**
     * 计算最优降级级别
     */
    private DegradationLevel calculateOptimalDegradationLevel(double memoryUsageRatio, int totalErrors) {
        // 基于内存使用率的降级
        DegradationLevel memoryBasedLevel = DegradationLevel.NONE;
        if (memoryUsageRatio >= MEMORY_THRESHOLD_EMERGENCY) {
            memoryBasedLevel = DegradationLevel.EMERGENCY;
        } else if (memoryUsageRatio >= MEMORY_THRESHOLD_HEAVY) {
            memoryBasedLevel = DegradationLevel.HEAVY;
        } else if (memoryUsageRatio >= MEMORY_THRESHOLD_MODERATE) {
            memoryBasedLevel = DegradationLevel.MODERATE;
        } else if (memoryUsageRatio >= MEMORY_THRESHOLD_LIGHT) {
            memoryBasedLevel = DegradationLevel.LIGHT;
        }
        
        // 基于错误数量的降级
        DegradationLevel errorBasedLevel = DegradationLevel.NONE;
        if (totalErrors >= ERROR_THRESHOLD_EMERGENCY) {
            errorBasedLevel = DegradationLevel.EMERGENCY;
        } else if (totalErrors >= ERROR_THRESHOLD_HEAVY) {
            errorBasedLevel = DegradationLevel.HEAVY;
        } else if (totalErrors >= ERROR_THRESHOLD_MODERATE) {
            errorBasedLevel = DegradationLevel.MODERATE;
        } else if (totalErrors >= ERROR_THRESHOLD_LIGHT) {
            errorBasedLevel = DegradationLevel.LIGHT;
        }
        
        // 取较高的降级级别
        return memoryBasedLevel.getLevel() > errorBasedLevel.getLevel() ? 
               memoryBasedLevel : errorBasedLevel;
    }
    
    /**
     * 设置降级级别
     */
    public void setDegradationLevel(DegradationLevel level) {
        DegradationLevel oldLevel = getCurrentDegradationLevel();
        currentDegradationLevel.set(level.getLevel());
        lastDegradationChange = Instant.now();
        
        if (level.getLevel() > oldLevel.getLevel()) {
            degradationActivations.increment();
        } else if (level.getLevel() < oldLevel.getLevel()) {
            degradationDeactivations.increment();
        }
        
        logger.info("降级级别变更: {} -> {}", oldLevel.getDescription(), level.getDescription());
    }
    
    /**
     * 获取当前降级级别
     */
    public DegradationLevel getCurrentDegradationLevel() {
        return DegradationLevel.fromLevel(currentDegradationLevel.get());
    }
    
    /**
     * 获取当前采样率
     */
    public double getCurrentSamplingRate() {
        return getCurrentDegradationLevel().getSamplingRate();
    }
    
    /**
     * 检查是否应该收集指标
     */
    public boolean shouldCollectMetrics() {
        DegradationLevel level = getCurrentDegradationLevel();
        if (level == DegradationLevel.EMERGENCY) {
            return false;
        }
        
        if (level == DegradationLevel.NONE) {
            return true;
        }
        
        // 基于采样率决定
        return secureRandom.nextDouble() < level.getSamplingRate();
    }
    
    /**
     * 检查特定组件是否应该收集指标
     */
    public boolean shouldCollectMetrics(String component) {
        if (!shouldCollectMetrics()) {
            return false;
        }
        
        // 检查组件特定的错误计数
        Integer errorCount = componentErrorCounts.get(component);
        if (errorCount != null && errorCount > 10) {
            // 高错误组件降低采样率
            return secureRandom.nextDouble() < 0.1;
        }
        
        return true;
    }
    
    /**
     * 记录组件错误
     */
    public void recordComponentError(String component) {
        componentErrorCounts.merge(component, 1, Integer::sum);
    }
    
    /**
     * 重置组件错误计数
     */
    public void resetComponentErrors(String component) {
        componentErrorCounts.remove(component);
        logger.debug("重置组件 {} 的错误计数", component);
    }
    
    /**
     * 启用/禁用自动模式
     */
    public void setAutoModeEnabled(boolean enabled) {
        autoModeEnabled.set(enabled);
        logger.info("降级策略自动模式: {}", enabled ? "启用" : "禁用");
    }
    
    /**
     * 检查自动模式是否启用
     */
    public boolean isAutoModeEnabled() {
        return autoModeEnabled.get();
    }
    
    /**
     * 获取降级状态信息
     */
    public DegradationStatus getDegradationStatus() {
        DegradationLevel level = getCurrentDegradationLevel();
        return new DegradationStatus(
            level,
            level.getSamplingRate(),
            autoModeEnabled.get(),
            Duration.between(lastDegradationChange, Instant.now()),
            componentErrorCounts.size()
        );
    }
    
    /**
     * 强制恢复到正常模式
     */
    public void forceRecovery() {
        setDegradationLevel(DegradationLevel.NONE);
        componentErrorCounts.clear();
        logger.info("强制恢复到正常模式");
    }
    

    
    /**
     * 降级状态信息
     */
    public static class DegradationStatus {
        private final DegradationLevel level;
        private final double samplingRate;
        private final boolean autoModeEnabled;
        private final Duration timeSinceLastChange;
        private final int errorComponentCount;
        
        public DegradationStatus(DegradationLevel level, double samplingRate, 
                               boolean autoModeEnabled, Duration timeSinceLastChange, 
                               int errorComponentCount) {
            this.level = level;
            this.samplingRate = samplingRate;
            this.autoModeEnabled = autoModeEnabled;
            this.timeSinceLastChange = timeSinceLastChange;
            this.errorComponentCount = errorComponentCount;
        }
        
        public DegradationLevel getLevel() { return level; }
        public double getSamplingRate() { return samplingRate; }
        public boolean isAutoModeEnabled() { return autoModeEnabled; }
        public Duration getTimeSinceLastChange() { return timeSinceLastChange; }
        public int getErrorComponentCount() { return errorComponentCount; }
    }
}