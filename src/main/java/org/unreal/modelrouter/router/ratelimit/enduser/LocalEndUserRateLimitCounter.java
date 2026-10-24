package org.unreal.modelrouter.router.ratelimit.enduser;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本进程内存的终端用户限流计数器（默认实现，单副本语义）。
 *
 * <p>按「桶键 + 窗口起点」缓存 {@link AtomicLong}，窗口切换即换 key ⇒ 不需要在
 * 计数器里做窗口重置，也不会把上一窗口的计数带到下一窗口。</p>
 *
 * <p><b>有界是硬要求</b>：缓存用 Caffeine 的 {@code maximumSize} + {@code expireAfterWrite}
 * 双重约束。反面教材是 {@code TokenBucketRateLimiter} 里无淘汰的
 * {@code ConcurrentHashMap}（条目随 key 数量单调增长）。代价是被淘汰的桶在下一窗口内
 * 重新出现时会从 0 开始计（相当于该桶获得一次额外突发），这与
 * {@code ClientIpRateLimiterCache} 的淘汰语义一致，属于可接受的取舍。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.2
 */
public final class LocalEndUserRateLimitCounter implements EndUserRateLimitCounter {

    /** 缓存存活时间：2 个窗口，足以覆盖本窗口并让过期桶自然消失。 */
    private static final Duration TTL = Duration.ofSeconds(WINDOW_SECONDS * 2L);

    private final Cache<String, AtomicLong> counts;

    /**
     * 构造计数器。
     *
     * @param maxEntries 缓存条目上限（小于 1 时按 1 处理）
     */
    public LocalEndUserRateLimitCounter(final int maxEntries) {
        this.counts = Caffeine.newBuilder()
                .maximumSize(Math.max(1, maxEntries))
                .expireAfterWrite(TTL)
                .build();
    }

    @Override
    public Decision record(final String bucketKey,
                           final long windowStartEpochSecond,
                           final int limit,
                           final long nowEpochSecond) {
        final long retryAfter = EndUserRateLimitCounter.retryAfterSeconds(windowStartEpochSecond, nowEpochSecond);
        final int effectiveLimit = Math.max(1, limit);
        final AtomicLong counter = counts.get(
                cacheKey(bucketKey, windowStartEpochSecond), key -> new AtomicLong());

        // CAS 循环：保证「判定未达上限」与「占用一个名额」原子成对，不会因并发多放行
        while (true) {
            final long current = counter.get();
            if (current >= effectiveLimit) {
                return new Decision(false, effectiveLimit, effectiveLimit, retryAfter, false);
            }
            if (counter.compareAndSet(current, current + 1L)) {
                return new Decision(true, current + 1L, effectiveLimit, retryAfter, false);
            }
        }
    }

    /**
     * 当前缓存的条目数（先做一次惰性清理）。
     *
     * @return 估计条目数
     */
    public long estimatedSize() {
        counts.cleanUp();
        return counts.estimatedSize();
    }

    /**
     * 拼缓存键：窗口起点参与键，窗口切换即天然隔离。
     *
     * @param bucketKey              桶键
     * @param windowStartEpochSecond 窗口起点
     * @return 缓存键
     */
    private static String cacheKey(final String bucketKey, final long windowStartEpochSecond) {
        return bucketKey + ":" + windowStartEpochSecond;
    }
}
