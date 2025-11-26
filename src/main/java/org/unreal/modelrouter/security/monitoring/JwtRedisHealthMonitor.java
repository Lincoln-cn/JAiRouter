package org.unreal.modelrouter.security.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.security.cache.JwtBlacklistCacheManager;
import org.unreal.modelrouter.security.config.RedisJwtCacheConfiguration;

import java.time.Duration;
import java.util.Map;

/**
 * JWT Redis健康监控器
 * 监控JWT相关的Redis服务健康状态
 */
@Slf4j
@Component("jwtRedisHealthIndicator")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.persistence.redis.enabled", havingValue = "true")
public class JwtRedisHealthMonitor implements HealthIndicator {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final JwtBlacklistCacheManager cacheManager;
    private final RedisJwtCacheConfiguration.RedisJwtHealthChecker healthChecker;
    
    private static final String HEALTH_CHECK_KEY = "jwt:health_monitor";
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(5);
    
    @Override
    public Health health() {
        try {
            Health.Builder builder = Health.up();
            
            // 检查Redis连接
            boolean redisHealthy = checkRedisConnection();
            if (!redisHealthy) {
                return Health.down()
                    .withDetail("redis", "Connection failed")
                    .withDetail("status", "Redis connection is not available")
                    .build();
            }
            
            builder.withDetail("redis", "Connected");
            
            // 检查JWT持久化功能
            boolean persistenceHealthy = checkJwtPersistence();
            builder.withDetail("jwt-persistence", persistenceHealthy ? "Healthy" : "Issues detected");
            
            // 检查黑名单缓存功能
            Map<String, Object> cacheStats = checkBlacklistCache();
            builder.withDetail("blacklist-cache", cacheStats);
            
            // 检查Redis性能
            Map<String, Object> performanceStats = checkRedisPerformance();
            builder.withDetail("performance", performanceStats);
            
            // 综合健康状态
            if (persistenceHealthy && !cacheStats.containsKey("error")) {
                return builder.build();
            } else {
                return builder.status("DEGRADED")
                    .withDetail("warning", "Some JWT Redis features have issues")
                    .build();
            }
            
        } catch (Exception e) {
            log.error("JWT Redis health check failed: {}", e.getMessage(), e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("status", "Health check failed")
                .build();
        }
    }
    
    /**
     * 检查Redis连接状态
     */
    private boolean checkRedisConnection() {
        try {
            return healthChecker.isHealthy();
        } catch (Exception e) {
            log.warn("Redis connection check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查JWT持久化功能
     */
    private boolean checkJwtPersistence() {
        try {
            String testKey = HEALTH_CHECK_KEY + ":persistence";
            String testValue = "test_" + System.currentTimeMillis();
            
            // 测试写入
            Boolean setResult = redisTemplate.opsForValue()
                .set(testKey, testValue, Duration.ofSeconds(10))
                .block(HEALTH_CHECK_TIMEOUT);
            
            if (!Boolean.TRUE.equals(setResult)) {
                return false;
            }
            
            // 测试读取
            String getValue = redisTemplate.opsForValue()
                .get(testKey)
                .block(HEALTH_CHECK_TIMEOUT);
            
            if (!testValue.equals(getValue)) {
                return false;
            }
            
            // 测试删除
            Long deleteResult = redisTemplate.delete(testKey)
                .block(HEALTH_CHECK_TIMEOUT);
            
            return deleteResult != null && deleteResult > 0;
            
        } catch (Exception e) {
            log.warn("JWT persistence health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查黑名单缓存功能
     */
    private Map<String, Object> checkBlacklistCache() {
        try {
            Map<String, Object> stats = cacheManager.getBlacklistCacheStats()
                .block(HEALTH_CHECK_TIMEOUT);
            
            if (stats == null) {
                return Map.of("error", "Failed to get cache stats");
            }
            
            // 添加健康状态指标
            stats.put("healthy", true);
            stats.put("lastCheck", System.currentTimeMillis());
            
            return stats;
            
        } catch (Exception e) {
            log.warn("Blacklist cache health check failed: {}", e.getMessage());
            return Map.of(
                "error", e.getMessage(),
                "healthy", false,
                "lastCheck", System.currentTimeMillis()
            );
        }
    }
    
    /**
     * 检查Redis性能指标
     */
    private Map<String, Object> checkRedisPerformance() {
        try {
            long startTime = System.currentTimeMillis();
            
            // 测试简单操作的响应时间
            String testKey = HEALTH_CHECK_KEY + ":performance";
            String testValue = "perf_test";
            
            redisTemplate.opsForValue()
                .set(testKey, testValue, Duration.ofSeconds(5))
                .block(HEALTH_CHECK_TIMEOUT);
            
            redisTemplate.opsForValue()
                .get(testKey)
                .block(HEALTH_CHECK_TIMEOUT);
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            // 清理测试键
            redisTemplate.delete(testKey).subscribe();
            
            return Map.of(
                "responseTime", responseTime + "ms",
                "status", responseTime < 100 ? "excellent" : 
                         responseTime < 500 ? "good" : 
                         responseTime < 1000 ? "acceptable" : "slow",
                "threshold", "< 100ms excellent, < 500ms good, < 1000ms acceptable",
                "lastCheck", System.currentTimeMillis()
            );
            
        } catch (Exception e) {
            log.warn("Redis performance check failed: {}", e.getMessage());
            return Map.of(
                "error", e.getMessage(),
                "status", "failed",
                "lastCheck", System.currentTimeMillis()
            );
        }
    }
    
    /**
     * 获取详细的健康报告
     */
    public Map<String, Object> getDetailedHealthReport() {
        try {
            Map<String, Object> report = new java.util.HashMap<>();
            
            // Redis连接信息
            report.put("redis", Map.of(
                "connected", checkRedisConnection(),
                "connectionInfo", healthChecker.getConnectionInfo()
            ));
            
            // JWT持久化状态
            report.put("jwtPersistence", Map.of(
                "healthy", checkJwtPersistence(),
                "features", Map.of(
                    "tokenStorage", "enabled",
                    "blacklistCache", "enabled",
                    "ttlManagement", "enabled",
                    "autoCleanup", "enabled"
                )
            ));
            
            // 黑名单缓存详细信息
            Map<String, Object> cacheStats = checkBlacklistCache();
            report.put("blacklistCache", cacheStats);
            
            // 性能指标
            Map<String, Object> performanceStats = checkRedisPerformance();
            report.put("performance", performanceStats);
            
            // 系统信息
            report.put("system", Map.of(
                "timestamp", System.currentTimeMillis(),
                "timezone", java.time.ZoneId.systemDefault().toString(),
                "javaVersion", System.getProperty("java.version"),
                "springBootVersion", org.springframework.boot.SpringBootVersion.getVersion()
            ));
            
            return report;
            
        } catch (Exception e) {
            log.error("Failed to generate detailed health report: {}", e.getMessage(), e);
            return Map.of(
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
        }
    }
    
    /**
     * 获取Redis连接池状态
     */
    public Map<String, Object> getConnectionPoolStatus() {
        try {
            // 这里可以添加连接池状态检查
            // 由于Lettuce的连接池信息获取比较复杂，这里提供基本信息
            return Map.of(
                "type", "Lettuce",
                "status", "active",
                "lastCheck", System.currentTimeMillis(),
                "note", "Detailed pool metrics require additional configuration"
            );
            
        } catch (Exception e) {
            log.warn("Failed to get connection pool status: {}", e.getMessage());
            return Map.of(
                "error", e.getMessage(),
                "lastCheck", System.currentTimeMillis()
            );
        }
    }
}