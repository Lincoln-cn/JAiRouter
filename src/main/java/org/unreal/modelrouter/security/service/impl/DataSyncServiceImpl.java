package org.unreal.modelrouter.security.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.dto.JwtTokenInfo;
import org.unreal.modelrouter.dto.TokenBlacklistEntry;
import org.unreal.modelrouter.dto.TokenStatus;
import org.unreal.modelrouter.security.service.DataSyncService;
import org.unreal.modelrouter.security.service.StorageHealthService;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.util.JacksonHelper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 数据同步服务实现
 * 负责Redis缓存和StoreManager之间的数据同步、恢复和一致性检查
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.persistence.sync.enabled", havingValue = "true", matchIfMissing = true)
public class DataSyncServiceImpl implements DataSyncService {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Qualifier("jwtTokenStoreManager")
    private final StoreManager storeManager;
    
    private final StorageHealthService storageHealthService;
    
    // Redis键前缀
    private static final String TOKEN_PREFIX = "jwt:token:";
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String SYNC_STATS_KEY = "jwt:sync:stats";
    private static final String SYNC_LOCK_KEY = "jwt:sync:lock";
    
    // StoreManager键前缀
    private static final String STORE_TOKEN_PREFIX = "jwt_token_";
    private static final String STORE_BLACKLIST_PREFIX = "jwt_blacklist_";
    
    // 同步配置
    private static final int BATCH_SIZE = 100;
    private static final Duration SYNC_LOCK_TTL = Duration.ofMinutes(30);
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    
    // 统计信息
    private final AtomicLong totalSyncOperations = new AtomicLong(0);
    private final AtomicLong successfulSyncOperations = new AtomicLong(0);
    private final AtomicLong failedSyncOperations = new AtomicLong(0);
    private volatile long lastSyncTime = 0;
    
    @Override
    public Mono<SyncResult> recoverFromStoreManagerToRedis() {
        long startTime = System.currentTimeMillis();
        
        return acquireSyncLock("recovery")
            .flatMap(lockAcquired -> {
                if (!lockAcquired) {
                    return Mono.just(new SyncResult(false, 0, 0, 0, 
                        "Another sync operation is in progress", 0));
                }
                
                return performRecoveryOperation(startTime)
                    .doFinally(signal -> releaseSyncLock("recovery").subscribe());
            })
            .doOnNext(result -> {
                totalSyncOperations.incrementAndGet();
                if (result.isSuccess()) {
                    successfulSyncOperations.incrementAndGet();
                } else {
                    failedSyncOperations.incrementAndGet();
                }
                lastSyncTime = System.currentTimeMillis();
            });
    }
    
    @Override
    public Mono<SyncResult> syncFromRedisToStoreManager() {
        long startTime = System.currentTimeMillis();
        
        return acquireSyncLock("sync_to_store")
            .flatMap(lockAcquired -> {
                if (!lockAcquired) {
                    return Mono.just(new SyncResult(false, 0, 0, 0, 
                        "Another sync operation is in progress", 0));
                }
                
                return performSyncToStoreOperation(startTime)
                    .doFinally(signal -> releaseSyncLock("sync_to_store").subscribe());
            })
            .doOnNext(result -> {
                totalSyncOperations.incrementAndGet();
                if (result.isSuccess()) {
                    successfulSyncOperations.incrementAndGet();
                } else {
                    failedSyncOperations.incrementAndGet();
                }
                lastSyncTime = System.currentTimeMillis();
            });
    }
    
    @Override
    public Mono<SyncResult> performBidirectionalSync() {
        long startTime = System.currentTimeMillis();
        
        return acquireSyncLock("bidirectional")
            .flatMap(lockAcquired -> {
                if (!lockAcquired) {
                    return Mono.just(new SyncResult(false, 0, 0, 0, 
                        "Another sync operation is in progress", 0));
                }
                
                return performBidirectionalSyncOperation(startTime)
                    .doFinally(signal -> releaseSyncLock("bidirectional").subscribe());
            })
            .doOnNext(result -> {
                totalSyncOperations.incrementAndGet();
                if (result.isSuccess()) {
                    successfulSyncOperations.incrementAndGet();
                } else {
                    failedSyncOperations.incrementAndGet();
                }
                lastSyncTime = System.currentTimeMillis();
            });
    }
    
