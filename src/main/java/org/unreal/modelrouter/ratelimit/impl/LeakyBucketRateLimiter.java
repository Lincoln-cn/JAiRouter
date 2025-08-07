package org.unreal.modelrouter.ratelimit.impl;

import org.unreal.modelrouter.ratelimit.BaseRateLimiter;
import org.unreal.modelrouter.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.ratelimit.RateLimitContext;
import org.unreal.modelrouter.ratelimit.RateLimiter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 漏桶限流器实现
 */
public class LeakyBucketRateLimiter implements RateLimiter {
    private final RateLimitConfig config;
    private final AtomicLong water;
    private final AtomicLong lastLeak;

    public LeakyBucketRateLimiter(RateLimitConfig config) {
        this.config = config;
        this.water = new AtomicLong(0);
        this.lastLeak = new AtomicLong(System.nanoTime());
    }

    @Override
    public boolean tryAcquire(RateLimitContext context) {
        leak();
        long current = water.get();
        if (current + context.getTokens() > config.getCapacity()) return false;
        return water.compareAndSet(current, current + context.getTokens());
    }

    private void leak() {
        long now = System.nanoTime();
        long last = lastLeak.get();
        long passed = now - last;
        long toLeak = (passed * config.getRate()) / 1_000_000_000L;
        if (toLeak > 0 && lastLeak.compareAndSet(last, now)) {
            water.updateAndGet(v -> Math.max(0, v - toLeak));
        }
    }

    @Override public RateLimitConfig getConfig() { return config; }
}
