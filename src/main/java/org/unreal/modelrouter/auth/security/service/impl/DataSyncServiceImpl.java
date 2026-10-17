package org.unreal.modelrouter.auth.security.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.common.dto.JwtTokenInfo;
import org.unreal.modelrouter.common.dto.TokenBlacklistEntry;
import org.unreal.modelrouter.auth.security.service.DataSyncService;
import org.unreal.modelrouter.auth.security.service.StorageHealthService;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.common.util.JacksonHelper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据同步服务实现
 * 负责Redis缓存和StoreManager之间的数据同步、恢复和一致性检查
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "jairouter.security.jwt.persistence.sync.enabled",
        havingValue = "true", matchIfMissing = true)
public final class DataSyncServiceImpl implements DataSyncService {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
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
            .doOnNext(result -> log.info("数据恢复完成: {}", result));
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
    
    
    // 私有方法
    
    private Mono<Boolean> acquireSyncLock(final String operation) {
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
    
    private Mono<Void> releaseSyncLock(final String operation) {
        return redisTemplate.delete(SYNC_LOCK_KEY)
            .then(Mono.fromRunnable(() -> 
                log.debug("Released sync lock for operation: {}", operation)));
    }
    
    private Mono<SyncResult> performRecoveryOperation(final long startTime) {
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
    
    private Mono<Void> recoverTokenBatch(final List<String> tokenKeys, final AtomicLong processedCount, 
                                        final AtomicLong successCount, final AtomicLong failureCount) {
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
    
    private Mono<Void> recoverBlacklistBatch(final List<String> blacklistKeys, final AtomicLong processedCount, 
                                           final AtomicLong successCount, final AtomicLong failureCount) {
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
    
    
    
    
    
    
    
    
    
    private Duration calculateTokenTTL(final JwtTokenInfo tokenInfo) {
        if (tokenInfo.getExpiresAt() != null) {
            Duration ttl = Duration.between(LocalDateTime.now(), tokenInfo.getExpiresAt());
            return ttl.isNegative() ? Duration.ofMinutes(1) : ttl;
        }
        return DEFAULT_TTL;
    }
    
    private Duration calculateBlacklistTTL(final TokenBlacklistEntry entry) {
        if (entry.getExpiresAt() != null) {
            Duration ttl = Duration.between(LocalDateTime.now(), entry.getExpiresAt());
            return ttl.isNegative() ? Duration.ofMinutes(1) : ttl;
        }
        return DEFAULT_TTL;
    }
    
    private JwtTokenInfo convertToTokenInfo(final Map<String, Object> tokenData) {
        try {
            return JacksonHelper.getObjectMapper().convertValue(tokenData, JwtTokenInfo.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert map to JwtTokenInfo", e);
        }
    }
    
    private TokenBlacklistEntry convertToBlacklistEntry(final Map<String, Object> blacklistData) {
        try {
            return JacksonHelper.getObjectMapper().convertValue(blacklistData, TokenBlacklistEntry.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert map to TokenBlacklistEntry", e);
        }
    }
}