package org.unreal.modelrouter.security.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 存储系统健康状态监控服务
 * 监控Redis连接状态、内存使用情况等存储相关指标
 */
@Service
@ConditionalOnProperty(name = "jairouter.security.monitoring.jwt-persistence.enabled", havingValue = "true")
public class StorageHealthMetricsService {
    
    private static final Logger log = LoggerFactory.getLogger(StorageHealthMetricsService.class);
    
    private final MeterRegistry meterRegistry;
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
    // 健康状态指标
    private final AtomicLong redisConnectionStatus = new AtomicLong(0); // 0=down, 1=up
    private final AtomicLong memoryHealthStatus = new AtomicLong(1); // 0=unhealthy, 1=healthy
    private final AtomicLong compositeStorageStatus = new AtomicLong(1);
    
    // 性能指标
    private final AtomicLong redisResponseTime = new AtomicLong(0);
    private final AtomicLong memoryUsagePercent = new AtomicLong(0);
    private final AtomicLong redisMemoryUsage = new AtomicLong(0);
    
    // 连接池指标
    private final AtomicLong redisActiveConnections = new AtomicLong(0);
    private final AtomicLong redisIdleConnections = new AtomicLong(0);
    
    // 错误统计
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastErrorTimes = new ConcurrentHashMap<>();
    
    public StorageHealthMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    @PostConstruct
    public void init() {
        registerHealthGauges();
        log.info("存储健康监控服务已初始化");
    }
    
    /**
     * 定期检查存储系统健康状态
     */
    @Scheduled(fixedRateString = "${jairouter.security.monitoring.jwt-persistence.health-checks.check-interval:60}000")
    public void checkStorageHealth() {
        checkRedisHealth();
        checkMemoryHealth();
        updateCompositeStorageStatus();
    }
    
    /**
     * 检查Redis健康状态
     */
    public void checkRedisHealth() {
        if (redisTemplate == null) {
            redisConnectionStatus.set(0);
            return;
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            long startTime = System.currentTimeMillis();
            
            // 执行ping命令检查连接
            String result = redisTemplate.getConnectionFactory()
                .getConnection()
                .ping();
                
            long responseTime = System.currentTimeMillis() - startTime;
            redisResponseTime.set(responseTime);
            
            if ("PONG".equals(result)) {
                redisConnectionStatus.set(1);
                recordSuccess("redis_ping");
                
                // 获取Redis内存使用情况
                checkRedisMemoryUsage();
                
            } else {
                redisConnectionStatus.set(0);
                recordError("redis_ping", "Unexpected ping response: " + result);
            }
            
        } catch (Exception e) {
            redisConnectionStatus.set(0);
            recordError("redis_ping", e.getMessage());
            log.warn("Redis健康检查失败: {}", e.getMessage());
        } finally {
            sample.stop(Timer.builder("jwt.storage.health.check.duration")
                .tag("storage_type", "redis")
                .tag("component", "jwt-persistence")
                .register(meterRegistry));
        }
    }
    
    /**
     * 检查Redis内存使用情况
     */
    private void checkRedisMemoryUsage() {
        try {
            // 这里可以通过Redis INFO命令获取内存使用情况
            // 简化实现，实际项目中可以解析INFO memory命令的结果
            redisMemoryUsage.set(0); // 占位实现
            
        } catch (Exception e) {
            log.debug("获取Redis内存使用情况失败: {}", e.getMessage());
        }
    }
    
    /**
     * 检查内存健康状态
     */
    public void checkMemoryHealth() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();
            
            // 计算内存使用百分比
            long usagePercent = (usedMemory * 100) / maxMemory;
            memoryUsagePercent.set(usagePercent);
            
