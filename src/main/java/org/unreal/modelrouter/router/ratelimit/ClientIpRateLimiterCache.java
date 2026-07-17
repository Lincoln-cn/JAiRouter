package org.unreal.modelrouter.router.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端 IP 限流器缓存
 *
 * 使用 Caffeine 替代 ConcurrentHashMap，解决内存泄漏风险：
 * - 自动过期清理：30 分钟无访问自动移除
 * - 内存上限：最多 10000 条目
 * - 统计功能：命中率、淘汰数
 *
 * @author JAiRouter Team
 * @since v2.7.10
 */
public class ClientIpRateLimiterCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientIpRateLimiterCache.class);

    // 缓存配置
    private static final int MAX_SIZE = 10000;
    private static final int EXPIRE_MINUTES = 30;

    // 统计信息
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);

    // Caffeine 缓存：serviceType:clientIp -> RateLimiter
    private final Cache<String, RateLimiter> cache;

    public ClientIpRateLimiterCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_SIZE)
                .expireAfterAccess(EXPIRE_MINUTES, TimeUnit.MINUTES)
                .removalListener((String key, RateLimiter value, RemovalCause cause) -> {
                    if (cause.wasEvicted()) {
                        evictionCount.incrementAndGet();
                        LOGGER.debug("Rate limiter evicted: key={}, cause={}", key, cause);
                    }
                })
                .recordStats()
                .build();

        LOGGER.info("ClientIpRateLimiterCache initialized: maxSize={}, expireAfterAccess={}min",
                MAX_SIZE, EXPIRE_MINUTES);
    }

    /**
     * 获取限流器
     *
     * @param serviceType 服务类型
     * @param clientIp 客户端 IP
     * @param loader 加载器（缓存未命中时调用）
     * @return 限流器
     */
    public RateLimiter get(final ServiceType serviceType, final String clientIp,
                           final java.util.function.Supplier<RateLimiter> loader) {
        String key = generateKey(serviceType, clientIp);
        RateLimiter limiter = cache.getIfPresent(key);

        if (limiter != null) {
            hitCount.incrementAndGet();
            return limiter;
        }

        missCount.incrementAndGet();
        limiter = loader.get();
        if (limiter != null) {
            cache.put(key, limiter);
        }
        return limiter;
    }

    /**
     * 获取已存在的限流器（不创建）
     */
    public RateLimiter getIfPresent(final ServiceType serviceType, final String clientIp) {
        String key = generateKey(serviceType, clientIp);
        RateLimiter limiter = cache.getIfPresent(key);
        if (limiter != null) {
            hitCount.incrementAndGet();
        } else {
            missCount.incrementAndGet();
        }
        return limiter;
    }

    /**
     * 放入限流器
     */
    public void put(final ServiceType serviceType, final String clientIp, final RateLimiter limiter) {
        String key = generateKey(serviceType, clientIp);
        cache.put(key, limiter);
    }

    /**
     * 移除限流器
     */
    public void invalidate(final ServiceType serviceType, final String clientIp) {
        String key = generateKey(serviceType, clientIp);
        cache.invalidate(key);
    }

    /**
     * 清空缓存
     */
    public void invalidateAll() {
        cache.invalidateAll();
        LOGGER.info("ClientIpRateLimiterCache cleared");
    }

    /**
     * 获取缓存大小
     */
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = cache.stats();
        return new CacheStats(
                hitCount.get(),
                missCount.get(),
                evictionCount.get(),
                size(),
                stats.hitRate()
        );
    }

    /**
     * 生成缓存键
     */
    private String generateKey(final ServiceType serviceType, final String clientIp) {
        return serviceType.name() + ":" + clientIp;
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final long hitCount;
        private final long missCount;
        private final long evictionCount;
        private final long size;
        private final double hitRate;

        public CacheStats(final long hitCount, final long missCount, final long evictionCount,
                          final long size, final double hitRate) {
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.evictionCount = evictionCount;
            this.size = size;
            this.hitRate = hitRate;
        }

        public long getHitCount() {
            return hitCount;
        }

        public long getMissCount() {
            return missCount;
        }

        public long getEvictionCount() {
            return evictionCount;
        }

        public long getSize() {
            return size;
        }

        public double getHitRate() {
            return hitRate;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{hits=%d, misses=%d, evictions=%d, size=%d, hitRate=%.2f%%}",
                    hitCount, missCount, evictionCount, size, hitRate * 100);
        }
    }
}
