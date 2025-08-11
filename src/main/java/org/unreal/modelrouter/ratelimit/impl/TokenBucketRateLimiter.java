package org.unreal.modelrouter.ratelimit.impl;

import org.unreal.modelrouter.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.ratelimit.RateLimitContext;
import org.unreal.modelrouter.ratelimit.RateLimiter;

import java.util.concurrent.atomic.AtomicLong;

public class TokenBucketRateLimiter implements RateLimiter {
    private final RateLimitConfig config;
    private final AtomicLong tokens;
    private final AtomicLong lastRefillTimestamp;

    public TokenBucketRateLimiter(final RateLimitConfig config) {
        this.config = config;
        this.tokens = new AtomicLong(config.getCapacity());
        this.lastRefillTimestamp = new AtomicLong(System.nanoTime());
    }

    /**
     * 尝试获取令牌
     * @param context 限流上下文
     * @return 是否获取成功
     */
    @Override
    public boolean tryAcquire(final RateLimitContext context) {
        refill();
        long current = tokens.get();
        if (current < context.getTokens()) {
            return false;
        }
        return tokens.compareAndSet(current, current - context.getTokens());
    }

    private void refill() {
        long now = System.nanoTime();
        long last = lastRefillTimestamp.get();
        long passed = now - last;
        long toAdd = (passed * config.getRate()) / 1_000_000_000L;
        if (toAdd > 0 && lastRefillTimestamp.compareAndSet(last, now)) {
            tokens.updateAndGet(v -> Math.min(config.getCapacity(), v + toAdd));
        }
    }

    /**
     * 获取限流配置
     * @return 限流配置
     */
    @Override 
    public RateLimitConfig getConfig() { 
        return config; 
    }
}