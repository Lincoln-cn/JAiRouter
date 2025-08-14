package org.unreal.modelrouter.ratelimit.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
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
        long now = System.currentTimeMillis();
        long window = 1000L;
        while (!q.isEmpty() && q.peek() < now - window) {
            q.poll();
        }
        boolean allowed;
        if (q.size() >= config.getRate()) {
            allowed = false;
        } else {
            q.offer(now);
            allowed = true;
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
    private void recordRateLimitMetrics(RateLimitContext context, boolean allowed) {
        if (metricsCollector != null) {
            try {
                String serviceName = context.getServiceType() != null ? 
                    context.getServiceType().name().toLowerCase() : "unknown";
                metricsCollector.recordRateLimit(serviceName, "sliding_window", allowed);
            } catch (Exception e) {
                // 静默处理指标记录异常，不影响业务逻辑
            }
        }
    }
}
