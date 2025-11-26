package org.unreal.modelrouter.security.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JWT持久化监控指标服务
 * 提供令牌操作、黑名单操作、存储健康状态等监控指标
 */
@Service
@ConditionalOnProperty(name = "jairouter.security.monitoring.jwt-persistence.enabled", havingValue = "true")
public class JwtPersistenceMetricsService {
    
    private static final Logger log = LoggerFactory.getLogger(JwtPersistenceMetricsService.class);
    
    private final MeterRegistry meterRegistry;
    
    // 令牌操作计时器
    private final Map<String, Timer> tokenOperationTimers = new ConcurrentHashMap<>();
    
    // 黑名单操作计时器
    private final Map<String, Timer> blacklistOperationTimers = new ConcurrentHashMap<>();
    
    // 存储健康状态计量器
    private final Map<String, Gauge> storageHealthGauges = new ConcurrentHashMap<>();
    
    // 计数器
    private final Counter tokenCreateCounter;
    private final Counter tokenValidateCounter;
    private final Counter tokenRevokeCounter;
    private final Counter blacklistAddCounter;
    private final Counter blacklistCheckCounter;
    private final Counter storageErrorCounter;
    
    // 容量指标
    private final AtomicLong activeTokenCount = new AtomicLong(0);
    private final AtomicLong blacklistSize = new AtomicLong(0);
    private final AtomicLong memoryUsageBytes = new AtomicLong(0);
    private final AtomicLong redisUsageBytes = new AtomicLong(0);
    
    // 性能指标
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong errorOperations = new AtomicLong(0);
    
    public JwtPersistenceMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 初始化计数器
        this.tokenCreateCounter = Counter.builder("jwt.token.create")
                .description("JWT令牌创建次数")
                .tag("component", "jwt-persistence")
                .register(meterRegistry);
                
        this.tokenValidateCounter = Counter.builder("jwt.token.validate")
                .description("JWT令牌验证次数")
                .tag("component", "jwt-persistence")
                .register(meterRegistry);
                
        this.tokenRevokeCounter = Counter.builder("jwt.token.revoke")
                .description("JWT令牌撤销次数")
                .tag("component", "jwt-persistence")
                .register(meterRegistry);
                
        this.blacklistAddCounter = Counter.builder("jwt.blacklist.add")
                .description("黑名单添加次数")
                .tag("component", "jwt-persistence")
                .register(meterRegistry);
                
        this.blacklistCheckCounter = Counter.builder("jwt.blacklist.check")
                .description("黑名单检查次数")
                .tag("component", "jwt-persistence")
                .register(meterRegistry);
                
        this.storageErrorCounter = Counter.builder("jwt.storage.error")
                .description("存储操作错误次数")
                .tag("component", "jwt-persistence")
                .register(meterRegistry);
        
        // 注册容量指标
        registerCapacityGauges();
        
