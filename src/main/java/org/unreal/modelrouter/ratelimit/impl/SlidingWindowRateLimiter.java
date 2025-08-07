package org.unreal.modelrouter.ratelimit.impl;

import org.unreal.modelrouter.ratelimit.BaseRateLimiter;
import org.unreal.modelrouter.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.ratelimit.RateLimitContext;
import org.unreal.modelrouter.ratelimit.RateLimiter;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 滑动窗口限流器实现
 */
public class SlidingWindowRateLimiter extends BaseRateLimiter {
    private final Queue<Long> requestTimestamps;
    private final AtomicLong windowStart;

    public SlidingWindowRateLimiter(RateLimitConfig config) {
        super(config);
        this.requestTimestamps = new ConcurrentLinkedQueue<>();
        this.windowStart = new AtomicLong(System.currentTimeMillis());
    }

    @Override
    public boolean tryAcquire(RateLimitContext context) {
        RateLimiter limiter = getScopedLimiter(context);
        if (limiter != this) {
            return limiter.tryAcquire(context);
        }

        long now = System.currentTimeMillis();
        long windowSizeMillis = 1000; // 1秒窗口，可根据需要调整

        // 移除窗口外的请求记录
        while (!requestTimestamps.isEmpty() &&
                requestTimestamps.peek() < now - windowSizeMillis) {
            requestTimestamps.poll();
        }

        // 检查是否超过限制
        if (requestTimestamps.size() >= config.getRate()) {
            return false;
        }

        requestTimestamps.offer(now);
        return true;
    }

    @Override
    protected RateLimiter createScopedLimiter() {
        return new SlidingWindowRateLimiter(config);
    }
}
