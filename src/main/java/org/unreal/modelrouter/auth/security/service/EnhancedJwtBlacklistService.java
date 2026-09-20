package org.unreal.modelrouter.auth.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 增强的JWT黑名单服务
 * 提供双重保障：Redis + 本地缓存，确保撤销的令牌真正被阻止
 */
@Slf4j
@Service("redisJwtBlacklistService")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.blacklist.redis.enabled", havingValue = "true")
public class EnhancedJwtBlacklistService {

    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";
    private static final String BLACKLIST_BACKUP_KEY_PREFIX = "jwt:blacklist:backup:";

    /** 单次 Redis 查询超时（存储不可达时避免拖死鉴权链路）。 */
    private static final Duration REDIS_CHECK_TIMEOUT = Duration.ofMillis(300);

    /** 存储不可用后的短路窗口默认值：窗口内跳过 Redis 查询，到期后自动重试以感知恢复。 */
    private static final long DEFAULT_DEGRADED_RETRY_INTERVAL_MILLIS = 30_000L;

    private final ReactiveStringRedisTemplate redisTemplate;

    // 本地缓存作为备份，防止Redis连接问题
    private final ConcurrentMap<String, Long> localBlacklistCache = new ConcurrentHashMap<>();

    // 最大本地缓存大小
    private static final int MAX_LOCAL_CACHE_SIZE = 10000;

    /** 降级告警是否已打过（issue #76：只打一次，避免每请求刷屏）。 */
    private final AtomicBoolean storageDegradationLogged = new AtomicBoolean(false);

    /** 短路窗口的截止时间戳；0 表示未处于降级状态。 */
    private volatile long redisRetryAfterMillis = 0L;

    /** 短路窗口时长；测试可调小以避免等待。 */
    private volatile long degradedRetryIntervalMillis = DEFAULT_DEGRADED_RETRY_INTERVAL_MILLIS;

    /**
     * 将令牌加入黑名单
     *
     * @param tokenId 令牌ID或令牌哈希
     * @param ttlSeconds 过期时间（秒）
     * @return 操作结果
     */
    public Mono<Boolean> addToBlacklist(final String tokenId, final long ttlSeconds) {
        if (tokenId == null || tokenId.trim().isEmpty()) {
            log.warn("尝试将空的tokenId加入黑名单");
            return Mono.just(false);
        }

        String trimmedTokenId = tokenId.trim();
        String blacklistKey = BLACKLIST_KEY_PREFIX + trimmedTokenId;
        String backupKey = BLACKLIST_BACKUP_KEY_PREFIX + trimmedTokenId;

        // 1. 立即加入本地缓存
        long expirationTime = System.currentTimeMillis() + (ttlSeconds * 1000);
        localBlacklistCache.put(trimmedTokenId, expirationTime);

        // 清理本地缓存（如果超过最大大小）
        cleanupLocalCache();

        // 2. 加入Redis黑名单
        return redisTemplate.opsForValue()
                .set(blacklistKey, "blacklisted", Duration.ofSeconds(ttlSeconds))
                .flatMap(success -> {
                    if (success) {
                        // 3. 创建备份条目（更长的过期时间）
                        return redisTemplate.opsForValue()
                                .set(backupKey, "blacklisted", Duration.ofSeconds(ttlSeconds + 3600)) // 额外1小时
                                .doOnSuccess(backupSuccess -> {
                                    if (backupSuccess) {
                                        log.debug("令牌已成功加入黑名单: tokenId={}, ttl={}s", trimmedTokenId, ttlSeconds);
                                    } else {
                                        log.warn("令牌黑名单备份创建失败: tokenId={}", trimmedTokenId);
                                    }
                                })
                                .map(backupSuccess -> true); // 主要操作成功即可
                    } else {
                        log.error("令牌加入Redis黑名单失败: tokenId={}", trimmedTokenId);
                        return Mono.just(false);
                    }
                })
                .onErrorResume(ex -> {
                    log.error("令牌加入黑名单时发生异常: tokenId={}, error={}", trimmedTokenId, ex.getMessage());
                    // Redis失败时，至少本地缓存已经生效
                    return Mono.just(true);
                });
    }