        log.info("JWT持久化监控指标服务已初始化");
    }
    
    /**
     * 记录令牌操作时间
     */
    public Timer.Sample startTokenOperation(String operation, String storageType) {
        String key = operation + "." + storageType;
        Timer timer = tokenOperationTimers.computeIfAbsent(key, k -> 
            Timer.builder("jwt.token.operation.duration")
                .description("JWT令牌操作耗时")
                .tag("operation", operation)
                .tag("storage_type", storageType)
                .tag("component", "jwt-persistence")
                .register(meterRegistry));
        
        totalOperations.incrementAndGet();
        return Timer.start(meterRegistry);
    }
    
    /**
     * 完成令牌操作计时
     */
    public void finishTokenOperation(Timer.Sample sample, String operation, String storageType, boolean success) {
        String key = operation + "." + storageType;
        Timer timer = tokenOperationTimers.get(key);
        if (timer != null && sample != null) {
            sample.stop(timer);
        }
        
        // 更新计数器
        switch (operation) {
            case "create":
                tokenCreateCounter.increment();
                break;
            case "validate":
                tokenValidateCounter.increment();
                break;
            case "revoke":
                tokenRevokeCounter.increment();
                break;
        }
        
        if (!success) {
            errorOperations.incrementAndGet();
            Counter.builder("jwt.storage.error")
                .description("存储操作错误次数")
                .tag("operation", operation)
                .tag("storage_type", storageType)
                .tag("component", "jwt-persistence")
                .register(meterRegistry)
                .increment();
        }
    }
    
    /**
     * 记录黑名单操作时间
     */
    public Timer.Sample startBlacklistOperation(String operation, String storageType) {
        String key = operation + "." + storageType;
        Timer timer = blacklistOperationTimers.computeIfAbsent(key, k -> 
            Timer.builder("jwt.blacklist.operation.duration")
                .description("JWT黑名单操作耗时")
                .tag("operation", operation)
                .tag("storage_type", storageType)
                .tag("component", "jwt-persistence")
                .register(meterRegistry));
        
        return Timer.start(meterRegistry);
    }
    
    /**
     * 完成黑名单操作计时
     */
    public void finishBlacklistOperation(Timer.Sample sample, String operation, String storageType, boolean success) {
        String key = operation + "." + storageType;
        Timer timer = blacklistOperationTimers.get(key);
        if (timer != null && sample != null) {
            sample.stop(timer);
        }
        
        // 更新计数器
        switch (operation) {
            case "add":
                blacklistAddCounter.increment();
                break;
            case "check":
                blacklistCheckCounter.increment();
                break;
        }
        
        if (!success) {
            Counter.builder("jwt.storage.error")
                .description("存储操作错误次数")
                .tag("operation", operation)
                .tag("storage_type", storageType)
                .tag("component", "jwt-persistence")
                .register(meterRegistry)
                .increment();
        }
    }
    
    /**
     * 更新存储健康状态
     */
    public void updateStorageHealth(String storageType, boolean healthy) {
        // 这里我们使用简单的方式，通过AtomicLong来存储健康状态
        // 在实际使用中，可以通过其他方式来动态更新Gauge值
    }
    
    /**
     * 更新活跃令牌数量
     */
    public void updateActiveTokenCount(long count) {
        activeTokenCount.set(count);
    }
    
    /**
     * 更新黑名单大小
     */
    public void updateBlacklistSize(long size) {
        blacklistSize.set(size);
    }
    
    /**
     * 更新内存使用量
     */
    public void updateMemoryUsage(long bytes) {
        memoryUsageBytes.set(bytes);
    }
    
    /**
     * 更新Redis使用量
     */
    public void updateRedisUsage(long bytes) {
        redisUsageBytes.set(bytes);
    }
    
    /**
     * 记录清理操作统计
     */
    public void recordCleanupOperation(String type, long itemsRemoved, Duration duration) {
        Timer.builder("jwt.cleanup.duration")
            .description("清理操作耗时")
            .tag("cleanup_type", type)
            .tag("component", "jwt-persistence")
            .register(meterRegistry)
            .record(duration);
            
        Counter.builder("jwt.cleanup.items.removed")
            .description("清理操作移除的项目数")
            .tag("cleanup_type", type)
            .tag("component", "jwt-persistence")
            .register(meterRegistry)
            .increment(itemsRemoved);
    }
    
    /**
     * 获取错误率
     */
    public double getErrorRate() {
        long total = totalOperations.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) errorOperations.get() / total;
    }
    
    /**
     * 获取当前指标摘要
     */
    public Map<String, Object> getMetricsSummary() {
        Map<String, Object> summary = new ConcurrentHashMap<>();
        
        // 基本计数
        summary.put("activeTokens", activeTokenCount.get());
        summary.put("blacklistSize", blacklistSize.get());
        summary.put("totalOperations", totalOperations.get());
        summary.put("errorOperations", errorOperations.get());
        summary.put("errorRate", getErrorRate());
        
        // 容量信息
        summary.put("memoryUsageBytes", memoryUsageBytes.get());
        summary.put("redisUsageBytes", redisUsageBytes.get());
        
        // 操作计数
        summary.put("tokenCreateCount", tokenCreateCounter.count());
        summary.put("tokenValidateCount", tokenValidateCounter.count());
        summary.put("tokenRevokeCount", tokenRevokeCounter.count());
        summary.put("blacklistAddCount", blacklistAddCounter.count());
        summary.put("blacklistCheckCount", blacklistCheckCounter.count());
        summary.put("storageErrorCount", storageErrorCounter.count());
        
        return summary;
    }
    
    /**
     * 注册容量指标
     */
    private void registerCapacityGauges() {
        // 活跃令牌数量
        Gauge.builder("jwt.token.active.count", activeTokenCount, AtomicLong::doubleValue)
            .description("活跃JWT令牌数量")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        // 黑名单大小
        Gauge.builder("jwt.blacklist.size", blacklistSize, AtomicLong::doubleValue)
            .description("JWT黑名单大小")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        // 内存使用量
        Gauge.builder("jwt.storage.memory.usage.bytes", memoryUsageBytes, AtomicLong::doubleValue)
            .description("内存存储使用量")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        // Redis使用量
        Gauge.builder("jwt.storage.redis.usage.bytes", redisUsageBytes, AtomicLong::doubleValue)
            .description("Redis存储使用量")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        // 错误率
        Gauge.builder("jwt.operation.error.rate", this, JwtPersistenceMetricsService::getErrorRate)
            .description("操作错误率")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
    }
}