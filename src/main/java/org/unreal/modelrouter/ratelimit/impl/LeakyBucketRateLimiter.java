package org.unreal.modelrouter.ratelimit.impl;

import org.unreal.modelrouter.ratelimit.BaseRateLimiter;
import org.unreal.modelrouter.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.ratelimit.RateLimitContext;
import org.unreal.modelrouter.ratelimit.RateLimiter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 漏桶限流器实现
 */
public class LeakyBucketRateLimiter extends BaseRateLimiter {
    private final AtomicLong waterLevel; // 当前水量
    private final AtomicLong lastLeakTimestamp; // 上次漏水时间

    public LeakyBucketRateLimiter(RateLimitConfig config) {
        super(config);
        this.waterLevel = new AtomicLong(0);
        this.lastLeakTimestamp = new AtomicLong(System.nanoTime());
    }

    @Override
    public boolean tryAcquire(RateLimitContext context) {
        RateLimiter limiter = getScopedLimiter(context);
        if (limiter != this) {
            return limiter.tryAcquire(context);
        }

        leak();

        long currentLevel = waterLevel.get();
        if (currentLevel + context.getTokens() > config.getCapacity()) {
            return false; // 水满，拒绝请求
        }

        return waterLevel.compareAndSet(currentLevel, currentLevel + context.getTokens());
    }

    private void leak() {
        long now = System.nanoTime();
        long lastLeakTime = lastLeakTimestamp.get();
        long timePassed = now - lastLeakTime;

        // 计算应该漏出的水量
        long waterToLeak = (timePassed * config.getRate()) / 1_000_000_000L;

        if (waterToLeak > 0) {
            if (lastLeakTimestamp.compareAndSet(lastLeakTime, now)) {
                waterLevel.updateAndGet(currentLevel ->
                        Math.max(0, currentLevel - waterToLeak)
                );
            }
        }
    }

    @Override
    protected RateLimiter createScopedLimiter() {
        return new LeakyBucketRateLimiter(config);
    }
}
