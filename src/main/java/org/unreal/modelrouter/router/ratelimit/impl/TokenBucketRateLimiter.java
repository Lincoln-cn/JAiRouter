package org.unreal.modelrouter.router.ratelimit.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.router.ratelimit.RateLimitContext;
import org.unreal.modelrouter.router.ratelimit.RateLimiter;

import java.util.concurrent.atomic.AtomicLong;

public class TokenBucketRateLimiter implements RateLimiter {
    private final RateLimitConfig config;
    private final AtomicLong tokens;
    private final AtomicLong lastRefillTimestamp;
    
    @Autowired(required = false)
    private MetricsCollector metricsCollector;

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
        
        // 使用循环CAS来避免竞争条件
        boolean allowed = false;
        while (true) {
            long current = tokens.get();
            if (current < context.getTokens()) {
                allowed = false;
                break;
            }
            
            long newValue = current - context.getTokens();
            if (tokens.compareAndSet(current, newValue)) {
                allowed = true;
                break;
            }
            // CAS失败，重试
        }
        
        // 记录限流指标
        recordRateLimitMetrics(context, allowed);
        return allowed;
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

    /**
     * 记录限流指标
     */
    private void recordRateLimitMetrics(final RateLimitContext context, final boolean allowed) {
        if (metricsCollector != null) {
            try {
                String serviceName = context.getServiceType() != null
                    ? context.getServiceType().name().toLowerCase() : "unknown";
                metricsCollector.recordRateLimit(serviceName, "token_bucket", allowed);
            } catch (Exception e) {
                // 静默处理指标记录异常，不影响业务逻辑
            }
        }
    }

    /**
     * 获取剩余令牌数
     */
    @Override
    public long getRemainingCapacity() {
        refill();
        return tokens.get();
    }

    /**
     * 获取容量使用率
     */
    @Override
    public double getUsageRatio() {
        long remaining = getRemainingCapacity();
        long capacity = config.getCapacity();
        if (capacity <= 0) {
            return 0;
        }
        return 1.0 - ((double) remaining / capacity);
    }
}