package org.unreal.modelrouter.ratelimit.impl;

import org.unreal.modelrouter.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.ratelimit.RateLimitContext;
import org.unreal.modelrouter.ratelimit.RateLimiter;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 滑动窗口限流器实现
 */
public class SlidingWindowRateLimiter implements RateLimiter {
    private final RateLimitConfig config;
    private final Queue<Long> q = new ConcurrentLinkedQueue<>();

    public SlidingWindowRateLimiter(RateLimitConfig config) {
        this.config = config;
    }

    @Override
    public boolean tryAcquire(RateLimitContext context) {
        long now = System.currentTimeMillis();
        long window = 1000L;
        while (!q.isEmpty() && q.peek() < now - window) q.poll();
        if (q.size() >= config.getRate()) return false;
        q.offer(now);
        return true;
    }

    @Override public RateLimitConfig getConfig() { return config; }
}
