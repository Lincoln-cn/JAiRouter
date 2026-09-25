package org.unreal.modelrouter.router.ratelimit.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.router.ratelimit.RateLimitContext;
import org.unreal.modelrouter.router.ratelimit.RateLimiter;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 滑动窗口限流器实现
 *
 * <p>#122: 准入判定与记录在同一临界区内完成（避免 check-then-act 竞态超发）；
 * 窗口时间戳存放在 {@link ArrayDeque} 上，{@code size()} 为 O(1)，
 * 不再在热路径上调用 {@code ConcurrentLinkedQueue.size()}（O(n)）。</p>
 */
public class SlidingWindowRateLimiter implements RateLimiter {
    private static final long WINDOW_MS = 1000L;

    private final RateLimitConfig config;
    /** 窗口内已放行时间戳；仅在 {@link #lock} 保护下访问 */
    private final Deque<Long> window = new ArrayDeque<>();
    private final Object lock = new Object();

    @Autowired(required = false)
    private MetricsCollector metricsCollector;

    public SlidingWindowRateLimiter(final RateLimitConfig config) {
        this.config = config;
    }

    /**
     * 尝试获取令牌
     * @param context 限流上下文
     * @return 是否获取成功
     */
    @Override
    public boolean tryAcquire(final RateLimitContext context) {
        final long now = System.currentTimeMillis();
        final boolean allowed;
        synchronized (lock) {
            pruneExpired(now);
            if (window.size() >= config.getRate()) {
                allowed = false;
            } else {
                window.addLast(now);
                allowed = true;
            }
        }

        // 记录限流指标
        recordRateLimitMetrics(context, allowed);
        return allowed;
    }

    /**
     * 获取限流配置
     * @return 限流配置
     */
    @Override 
    public RateLimitConfig getConfig() { 
        return config; 
    }

    /**
     * 记录限流指标
     */
    private void recordRateLimitMetrics(final RateLimitContext context, final boolean allowed) {
        if (metricsCollector != null) {
            try {
                String serviceName = context.getServiceType() != null
                    ? context.getServiceType().name().toLowerCase() : "unknown";
                metricsCollector.recordRateLimit(serviceName, "sliding_window", allowed);
            } catch (Exception e) {
                // 静默处理指标记录异常，不影响业务逻辑
            }
        }
    }

    /**
     * 获取剩余请求数
     */
    @Override
    public long getRemainingCapacity() {
        final long now = System.currentTimeMillis();
        synchronized (lock) {
            pruneExpired(now);
            return Math.max(0, config.getRate() - window.size());
        }
    }

    /**
     * 获取容量使用率
     */
    @Override
    public double getUsageRatio() {
        final long now = System.currentTimeMillis();
        final long maxRequests = config.getRate();
        if (maxRequests <= 0) {
            return 0;
        }
        synchronized (lock) {
            pruneExpired(now);
            return (double) window.size() / maxRequests;
        }
    }

    /** 清理窗口外过期时间戳（调用方须持有 {@link #lock}） */
    private void pruneExpired(final long now) {
        final long cutoff = now - WINDOW_MS;
        while (!window.isEmpty() && window.peekFirst() < cutoff) {
            window.removeFirst();
        }
    }
}
