package org.unreal.modelrouter.security.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 清理操作监控指标服务
 * 监控JWT令牌和黑名单的清理操作统计
 */
@Service
@ConditionalOnProperty(name = "jairouter.security.monitoring.jwt-persistence.enabled", havingValue = "true")
public class CleanupMetricsService {
    
    private static final Logger log = LoggerFactory.getLogger(CleanupMetricsService.class);
    
    private final MeterRegistry meterRegistry;
    
    // 清理操作计时器
    private final Timer tokenCleanupTimer;
    private final Timer blacklistCleanupTimer;
    
    // 清理统计计数器
    private final Counter tokenCleanupCounter;
    private final Counter blacklistCleanupCounter;
    private final Counter cleanupErrorCounter;
    
    // 清理结果指标
    private final AtomicLong lastTokenCleanupCount = new AtomicLong(0);
    private final AtomicLong lastBlacklistCleanupCount = new AtomicLong(0);
    private final AtomicLong totalTokensRemoved = new AtomicLong(0);
    private final AtomicLong totalBlacklistItemsRemoved = new AtomicLong(0);
    
    // 清理时间记录
    private final AtomicLong lastTokenCleanupTime = new AtomicLong(0);
    private final AtomicLong lastBlacklistCleanupTime = new AtomicLong(0);
    
    // 清理性能指标
    private final AtomicLong avgTokenCleanupDuration = new AtomicLong(0);
    private final AtomicLong avgBlacklistCleanupDuration = new AtomicLong(0);
    
    // 清理频率统计
    private final Map<String, AtomicLong> cleanupFrequency = new ConcurrentHashMap<>();
    
    public CleanupMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 初始化计时器
        this.tokenCleanupTimer = Timer.builder("jwt.cleanup.token.duration")
            .description("令牌清理操作耗时")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        this.blacklistCleanupTimer = Timer.builder("jwt.cleanup.blacklist.duration")
            .description("黑名单清理操作耗时")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
        
        // 初始化计数器
        this.tokenCleanupCounter = Counter.builder("jwt.cleanup.token.executions")
            .description("令牌清理执行次数")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        this.blacklistCleanupCounter = Counter.builder("jwt.cleanup.blacklist.executions")
            .description("黑名单清理执行次数")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        this.cleanupErrorCounter = Counter.builder("jwt.cleanup.errors")
            .description("清理操作错误次数")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
        
        // 注册指标
        registerCleanupGauges();
        
