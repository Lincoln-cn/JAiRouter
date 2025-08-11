package org.unreal.modelrouter.ratelimit.impl;

import org.unreal.modelrouter.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.ratelimit.RateLimitContext;
import org.unreal.modelrouter.ratelimit.RateLimiter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 支持预热的令牌桶限流器
 * 在预热期间逐步增加令牌生成速率，直到达到设定的最大速率
 */
public class WarmUpRateLimiter implements RateLimiter {
    private final RateLimitConfig config;
    private final AtomicLong tokens;
    private final AtomicLong lastRefillTimestamp;
    private final long warmUpPeriod; // 预热期（纳秒）
    private final AtomicLong lastWarmUpTime; // 上次预热时间戳

    public WarmUpRateLimiter(final RateLimitConfig config) {
        this.config = config;
        this.tokens = new AtomicLong(config.getCapacity());
        this.lastRefillTimestamp = new AtomicLong(System.nanoTime());
        // 使用配置中的预热期（转换为纳秒）
        this.warmUpPeriod = config.getWarmUpPeriod() * 1_000_000_000L; 
        this.lastWarmUpTime = new AtomicLong(System.nanoTime());
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
        
        // 计算当前应该使用的速率（考虑预热）
        long currentRate = calculateCurrentRate(now);
        
        long toAdd = (passed * currentRate) / 1_000_000_000L;
        if (toAdd > 0 && lastRefillTimestamp.compareAndSet(last, now)) {
            tokens.updateAndGet(v -> Math.min(config.getCapacity(), v + toAdd));
        }
    }

    /**
     * 计算当前速率，考虑预热阶段
     * @param now 当前时间戳（纳秒）
     * @return 当前速率
     */
    private long calculateCurrentRate(final long now) {
        long timeSinceLastWarmUp = now - lastWarmUpTime.get();
        
        // 如果已经过了预热期，直接返回配置的速率
        if (timeSinceLastWarmUp >= warmUpPeriod) {
            return config.getRate();
        }
        
        // 在预热期内，逐步增加速率
        // 使用线性增长: rate * (time / warmUpPeriod)
        return Math.max(1, (config.getRate() * timeSinceLastWarmUp) / warmUpPeriod);
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
     * 重置预热状态，用于手动触发预热
     */
    public void resetWarmUp() {
        lastWarmUpTime.set(System.nanoTime());
    }
}
