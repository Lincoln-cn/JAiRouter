package org.unreal.modelrouter.ratelimit;

import java.util.concurrent.ConcurrentHashMap;

public class ScopedRateLimiterWrapper implements RateLimiter {
    private final RateLimitConfig config;
    private final java.util.function.Function<RateLimitConfig, RateLimiter> factory;
    private final java.util.concurrent.ConcurrentMap<String, RateLimiter> map = new ConcurrentHashMap<>();

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
        if (config.getScope() == null) {
            return factory.apply(config).tryAcquire(ctx);
        }
        String key = switch (config.getScope().toLowerCase()) {
            case "service" -> ctx.getServiceType().name();
            case "model" -> ctx.getServiceType() + ":" + ctx.getModelName();
            case "client-ip" -> ctx.getClientIp();
            case "instance" -> ctx.getServiceType() + ":" + ctx.getInstanceId();
            default -> "default";
        };
        RateLimiter l = map.computeIfAbsent(key, k -> factory.apply(config));
        return l.tryAcquire(ctx);
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