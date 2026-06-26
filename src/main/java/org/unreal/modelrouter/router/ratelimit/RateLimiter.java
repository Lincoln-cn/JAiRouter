package org.unreal.modelrouter.router.ratelimit;

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

    /**
     * 获取剩余容量（令牌数/请求数）
     * 用于监控和指标导出
     * @return 剩余容量，-1 表示不支持或未知
     */
    default long getRemainingCapacity() {
        return -1;
    }

    /**
     * 获取容量使用率（0.0 ~ 1.0）
     * 用于监控和指标导出
     * @return 使用率，-1 表示不支持或未知
     */
    default double getUsageRatio() {
        return -1;
    }
}