        log.info("清理操作监控服务已初始化");
    }
    
    /**
     * 开始令牌清理操作计时
     */
    public Timer.Sample startTokenCleanup() {
        return Timer.start(meterRegistry);
    }
    
    /**
     * 完成令牌清理操作
     */
    public void finishTokenCleanup(Timer.Sample sample, long itemsRemoved, boolean success) {
        if (sample != null) {
            sample.stop(tokenCleanupTimer);
        }
        
        if (success) {
            tokenCleanupCounter.increment();
            lastTokenCleanupCount.set(itemsRemoved);
            totalTokensRemoved.addAndGet(itemsRemoved);
            lastTokenCleanupTime.set(System.currentTimeMillis());
            
            // 记录清理项目数量
            Counter.builder("jwt.cleanup.token.items.removed")
                .description("令牌清理移除的项目数")
                .tag("component", "jwt-persistence")
                .register(meterRegistry)
                .increment(itemsRemoved);
                
            updateCleanupFrequency("token");
            
            log.debug("令牌清理完成，移除 {} 个过期令牌", itemsRemoved);
        } else {
            Counter.builder("jwt.cleanup.errors")
                .description("清理操作错误次数")
                .tag("cleanup_type", "token")
                .tag("component", "jwt-persistence")
                .register(meterRegistry)
                .increment();
            log.warn("令牌清理操作失败");
        }
    }
    
    /**
     * 开始黑名单清理操作计时
     */
    public Timer.Sample startBlacklistCleanup() {
        return Timer.start(meterRegistry);
    }
    
    /**
     * 完成黑名单清理操作
     */
    public void finishBlacklistCleanup(Timer.Sample sample, long itemsRemoved, boolean success) {
        if (sample != null) {
            sample.stop(blacklistCleanupTimer);
        }
        
        if (success) {
            blacklistCleanupCounter.increment();
            lastBlacklistCleanupCount.set(itemsRemoved);
            totalBlacklistItemsRemoved.addAndGet(itemsRemoved);
            lastBlacklistCleanupTime.set(System.currentTimeMillis());
            
            // 记录清理项目数量
            Counter.builder("jwt.cleanup.blacklist.items.removed")
                .description("黑名单清理移除的项目数")
                .tag("component", "jwt-persistence")
                .register(meterRegistry)
                .increment(itemsRemoved);
                
            updateCleanupFrequency("blacklist");
            
            log.debug("黑名单清理完成，移除 {} 个过期条目", itemsRemoved);
        } else {
            Counter.builder("jwt.cleanup.errors")
                .description("清理操作错误次数")
                .tag("cleanup_type", "blacklist")
                .tag("component", "jwt-persistence")
                .register(meterRegistry)
                .increment();
            log.warn("黑名单清理操作失败");
        }
    }
    
    /**
     * 记录手动清理操作
     */
    public void recordManualCleanup(String type, long itemsRemoved, Duration duration) {
        Timer.builder("jwt.cleanup.manual.duration")
            .description("手动清理操作耗时")
            .tag("cleanup_type", type)
            .tag("component", "jwt-persistence")
            .register(meterRegistry)
            .record(duration);
            
        Counter.builder("jwt.cleanup.manual.executions")
            .description("手动清理执行次数")
            .tag("cleanup_type", type)
            .tag("component", "jwt-persistence")
            .register(meterRegistry)
            .increment();
            
        Counter.builder("jwt.cleanup.manual.items.removed")
            .description("手动清理移除的项目数")
            .tag("cleanup_type", type)
            .tag("component", "jwt-persistence")
            .register(meterRegistry)
            .increment(itemsRemoved);
            
        log.info("手动清理操作完成 - 类型: {}, 移除项目: {}, 耗时: {}ms", 
            type, itemsRemoved, duration.toMillis());
    }
    
    /**
     * 更新清理频率统计
     */
    private void updateCleanupFrequency(String type) {
        String key = type + "_frequency";
        cleanupFrequency.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * 获取清理统计摘要
     */
    public Map<String, Object> getCleanupStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        // 基本统计
        stats.put("tokenCleanupExecutions", tokenCleanupCounter.count());
        stats.put("blacklistCleanupExecutions", blacklistCleanupCounter.count());
        stats.put("cleanupErrors", cleanupErrorCounter.count());
        
        // 最近清理结果
        stats.put("lastTokenCleanupCount", lastTokenCleanupCount.get());
        stats.put("lastBlacklistCleanupCount", lastBlacklistCleanupCount.get());
        stats.put("lastTokenCleanupTime", lastTokenCleanupTime.get());
        stats.put("lastBlacklistCleanupTime", lastBlacklistCleanupTime.get());
        
        // 累计统计
        stats.put("totalTokensRemoved", totalTokensRemoved.get());
        stats.put("totalBlacklistItemsRemoved", totalBlacklistItemsRemoved.get());
        
        // 性能指标
        stats.put("avgTokenCleanupDurationMs", avgTokenCleanupDuration.get());
        stats.put("avgBlacklistCleanupDurationMs", avgBlacklistCleanupDuration.get());
        
        // 频率统计
        Map<String, Object> frequency = new ConcurrentHashMap<>();
        cleanupFrequency.forEach((key, count) -> frequency.put(key, count.get()));
        stats.put("cleanupFrequency", frequency);
        
        // 计算清理效率
        long totalExecutions = (long) (tokenCleanupCounter.count() + blacklistCleanupCounter.count());
        if (totalExecutions > 0) {
            double avgItemsPerExecution = (double) (totalTokensRemoved.get() + totalBlacklistItemsRemoved.get()) / totalExecutions;
            stats.put("avgItemsPerExecution", avgItemsPerExecution);
        }
        
        return stats;
    }
    
    /**
     * 获取清理健康状态
     */
    public boolean isCleanupHealthy() {
        // 检查最近是否有清理操作
        long currentTime = System.currentTimeMillis();
        long lastTokenCleanup = lastTokenCleanupTime.get();
        long lastBlacklistCleanup = lastBlacklistCleanupTime.get();
        
        // 如果超过24小时没有清理操作，认为不健康
        long maxInterval = 24 * 60 * 60 * 1000; // 24小时
        
        boolean tokenCleanupHealthy = (currentTime - lastTokenCleanup) < maxInterval;
        boolean blacklistCleanupHealthy = (currentTime - lastBlacklistCleanup) < maxInterval;
        
        return tokenCleanupHealthy && blacklistCleanupHealthy;
    }
    
    /**
     * 重置清理统计
     */
    public void resetCleanupStats() {
        lastTokenCleanupCount.set(0);
        lastBlacklistCleanupCount.set(0);
        totalTokensRemoved.set(0);
        totalBlacklistItemsRemoved.set(0);
        cleanupFrequency.clear();
        
        log.info("清理统计已重置");
    }
    
    /**
     * 注册清理相关的Gauge指标
     */
    private void registerCleanupGauges() {
        // 最近清理数量
        Gauge.builder("jwt.cleanup.token.last.count", lastTokenCleanupCount, AtomicLong::doubleValue)
            .description("最近一次令牌清理数量")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        Gauge.builder("jwt.cleanup.blacklist.last.count", lastBlacklistCleanupCount, AtomicLong::doubleValue)
            .description("最近一次黑名单清理数量")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        // 累计清理数量
        Gauge.builder("jwt.cleanup.token.total.removed", totalTokensRemoved, AtomicLong::doubleValue)
            .description("累计清理的令牌数量")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        Gauge.builder("jwt.cleanup.blacklist.total.removed", totalBlacklistItemsRemoved, AtomicLong::doubleValue)
            .description("累计清理的黑名单项目数量")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        // 平均清理时间
        Gauge.builder("jwt.cleanup.token.avg.duration.ms", avgTokenCleanupDuration, AtomicLong::doubleValue)
            .description("令牌清理平均耗时")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        Gauge.builder("jwt.cleanup.blacklist.avg.duration.ms", avgBlacklistCleanupDuration, AtomicLong::doubleValue)
            .description("黑名单清理平均耗时")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        // 清理健康状态
        Gauge.builder("jwt.cleanup.health.status", this, service -> service.isCleanupHealthy() ? 1.0 : 0.0)
            .description("清理操作健康状态")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
    }
}