    /**
     * 检查令牌是否在黑名单中
     * 使用多重检查策略：本地缓存 -> Redis主键 -> Redis备份键
     *
     * @param tokenId 令牌ID或令牌哈希
     * @return 是否在黑名单中
     */
    public Mono<Boolean> isBlacklisted(final String tokenId) {
        if (tokenId == null || tokenId.trim().isEmpty()) {
            return Mono.just(false);
        }

        String trimmedTokenId = tokenId.trim();

        // 1. 首先检查本地缓存
        Long expirationTime = localBlacklistCache.get(trimmedTokenId);
        if (expirationTime != null) {
            if (System.currentTimeMillis() < expirationTime) {
                log.debug("令牌在本地黑名单缓存中: tokenId={}", trimmedTokenId);
                return Mono.just(true);
            } else {
                // 本地缓存过期，移除
                localBlacklistCache.remove(trimmedTokenId);
            }
        }

        // 2. 存储已被判定为不可用时短路，避免每个请求都付出一次连接超时代价。
        //    窗口内仅依据本地缓存判定（上面已查），到期后自动重试以感知恢复。
        if (System.currentTimeMillis() < redisRetryAfterMillis) {
            log.debug("黑名单存储处于降级短路窗口，跳过 Redis 查询: tokenId={}", trimmedTokenId);
            return Mono.just(false);
        }

        // 3. 检查Redis主键（短超时：Redis 不可达时避免拖死管理台/鉴权链路）
        String blacklistKey = BLACKLIST_KEY_PREFIX + trimmedTokenId;
        return redisTemplate.hasKey(blacklistKey)
                .timeout(REDIS_CHECK_TIMEOUT)
                .flatMap(exists -> {
                    if (exists) {
                        log.debug("令牌在Redis黑名单中: tokenId={}", trimmedTokenId);
                        // 同步到本地缓存
                        localBlacklistCache.put(trimmedTokenId, System.currentTimeMillis() + 3600000); // 1小时
                        return Mono.just(true);
                    } else {
                        // 4. 检查Redis备份键
                        String backupKey = BLACKLIST_BACKUP_KEY_PREFIX + trimmedTokenId;
                        return redisTemplate.hasKey(backupKey)
                                .timeout(REDIS_CHECK_TIMEOUT)
                                .map(backupExists -> {
                                    if (backupExists) {
                                        log.debug("令牌在Redis备份黑名单中: tokenId={}", trimmedTokenId);
                                        localBlacklistCache.put(trimmedTokenId, System.currentTimeMillis() + 3600000);
                                        return true;
                                    }
                                    return false;
                                });
                    }
                })
                // 查询成功即证明存储可用：撤销降级状态（恢复后重新生效）
                .doOnNext(ignored -> clearStorageDegradation())
                .onErrorResume(ex -> handleStorageUnavailable(trimmedTokenId, ex));
    }

    /**
     * 黑名单存储不可用时的降级处理.
     *
     * <p>降级语义：仅依据本地缓存判定；本地缓存无记录时放行（返回 {@code false}），
     * 即<b>存储不可用期间被撤销/登出的令牌不会被拦截</b>——这是本方法的既有取舍
     * （可用性优先），但必须让运维可见：首次失败打一条带安全含义的 WARN，其后同类
     * 失败降为 DEBUG，避免每个请求刷屏掩盖真正的鉴权失败（见 issue #76）。</p>
     */
    private Mono<Boolean> handleStorageUnavailable(final String trimmedTokenId, final Throwable ex) {
        // 标记降级窗口：窗口内后续请求直接短路，不再逐次等待连接超时
        redisRetryAfterMillis = System.currentTimeMillis() + degradedRetryIntervalMillis;

        Long localExpiration = localBlacklistCache.get(trimmedTokenId);
        if (localExpiration != null && System.currentTimeMillis() < localExpiration) {
            log.debug("黑名单存储不可用，使用本地缓存判定: tokenId={}", trimmedTokenId);
            return Mono.just(true);
        }

        if (storageDegradationLogged.compareAndSet(false, true)) {
            log.warn("JWT 黑名单校验已降级：黑名单存储不可达，撤销/登出的令牌在存储恢复前不会被拦截"
                    + "（仅依据本地缓存判定）。error={}, tokenId={}", ex.getMessage(), trimmedTokenId);
        } else {
            log.debug("黑名单存储仍不可用，继续降级放行: tokenId={}, error={}",
                    trimmedTokenId, ex.getMessage());
        }
        return Mono.just(false);
    }

    /**
     * 黑名单存储恢复可用时清除降级状态（幂等）.
     */
    private void clearStorageDegradation() {
        redisRetryAfterMillis = 0L;
        if (storageDegradationLogged.compareAndSet(true, false)) {
            log.info("JWT 黑名单存储已恢复，Redis 黑名单校验重新生效");
        }
    }

    /**
     * 调整存储不可用后的短路窗口时长（包可见，仅供测试避免等待窗口到期）.
     */
    void setDegradedRetryIntervalMillis(final long millis) {
        this.degradedRetryIntervalMillis = millis;
    }

