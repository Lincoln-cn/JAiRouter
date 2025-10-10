package org.unreal.modelrouter.security.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.dto.TokenBlacklistEntry;
import org.unreal.modelrouter.security.service.JwtBlacklistService;
import org.unreal.modelrouter.util.JacksonHelper;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT黑名单缓存管理器
 * 提供增强的TTL管理和自动清理功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.blacklist.redis.enabled", havingValue = "true")
public class JwtBlacklistCacheManager {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final JwtBlacklistService blacklistService;
    
    // 本地缓存用于快速查询
    private final Map<String, TokenBlacklistEntry> localCache = new ConcurrentHashMap<>();
    private volatile long lastCacheSync = 0;
    private static final long CACHE_SYNC_INTERVAL = 300000; // 5分钟同步间隔
    
    // Redis键前缀
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String EXPIRY_INDEX_PREFIX = "jwt:blacklist_expiry:";
    private static final String STATS_KEY = "jwt:blacklist_cache_stats";
    
    /**
     * 添加令牌到黑名单缓存，带TTL管理
     */
    public Mono<Void> addToBlacklistCache(String tokenHash, String reason, String addedBy, Duration ttl) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Token hash cannot be null or empty"));
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(ttl);
        
        TokenBlacklistEntry entry = new TokenBlacklistEntry();
        entry.setTokenHash(tokenHash);
        entry.setReason(reason != null ? reason : "Cache entry");
        entry.setAddedBy(addedBy != null ? addedBy : "system");
        entry.setAddedAt(now);
        entry.setExpiresAt(expiresAt);
        