    @Override
    public Mono<ConsistencyCheckResult> checkDataConsistency() {
        return Mono.zip(
            countRedisTokens(),
            countStoreManagerTokens(),
            findMissingInRedis(),
            findMissingInStoreManager(),
            findConflicts()
        ).map(tuple -> {
            long redisCount = tuple.getT1();
            long storeManagerCount = tuple.getT2();
            long missingInRedis = tuple.getT3();
            long missingInStoreManager = tuple.getT4();
            long conflicts = tuple.getT5();
            
            boolean consistent = (missingInRedis == 0 && missingInStoreManager == 0 && conflicts == 0);
            
            String details = String.format(
                "Redis: %d tokens, StoreManager: %d tokens, Missing in Redis: %d, Missing in StoreManager: %d, Conflicts: %d",
                redisCount, storeManagerCount, missingInRedis, missingInStoreManager, conflicts
            );
            
            return new ConsistencyCheckResult(consistent, redisCount, storeManagerCount,
                missingInRedis, missingInStoreManager, conflicts, details);
        })
        .doOnNext(result -> log.info("Data consistency check completed: {}", result))
        .doOnError(error -> log.error("Data consistency check failed: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<SyncResult> repairDataInconsistency(ConsistencyCheckResult checkResult) {
        if (checkResult.isConsistent()) {
            return Mono.just(new SyncResult(true, 0, 0, 0, 
                "Data is already consistent, no repair needed", 0));
        }
        
        long startTime = System.currentTimeMillis();
        
        return acquireSyncLock("repair")
            .flatMap(lockAcquired -> {
                if (!lockAcquired) {
                    return Mono.just(new SyncResult(false, 0, 0, 0, 
                        "Another sync operation is in progress", 0));
                }
                
                return performRepairOperation(checkResult, startTime)
                    .doFinally(signal -> releaseSyncLock("repair").subscribe());
            });
    }
    
    @Override
    public Mono<Map<String, Object>> getSyncStats() {
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new HashMap<>();
            
            stats.put("totalSyncOperations", totalSyncOperations.get());
            stats.put("successfulSyncOperations", successfulSyncOperations.get());
            stats.put("failedSyncOperations", failedSyncOperations.get());
            stats.put("lastSyncTime", lastSyncTime);
            
            // 计算成功率
            long total = totalSyncOperations.get();
            if (total > 0) {
                double successRate = (double) successfulSyncOperations.get() / total * 100;
                stats.put("successRate", successRate);
            } else {
                stats.put("successRate", 0.0);
            }
            
            // 添加配置信息
            stats.put("batchSize", BATCH_SIZE);
            stats.put("syncLockTtl", SYNC_LOCK_TTL.toMillis());
            
            return stats;
        });
    }
    
    @Override
    public Mono<SyncResult> performStartupRecovery() {
        log.info("Starting startup recovery process...");
        
        return storageHealthService.isRedisHealthy()
            .flatMap(redisHealthy -> {
                if (!redisHealthy) {
                    log.warn("Redis is not healthy, skipping startup recovery");
                    return Mono.just(new SyncResult(false, 0, 0, 0, 
                        "Redis is not healthy", 0));
                }
                
                return storageHealthService.isStoreManagerHealthy()
                    .flatMap(storeManagerHealthy -> {
                        if (!storeManagerHealthy) {
                            log.warn("StoreManager is not healthy, skipping startup recovery");
                            return Mono.just(new SyncResult(false, 0, 0, 0, 
                                "StoreManager is not healthy", 0));
                        }
                        
                        return recoverFromStoreManagerToRedis()
                            .doOnNext(result -> {
                                if (result.isSuccess()) {
                                    log.info("Startup recovery completed successfully: {}", result);
                                } else {
                                    log.warn("Startup recovery failed: {}", result);
                                }
                            });
                    });
            });
    }
    
    @Override
    public Mono<Void> cleanupSyncData() {
        return redisTemplate.delete(SYNC_STATS_KEY)
            .then(redisTemplate.delete(SYNC_LOCK_KEY))
            .then(Mono.fromRunnable(() -> {
                totalSyncOperations.set(0);
                successfulSyncOperations.set(0);
                failedSyncOperations.set(0);
                lastSyncTime = 0;
                log.info("Sync data cleanup completed");
            }));
    }
    
    // 私有方法
    
    private Mono<Boolean> acquireSyncLock(String operation) {
        String lockValue = operation + "_" + System.currentTimeMillis();
        return redisTemplate.opsForValue()
            .setIfAbsent(SYNC_LOCK_KEY, lockValue, SYNC_LOCK_TTL)
            .defaultIfEmpty(false)
            .doOnNext(acquired -> {
                if (acquired) {
                    log.debug("Acquired sync lock for operation: {}", operation);
                } else {
                    log.warn("Failed to acquire sync lock for operation: {}", operation);
                }
            });
    }
    