    /**
     * 从黑名单中移除令牌（用于测试或特殊情况）
     *
     * @param tokenId 令牌ID或令牌哈希
     * @return 操作结果
     */
    public Mono<Boolean> removeFromBlacklist(final String tokenId) {
        if (tokenId == null || tokenId.trim().isEmpty()) {
            return Mono.just(false);
        }

        String trimmedTokenId = tokenId.trim();

        // 1. 从本地缓存移除
        localBlacklistCache.remove(trimmedTokenId);

        // 2. 从Redis移除
        String blacklistKey = BLACKLIST_KEY_PREFIX + trimmedTokenId;
        String backupKey = BLACKLIST_BACKUP_KEY_PREFIX + trimmedTokenId;

        return redisTemplate.delete(blacklistKey)
                .flatMap(deleted -> redisTemplate.delete(backupKey))
                .map(backupDeleted -> {
                    log.debug("令牌已从黑名单移除: tokenId={}", trimmedTokenId);
                    return true;
                })
                .onErrorResume(ex -> {
                    log.warn("从黑名单移除令牌时发生异常: tokenId={}, error={}", trimmedTokenId, ex.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * 获取黑名单统计信息
     *
     * @return 统计信息
     */
    public Mono<BlacklistStats> getStats() {
        return Mono.fromCallable(() -> {
            BlacklistStats stats = new BlacklistStats();

            // 本地缓存统计
            cleanupLocalCache(); // 先清理过期条目
            stats.setLocalCacheSize(localBlacklistCache.size());
            stats.setLocalCacheMaxSize(MAX_LOCAL_CACHE_SIZE);

            return stats;
        }).flatMap(stats -> {
            // Redis统计（异步获取，失败不影响本地统计）
            return getRedisStats()
                    .map(redisStats -> {
                        stats.setRedisSize(redisStats.getRedisSize());
                        stats.setRedisBackupSize(redisStats.getRedisBackupSize());
                        stats.setRedisAvailable(true);
                        return stats;
                    })
                    .onErrorReturn(stats); // Redis失败时返回本地统计
        });
    }

    /**
     * 清理本地缓存中的过期条目
     */
    private void cleanupLocalCache() {
        if (localBlacklistCache.size() <= MAX_LOCAL_CACHE_SIZE) {
            // 只清理过期条目
            long currentTime = System.currentTimeMillis();
            localBlacklistCache.entrySet().removeIf(entry -> entry.getValue() < currentTime);
        } else {
            // 缓存过大，清理过期条目和最老的条目
            long currentTime = System.currentTimeMillis();
            localBlacklistCache.entrySet().removeIf(entry -> entry.getValue() < currentTime);

            // 如果还是太大，移除最老的条目
            while (localBlacklistCache.size() > MAX_LOCAL_CACHE_SIZE) {
                String oldestKey = localBlacklistCache.entrySet().stream()
                        .min((e1, e2) -> Long.compare(e1.getValue(), e2.getValue()))
                        .map(entry -> entry.getKey())
                        .orElse(null);

                if (oldestKey != null) {
                    localBlacklistCache.remove(oldestKey);
                } else {
                    break;
                }
            }
        }
    }

    /**
     * 获取Redis统计信息
     */
    private Mono<RedisStats> getRedisStats() {
        return redisTemplate.keys(BLACKLIST_KEY_PREFIX + "*")
                .count()
                .flatMap(mainCount ->
                    redisTemplate.keys(BLACKLIST_BACKUP_KEY_PREFIX + "*")
                            .count()
                            .map(backupCount -> {
                                RedisStats stats = new RedisStats();
                                stats.setRedisSize(mainCount.intValue());
                                stats.setRedisBackupSize(backupCount.intValue());
                                return stats;
                            })
                );
    }

    /**
     * 黑名单统计信息
     */
    public static class BlacklistStats {
        private int localCacheSize;
        private int localCacheMaxSize;
        private int redisSize;
        private int redisBackupSize;
        private boolean redisAvailable;

        // Getters and Setters
        public int getLocalCacheSize() {
            return localCacheSize;
        }

        public void setLocalCacheSize(final int localCacheSize) {
            this.localCacheSize = localCacheSize;
        }

        public int getLocalCacheMaxSize() {
            return localCacheMaxSize;
        }

        public void setLocalCacheMaxSize(final int localCacheMaxSize) {
            this.localCacheMaxSize = localCacheMaxSize;
        }

        public int getRedisSize() {
            return redisSize;
        }

        public void setRedisSize(final int redisSize) {
            this.redisSize = redisSize;
        }

        public int getRedisBackupSize() {
            return redisBackupSize;
        }

        public void setRedisBackupSize(final int redisBackupSize) {
            this.redisBackupSize = redisBackupSize;
        }

        public boolean isRedisAvailable() {
            return redisAvailable;
        }

        public void setRedisAvailable(final boolean redisAvailable) {
            this.redisAvailable = redisAvailable;
        }
    }

    /**
     * Redis统计信息
     */
    private static class RedisStats {
        private int redisSize;
        private int redisBackupSize;

        public int getRedisSize() {
            return redisSize;
        }

        public void setRedisSize(final int redisSize) {
            this.redisSize = redisSize;
        }

        public int getRedisBackupSize() {
            return redisBackupSize;
        }

        public void setRedisBackupSize(final int redisBackupSize) {
            this.redisBackupSize = redisBackupSize;
        }
    }
}