        return addEntryToCache(entry)
            .doOnSuccess(unused -> {
                // 更新本地缓存
                localCache.put(tokenHash, entry);
                log.debug("Added token to blacklist cache with TTL: {} - {}", ttl, tokenHash);
            })
            .doOnError(error -> log.error("Failed to add token to blacklist cache: {}", error.getMessage(), error));
    }
    
    /**
     * 检查令牌是否在黑名单缓存中
     */
    public Mono<Boolean> isInBlacklistCache(String tokenHash) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            return Mono.just(false);
        }
        
        // 首先检查本地缓存
        TokenBlacklistEntry localEntry = localCache.get(tokenHash);
        if (localEntry != null) {
            if (isEntryExpired(localEntry)) {
                localCache.remove(tokenHash);
                return Mono.just(false);
            }
            return Mono.just(true);
        }
        
        // 检查Redis缓存
        return checkRedisCache(tokenHash)
            .doOnNext(isBlacklisted -> {
                if (isBlacklisted) {
                    // 异步获取完整条目并加入本地缓存
                    getBlacklistEntryFromRedis(tokenHash)
                        .subscribe(entry -> {
                            if (entry != null && !isEntryExpired(entry)) {
                                localCache.put(tokenHash, entry);
                            }
                        });
                }
            });
    }
    
    /**
     * 从黑名单缓存中移除令牌
     */
    public Mono<Void> removeFromBlacklistCache(String tokenHash) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            return Mono.empty();
        }
        
        return removeEntryFromCache(tokenHash)
            .doOnSuccess(unused -> {
                localCache.remove(tokenHash);
                log.debug("Removed token from blacklist cache: {}", tokenHash);
            })
            .doOnError(error -> log.error("Failed to remove token from blacklist cache: {}", error.getMessage(), error));
    }
    
    /**
     * 获取黑名单缓存统计信息
     */
    public Mono<Map<String, Object>> getBlacklistCacheStats() {
        return redisTemplate.opsForValue().get(STATS_KEY)
            .map(statsJson -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stats = JacksonHelper.getObjectMapper()
                        .readValue(statsJson, Map.class);
                    
                    // 添加实时统计信息
                    stats.put("localCacheSize", localCache.size());
                    stats.put("lastCacheSync", lastCacheSync);
                    stats.put("cacheSyncInterval", CACHE_SYNC_INTERVAL);
                    stats.put("currentTime", System.currentTimeMillis());
                    
                    return stats;
                } catch (Exception e) {
                    log.warn("Failed to parse blacklist cache stats: {}", e.getMessage());
                    Map<String, Object> defaultStats = new ConcurrentHashMap<>();
                    defaultStats.put("localCacheSize", localCache.size());
                    defaultStats.put("error", "Failed to parse stats");
                    return defaultStats;
                }
            })
            .defaultIfEmpty(Map.of("localCacheSize", localCache.size()));
    }
    
    /**
     * 定时清理过期的缓存条目
     */
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public void cleanupExpiredCacheEntries() {
        try {
            log.debug("Starting blacklist cache cleanup");
            
            // 清理本地缓存中的过期条目
            int localCleanedCount = cleanupLocalCache();
            
            // 清理Redis缓存中的过期条目
            cleanupRedisCache()
                .doOnSuccess(redisCleanedCount -> {
                    log.info("Blacklist cache cleanup completed - Local: {}, Redis: {}", 
                        localCleanedCount, redisCleanedCount);
                    
                    // 更新统计信息
                    updateCacheStats(0, localCleanedCount + redisCleanedCount).subscribe();
                })
                .doOnError(error -> log.error("Failed to cleanup Redis blacklist cache: {}", error.getMessage()))
                .subscribe();
            
        } catch (Exception e) {
            log.error("Failed to cleanup blacklist cache: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 定时同步本地缓存与Redis
     */
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public void syncLocalCacheWithRedis() {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastCacheSync > CACHE_SYNC_INTERVAL) {
            try {
                log.debug("Starting local cache sync with Redis");
                
                // 验证本地缓存条目在Redis中是否仍然存在
                localCache.entrySet().removeIf(entry -> {
                    String tokenHash = entry.getKey();
                    TokenBlacklistEntry localEntry = entry.getValue();
                    
                    // 检查本地条目是否过期
                    if (isEntryExpired(localEntry)) {
                        return true;
                    }
                    
                    // 检查Redis中是否仍然存在
                    try {
                        Boolean exists = redisTemplate.hasKey(BLACKLIST_PREFIX + tokenHash)
                            .block(Duration.ofSeconds(1));
                        return !Boolean.TRUE.equals(exists);
                    } catch (Exception e) {
                        log.warn("Failed to check Redis key existence for {}: {}", tokenHash, e.getMessage());
                        return false;
                    }
                });
                
                lastCacheSync = currentTime;
                log.debug("Local cache sync completed, current size: {}", localCache.size());
                
            } catch (Exception e) {
                log.error("Failed to sync local cache with Redis: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * 获取即将过期的缓存条目数量
     */
    public Mono<Long> getExpiringCacheEntriesCount(Duration timeUntilExpiry) {
        LocalDateTime threshold = LocalDateTime.now().plus(timeUntilExpiry);
        
        // 统计本地缓存中即将过期的条目
        long localExpiringCount = localCache.values().stream()
            .filter(entry -> entry.getExpiresAt() != null && 
                           entry.getExpiresAt().isBefore(threshold))
            .count();
        
        // 统计Redis缓存中即将过期的条目
        return getExpiringRedisEntriesCount(threshold)
            .map(redisCount -> localExpiringCount + redisCount)
            .defaultIfEmpty(localExpiringCount);
    }
    
    // 私有辅助方法
    
    private Mono<Void> addEntryToCache(TokenBlacklistEntry entry) {
        try {
            String entryJson = JacksonHelper.getObjectMapper().writeValueAsString(entry);
            String cacheKey = BLACKLIST_PREFIX + entry.getTokenHash();
            Duration ttl = Duration.between(LocalDateTime.now(), entry.getExpiresAt());
            
            return redisTemplate.opsForValue().set(cacheKey, entryJson, ttl)
                .then(addToExpiryIndex(entry))
                .then();
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to serialize blacklist entry", e));
        }
    }
    
    private Mono<Boolean> checkRedisCache(String tokenHash) {
        String cacheKey = BLACKLIST_PREFIX + tokenHash;
        
        return redisTemplate.hasKey(cacheKey)
            .flatMap(exists -> {
                if (!exists) {
                    return Mono.just(false);
                }
                
                // 获取条目并检查是否过期
                return redisTemplate.opsForValue().get(cacheKey)
                    .map(entryJson -> {
                        try {
                            TokenBlacklistEntry entry = JacksonHelper.getObjectMapper()
                                .readValue(entryJson, TokenBlacklistEntry.class);
                            
                            if (isEntryExpired(entry)) {
                                // 异步删除过期条目
                                removeEntryFromCache(tokenHash).subscribe();
                                return false;
                            }
                            
                            return true;
                        } catch (Exception e) {
                            log.warn("Failed to deserialize blacklist entry: {}", e.getMessage());
                            return false;
                        }
                    })
                    .defaultIfEmpty(false);
            });
    }
    
    private Mono<TokenBlacklistEntry> getBlacklistEntryFromRedis(String tokenHash) {
        String cacheKey = BLACKLIST_PREFIX + tokenHash;
        
        return redisTemplate.opsForValue().get(cacheKey)
            .map(entryJson -> {
                try {
                    return JacksonHelper.getObjectMapper().readValue(entryJson, TokenBlacklistEntry.class);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to deserialize blacklist entry", e);
                }
            });
    }
    
    private Mono<Void> removeEntryFromCache(String tokenHash) {
        String cacheKey = BLACKLIST_PREFIX + tokenHash;
        
        return redisTemplate.delete(cacheKey)
            .then(removeFromExpiryIndex(tokenHash))
            .then();
    }
    
    private Mono<Void> addToExpiryIndex(TokenBlacklistEntry entry) {
        if (entry.getExpiresAt() == null) {
            return Mono.empty();
        }
        
        String expiryKey = EXPIRY_INDEX_PREFIX + entry.getExpiresAt().toLocalDate();
        
        return redisTemplate.opsForSet().add(expiryKey, entry.getTokenHash())
            .then(redisTemplate.expire(expiryKey, Duration.ofDays(1)))
            .then();
    }
    
    private Mono<Void> removeFromExpiryIndex(String tokenHash) {
        // 由于不知道确切的过期日期，需要从多个可能的索引中移除
        LocalDateTime now = LocalDateTime.now();
        
        return reactor.core.publisher.Flux.range(0, 7) // 检查未来7天的索引
            .flatMap(days -> {
                String expiryKey = EXPIRY_INDEX_PREFIX + now.plusDays(days).toLocalDate();
                return redisTemplate.opsForSet().remove(expiryKey, tokenHash);
            })
            .then();
    }
    
    private int cleanupLocalCache() {
        int cleanedCount = 0;
        
        for (Map.Entry<String, TokenBlacklistEntry> entry : localCache.entrySet()) {
            if (isEntryExpired(entry.getValue())) {
                localCache.remove(entry.getKey());
                cleanedCount++;
            }
        }
        
        return cleanedCount;
    }
    
    private Mono<Integer> cleanupRedisCache() {
        LocalDateTime now = LocalDateTime.now();
        
        // 清理今天和昨天的过期索引
        return reactor.core.publisher.Flux.just(
                EXPIRY_INDEX_PREFIX + now.toLocalDate(),
                EXPIRY_INDEX_PREFIX + now.minusDays(1).toLocalDate()
            )
            .flatMap(expiryKey -> 
                redisTemplate.opsForSet().members(expiryKey)
                    .flatMap(tokenHash -> {
                        String cacheKey = BLACKLIST_PREFIX + tokenHash;
                        return redisTemplate.opsForValue().get(cacheKey)
                            .map(entryJson -> {
                                try {
                                    TokenBlacklistEntry entry = JacksonHelper.getObjectMapper()
                                        .readValue(entryJson, TokenBlacklistEntry.class);
                                    return isEntryExpired(entry) ? tokenHash : null;
                                } catch (Exception e) {
                                    return tokenHash; // 如果解析失败，也删除
                                }
                            })
                            .filter(hash -> hash != null)
                            .flatMap(expiredHash -> 
                                redisTemplate.delete(BLACKLIST_PREFIX + expiredHash)
                                    .then(redisTemplate.opsForSet().remove(expiryKey, expiredHash))
                                    .thenReturn(1)
                            );
                    })
            )
            .reduce(0, Integer::sum);
    }
    
    private Mono<Long> getExpiringRedisEntriesCount(LocalDateTime threshold) {
        String todayKey = EXPIRY_INDEX_PREFIX + LocalDateTime.now().toLocalDate();
        String tomorrowKey = EXPIRY_INDEX_PREFIX + LocalDateTime.now().plusDays(1).toLocalDate();
        
        return reactor.core.publisher.Flux.just(todayKey, tomorrowKey)
            .flatMap(expiryKey -> 
                redisTemplate.opsForSet().members(expiryKey)
                    .flatMap(tokenHash -> {
                        String cacheKey = BLACKLIST_PREFIX + tokenHash;
                        return redisTemplate.opsForValue().get(cacheKey)
                            .map(entryJson -> {
                                try {
                                    TokenBlacklistEntry entry = JacksonHelper.getObjectMapper()
                                        .readValue(entryJson, TokenBlacklistEntry.class);
                                    
                                    if (entry.getExpiresAt() != null && 
                                        entry.getExpiresAt().isBefore(threshold)) {
                                        return 1L;
                                    }
                                } catch (Exception e) {
                                    log.warn("Failed to check entry expiry: {}", e.getMessage());
                                }
                                return 0L;
                            })
                            .defaultIfEmpty(0L);
                    })
            )
            .reduce(0L, Long::sum);
    }
    
    private Mono<Void> updateCacheStats(int addedCount, int cleanedCount) {
        return redisTemplate.opsForValue().get(STATS_KEY)
            .map(statsJson -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stats = JacksonHelper.getObjectMapper()
                        .readValue(statsJson, Map.class);
                    
                    long totalAdded = stats.get("totalAdded") instanceof Number ? 
                        ((Number) stats.get("totalAdded")).longValue() : 0L;
                    long totalCleaned = stats.get("totalCleaned") instanceof Number ? 
                        ((Number) stats.get("totalCleaned")).longValue() : 0L;
                    
                    stats.put("totalAdded", totalAdded + addedCount);
                    stats.put("totalCleaned", totalCleaned + cleanedCount);
                    stats.put("lastCleanup", System.currentTimeMillis());
                    
                    return stats;
                } catch (Exception e) {
                    Map<String, Object> newStats = new ConcurrentHashMap<>();
                    newStats.put("totalAdded", (long) addedCount);
                    newStats.put("totalCleaned", (long) cleanedCount);
                    newStats.put("lastCleanup", System.currentTimeMillis());
                    return newStats;
                }
            })
            .defaultIfEmpty(Map.of(
                "totalAdded", (long) addedCount,
                "totalCleaned", (long) cleanedCount,
                "lastCleanup", System.currentTimeMillis()
            ))
            .flatMap(stats -> {
                try {
                    String statsJson = JacksonHelper.getObjectMapper().writeValueAsString(stats);
                    return redisTemplate.opsForValue().set(STATS_KEY, statsJson, Duration.ofHours(24));
                } catch (Exception e) {
                    return Mono.error(new RuntimeException("Failed to serialize cache stats", e));
                }
            })
            .then();
    }
    
    private boolean isEntryExpired(TokenBlacklistEntry entry) {
        if (entry.getExpiresAt() == null) {
            return false;
        }
        return entry.getExpiresAt().isBefore(LocalDateTime.now());
    }
}