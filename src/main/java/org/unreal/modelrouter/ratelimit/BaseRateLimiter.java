package org.unreal.modelrouter.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 抽象限流器实现，提供基础功能
 */
public abstract class BaseRateLimiter{
    protected final RateLimitConfig config;
    protected final ConcurrentMap<String, RateLimiter> scopedLimiters = new ConcurrentHashMap<>();

    public BaseRateLimiter(RateLimitConfig config) {
        this.config = config;
    }

    /**
     * 根据上下文生成作用域键
     * @param context 限流上下文
     * @return 作用域键
     */
    public String generateKey(RateLimitContext context) {
        switch (config.getScope().toLowerCase()) {
            case "service":
                return context.getServiceType().name();
            case "model":
                return context.getServiceType().name() + ":" + context.getModelName();
            case "client-ip":
                return context.getClientIp();
            case "instance":
                // 这里需要根据实际实例信息生成键
                return context.getServiceType().name() + ":" + context.getModelName() + ":" + context.getClientIp();
            default:
                return "default";
        }
    }

    /**
     * 创建作用域限流器
     * @return 限流器实例
     */
    protected abstract RateLimiter createScopedLimiter();
}