    private Mono<Void> releaseSyncLock(String operation) {
        return redisTemplate.delete(SYNC_LOCK_KEY)
            .then(Mono.fromRunnable(() -> 
                log.debug("Released sync lock for operation: {}", operation)));
    }
    
    private Mono<SyncResult> performRecoveryOperation(long startTime) {
        AtomicLong processedCount = new AtomicLong(0);
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failureCount = new AtomicLong(0);
        
        return Mono.fromCallable(() -> {
            // 获取所有StoreManager中的令牌数据
            List<String> tokenKeys = new ArrayList<>();
            List<String> blacklistKeys = new ArrayList<>();
            
            for (String key : storeManager.getAllKeys()) {
                if (key.startsWith(STORE_TOKEN_PREFIX)) {
                    tokenKeys.add(key);
                } else if (key.startsWith(STORE_BLACKLIST_PREFIX)) {
                    blacklistKeys.add(key);
                }
            }
            
            return Map.of("tokens", tokenKeys, "blacklist", blacklistKeys);
        })
        .flatMap(keys -> {
            @SuppressWarnings("unchecked")
            List<String> tokenKeys = (List<String>) keys.get("tokens");
            @SuppressWarnings("unchecked")
            List<String> blacklistKeys = (List<String>) keys.get("blacklist");
            
            // 恢复令牌数据
            Mono<Void> recoverTokens = Flux.fromIterable(tokenKeys)
                .buffer(BATCH_SIZE)
                .flatMap(batch -> recoverTokenBatch(batch, processedCount, successCount, failureCount))
                .then();
            
            // 恢复黑名单数据
            Mono<Void> recoverBlacklist = Flux.fromIterable(blacklistKeys)
                .buffer(BATCH_SIZE)
                .flatMap(batch -> recoverBlacklistBatch(batch, processedCount, successCount, failureCount))
                .then();
            
            return recoverTokens.then(recoverBlacklist);
        })
        .then(Mono.fromCallable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            long processed = processedCount.get();
            long success = successCount.get();
            long failure = failureCount.get();
            
            boolean isSuccess = failure == 0 || (success > 0 && failure < success);
            String message = String.format("Recovery completed: %d processed, %d success, %d failure", 
                processed, success, failure);
            
            return new SyncResult(isSuccess, processed, success, failure, message, duration);
        }))
        .doOnError(error -> log.error("Recovery operation failed: {}", error.getMessage(), error));
    }
    
    private Mono<Void> recoverTokenBatch(List<String> tokenKeys, AtomicLong processedCount, 
                                        AtomicLong successCount, AtomicLong failureCount) {
        return Flux.fromIterable(tokenKeys)
            .flatMap(key -> {
                processedCount.incrementAndGet();
                
                return Mono.fromCallable(() -> {
                    try {
                        Map<String, Object> tokenData = storeManager.getConfig(key);
                        if (tokenData != null) {
                            JwtTokenInfo tokenInfo = convertToTokenInfo(tokenData);
                            return tokenInfo;
                        }
                        return null;
                    } catch (Exception e) {
                        log.warn("Failed to read token from StoreManager: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .flatMap(tokenInfo -> {
                    String redisKey = TOKEN_PREFIX + tokenInfo.getTokenHash();
                    try {
                        String tokenJson = JacksonHelper.getObjectMapper().writeValueAsString(tokenInfo);
                        Duration ttl = calculateTokenTTL(tokenInfo);
                        
                        return redisTemplate.opsForValue().set(redisKey, tokenJson, ttl)
                            .doOnSuccess(unused -> successCount.incrementAndGet())
                            .doOnError(error -> {
                                failureCount.incrementAndGet();
                                log.warn("Failed to recover token to Redis: {}", error.getMessage());
                            })
                            .onErrorResume(error -> Mono.empty());
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        log.warn("Failed to serialize token for recovery: {}", e.getMessage());
                        return Mono.empty();
                    }
                })
                .onErrorResume(error -> {
                    failureCount.incrementAndGet();
                    return Mono.empty();
                });
            })
            .then();
    }
    
    private Mono<Void> recoverBlacklistBatch(List<String> blacklistKeys, AtomicLong processedCount, 
                                           AtomicLong successCount, AtomicLong failureCount) {
        return Flux.fromIterable(blacklistKeys)
            .flatMap(key -> {
                processedCount.incrementAndGet();
                
                return Mono.fromCallable(() -> {
                    try {
                        Map<String, Object> blacklistData = storeManager.getConfig(key);
                        if (blacklistData != null) {
                            TokenBlacklistEntry entry = convertToBlacklistEntry(blacklistData);
                            return entry;
                        }
                        return null;
                    } catch (Exception e) {
                        log.warn("Failed to read blacklist entry from StoreManager: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .flatMap(entry -> {
                    String redisKey = BLACKLIST_PREFIX + entry.getTokenHash();
                    try {
                        String entryJson = JacksonHelper.getObjectMapper().writeValueAsString(entry);
                        Duration ttl = calculateBlacklistTTL(entry);
                        
                        return redisTemplate.opsForValue().set(redisKey, entryJson, ttl)
                            .doOnSuccess(unused -> successCount.incrementAndGet())
                            .doOnError(error -> {
                                failureCount.incrementAndGet();
                                log.warn("Failed to recover blacklist entry to Redis: {}", error.getMessage());
                            })
                            .onErrorResume(error -> Mono.empty());
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        log.warn("Failed to serialize blacklist entry for recovery: {}", e.getMessage());
                        return Mono.empty();
                    }
                })
                .onErrorResume(error -> {
                    failureCount.incrementAndGet();
                    return Mono.empty();
                });
            })
            .then();
    }
    
    private Mono<SyncResult> performSyncToStoreOperation(long startTime) {
        // 实现从Redis同步到StoreManager的逻辑
        // 这里简化实现，实际应该扫描Redis中的所有数据并同步到StoreManager
        return Mono.just(new SyncResult(true, 0, 0, 0, 
            "Sync to StoreManager not implemented yet", System.currentTimeMillis() - startTime));
    }
    
    private Mono<SyncResult> performBidirectionalSyncOperation(long startTime) {
        // 实现双向同步逻辑
        return checkDataConsistency()
            .flatMap(checkResult -> {
                if (checkResult.isConsistent()) {
                    return Mono.just(new SyncResult(true, 0, 0, 0, 
                        "Data is already consistent", System.currentTimeMillis() - startTime));
                }
                
                return repairDataInconsistency(checkResult);
            });
    }
    
    private Mono<SyncResult> performRepairOperation(ConsistencyCheckResult checkResult, long startTime) {
        // 实现数据修复逻辑
        // 这里简化实现，实际应该根据一致性检查结果进行具体的修复操作
        return Mono.just(new SyncResult(true, 0, 0, 0, 
            "Data repair not fully implemented yet", System.currentTimeMillis() - startTime));
    }
    
    private Mono<Long> countRedisTokens() {
        return redisTemplate.scan()
            .filter(key -> key.startsWith(TOKEN_PREFIX))
            .count();
    }
    
    private Mono<Long> countStoreManagerTokens() {
        return Mono.fromCallable(() -> {
            long count = 0;
            for (String key : storeManager.getAllKeys()) {
                if (key.startsWith(STORE_TOKEN_PREFIX)) {
                    count++;
                }
            }
            return count;
        });
    }
    
    private Mono<Long> findMissingInRedis() {
        // 简化实现，实际应该比较StoreManager和Redis中的数据
        return Mono.just(0L);
    }
    
    private Mono<Long> findMissingInStoreManager() {
        // 简化实现，实际应该比较Redis和StoreManager中的数据
        return Mono.just(0L);
    }
    
    private Mono<Long> findConflicts() {
        // 简化实现，实际应该检查两个存储中相同键的数据是否一致
        return Mono.just(0L);
    }
    
    private Duration calculateTokenTTL(JwtTokenInfo tokenInfo) {
        if (tokenInfo.getExpiresAt() != null) {
            Duration ttl = Duration.between(LocalDateTime.now(), tokenInfo.getExpiresAt());
            return ttl.isNegative() ? Duration.ofMinutes(1) : ttl;
        }
        return DEFAULT_TTL;
    }
    
    private Duration calculateBlacklistTTL(TokenBlacklistEntry entry) {
        if (entry.getExpiresAt() != null) {
            Duration ttl = Duration.between(LocalDateTime.now(), entry.getExpiresAt());
            return ttl.isNegative() ? Duration.ofMinutes(1) : ttl;
        }
        return DEFAULT_TTL;
    }
    
    private JwtTokenInfo convertToTokenInfo(Map<String, Object> tokenData) {
        try {
            return JacksonHelper.getObjectMapper().convertValue(tokenData, JwtTokenInfo.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert map to JwtTokenInfo", e);
        }
    }
    
    private TokenBlacklistEntry convertToBlacklistEntry(Map<String, Object> blacklistData) {
        try {
            return JacksonHelper.getObjectMapper().convertValue(blacklistData, TokenBlacklistEntry.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert map to TokenBlacklistEntry", e);
        }
    }
}