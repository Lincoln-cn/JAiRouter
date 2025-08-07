package org.unreal.modelrouter.ratelimit.impl;

import org.unreal.modelrouter.ratelimit.BaseRateLimiter;
import org.unreal.modelrouter.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.ratelimit.RateLimitContext;
import org.unreal.modelrouter.ratelimit.RateLimiter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 令牌桶限流器实现
 */
public class TokenBucketRateLimiter extends BaseRateLimiter {
    private final AtomicLong tokens;
    private final AtomicLong lastRefillTimestamp;

    public TokenBucketRateLimiter(RateLimitConfig config) {
        super(config);
        this.tokens = new AtomicLong(config.getCapacity());
        this.lastRefillTimestamp = new AtomicLong(System.nanoTime());
    }

    @Override
    public boolean tryAcquire(RateLimitContext context) {
        RateLimiter limiter = getScopedLimiter(context);
        if (limiter != this) {
            return limiter.tryAcquire(context);
        }

        refill();

        long currentTokens = tokens.get();
        if (currentTokens < context.getTokens()) {
            return false;
        }

        return tokens.compareAndSet(currentTokens, currentTokens - context.getTokens());
    }

    private void refill() {
        long now = System.nanoTime();
        long lastRefillTime = lastRefillTimestamp.get();
        long timePassed = now - lastRefillTime;

        // 计算需要补充的令牌数
        long tokensToAdd = (timePassed * config.getRate()) / 1_000_000_000L;

        if (tokensToAdd > 0) {
            // 更新上次补充时间
            if (lastRefillTimestamp.compareAndSet(lastRefillTime, now)) {
                // 补充令牌，但不超过桶容量
                tokens.updateAndGet(currentTokens ->
                        Math.min(config.getCapacity(), currentTokens + tokensToAdd)
                );
            }
        }
    }

    @Override
    protected RateLimiter createScopedLimiter() {
        return new TokenBucketRateLimiter(config);
    }
}
