package org.unreal.modelrouter.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 抽象限流器实现，提供基础功能
 */
public abstract class BaseRateLimiter {
    private final RateLimitConfig config;
    private final ConcurrentMap<String, RateLimiter> scopedLimiters = new ConcurrentHashMap<>();

    public BaseRateLimiter(final RateLimitConfig config) {
        this.config = config;
    }

    /**
     * 根据上下文生成作用域键
     * @param context 限流上下文
     * @return 作用域键
     */
    public String generateKey(final RateLimitContext context) {
        return switch (config.getScope().toLowerCase()) {
            case "service" -> context.getServiceType().name();
            case "model" -> context.getServiceType().name() + ":" + context.getModelName();
            case "client-ip" -> context.getClientIp();
            case "instance" ->
                // 这里需要根据实际实例信息生成键
                    context.getServiceType().name() + ":" + context.getModelName() + ":" + context.getClientIp();
            default -> "default";
        };
    }

    /**
     * 获取限流配置
     * @return 限流配置
     */
    public RateLimitConfig getConfig() {
        return config;
    }

    /**
     * 获取作用域限流器映射
     * @return 作用域限流器映射
     */
    public ConcurrentMap<String, RateLimiter> getScopedLimiters() {
        return scopedLimiters;
    }

    /**
     * 创建作用域限流器
     * @return 限流器实例
     */
    protected abstract RateLimiter createScopedLimiter();
}
