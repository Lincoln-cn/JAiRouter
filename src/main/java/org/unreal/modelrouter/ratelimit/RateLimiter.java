package org.unreal.modelrouter.ratelimit;

/**
 * 限流器基础接口
 */
public interface RateLimiter {
    /**
     * 尝试获取令牌
     * @param context 限流上下文
     * @return true表示允许，false表示拒绝
     */
    boolean tryAcquire(RateLimitContext context);

    /**
     * 获取限流配置
     * @return 限流配置
     */
    RateLimitConfig getConfig();
}
