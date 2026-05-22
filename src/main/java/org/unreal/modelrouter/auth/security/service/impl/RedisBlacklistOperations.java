package org.unreal.modelrouter.auth.security.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.unreal.modelrouter.common.dto.TokenBlacklistEntry;
import org.unreal.modelrouter.common.util.JacksonHelper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Redis黑名单操作封装
 * 处理所有与Redis相关的黑名单存储、查询、删除操作
 */
@Slf4j
@RequiredArgsConstructor
public class RedisBlacklistOperations {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    // Redis键前缀
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String BLACKLIST_INDEX_KEY = "jwt:blacklist_index";
    private static final String BLACKLIST_STATS_KEY = "jwt:blacklist_stats";

    // TTL设置
    private static final Duration DEFAULT_BLACKLIST_TTL = Duration.ofHours(24);
    private static final Duration INDEX_TTL = Duration.ofHours(1);
    private static final Duration STATS_TTL = Duration.ofMinutes(5);

    /**
     * 添加黑名单条目到Redis
     */
    public Mono<Void> addToBlacklist(final TokenBlacklistEntry entry) {
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
                .then(updateBlacklistIndex(entry.getTokenHash(), true))
                .then(updateBlacklistStats(1, 0));
        });
    }

    /**
     * 检查token是否在Redis黑名单中
     */
    public Mono<Boolean> isBlacklisted(final String tokenHash) {
        String blacklistKey = BLACKLIST_PREFIX + tokenHash;

        return redisTemplate.hasKey(blacklistKey)
            .flatMap(exists -> {
                if (!exists) {
                    return Mono.just(false);
                }

                return redisTemplate.opsForValue().get(blacklistKey)
                    .map(entryJson -> {
                        try {
                            TokenBlacklistEntry entry = JacksonHelper.getObjectMapper()
                                .readValue(entryJson, TokenBlacklistEntry.class);

                            if (entry.getExpiresAt() != null
                            && entry.getExpiresAt().isBefore(LocalDateTime.now())) {
                                redisTemplate.delete(blacklistKey).subscribe();
                                updateBlacklistIndex(tokenHash, false).subscribe();
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

    /**
     * 从Redis移除黑名单条目
     */
    public Mono<Void> removeFromBlacklist(final String tokenHash) {
        String blacklistKey = BLACKLIST_PREFIX + tokenHash;

        return redisTemplate.hasKey(blacklistKey)
            .flatMap(exists -> {
                if (exists) {
                    return redisTemplate.delete(blacklistKey)
                        .then(updateBlacklistIndex(tokenHash, false))
                        .then(updateBlacklistStats(-1, 0));
                }
                return Mono.empty();
            });
    }

    /**
     * 获取Redis黑名单大小
     */
    public Mono<Long> getBlacklistSize() {
        return redisTemplate.opsForSet().size(BLACKLIST_INDEX_KEY)
            .defaultIfEmpty(0L);
    }

    /**
     * 清理Redis过期条目
     */
    public Mono<Void> cleanupExpiredEntries() {
        return redisTemplate.opsForSet().members(BLACKLIST_INDEX_KEY)
            .flatMap(tokenHash -> {
                String blacklistKey = BLACKLIST_PREFIX + tokenHash;
                return redisTemplate.opsForValue().get(blacklistKey)
                    .map(entryJson -> {
                        try {
                            TokenBlacklistEntry entry = JacksonHelper.getObjectMapper()
                                .readValue(entryJson, TokenBlacklistEntry.class);

                            if (entry.getExpiresAt() != null
                            && entry.getExpiresAt().isBefore(LocalDateTime.now())) {
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
                            .then(updateBlacklistIndex(expiredTokenHash, false))
                    );
            })
            .then();
    }

    /**
     * 获取Redis黑名单统计信息
     */
    public Mono<Map<String, Object>> getBlacklistStats() {
        return redisTemplate.opsForValue().get(BLACKLIST_STATS_KEY)
            .map(statsJson -> {
                try {
                    return JacksonHelper.getObjectMapper().readValue(statsJson,
                        new TypeReference<Map<String, Object>>() { });
                } catch (Exception e) {
                    log.warn("Failed to deserialize blacklist stats: {}", e.getMessage());
                    return new HashMap<String, Object>();
                }
            })
            .defaultIfEmpty(new HashMap<>())
            .flatMap(stats -> getBlacklistSize()
                .map(currentSize -> {
                    stats.put("currentSize", currentSize);
                    stats.put("lastUpdated", System.currentTimeMillis());
                    return stats;
                }));
    }

    /**
     * 获取单个黑名单条目
     */
    public Mono<TokenBlacklistEntry> getBlacklistEntry(final String tokenHash) {
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

    /**
     * 批量添加黑名单条目
     */
    public Mono<Void> batchAddToBlacklist(final List<String> tokenHashes, final String reason, final String addedBy) {
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

                    return addToBlacklist(entry);
                }
                return Mono.empty();
            })
            .then(updateBlacklistStats(tokenHashes.size(), 0));
    }

    /**
     * 获取即将过期的条目数量
     */
    public Mono<Long> getExpiringEntriesCount(final int hoursUntilExpiry) {
        LocalDateTime threshold = LocalDateTime.now().plusHours(hoursUntilExpiry);

        return redisTemplate.opsForSet().members(BLACKLIST_INDEX_KEY)
            .flatMap(tokenHash -> {
                String blacklistKey = BLACKLIST_PREFIX + tokenHash;
                return redisTemplate.opsForValue().get(blacklistKey)
                    .map(entryJson -> {
                        try {
                            TokenBlacklistEntry entry = JacksonHelper.getObjectMapper()
                                .readValue(entryJson, TokenBlacklistEntry.class);

                            if (entry.getExpiresAt() != null
                            && entry.getExpiresAt().isBefore(threshold)) {
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

    /**
     * 检查Redis服务是否可用
     */
    public Mono<Boolean> isServiceAvailable() {
        return redisTemplate.hasKey(BLACKLIST_INDEX_KEY)
            .map(exists -> true)
            .onErrorReturn(false);
    }

    // 私有辅助方法

    private Mono<Void> updateBlacklistIndex(final String tokenHash, final boolean add) {
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

    private Mono<Void> updateBlacklistStats(final int sizeChange, final int cleanedCount) {
        return redisTemplate.opsForValue().get(BLACKLIST_STATS_KEY)
            .map(statsJson -> {
                try {
                    return JacksonHelper.getObjectMapper().readValue(statsJson,
                        new TypeReference<Map<String, Object>>() { });
                } catch (Exception e) {
                    return new HashMap<String, Object>();
                }
            })
            .defaultIfEmpty(new HashMap<>())
            .flatMap(stats -> {
                long totalAdded = stats.get("totalAdded") instanceof Number
                ? ((Number) stats.get("totalAdded")).longValue() : 0L;
                long totalCleaned = stats.get("totalCleaned") instanceof Number
                ? ((Number) stats.get("totalCleaned")).longValue() : 0L;
                long currentSize = stats.get("currentSize") instanceof Number
                ? ((Number) stats.get("currentSize")).longValue() : 0L;

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

    private Duration calculateBlacklistTTL(final TokenBlacklistEntry entry) {
        if (entry.getExpiresAt() != null) {
            Duration ttl = Duration.between(LocalDateTime.now(), entry.getExpiresAt());
            return ttl.isNegative() ? Duration.ofMinutes(1) : ttl;
        }
        return DEFAULT_BLACKLIST_TTL;
    }
}
