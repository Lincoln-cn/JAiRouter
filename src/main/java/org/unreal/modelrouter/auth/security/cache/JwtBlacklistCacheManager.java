package org.unreal.modelrouter.auth.security.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.common.dto.TokenBlacklistEntry;
import org.unreal.modelrouter.auth.security.service.JwtBlacklistService;
import org.unreal.modelrouter.common.util.JacksonHelper;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

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

    /**
     * 批量核对 Redis 的存在性超时（issue #258）.
     *
     * <p>整批 key 合并为一次流水线往返，故用整批超时；不再对每个 key 单独 block 1 秒。</p>
     */
    private static final Duration SYNC_BATCH_TIMEOUT = Duration.ofSeconds(5);
    
    // 用于定时任务互斥的锁，防止cleanup和sync任务并发执行
    private final ReentrantLock cacheOperationLock = new ReentrantLock();
    
    // Redis键前缀
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String EXPIRY_INDEX_PREFIX = "jwt:blacklist_expiry:";
    private static final String STATS_KEY = "jwt:blacklist_cache_stats";
    
    /**
     * 添加令牌到黑名单缓存，带TTL管理
     */
    public Mono<Void> addToBlacklistCache(
            final String tokenHash, final String reason,
            final String addedBy, final Duration ttl) {
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
    public Mono<Boolean> isInBlacklistCache(final String tokenHash) {
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
    public Mono<Void> removeFromBlacklistCache(final String tokenHash) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            return Mono.empty();
        }
        
        return removeEntryFromCache(tokenHash)
            .doOnSuccess(unused -> {
                localCache.remove(tokenHash);
                log.debug("Removed token from blacklist cache: {}", tokenHash);
            })
            .doOnError(error -> log.error(
                    "Failed to remove token from blacklist cache: {}",
                    error.getMessage(), error));
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
        if (!cacheOperationLock.tryLock()) {
            log.debug("Cache operation in progress, skipping cleanup task");
            return;
        }
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
        } finally {
            cacheOperationLock.unlock();
        }
    }

    /**
     * 定时同步本地缓存与Redis
     *
     * <p><b>issue #258</b>：原实现用 {@code removeIf} 对每个条目单独执行
     * {@code hasKey(...).block(1s)}，最坏 N 次串行往返且全程持有缓存锁，条目多时会长时间
     * 占住调度线程、期间其他缓存操作被 {@code tryLock} 跳过。现拆为三步：</p>
     * <ol>
     *   <li>锁内取快照：只做本地过期清理与复制，不涉及网络；</li>
     *   <li><b>锁外</b>批量核对：N 个 key 合并为一次流水线往返；</li>
     *   <li>锁内仅做内存删除。</li>
     * </ol>
     * <p>Redis 异常或返回数量不匹配时保留本地条目，与原先的失败语义一致。</p>
     */
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public void syncLocalCacheWithRedis() {
        final long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheSync <= CACHE_SYNC_INTERVAL) {
            return;
        }

        // 1) 锁内取快照：仅本地过期清理 + 复制（无网络调用）
        final Map<String, TokenBlacklistEntry> snapshot;
        if (!cacheOperationLock.tryLock()) {
            log.debug("Cache operation in progress, skipping sync task");
            return;
        }
        try {
            log.debug("Starting local cache sync with Redis");
            localCache.entrySet().removeIf(entry -> isEntryExpired(entry.getValue()));
            snapshot = new HashMap<>(localCache);
        } finally {
            cacheOperationLock.unlock();
        }

        if (snapshot.isEmpty()) {
            lastCacheSync = currentTime;
            return;
        }

        // 2) 锁外批量核对 Redis：一次流水线往返，不再逐条 block 并持锁
        final List<String> keys = new ArrayList<>(snapshot.keySet());
        final List<Boolean> exists;
        try {
            final Mono<List<Boolean>> existenceCheck = Mono.zip(
                    keys.stream()
                            .map(key -> redisTemplate.hasKey(BLACKLIST_PREFIX + key))
                            .collect(Collectors.toList()),
                    values -> Arrays.stream(values)
                            .map(value -> Boolean.TRUE.equals(value))
                            .collect(Collectors.toList()));
            exists = existenceCheck.defaultIfEmpty(List.of()).block(SYNC_BATCH_TIMEOUT);
        } catch (Exception e) {
            log.warn("Failed to batch check Redis keys (保留本地条目): {}", e.getMessage());
            return;
        }
        if (exists == null || exists.size() != keys.size()) {
            log.warn("Failed to batch check Redis keys: 返回数量 {} 与请求数量 {} 不匹配，保留本地条目",
                    exists == null ? null : exists.size(), keys.size());
            return;
        }

        // 3) 锁内仅做内存删除
        if (!cacheOperationLock.tryLock()) {
            log.debug("Cache operation in progress, skipping sync apply");
            return;
        }
        try {
            for (int i = 0; i < keys.size(); i++) {
                if (!exists.get(i)) {
                    localCache.remove(keys.get(i));
                }
            }
            lastCacheSync = currentTime;
            log.debug("Local cache sync completed, current size: {}", localCache.size());
        } finally {
            cacheOperationLock.unlock();
        }
    }

    /**
     * 获取即将过期的缓存条目数量
     */
    public Mono<Long> getExpiringCacheEntriesCount(final Duration timeUntilExpiry) {
        LocalDateTime threshold = LocalDateTime.now().plus(timeUntilExpiry);
        
        // 统计本地缓存中即将过期的条目
        long localExpiringCount = localCache.values().stream()
            .filter(entry -> entry.getExpiresAt() != null 
            && entry.getExpiresAt().isBefore(threshold))
            .count();
        
        // 统计Redis缓存中即将过期的条目
        return getExpiringRedisEntriesCount(threshold)
            .map(redisCount -> localExpiringCount + redisCount)
            .defaultIfEmpty(localExpiringCount);
    }
    
    // 私有辅助方法
    
    private Mono<Void> addEntryToCache(final TokenBlacklistEntry entry) {
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
    
    private Mono<Boolean> checkRedisCache(final String tokenHash) {
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
    
    private Mono<TokenBlacklistEntry> getBlacklistEntryFromRedis(final String tokenHash) {
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
    
    private Mono<Void> removeEntryFromCache(final String tokenHash) {
        String cacheKey = BLACKLIST_PREFIX + tokenHash;
        
        return redisTemplate.delete(cacheKey)
            .then(removeFromExpiryIndex(tokenHash))
            .then();
    }
    
    private Mono<Void> addToExpiryIndex(final TokenBlacklistEntry entry) {
        if (entry.getExpiresAt() == null) {
            return Mono.empty();
        }
        
        String expiryKey = EXPIRY_INDEX_PREFIX + entry.getExpiresAt().toLocalDate();
        
        return redisTemplate.opsForSet().add(expiryKey, entry.getTokenHash())
            .then(redisTemplate.expire(expiryKey, Duration.ofDays(1)))
            .then();
    }
    
    private Mono<Void> removeFromExpiryIndex(final String tokenHash) {
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
    
    private Mono<Long> getExpiringRedisEntriesCount(final LocalDateTime threshold) {
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
                                    
                                    if (entry.getExpiresAt() != null 
                                    && entry.getExpiresAt().isBefore(threshold)) {
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
    
    private Mono<Void> updateCacheStats(final int addedCount, final int cleanedCount) {
        return redisTemplate.opsForValue().get(STATS_KEY)
            .map(statsJson -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stats = JacksonHelper.getObjectMapper()
                        .readValue(statsJson, Map.class);
                    
                    long totalAdded = stats.get("totalAdded") instanceof Number 
                    ? ((Number) stats.get("totalAdded")).longValue() : 0L;
                    long totalCleaned = stats.get("totalCleaned") instanceof Number 
                    ? ((Number) stats.get("totalCleaned")).longValue() : 0L;
                    
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
    
    private boolean isEntryExpired(final TokenBlacklistEntry entry) {
        if (entry.getExpiresAt() == null) {
            return false;
        }
        return entry.getExpiresAt().isBefore(LocalDateTime.now());
    }
}