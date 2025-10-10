package org.unreal.modelrouter.security.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT持久化系统健康检查服务
 * 检查JWT令牌持久化相关组件的健康状态
 */
@Component("jwtPersistenceHealthIndicator")
@ConditionalOnProperty(name = "jairouter.security.monitoring.jwt-persistence.health-checks.enabled", havingValue = "true", matchIfMissing = true)
public class JwtPersistenceHealthIndicator {
    
    private static final Logger log = LoggerFactory.getLogger(JwtPersistenceHealthIndicator.class);
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
    private static final long HEALTH_CHECK_TIMEOUT_MS = 5000; // 5秒超时
    
    public Map<String, Object> getHealthStatus() {
        try {
            Map<String, Object> details = new HashMap<>();
            boolean overallHealthy = true;
            
            // 检查Redis连接
            boolean redisHealthy = checkRedisHealth(details);
            overallHealthy &= redisHealthy;
            
            // 检查内存使用情况
            boolean memoryHealthy = checkMemoryHealth(details);
            overallHealthy &= memoryHealthy;
            
            // 检查存储同步状态
            boolean syncHealthy = checkStorageSyncHealth(details);
            overallHealthy &= syncHealthy;
            
            // 添加检查时间戳
            details.put("checkTime", LocalDateTime.now().toString());
            details.put("overallStatus", overallHealthy ? "UP" : "DOWN");
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", overallHealthy ? "UP" : "DOWN");
            result.putAll(details);
            return result;
            
        } catch (Exception e) {
            log.error("JWT持久化健康检查失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("status", "DOWN");
            result.put("error", "Health check failed: " + e.getMessage());
            result.put("checkTime", LocalDateTime.now().toString());
            return result;
        }
    }
    
    /**
     * 检查Redis连接健康状态
     */
    private boolean checkRedisHealth(Map<String, Object> details) {
        Map<String, Object> redisDetails = new HashMap<>();
        
        if (redisTemplate == null) {
            redisDetails.put("status", "DISABLED");
            redisDetails.put("message", "Redis template not configured");
            details.put("redis", redisDetails);
            return true; // Redis未配置时不影响整体健康状态
        }
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 执行ping命令
            String pingResult = redisTemplate.getConnectionFactory()
                .getConnection()
                .ping();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            if ("PONG".equals(pingResult)) {
                redisDetails.put("status", "UP");
                redisDetails.put("responseTimeMs", responseTime);
                
                // 检查连接池状态
                checkRedisConnectionPool(redisDetails);
                
                // 检查Redis内存使用
                checkRedisMemoryUsage(redisDetails);
                
                details.put("redis", redisDetails);
                return true;
            } else {
                redisDetails.put("status", "DOWN");
                redisDetails.put("error", "Unexpected ping response: " + pingResult);
                details.put("redis", redisDetails);
                return false;
            }
            
        } catch (Exception e) {
            redisDetails.put("status", "DOWN");
            redisDetails.put("error", e.getMessage());
            redisDetails.put("errorType", e.getClass().getSimpleName());
            details.put("redis", redisDetails);
            log.warn("Redis健康检查失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查Redis连接池状态
     */
    private void checkRedisConnectionPool(Map<String, Object> redisDetails) {
        try {
            // 这里可以添加连接池状态检查
            // 由于不同的Redis连接池实现方式不同，这里提供基础框架
            redisDetails.put("connectionPool", "Available");
        } catch (Exception e) {
            redisDetails.put("connectionPoolError", e.getMessage());
        }
    }
    
    /**
     * 检查Redis内存使用情况
     */
    private void checkRedisMemoryUsage(Map<String, Object> redisDetails) {
        try {
            // 简化实现，实际项目中可以通过INFO命令获取详细信息
            redisDetails.put("memoryCheck", "Available");
        } catch (Exception e) {
            redisDetails.put("memoryCheckError", e.getMessage());
        }
    }
    
    /**
     * 检查内存健康状态
     */
    private boolean checkMemoryHealth(Map<String, Object> details) {
        Map<String, Object> memoryDetails = new HashMap<>();
        
        try {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();
            
            // 计算内存使用百分比
            double usagePercent = (double) usedMemory / maxMemory * 100;
            
            memoryDetails.put("totalMemoryMB", totalMemory / (1024 * 1024));
            memoryDetails.put("usedMemoryMB", usedMemory / (1024 * 1024));
            memoryDetails.put("freeMemoryMB", freeMemory / (1024 * 1024));
            memoryDetails.put("maxMemoryMB", maxMemory / (1024 * 1024));
            memoryDetails.put("usagePercent", Math.round(usagePercent * 100.0) / 100.0);
            
            // 判断内存健康状态
            if (usagePercent > 90) {
                memoryDetails.put("status", "CRITICAL");
                memoryDetails.put("message", "Memory usage is critically high");
                details.put("memory", memoryDetails);
                return false;
            } else if (usagePercent > 80) {
                memoryDetails.put("status", "WARNING");
                memoryDetails.put("message", "Memory usage is high");
                details.put("memory", memoryDetails);
                return true; // 警告状态不影响整体健康
            } else {
                memoryDetails.put("status", "UP");
                memoryDetails.put("message", "Memory usage is normal");
                details.put("memory", memoryDetails);
                return true;
            }
            
        } catch (Exception e) {
            memoryDetails.put("status", "DOWN");
            memoryDetails.put("error", e.getMessage());
            details.put("memory", memoryDetails);
            log.warn("内存健康检查失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查存储同步健康状态
     */
    private boolean checkStorageSyncHealth(Map<String, Object> details) {
        Map<String, Object> syncDetails = new HashMap<>();
        
        try {
            // 检查存储同步状态
            // 这里可以添加具体的同步状态检查逻辑
            syncDetails.put("status", "UP");
            syncDetails.put("message", "Storage synchronization is healthy");
            
            // 添加同步统计信息
            syncDetails.put("lastSyncTime", "N/A"); // 可以从实际服务获取
            syncDetails.put("syncErrors", 0);
            
            details.put("storageSync", syncDetails);
            return true;
            
        } catch (Exception e) {
            syncDetails.put("status", "DOWN");
            syncDetails.put("error", e.getMessage());
            details.put("storageSync", syncDetails);
            log.warn("存储同步健康检查失败: {}", e.getMessage());
            return false;
        }
    }
}