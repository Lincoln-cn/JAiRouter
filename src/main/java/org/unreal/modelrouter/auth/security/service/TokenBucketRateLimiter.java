package org.unreal.modelrouter.auth.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于滑动窗口的内存速率限制器
 * 为每个 API Key 维护独立的请求计数窗口
 *
 * @author JAiRouter Team
 * @since v2.7.6
 */
@Slf4j
@Component
public class TokenBucketRateLimiter {

    private static final long WINDOW_SIZE_MS = 60_000L;

    private final ConcurrentHashMap<String, SlidingWindow> windows = new ConcurrentHashMap<>();

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
        SlidingWindow window = windows.computeIfAbsent(keyId, k -> new SlidingWindow());
        return window.tryAcquire(limitPerMinute);
    }

    /**
     * 获取当前窗口内的请求数
     *
     * @param keyId API Key ID
     * @return 当前窗口内的请求数
     */
    public int getCurrentCount(final String keyId) {
        SlidingWindow window = windows.get(keyId);
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
        windows.remove(keyId);
        log.debug("速率限制窗口已重置: {}", keyId);
    }

    /**
     * 清除所有速率限制窗口
     */
    public void resetAll() {
        windows.clear();
        log.info("所有速率限制窗口已重置");
    }

    /**
     * 获取当前活跃的窗口数量（用于监控）
     *
     * @return 活跃窗口数
     */
    public int getActiveWindowCount() {
        return windows.size();
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
