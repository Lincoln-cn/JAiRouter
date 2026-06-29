package org.unreal.modelrouter.router.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;

import java.util.concurrent.ConcurrentHashMap;

public class ScopedRateLimiterWrapper implements RateLimiter {
    private static final Logger logger = LoggerFactory.getLogger(ScopedRateLimiterWrapper.class);
    private final RateLimitConfig config;
    private final java.util.function.Function<RateLimitConfig, RateLimiter> factory;
    private final java.util.concurrent.ConcurrentMap<String, RateLimiter> map = new ConcurrentHashMap<>();
    
    @Autowired(required = false)
    private MetricsCollector metricsCollector;

    public ScopedRateLimiterWrapper(final RateLimitConfig config,
                                    final java.util.function.Function<RateLimitConfig, RateLimiter> factory) {
        this.config = config;
        this.factory = factory;
    }

    /**
     * 尝试获取令牌
     * @param ctx 限流上下文
     * @return 是否获取成功
     */
    @Override
    public boolean tryAcquire(final RateLimitContext ctx) {
        boolean allowed;
        String serviceName = ctx.getServiceType() != null ? ctx.getServiceType().name() : "unknown";
        String algorithm = config.getAlgorithm() != null ? config.getAlgorithm() : "unknown";
        
        if (config.getScope() == null) {
            allowed = factory.apply(config).tryAcquire(ctx);
        } else {
            String key = switch (config.getScope().toLowerCase()) {
                case "service" -> ctx.getServiceType().name();
                case "model" -> ctx.getServiceType() + ":" + ctx.getModelName();
                case "client-ip" -> ctx.getClientIp();
                case "instance" -> ctx.getServiceType() + ":" + ctx.getInstanceId();
                default -> "default";
            };
            RateLimiter l = map.computeIfAbsent(key, k -> factory.apply(config));
            allowed = l.tryAcquire(ctx);
        }
        
        // 记录限流指标
        recordRateLimitMetrics(serviceName, algorithm, allowed);
        
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
    private void recordRateLimitMetrics(final String service, final String algorithm, final boolean allowed) {
        if (metricsCollector != null) {
            try {
                metricsCollector.recordRateLimit(service, algorithm, allowed);
            } catch (Exception e) {
                logger.warn("Failed to record rate limit metrics: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取剩余容量
     * 对于多作用域限流器，返回所有作用域的平均剩余容量
     */
    @Override
    public long getRemainingCapacity() {
        if (config.getScope() == null || map.isEmpty()) {
            // 单一限流器
            return factory.apply(config).getRemainingCapacity();
        }
        // 多作用域：返回平均剩余容量
        double avg = map.values().stream()
                .mapToLong(RateLimiter::getRemainingCapacity)
                .filter(v -> v >= 0)
                .average()
                .orElse(-1);
        return avg >= 0 ? (long) avg : -1;
    }

    /**
     * 获取容量使用率
     * 对于多作用域限流器，返回所有作用域的平均使用率
     */
    @Override
    public double getUsageRatio() {
        if (config.getScope() == null || map.isEmpty()) {
            // 单一限流器
            return factory.apply(config).getUsageRatio();
        }
        // 多作用域：返回平均使用率
        return map.values().stream()
                .mapToDouble(RateLimiter::getUsageRatio)
                .filter(v -> v >= 0)
                .average()
                .orElse(-1);
    }
}