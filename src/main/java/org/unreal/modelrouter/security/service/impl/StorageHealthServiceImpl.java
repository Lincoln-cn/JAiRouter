package org.unreal.modelrouter.security.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.service.StorageHealthService;
import org.unreal.modelrouter.store.StoreManager;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 存储系统健康检查服务实现
 * 监控Redis和StoreManager的健康状态，提供故障检测和自动降级功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageHealthServiceImpl implements StorageHealthService {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Qualifier("jwtTokenStoreManager")
    private final StoreManager storeManager;
    
    // 健康状态缓存
    private volatile boolean redisHealthy = true;
    private volatile boolean storeManagerHealthy = true;
    private volatile long lastRedisCheck = 0;
    private volatile long lastStoreManagerCheck = 0;
    
    // 健康检查配置
    private static final long HEALTH_CHECK_INTERVAL = 30000; // 30秒
    private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(5);
    private static final String HEALTH_CHECK_KEY = "jwt:health:check";
    
    // 故障统计
    private final Map<String, AtomicInteger> failureCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> successCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastFailureTime = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastSuccessTime = new ConcurrentHashMap<>();
    
    // 故障阈值配置
    private static final int FAILURE_THRESHOLD = 5; // 连续失败5次后标记为不健康
    private static final long RECOVERY_WINDOW = 300000; // 5分钟恢复窗口
    
    @Override
    public Mono<Boolean> isRedisHealthy() {
        long currentTime = System.currentTimeMillis();
        
        // 如果最近检查过且在缓存有效期内，直接返回缓存结果
        if (currentTime - lastRedisCheck < HEALTH_CHECK_INTERVAL) {
            return Mono.just(redisHealthy);
        }
        
        return checkRedisHealth()
            .doOnNext(healthy -> {
                redisHealthy = healthy;
                lastRedisCheck = currentTime;
                
                if (healthy) {
                    recordStorageSuccess("redis", "health_check").subscribe();
                } else {
                    recordStorageFailure("redis", "health_check", 
                        new RuntimeException("Redis health check failed")).subscribe();
                }
            })
            .doOnError(error -> {
                redisHealthy = false;
                lastRedisCheck = currentTime;
                recordStorageFailure("redis", "health_check", error).subscribe();
            })
            .onErrorReturn(false);
    }
    
    @Override
    public Mono<Boolean> isStoreManagerHealthy() {
        long currentTime = System.currentTimeMillis();
        
        // 如果最近检查过且在缓存有效期内，直接返回缓存结果
        if (currentTime - lastStoreManagerCheck < HEALTH_CHECK_INTERVAL) {
            return Mono.just(storeManagerHealthy);
        }
        
        return checkStoreManagerHealth()
            .doOnNext(healthy -> {
                storeManagerHealthy = healthy;
                lastStoreManagerCheck = currentTime;
                
                if (healthy) {
                    recordStorageSuccess("storemanager", "health_check").subscribe();
                } else {
                    recordStorageFailure("storemanager", "health_check", 
                        new RuntimeException("StoreManager health check failed")).subscribe();
                }
            })
            .doOnError(error -> {
                storeManagerHealthy = false;
                lastStoreManagerCheck = currentTime;
                recordStorageFailure("storemanager", "health_check", error).subscribe();
            })
            .onErrorReturn(false);
    }
    
    @Override
    public Mono<Map<String, Boolean>> getAllStorageHealth() {
        return Mono.zip(isRedisHealthy(), isStoreManagerHealthy())
            .map(tuple -> {
                Map<String, Boolean> healthMap = new HashMap<>();
                healthMap.put("redis", tuple.getT1());
                healthMap.put("storemanager", tuple.getT2());
                return healthMap;
            });
    }
    
    @Override
    public Mono<Map<String, Object>> getDetailedHealthInfo() {
        return getAllStorageHealth()
            .map(healthMap -> {
                Map<String, Object> detailedInfo = new HashMap<>();
                
                // 基本健康状态
                detailedInfo.put("health", healthMap);
                
                // 最后检查时间
                Map<String, Object> lastCheckTimes = new HashMap<>();
                lastCheckTimes.put("redis", lastRedisCheck);
                lastCheckTimes.put("storemanager", lastStoreManagerCheck);
                detailedInfo.put("lastCheckTimes", lastCheckTimes);
                
                // 故障统计
                Map<String, Object> failureStats = new HashMap<>();
                failureCounters.forEach((key, counter) -> 
                    failureStats.put(key + "_failures", counter.get()));
                successCounters.forEach((key, counter) -> 
                    failureStats.put(key + "_successes", counter.get()));
                detailedInfo.put("stats", failureStats);
                
                // 最后故障时间
                Map<String, Object> lastFailureTimes = new HashMap<>();
                lastFailureTime.forEach((key, time) -> 
                    lastFailureTimes.put(key + "_last_failure", time.get()));
                detailedInfo.put("lastFailureTimes", lastFailureTimes);
                
                // 最后成功时间
                Map<String, Object> lastSuccessTimes = new HashMap<>();
                lastSuccessTime.forEach((key, time) -> 
                    lastSuccessTimes.put(key + "_last_success", time.get()));
                detailedInfo.put("lastSuccessTimes", lastSuccessTimes);
                
                // 配置信息
                Map<String, Object> config = new HashMap<>();
                config.put("healthCheckInterval", HEALTH_CHECK_INTERVAL);
                config.put("failureThreshold", FAILURE_THRESHOLD);
                config.put("recoveryWindow", RECOVERY_WINDOW);
                detailedInfo.put("config", config);
                
                return detailedInfo;
            });
    }
    
    @Override
    public Mono<Void> refreshHealthStatus() {
        return Mono.fromRunnable(() -> {
            // 重置缓存时间，强制下次检查时重新检测
            lastRedisCheck = 0;
            lastStoreManagerCheck = 0;
            log.info("Storage health status cache refreshed");
        });
    }
    
    @Override
    public Mono<Boolean> shouldFallbackToSecondary() {
        return isRedisHealthy()
            .map(redisHealthy -> {
                if (!redisHealthy) {
                    // Redis不健康，检查是否在恢复窗口内
                    AtomicLong lastFailure = lastFailureTime.get("redis");
                    if (lastFailure != null) {
                        long timeSinceLastFailure = System.currentTimeMillis() - lastFailure.get();
                        return timeSinceLastFailure < RECOVERY_WINDOW;
                    }
                    return true;
                }
                
                // Redis健康，检查故障计数
                AtomicInteger failures = failureCounters.get("redis");
                return failures != null && failures.get() >= FAILURE_THRESHOLD;
            });
    }
    
    @Override
    public Mono<String> getRecommendedPrimaryStorage() {
        return getAllStorageHealth()
            .map(healthMap -> {
                boolean redisHealthy = healthMap.get("redis");
                boolean storeManagerHealthy = healthMap.get("storemanager");
                
                if (redisHealthy) {
                    return "redis";
                } else if (storeManagerHealthy) {
                    return "storemanager";
                } else {
                    // 两个都不健康，返回默认的
                    log.warn("Both storage systems are unhealthy, defaulting to storemanager");
                    return "storemanager";
                }
            });
    }
    
    @Override
    public Mono<Void> recordStorageFailure(String storageType, String operation, Throwable error) {
        return Mono.fromRunnable(() -> {
            String key = storageType + "_" + operation;
            
            failureCounters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
            lastFailureTime.computeIfAbsent(key, k -> new AtomicLong(0)).set(System.currentTimeMillis());
            
            log.warn("Storage failure recorded - Type: {}, Operation: {}, Error: {}", 
                storageType, operation, error.getMessage());
            
            // 检查是否达到故障阈值
            if (failureCounters.get(key).get() >= FAILURE_THRESHOLD) {
                log.error("Storage {} has reached failure threshold for operation {}", 
                    storageType, operation);
            }
        });
    }
    
    @Override
    public Mono<Void> recordStorageSuccess(String storageType, String operation) {
        return Mono.fromRunnable(() -> {
            String key = storageType + "_" + operation;
            
            successCounters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
            lastSuccessTime.computeIfAbsent(key, k -> new AtomicLong(0)).set(System.currentTimeMillis());
            
            // 成功操作时重置故障计数
            AtomicInteger failures = failureCounters.get(key);
            if (failures != null && failures.get() > 0) {
                failures.set(0);
                log.info("Storage {} failure count reset after successful operation {}", 
                    storageType, operation);
            }
            
            log.debug("Storage success recorded - Type: {}, Operation: {}", storageType, operation);
        });
    }
    
    @Override
    public Mono<Map<String, Object>> getStorageStats() {
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new HashMap<>();
            
            // 故障统计
            Map<String, Integer> failures = new HashMap<>();
            failureCounters.forEach((key, counter) -> failures.put(key, counter.get()));
            stats.put("failures", failures);
            
            // 成功统计
            Map<String, Integer> successes = new HashMap<>();
            successCounters.forEach((key, counter) -> successes.put(key, counter.get()));
            stats.put("successes", successes);
            
            // 计算成功率
            Map<String, Double> successRates = new HashMap<>();
            failureCounters.keySet().forEach(key -> {
                int failureCount = failureCounters.get(key).get();
                int successCount = successCounters.getOrDefault(key, new AtomicInteger(0)).get();
                int totalCount = failureCount + successCount;
                
                if (totalCount > 0) {
                    double successRate = (double) successCount / totalCount * 100;
                    successRates.put(key, successRate);
                }
            });
            stats.put("successRates", successRates);
            
            // 当前健康状态
            stats.put("currentHealth", Map.of(
                "redis", redisHealthy,
                "storemanager", storeManagerHealthy
            ));
            
            // 最后检查时间
            stats.put("lastHealthCheck", Map.of(
                "redis", lastRedisCheck,
                "storemanager", lastStoreManagerCheck
            ));
            
            return stats;
        });
    }
    
    // 私有方法
    
    private Mono<Boolean> checkRedisHealth() {
        String testValue = "health_check_" + System.currentTimeMillis();
        
        return redisTemplate.opsForValue()
            .set(HEALTH_CHECK_KEY, testValue, Duration.ofMinutes(1))
            .timeout(REDIS_TIMEOUT)
            .then(redisTemplate.opsForValue().get(HEALTH_CHECK_KEY))
            .timeout(REDIS_TIMEOUT)
            .map(value -> testValue.equals(value))
            .then(redisTemplate.delete(HEALTH_CHECK_KEY))
            .timeout(REDIS_TIMEOUT)
            .map(deleted -> true)
            .onErrorReturn(false)
            .doOnNext(healthy -> {
                if (healthy) {
                    log.debug("Redis health check passed");
                } else {
                    log.warn("Redis health check failed");
                }
            });
    }
    
    private Mono<Boolean> checkStoreManagerHealth() {
        return Mono.fromCallable(() -> {
            try {
                String testKey = "health_check_" + System.currentTimeMillis();
                Map<String, Object> testData = Map.of(
                    "timestamp", LocalDateTime.now().toString(),
                    "test", true
                );
                
                // 测试保存
                storeManager.saveConfig(testKey, testData);
                
                // 测试读取
                Map<String, Object> retrieved = storeManager.getConfig(testKey);
                boolean success = retrieved != null && retrieved.containsKey("test");
                
                // 清理测试数据
                if (storeManager.exists(testKey)) {
                    storeManager.deleteConfig(testKey);
                }
                
                if (success) {
                    log.debug("StoreManager health check passed");
                } else {
                    log.warn("StoreManager health check failed - data mismatch");
                }
                
                return success;
                
            } catch (Exception e) {
                log.warn("StoreManager health check failed with exception: {}", e.getMessage());
                return false;
            }
        });
    }
}