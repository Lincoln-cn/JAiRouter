package org.unreal.modelrouter.security.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.dto.TokenBlacklistEntry;
import org.unreal.modelrouter.security.service.JwtBlacklistService;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.util.JacksonHelper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于Redis缓存和StoreManager的JWT黑名单服务实现
 * 使用Redis作为主要缓存层，StoreManager作为持久化存储和故障回退
 * 结合内存缓存提高查询性能
 */
@Slf4j
@Service("redisJwtBlacklistService")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.blacklist.redis.enabled", havingValue = "true")
public class RedisJwtBlacklistServiceImpl implements JwtBlacklistService {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    private final StoreManager fallbackStoreManager;
    
    // 内存缓存用于提高查询性能
    private final Map<String, TokenBlacklistEntry> memoryCache = new ConcurrentHashMap<>();
    private volatile long lastCacheUpdate = 0;
    private static final long CACHE_REFRESH_INTERVAL = 60000; // 1分钟缓存刷新间隔
    
    // Redis键前缀
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String BLACKLIST_INDEX_KEY = "jwt:blacklist_index";
    private static final String BLACKLIST_STATS_KEY = "jwt:blacklist_stats";
    
    // TTL设置
    private static final Duration DEFAULT_BLACKLIST_TTL = Duration.ofHours(24);
    private static final Duration INDEX_TTL = Duration.ofHours(1);
    private static final Duration STATS_TTL = Duration.ofMinutes(5);
    
    @Override
    public Mono<Void> addToBlacklist(String tokenHash, String reason, String addedBy) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Token hash cannot be null or empty"));
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // 创建黑名单条目
        TokenBlacklistEntry entry = new TokenBlacklistEntry();
        entry.setTokenHash(tokenHash);
        entry.setReason(reason != null ? reason : "Manual revocation");
        entry.setAddedBy(addedBy != null ? addedBy : "system");
        entry.setAddedAt(now);
        entry.setExpiresAt(now.plusHours(24)); // 默认24小时后过期
        
