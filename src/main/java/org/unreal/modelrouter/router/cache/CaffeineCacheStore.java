package org.unreal.modelrouter.router.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.core.ResponseCacheProperties;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v2.9.9: Caffeine 响应缓存存储实现.
 *
 * <p>基于 Caffeine 的内存缓存（单节点默认实现，惯例参考
 * RolePermissionService 5min/200、ClientIpRateLimiterCache 10000/30min）：
 * <ul>
 *   <li>{@code maximumSize} 取自配置 maxSize（注入时读 Properties，默认 10000）</li>
 *   <li>写后过期：每次 put 以传入 ttl 为基准施加 ±10% 随机抖动防雪崩，
 *       经 {@link Expiry} 实现逐条目有效期（等效 expireAfterWrite + 抖动）</li>
 *   <li>null 值不缓存</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 2.9.9
 */
@Component
public final class CaffeineCacheStore implements CacheStore {

    /** TTL 抖动范围: ±10% */
    private static final double JITTER_RATIO = 0.1;

    /** TTL 为空时的兜底有效期（1h，与配置默认一致） */
    private static final Duration FALLBACK_TTL = Duration.ofHours(1);

    private static final Logger logger = LoggerFactory.getLogger(CaffeineCacheStore.class);

    private final Cache<String, CacheEntry> cache;

    public CaffeineCacheStore(final ResponseCacheProperties properties) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getMaxSize())
                .expireAfter(new Expiry<String, CacheEntry>() {
                    @Override
                    public long expireAfterCreate(final String key, final CacheEntry value,
                                                  final long currentTime) {
                        return jitteredTtlNanos(value);
                    }

                    @Override
                    public long expireAfterUpdate(final String key, final CacheEntry value,
                                                  final long currentTime, final long currentDuration) {
                        // 覆盖写入同样重置有效期（等效 expireAfterWrite：写操作刷新 TTL）
                        return jitteredTtlNanos(value);
                    }

                    @Override
                    public long expireAfterRead(final String key, final CacheEntry value,
                                                final long currentTime, final long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
        logger.info("CaffeineCacheStore initialized: maxSize={}", properties.getMaxSize());
    }

    @Override
    public Optional<Object> get(final String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        CacheEntry entry = cache.getIfPresent(key);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entry.value);
    }

    @Override
    public void put(final String key, final Object data, final Duration ttl) {
        if (key == null || key.isBlank() || data == null) {
            return;
        }
        long ttlNanos = ttl != null ? ttl.toNanos() : FALLBACK_TTL.toNanos();
        cache.put(key, new CacheEntry(data, ttlNanos));
    }

    @Override
    public long size() {
        // Caffeine 的 estimatedSize() 只是估算值，不能直接使用：
        // 1) 过期条目的物理清除依赖定时轮维护（按秒级粒度推进，短 TTL 的清除可能滞后约一个 tick）；
        // 2) 超过 maximumSize 的容量淘汰也在后台维护中异步执行。
        // 先 cleanUp() 同步完成容量淘汰，再按 asMap 实际迭代计数——Caffeine 的 map 视图迭代会
        // 跳过已过期条目——保证返回值精确等于“当前可命中的条目数”（成本 O(n)，n 受 maxSize 约束）。
        cache.cleanUp();
        long count = 0;
        for (Map.Entry<String, CacheEntry> ignored : cache.asMap().entrySet()) {
            count++;
        }
        return count;
    }

    @Override
    public void delete(final String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        cache.invalidate(key);
    }

    @Override
    public void deleteByPrefix(final String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        cache.cleanUp();
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

    /**
     * 计算 ±10% 抖动的条目有效期（纳秒）.
     *
     * <p>每条目在 expireAfterCreate 时独立取随机因子，
     * 防止大量同 TTL 条目同时过期造成雪崩。
     *
     * @param entry 缓存条目
     * @return 抖动后的有效期纳秒
     */
    private long jitteredTtlNanos(final CacheEntry entry) {
        double factor = ThreadLocalRandom.current().nextDouble(1.0 - JITTER_RATIO, 1.0 + JITTER_RATIO);
        return (long) (entry.ttlNanos * factor);
    }

    /**
     * 内部缓存条目：数据 + 基准 TTL（纳秒）.
     */
    private static final class CacheEntry {
        private final Object value;
        private final long ttlNanos;

        private CacheEntry(final Object value, final long ttlNanos) {
            this.value = value;
            this.ttlNanos = ttlNanos;
        }
    }
}