            // 判断内存健康状态 (超过80%认为不健康)
            if (usagePercent > 80) {
                memoryHealthStatus.set(0);
                recordError("memory_usage", "Memory usage too high: " + usagePercent + "%");
            } else {
                memoryHealthStatus.set(1);
                recordSuccess("memory_usage");
            }
            
        } catch (Exception e) {
            memoryHealthStatus.set(0);
            recordError("memory_check", e.getMessage());
            log.warn("内存健康检查失败: {}", e.getMessage());
        } finally {
            sample.stop(Timer.builder("jwt.storage.health.check.duration")
                .tag("storage_type", "memory")
                .tag("component", "jwt-persistence")
                .register(meterRegistry));
        }
    }
    
    /**
     * 更新复合存储状态
     */
    private void updateCompositeStorageStatus() {
        // 如果Redis和内存都健康，则复合存储健康
        long redisStatus = redisConnectionStatus.get();
        long memoryStatus = memoryHealthStatus.get();
        
        if (redisStatus == 1 && memoryStatus == 1) {
            compositeStorageStatus.set(1);
        } else if (memoryStatus == 1) {
            // Redis不可用但内存可用，降级模式
            compositeStorageStatus.set(0);
        } else {
            // 内存也不可用，完全不可用
            compositeStorageStatus.set(0);
        }
    }
    
    /**
     * 记录成功操作
     */
    private void recordSuccess(String operation) {
        // 重置错误计数
        errorCounts.computeIfAbsent(operation, k -> new AtomicLong(0)).set(0);
    }
    
    /**
     * 记录错误操作
     */
    private void recordError(String operation, String error) {
        errorCounts.computeIfAbsent(operation, k -> new AtomicLong(0)).incrementAndGet();
        lastErrorTimes.computeIfAbsent(operation, k -> new AtomicLong(0)).set(System.currentTimeMillis());
        
        log.debug("存储操作错误 - {}: {}", operation, error);
    }
    
    /**
     * 获取存储健康状态摘要
     */
    public Map<String, Object> getHealthSummary() {
        Map<String, Object> summary = new ConcurrentHashMap<>();
        
        // 基本健康状态
        summary.put("redisHealthy", redisConnectionStatus.get() == 1);
        summary.put("memoryHealthy", memoryHealthStatus.get() == 1);
        summary.put("compositeHealthy", compositeStorageStatus.get() == 1);
        
        // 性能指标
        summary.put("redisResponseTimeMs", redisResponseTime.get());
        summary.put("memoryUsagePercent", memoryUsagePercent.get());
        summary.put("redisMemoryUsage", redisMemoryUsage.get());
        
        // 错误统计
        Map<String, Object> errors = new ConcurrentHashMap<>();
        errorCounts.forEach((operation, count) -> {
            errors.put(operation + "_errors", count.get());
            AtomicLong lastError = lastErrorTimes.get(operation);
            if (lastError != null) {
                errors.put(operation + "_last_error_time", lastError.get());
            }
        });
        summary.put("errors", errors);
        
        return summary;
    }
    
    /**
     * 手动触发健康检查
     */
    public void triggerHealthCheck() {
        log.info("手动触发存储健康检查");
        checkStorageHealth();
    }
    
    /**
     * 注册健康状态指标
     */
    private void registerHealthGauges() {
        // Redis连接状态
        Gauge.builder("jwt.storage.redis.connection.status", redisConnectionStatus, AtomicLong::doubleValue)
            .description("Redis连接状态")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        // 内存健康状态
        Gauge.builder("jwt.storage.memory.health.status", memoryHealthStatus, AtomicLong::doubleValue)
            .description("内存健康状态")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        // 复合存储状态
        Gauge.builder("jwt.storage.composite.status", compositeStorageStatus, AtomicLong::doubleValue)
            .description("复合存储状态")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        // Redis响应时间
        Gauge.builder("jwt.storage.redis.response.time.ms", redisResponseTime, AtomicLong::doubleValue)
            .description("Redis响应时间")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        // 内存使用百分比
        Gauge.builder("jwt.storage.memory.usage.percent", memoryUsagePercent, AtomicLong::doubleValue)
            .description("内存使用百分比")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
            
        // Redis内存使用
        Gauge.builder("jwt.storage.redis.memory.usage.bytes", redisMemoryUsage, AtomicLong::doubleValue)
            .description("Redis内存使用量")
            .tag("component", "jwt-persistence")
            .register(meterRegistry);
    }
}