        return addToBlacklistInRedis(entry)
            .onErrorResume(error -> {
                log.warn("Failed to add token to blacklist in Redis, falling back to StoreManager: {}", error.getMessage());
                return addToBlacklistInFallback(entry);
            })
            .doOnSuccess(unused -> {
                // 更新内存缓存
                memoryCache.put(tokenHash, entry);
                log.debug("Successfully added token to blacklist: {}", tokenHash);
            })
            .doOnError(error -> log.error("Failed to add token to blacklist: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Boolean> isBlacklisted(String tokenHash) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            return Mono.just(false);
        }
        
        // 首先检查内存缓存
        TokenBlacklistEntry cachedEntry = memoryCache.get(tokenHash);
        if (cachedEntry != null) {
            // 检查是否过期
            if (cachedEntry.getExpiresAt() != null && 
                cachedEntry.getExpiresAt().isBefore(LocalDateTime.now())) {
                // 过期了，从缓存中移除
                memoryCache.remove(tokenHash);
                return Mono.just(false);
            }
            return Mono.just(true);
        }
        
        // 检查Redis
        return isBlacklistedInRedis(tokenHash)
            .onErrorResume(error -> {
                log.warn("Failed to check blacklist in Redis, falling back to StoreManager: {}", error.getMessage());
                return isBlacklistedInFallback(tokenHash);
            })
            .doOnNext(isBlacklisted -> {
                if (isBlacklisted) {
                    // 如果在黑名单中，尝试获取完整条目并加入内存缓存
                    getBlacklistEntry(tokenHash)
                        .subscribe(entry -> {
                            if (entry != null) {
                                memoryCache.put(tokenHash, entry);
                            }
                        });
                }
            })
            .doOnError(error -> log.error("Failed to check blacklist status for token: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Void> removeFromBlacklist(String tokenHash) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            return Mono.empty();
        }
        
        return removeFromBlacklistInRedis(tokenHash)
            .onErrorResume(error -> {
                log.warn("Failed to remove token from blacklist in Redis, falling back to StoreManager: {}", error.getMessage());
                return removeFromBlacklistInFallback(tokenHash);
            })
            .doOnSuccess(unused -> {
                // 从内存缓存中移除
                memoryCache.remove(tokenHash);
                log.debug("Successfully removed token from blacklist: {}", tokenHash);
            })
            .doOnError(error -> log.error("Failed to remove token from blacklist: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Long> getBlacklistSize() {
        // 刷新缓存（如果需要）
        refreshCacheIfNeeded();
        
        return getBlacklistSizeFromRedis()
            .onErrorResume(error -> {
                log.warn("Failed to get blacklist size from Redis, falling back to StoreManager: {}", error.getMessage());
                return getBlacklistSizeFromFallback();
            })
            .doOnNext(size -> log.debug("Blacklist size: {}", size))
            .doOnError(error -> log.error("Failed to get blacklist size: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Void> cleanupExpiredEntries() {
        return cleanupExpiredEntriesInRedis()
            .onErrorResume(error -> {
                log.warn("Failed to cleanup expired entries in Redis, falling back to StoreManager: {}", error.getMessage());
                return cleanupExpiredEntriesInFallback();
            })
            .doOnSuccess(unused -> {
                // 清理内存缓存中的过期条目
                LocalDateTime now = LocalDateTime.now();
                memoryCache.entrySet().removeIf(entry -> {
                    TokenBlacklistEntry blacklistEntry = entry.getValue();
                    return blacklistEntry.getExpiresAt() != null && 
                           blacklistEntry.getExpiresAt().isBefore(now);
                });
                log.info("Successfully cleaned up expired blacklist entries");
            })
            .doOnError(error -> log.error("Failed to cleanup expired blacklist entries: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Map<String, Object>> getBlacklistStats() {
        return getBlacklistStatsFromRedis()
            .onErrorResume(error -> {
                log.warn("Failed to get blacklist stats from Redis, falling back to StoreManager: {}", error.getMessage());
                return getBlacklistStatsFromFallback();
            })
            .map(stats -> {
                // 添加内存缓存统计信息
                stats.put("memoryCache", memoryCache.size());
                stats.put("lastCacheUpdate", lastCacheUpdate);
                stats.put("cacheRefreshInterval", CACHE_REFRESH_INTERVAL);
                stats.put("currentTime", System.currentTimeMillis());
                return stats;
            })
            .doOnError(error -> log.error("Failed to get blacklist stats: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<TokenBlacklistEntry> getBlacklistEntry(String tokenHash) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            return Mono.empty();
        }
        
        // 首先检查内存缓存
        TokenBlacklistEntry cachedEntry = memoryCache.get(tokenHash);
        if (cachedEntry != null) {
            return Mono.just(cachedEntry);
        }
        
        return getBlacklistEntryFromRedis(tokenHash)
            .onErrorResume(error -> {
                log.warn("Failed to get blacklist entry from Redis, falling back to StoreManager: {}", error.getMessage());
                return getBlacklistEntryFromFallback(tokenHash);
            })
            .doOnNext(entry -> {
                if (entry != null) {
                    // 加入内存缓存
                    memoryCache.put(tokenHash, entry);
                }
            })
            .doOnError(error -> log.error("Failed to get blacklist entry for token: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Void> batchAddToBlacklist(List<String> tokenHashes, String reason, String addedBy) {
        if (tokenHashes == null || tokenHashes.isEmpty()) {
            return Mono.empty();
        }
        
        return batchAddToBlacklistInRedis(tokenHashes, reason, addedBy)
            .onErrorResume(error -> {
                log.warn("Failed to batch add to blacklist in Redis, falling back to StoreManager: {}", error.getMessage());
                return batchAddToBlacklistInFallback(tokenHashes, reason, addedBy);
            })
            .doOnSuccess(unused -> log.info("Batch added {} tokens to blacklist", tokenHashes.size()))
            .doOnError(error -> log.error("Failed to batch add tokens to blacklist: {}", error.getMessage(), error));
    }
    
    @Override
    public Mono<Boolean> isServiceAvailable() {
        return redisTemplate.hasKey(BLACKLIST_INDEX_KEY)
            .map(exists -> true)
            .onErrorReturn(false)
            .doOnNext(available -> log.debug("Blacklist service availability: {}", available));
    }
    
    @Override
    public Mono<Long> getExpiringEntriesCount(int hoursUntilExpiry) {
        return getExpiringEntriesCountFromRedis(hoursUntilExpiry)
            .onErrorResume(error -> {
                log.warn("Failed to get expiring entries count from Redis, falling back to StoreManager: {}", error.getMessage());
                return getExpiringEntriesCountFromFallback(hoursUntilExpiry);
            })
            .doOnNext(count -> log.debug("Expiring entries count ({}h): {}", hoursUntilExpiry, count))
            .doOnError(error -> log.error("Failed to get expiring entries count: {}", error.getMessage(), error));
    }
    
    // Redis操作方法
    
    private Mono<Void> addToBlacklistInRedis(TokenBlacklistEntry entry) {
        return Mono.fromCallable(() -> {
            try {
                return JacksonHelper.getObjectMapper().writeValueAsString(entry);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize blacklist entry", e);
            }
        })
        .flatMap(entryJson -> {
            String blacklistKey = BLACKLIST_PREFIX + entry.getTokenHash();
            Duration ttl = calculateBlacklistTTL(entry);
            
            return redisTemplate.opsForValue().set(blacklistKey, entryJson, ttl)
                .then(updateBlacklistIndexInRedis(entry.getTokenHash(), true))
                .then(updateBlacklistStatsInRedis(1, 0))
                .then(addToBlacklistInFallback(entry)); // 同时保存到fallback存储
        });
    }
    
    private Mono<Boolean> isBlacklistedInRedis(String tokenHash) {
        String blacklistKey = BLACKLIST_PREFIX + tokenHash;
        
        return redisTemplate.hasKey(blacklistKey)
            .flatMap(exists -> {
                if (!exists) {
                    return Mono.just(false);
                }
                
                // 检查条目是否过期
                return redisTemplate.opsForValue().get(blacklistKey)
                    .map(entryJson -> {
                        try {
                            TokenBlacklistEntry entry = JacksonHelper.getObjectMapper()
                                .readValue(entryJson, TokenBlacklistEntry.class);
                            
                            if (entry.getExpiresAt() != null && 
                                entry.getExpiresAt().isBefore(LocalDateTime.now())) {
                                // 过期了，删除条目
                                redisTemplate.delete(blacklistKey).subscribe();
                                updateBlacklistIndexInRedis(tokenHash, false).subscribe();
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
    
    private Mono<Void> removeFromBlacklistInRedis(String tokenHash) {
        String blacklistKey = BLACKLIST_PREFIX + tokenHash;
        
        return redisTemplate.hasKey(blacklistKey)
            .flatMap(exists -> {
                if (exists) {
                    return redisTemplate.delete(blacklistKey)
                        .then(updateBlacklistIndexInRedis(tokenHash, false))
                        .then(updateBlacklistStatsInRedis(-1, 0))
                        .then(removeFromBlacklistInFallback(tokenHash));
                }
                return Mono.empty();
            });
    }
    
    private Mono<Long> getBlacklistSizeFromRedis() {
        return redisTemplate.opsForSet().size(BLACKLIST_INDEX_KEY)
            .defaultIfEmpty(0L);
    }
    
    private Mono<Void> cleanupExpiredEntriesInRedis() {
        return redisTemplate.opsForSet().members(BLACKLIST_INDEX_KEY)
            .flatMap(tokenHash -> {
                String blacklistKey = BLACKLIST_PREFIX + tokenHash;
                return redisTemplate.opsForValue().get(blacklistKey)
                    .map(entryJson -> {
                        try {
                            TokenBlacklistEntry entry = JacksonHelper.getObjectMapper()
                                .readValue(entryJson, TokenBlacklistEntry.class);
                            
                            if (entry.getExpiresAt() != null && 
                                entry.getExpiresAt().isBefore(LocalDateTime.now())) {
                                return tokenHash;
                            }
                        } catch (Exception e) {
                            log.warn("Failed to check blacklist entry expiry: {}", e.getMessage());
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .flatMap(expiredTokenHash -> 
                        redisTemplate.delete(BLACKLIST_PREFIX + expiredTokenHash)
                            .then(updateBlacklistIndexInRedis(expiredTokenHash, false))
                    );
            })
            .then();
    }
    
    private Mono<Map<String, Object>> getBlacklistStatsFromRedis() {
        return redisTemplate.opsForValue().get(BLACKLIST_STATS_KEY)
            .map(statsJson -> {
                try {
                    return JacksonHelper.getObjectMapper().readValue(statsJson, 
                        new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    log.warn("Failed to deserialize blacklist stats: {}", e.getMessage());
                    return new HashMap<String, Object>();
                }
            })
            .defaultIfEmpty(new HashMap<>())
            .flatMap(stats -> {
                // 添加实时统计信息
                return getBlacklistSizeFromRedis()
                    .map(currentSize -> {
                        stats.put("currentSize", currentSize);
                        stats.put("lastUpdated", System.currentTimeMillis());
                        return stats;
                    });
            });
    }
    
    private Mono<TokenBlacklistEntry> getBlacklistEntryFromRedis(String tokenHash) {
        String blacklistKey = BLACKLIST_PREFIX + tokenHash;
        
        return redisTemplate.opsForValue().get(blacklistKey)
            .map(entryJson -> {
                try {
                    return JacksonHelper.getObjectMapper().readValue(entryJson, TokenBlacklistEntry.class);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to deserialize blacklist entry", e);
                }
            });
    }
    
    private Mono<Void> batchAddToBlacklistInRedis(List<String> tokenHashes, String reason, String addedBy) {
        LocalDateTime now = LocalDateTime.now();
        
        return Flux.fromIterable(tokenHashes)
            .flatMap(tokenHash -> {
                if (tokenHash != null && !tokenHash.trim().isEmpty()) {
                    TokenBlacklistEntry entry = new TokenBlacklistEntry();
                    entry.setTokenHash(tokenHash);
                    entry.setReason(reason != null ? reason : "Batch revocation");
                    entry.setAddedBy(addedBy != null ? addedBy : "system");
                    entry.setAddedAt(now);
                    entry.setExpiresAt(now.plusHours(24));
                    
                    return addToBlacklistInRedis(entry)
                        .doOnSuccess(unused -> memoryCache.put(tokenHash, entry));
                }
                return Mono.empty();
            })
            .then(updateBlacklistStatsInRedis(tokenHashes.size(), 0));
    }
    
    private Mono<Long> getExpiringEntriesCountFromRedis(int hoursUntilExpiry) {
        LocalDateTime threshold = LocalDateTime.now().plusHours(hoursUntilExpiry);
        
        return redisTemplate.opsForSet().members(BLACKLIST_INDEX_KEY)
            .flatMap(tokenHash -> {
                String blacklistKey = BLACKLIST_PREFIX + tokenHash;
                return redisTemplate.opsForValue().get(blacklistKey)
                    .map(entryJson -> {
                        try {
                            TokenBlacklistEntry entry = JacksonHelper.getObjectMapper()
                                .readValue(entryJson, TokenBlacklistEntry.class);
                            
                            if (entry.getExpiresAt() != null && 
                                entry.getExpiresAt().isBefore(threshold)) {
                                return 1L;
                            }
                        } catch (Exception e) {
                            log.warn("Failed to check blacklist entry expiry: {}", e.getMessage());
                        }
                        return 0L;
                    })
                    .defaultIfEmpty(0L);
            })
            .reduce(0L, Long::sum);
    }
    
    // Redis索引和统计操作方法
    
    private Mono<Void> updateBlacklistIndexInRedis(String tokenHash, boolean add) {
        if (tokenHash == null) {
            return Mono.empty();
        }
        
        if (add) {
            return redisTemplate.opsForSet().add(BLACKLIST_INDEX_KEY, tokenHash)
                .then(redisTemplate.expire(BLACKLIST_INDEX_KEY, INDEX_TTL))
                .then();
        } else {
            return redisTemplate.opsForSet().remove(BLACKLIST_INDEX_KEY, tokenHash)
                .then();
        }
    }
    
    private Mono<Void> updateBlacklistStatsInRedis(int sizeChange, int cleanedCount) {
        return redisTemplate.opsForValue().get(BLACKLIST_STATS_KEY)
            .map(statsJson -> {
                try {
                    return JacksonHelper.getObjectMapper().readValue(statsJson, 
                        new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    return new HashMap<String, Object>();
                }
            })
            .defaultIfEmpty(new HashMap<>())
            .flatMap(stats -> {
                // 更新统计数据
                long totalAdded = stats.get("totalAdded") instanceof Number ? 
                    ((Number) stats.get("totalAdded")).longValue() : 0L;
                long totalCleaned = stats.get("totalCleaned") instanceof Number ? 
                    ((Number) stats.get("totalCleaned")).longValue() : 0L;
                long currentSize = stats.get("currentSize") instanceof Number ? 
                    ((Number) stats.get("currentSize")).longValue() : 0L;
                
                if (sizeChange > 0) {
                    totalAdded += sizeChange;
                }
                if (cleanedCount > 0) {
                    totalCleaned += cleanedCount;
                }
                currentSize += sizeChange;
                
                stats.put("totalAdded", totalAdded);
                stats.put("totalCleaned", totalCleaned);
                stats.put("currentSize", Math.max(0, currentSize));
                stats.put("lastUpdated", System.currentTimeMillis());
                
                try {
                    String updatedStatsJson = JacksonHelper.getObjectMapper().writeValueAsString(stats);
                    return redisTemplate.opsForValue().set(BLACKLIST_STATS_KEY, updatedStatsJson, STATS_TTL)
                        .then();
                } catch (Exception e) {
                    log.warn("Failed to serialize blacklist stats: {}", e.getMessage());
                    return Mono.empty();
                }
            });
    }
    
    // Fallback操作方法（委托给现有的StoreManager实现）
    
    private Mono<Void> addToBlacklistInFallback(TokenBlacklistEntry entry) {
        return Mono.fromRunnable(() -> {
            try {
                Map<String, Object> entryData = convertToMap(entry);
                String blacklistKey = "jwt_blacklist_" + entry.getTokenHash();
                fallbackStoreManager.saveConfig(blacklistKey, entryData);
            } catch (Exception e) {
                log.warn("Failed to add token to blacklist in fallback storage: {}", e.getMessage());
            }
        });
    }
    
    private Mono<Boolean> isBlacklistedInFallback(String tokenHash) {
        return Mono.fromCallable(() -> {
            try {
                String blacklistKey = "jwt_blacklist_" + tokenHash;
                Map<String, Object> entryData = fallbackStoreManager.getConfig(blacklistKey);
                
                if (entryData == null) {
                    return false;
                }
                
                TokenBlacklistEntry entry = convertFromMap(entryData);
                
                // 检查是否过期
                if (entry.getExpiresAt() != null && 
                    entry.getExpiresAt().isBefore(LocalDateTime.now())) {
                    // 过期了，删除条目
                    fallbackStoreManager.deleteConfig(blacklistKey);
                    return false;
                }
                
                return true;
            } catch (Exception e) {
                log.warn("Failed to check blacklist in fallback storage: {}", e.getMessage());
                return false;
            }
        });
    }
    
    private Mono<Void> removeFromBlacklistInFallback(String tokenHash) {
        return Mono.fromRunnable(() -> {
            try {
                String blacklistKey = "jwt_blacklist_" + tokenHash;
                if (fallbackStoreManager.exists(blacklistKey)) {
                    fallbackStoreManager.deleteConfig(blacklistKey);
                }
            } catch (Exception e) {
                log.warn("Failed to remove token from blacklist in fallback storage: {}", e.getMessage());
            }
        });
    }
    
    private Mono<Long> getBlacklistSizeFromFallback() {
        return Mono.just(0L);
    }
    
    private Mono<Void> cleanupExpiredEntriesInFallback() {
        return Mono.empty();
    }
    
    private Mono<Map<String, Object>> getBlacklistStatsFromFallback() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("error", "Fallback storage stats not available");
        stats.put("memoryCache", memoryCache.size());
        return Mono.just(stats);
    }
    
    private Mono<TokenBlacklistEntry> getBlacklistEntryFromFallback(String tokenHash) {
        return Mono.fromCallable(() -> {
            try {
                String blacklistKey = "jwt_blacklist_" + tokenHash;
                Map<String, Object> entryData = fallbackStoreManager.getConfig(blacklistKey);
                return entryData != null ? convertFromMap(entryData) : null;
            } catch (Exception e) {
                log.warn("Failed to get blacklist entry from fallback storage: {}", e.getMessage());
                return null;
            }
        });
    }
    
    private Mono<Void> batchAddToBlacklistInFallback(List<String> tokenHashes, String reason, String addedBy) {
        return Mono.empty();
    }
    
    private Mono<Long> getExpiringEntriesCountFromFallback(int hoursUntilExpiry) {
        return Mono.just(0L);
    }
    
    // 辅助方法
    
    private Duration calculateBlacklistTTL(TokenBlacklistEntry entry) {
        if (entry.getExpiresAt() != null) {
            Duration ttl = Duration.between(LocalDateTime.now(), entry.getExpiresAt());
            return ttl.isNegative() ? Duration.ofMinutes(1) : ttl;
        }
        return DEFAULT_BLACKLIST_TTL;
    }
    
    private void refreshCacheIfNeeded() {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastCacheUpdate > CACHE_REFRESH_INTERVAL) {
            try {
                // 清理过期的缓存条目
                LocalDateTime now = LocalDateTime.now();
                memoryCache.entrySet().removeIf(entry -> {
                    TokenBlacklistEntry blacklistEntry = entry.getValue();
                    return blacklistEntry.getExpiresAt() != null && 
                           blacklistEntry.getExpiresAt().isBefore(now);
                });
                
                lastCacheUpdate = currentTime;
                log.debug("Refreshed blacklist memory cache, current size: {}", memoryCache.size());
                
            } catch (Exception e) {
                log.warn("Failed to refresh blacklist cache: {}", e.getMessage());
            }
        }
    }
    
    private Map<String, Object> convertToMap(TokenBlacklistEntry entry) {
        try {
            return JacksonHelper.getObjectMapper().convertValue(entry, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert blacklist entry to map", e);
        }
    }
    
    private TokenBlacklistEntry convertFromMap(Map<String, Object> entryData) {
        try {
            return JacksonHelper.getObjectMapper().convertValue(entryData, TokenBlacklistEntry.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert map to blacklist entry", e);
        }
    }
    
    @Override
    public Mono<Long> cleanupExpiredEntriesWithCount() {
        return cleanupExpiredEntriesInRedis()
            .then(cleanupExpiredEntriesInFallback())
            .then(Mono.fromCallable(() -> {
                // 由于Redis和fallback的清理都是Mono<Void>，我们无法直接获取清理数量
                // 这里返回0作为占位符，实际的清理统计可以通过其他方式获取
                return 0L;
            }))
            .onErrorResume(error -> {
                log.warn("Failed to cleanup expired entries with count: {}", error.getMessage());
                return Mono.just(0L);
            });
    }
}