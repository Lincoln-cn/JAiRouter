package org.unreal.modelrouter.auth.security.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于滑动窗口的内存速率限制器
 * 为每个 API Key 维护独立的请求计数窗口
 *
 * <p><b>有界是硬要求</b>：窗口表用 Caffeine 的 {@code maximumSize} + {@code expireAfterWrite}
 * 双重约束（与 {@code ClientIpRateLimiterCache} 同一形态）。早期版本用无淘汰的
 * {@code ConcurrentHashMap}，条目随 key 数量单调增长（#625）。</p>
 *
 * <p><b>淘汰的代价</b>：某个 key 的窗口被淘汰后再出现，窗口会从 0 重新计数 ⇒ 理论上该 key
 * 能获得一次额外突发。这与 {@code ClientIpRateLimiterCache} 的淘汰语义一致，属可接受的取舍；
 * 需要严格计数的场景应使用配额账本（{@code jairouter.quota.*}）。</p>
 *
 * @author JAiRouter Team
 * @since v2.7.6
 */
@Slf4j
@Component
public class TokenBucketRateLimiter {

    private static final long WINDOW_SIZE_MS = 60_000L;

    /** 窗口条目上限：超出后按 Caffeine 的近似 LRU 淘汰（#625）。 */
    private static final long MAX_ENTRIES = 10_000L;

    /** 条目存活时间：2 个窗口，足以覆盖本窗口并让过期条目自然消失。 */
    private static final Duration EVICTION_TTL = Duration.ofMillis(WINDOW_SIZE_MS * 2L);

    private final Cache<String, SlidingWindow> windows = Caffeine.newBuilder()
            .maximumSize(MAX_ENTRIES)
            .expireAfterWrite(EVICTION_TTL)
            .build();

    /**
     * 检查并消耗一个请求配额
     *
     * @param keyId         API Key ID
     * @param limitPerMinute 每分钟请求上限（0 表示不限制）
     * @return true 表示允许，false 表示超出速率限制
     */
    public boolean tryAcquire(final String keyId, final int limitPerMinute) {
        if (limitPerMinute <= 0) {
            return true;
        }
        SlidingWindow window = windows.get(keyId, k -> new SlidingWindow());
        return window.tryAcquire(limitPerMinute);
    }

    /**
     * 获取当前窗口内的请求数
     *
     * @param keyId API Key ID
     * @return 当前窗口内的请求数
     */
    public int getCurrentCount(final String keyId) {
        SlidingWindow window = windows.getIfPresent(keyId);
        if (window == null) {
            return 0;
        }
        return window.getCurrentCount();
    }

    /**
     * 清除指定 API Key 的速率限制窗口
     *
     * @param keyId API Key ID
     */
    public void reset(final String keyId) {
        windows.invalidate(keyId);
        log.debug("速率限制窗口已重置: {}", keyId);
    }

    /**
     * 清除所有速率限制窗口
     */
    public void resetAll() {
        windows.invalidateAll();
        log.info("所有速率限制窗口已重置");
    }

    /**
     * 获取当前活跃的窗口数量（用于监控）
     *
     * <p>先执行一次待处理维护，使统计值尽量贴近实际条目数（Caffeine 的淘汰是惰性的）。</p>
     *
     * @return 活跃窗口数，不超过 {@value #MAX_ENTRIES}
     */
    public int getActiveWindowCount() {
        windows.cleanUp();
        return (int) windows.estimatedSize();
    }

    /**
     * 滑动窗口实现
     */
    private static class SlidingWindow {
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        private final AtomicInteger count = new AtomicInteger(0);

        boolean tryAcquire(final int limit) {
            long now = System.currentTimeMillis();
            long currentWindowStart = windowStart.get();

            if (now - currentWindowStart >= WINDOW_SIZE_MS) {
                if (windowStart.compareAndSet(currentWindowStart, now)) {
                    count.set(0);
                }
            }

            int current = count.incrementAndGet();
            if (current > limit) {
                count.decrementAndGet();
                return false;
            }
            return true;
        }

        int getCurrentCount() {
            long now = System.currentTimeMillis();
            if (now - windowStart.get() >= WINDOW_SIZE_MS) {
                return 0;
            }
            return count.get();
        }
    }
}
