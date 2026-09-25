package org.unreal.modelrouter.router.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;

import java.util.concurrent.TimeUnit;

public final class ScopedRateLimiterWrapper implements RateLimiter {
    private static final Logger logger = LoggerFactory.getLogger(ScopedRateLimiterWrapper.class);

    // #123: 作用域 key（client-ip/model/instance/rule 等）高基数且可被外部影响，
    // 使用与 ClientIpRateLimiterCache 一致的 Caffeine 有界缓存，避免 map 无界增长。
    // 条目被淘汰后该 key 的窗口状态会重置（下次访问重新建桶），与 ClientIpRateLimiterCache 语义一致。
    private static final int MAX_SIZE = 10000;
    private static final int EXPIRE_MINUTES = 30;

    private final RateLimitConfig config;
    private final java.util.function.Function<RateLimitConfig, RateLimiter> factory;
    private final Cache<String, RateLimiter> map = Caffeine.newBuilder()
            .maximumSize(MAX_SIZE)
            .expireAfterAccess(EXPIRE_MINUTES, TimeUnit.MINUTES)
            .build();

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
                case "rule" -> ctx.getRuleId() != null ? ctx.getRuleId() : "default";
                default -> "default";
            };
            RateLimiter l = map.get(key, k -> factory.apply(config));
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
        if (config.getScope() == null || map.asMap().isEmpty()) {
            // 单一限流器
            return factory.apply(config).getRemainingCapacity();
        }
        // 多作用域：返回平均剩余容量
        double avg = map.asMap().values().stream()
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
        if (config.getScope() == null || map.asMap().isEmpty()) {
            // 单一限流器
            return factory.apply(config).getUsageRatio();
        }
        // 多作用域：返回平均使用率
        return map.asMap().values().stream()
                .mapToDouble(RateLimiter::getUsageRatio)
                .filter(v -> v >= 0)
                .average()
                .orElse(-1);
    }

    /**
     * 当前作用域限流器条目数（监控/测试用，有界缓存下不应无界增长）
     */
    long size() {
        return map.estimatedSize();
    }

    /** 强制执行过期/淘汰维护（测试用） */
    void cleanUp() {
        map.cleanUp();
    }
}
