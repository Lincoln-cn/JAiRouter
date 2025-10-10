package org.unreal.modelrouter.security.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis连接健康检查服务
 * 专门负责Redis连接状态的监控和健康检查
 */
@Service
@ConditionalOnProperty(name = "jairouter.security.monitoring.jwt-persistence.health-checks.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConnectionHealthService {
    
    private static final Logger log = LoggerFactory.getLogger(RedisConnectionHealthService.class);
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
    // 健康状态缓存
    private final AtomicBoolean isHealthy = new AtomicBoolean(true);
    private final AtomicLong lastCheckTime = new AtomicLong(0);
    private final AtomicLong lastSuccessTime = new AtomicLong(0);
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    
    // 性能指标
    private final AtomicLong lastResponseTime = new AtomicLong(0);
    private final AtomicLong totalChecks = new AtomicLong(0);
    private final AtomicLong successfulChecks = new AtomicLong(0);
    
    private static final long HEALTH_CHECK_CACHE_DURATION = 30000; // 30秒缓存
    private static final long MAX_CONSECUTIVE_FAILURES = 3;
    private static final long MAX_RESPONSE_TIME_MS = 5000; // 5秒超时
    
    /**
     * 检查Redis连接状态
     */
    public boolean checkRedisConnection() {
        long currentTime = System.currentTimeMillis();
        
        // 如果缓存未过期，返回缓存结果
        if (currentTime - lastCheckTime.get() < HEALTH_CHECK_CACHE_DURATION) {
            return isHealthy.get();
        }
        
        return performHealthCheck(currentTime);
    }
    
    /**
     * 执行实际的健康检查
     */
    private boolean performHealthCheck(long currentTime) {
        if (redisTemplate == null) {
            log.debug("Redis template not configured, skipping health check");
            isHealthy.set(true); // Redis未配置时认为健康
            lastCheckTime.set(currentTime);
            return true;
        }
        
        totalChecks.incrementAndGet();
        lastCheckTime.set(currentTime);
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 执行ping命令
            String result = redisTemplate.getConnectionFactory()
                .getConnection()
                .ping();
            
            long responseTime = System.currentTimeMillis() - startTime;
            lastResponseTime.set(responseTime);
            
            if ("PONG".equals(result) && responseTime < MAX_RESPONSE_TIME_MS) {
                // 连接成功
                isHealthy.set(true);
                lastSuccessTime.set(currentTime);
                consecutiveFailures.set(0);
                successfulChecks.incrementAndGet();
                
                log.debug("Redis健康检查成功，响应时间: {}ms", responseTime);
                return true;
                
            } else {
                // 响应异常或超时
                handleHealthCheckFailure("Unexpected ping response or timeout: " + result + ", responseTime: " + responseTime + "ms");
                return false;
            }
            
        } catch (Exception e) {
            handleHealthCheckFailure("Redis connection failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 处理健康检查失败
     */
    private void handleHealthCheckFailure(String reason) {
        long failures = consecutiveFailures.incrementAndGet();
        
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            isHealthy.set(false);
            log.warn("Redis连接不健康，连续失败次数: {}, 原因: {}", failures, reason);
        } else {
            log.debug("Redis健康检查失败 ({}/{}): {}", failures, MAX_CONSECUTIVE_FAILURES, reason);
        }
    }
    
    /**
     * 获取详细的Redis健康状态
     */
    public Map<String, Object> getDetailedHealthStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 基本状态
        status.put("healthy", isHealthy.get());
        status.put("configured", redisTemplate != null);
        status.put("lastCheckTime", LocalDateTime.ofEpochSecond(lastCheckTime.get() / 1000, 0, java.time.ZoneOffset.UTC).toString());
        status.put("lastSuccessTime", LocalDateTime.ofEpochSecond(lastSuccessTime.get() / 1000, 0, java.time.ZoneOffset.UTC).toString());
        
        // 性能指标
        status.put("lastResponseTimeMs", lastResponseTime.get());
        status.put("consecutiveFailures", consecutiveFailures.get());
        status.put("totalChecks", totalChecks.get());
        status.put("successfulChecks", successfulChecks.get());
        
        // 计算成功率
        long total = totalChecks.get();
        if (total > 0) {
            double successRate = (double) successfulChecks.get() / total * 100;
            status.put("successRatePercent", Math.round(successRate * 100.0) / 100.0);
        } else {
            status.put("successRatePercent", 0.0);
        }
        
        // 连接信息
        if (redisTemplate != null) {
            try {
                status.put("connectionInfo", getRedisConnectionInfo());
            } catch (Exception e) {
                status.put("connectionInfoError", e.getMessage());
            }
        }
        
        return status;
    }
    
    /**
     * 获取Redis连接信息
     */
    private Map<String, Object> getRedisConnectionInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try {
            // 获取连接工厂信息
            var connectionFactory = redisTemplate.getConnectionFactory();
            info.put("connectionFactoryType", connectionFactory.getClass().getSimpleName());
            
            // 这里可以添加更多连接池相关信息
            // 由于不同的Redis客户端实现不同，这里提供基础框架
            info.put("status", "Available");
            
        } catch (Exception e) {
            info.put("error", e.getMessage());
        }
        
        return info;
    }
    
    /**
     * 手动触发健康检查
     */
    public boolean triggerHealthCheck() {
        log.info("手动触发Redis健康检查");
        // 清除缓存，强制执行新的检查
        lastCheckTime.set(0);
        return checkRedisConnection();
    }
    
    /**
     * 重置健康状态统计
     */
    public void resetHealthStats() {
        totalChecks.set(0);
        successfulChecks.set(0);
        consecutiveFailures.set(0);
        lastCheckTime.set(0);
        lastSuccessTime.set(0);
        lastResponseTime.set(0);
        isHealthy.set(true);
        
        log.info("Redis健康状态统计已重置");
    }
    
    /**
     * 获取当前健康状态（不触发检查）
     */
    public boolean getCurrentHealthStatus() {
        return isHealthy.get();
    }
    
    /**
     * 获取最后响应时间
     */
    public long getLastResponseTime() {
        return lastResponseTime.get();
    }
    
    /**
     * 获取连续失败次数
     */
    public long getConsecutiveFailures() {
        return consecutiveFailures.get();
    }
    
    /**
     * 检查是否需要告警
     */
    public boolean shouldAlert() {
        return !isHealthy.get() && consecutiveFailures.get() >= MAX_CONSECUTIVE_FAILURES;
    }
}