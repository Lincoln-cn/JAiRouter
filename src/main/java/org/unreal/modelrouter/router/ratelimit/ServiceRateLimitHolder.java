package org.unreal.modelrouter.router.ratelimit;

/**
 * v2.9.10: 服务级限流"已预扣"标志持有者.
 *
 * <p>通过 ThreadLocal 在 handler→selectInstance 调用链路中传递"服务级限流已执行"标志，
 * 以支持缓存命中提前短路场景下的恰一次限流语义（预扣在 handler 缓存读前执行，
 * selectInstance 内部根据标志跳过重复扣减）。
 *
 * <p>使用模式:
 * <pre>
 *   // handler 层（缓存读前预扣）
 *   rateLimitManager.tryAcquireService(context);
 *   ServiceRateLimitHolder.markAcquired();
 *   try {
 *       // 缓存命中 → 直接返回；未命中 → selectInstance（内部跳过服务级限流）
 *   } finally {
 *       ServiceRateLimitHolder.clear();
 *   }
 * </pre>
 *
 * @author JAiRouter Team
 * @since 2.9.10
 */
public final class ServiceRateLimitHolder {

    private static final ThreadLocal<Boolean> ACQUIRED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ServiceRateLimitHolder() {
    }

    /**
     * 标记当前请求已由 handler 层预扣服务级限流令牌.
     */
    public static void markAcquired() {
        ACQUIRED.set(Boolean.TRUE);
    }

    /**
     * 查询当前请求是否已由 handler 层预扣服务级限流令牌.
     *
     * @return true 表示已预扣，selectInstance 应跳过内部服务级限流检查
     */
    public static boolean isAcquired() {
        return Boolean.TRUE.equals(ACQUIRED.get());
    }

    /**
     * 清理当前线程的标志位（请求处理完成后调用，防泄漏）.
     */
    public static void clear() {
        ACQUIRED.remove();
    }
}
