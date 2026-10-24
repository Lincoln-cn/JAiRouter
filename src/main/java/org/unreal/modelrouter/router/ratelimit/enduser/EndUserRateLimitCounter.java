package org.unreal.modelrouter.router.ratelimit.enduser;

/**
 * 终端用户限流计数器（用户级限流）。
 *
 * <p>同一接口两个实现：{@link LocalEndUserRateLimitCounter}（本进程内存，默认）与
 * {@link RedisEndUserRateLimitCounter}（跨副本共享，需 Redis）。两者的窗口语义<b>严格一致</b>：
 * 按自然分钟对齐的固定窗口、每窗口每桶最多 {@code limit} 次。</p>
 *
 * <p><b>为什么是固定窗口而不是滑动窗口</b>：固定窗口能精确算出 {@code Retry-After}
 * （到下一个整分钟），成本只有一个计数器，多副本天然一致；代价是窗口交界处允许接近 2 倍突发
 * ——对「公平分配」这个目的可以接受，与既有 API Key 每分钟限流（固定窗口）语义也一致。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.2
 */
public interface EndUserRateLimitCounter {

    /** 固定窗口长度（秒）。 */
    long WINDOW_SECONDS = 60L;

    /**
     * 计数并判定一次请求。
     *
     * @param bucketKey            桶键（{@code API Key + 终端用户} 的摘要）
     * @param windowStartEpochSecond 窗口起点（按自然分钟对齐的 epoch 秒）
     * @param limit                本窗口允许的请求数
     * @param nowEpochSecond       当前 epoch 秒（由调用方传入，便于测试确定性）
     * @return 判定结果，绝不返回 {@code null}
     */
    Decision record(String bucketKey, long windowStartEpochSecond, int limit, long nowEpochSecond);

    /**
     * 判定结果。
     *
     * @param allowed           是否放行
     * @param used              本窗口已用量（被拒时为已用满的 {@code limit}；降级时为 0）
     * @param limit             本窗口上限
     * @param retryAfterSeconds 建议的重试等待秒数（到下一个整分钟，最小 1）
     * @param degraded          是否降级放行（计数后端不可用 ⇒ fail-open）
     */
    record Decision(boolean allowed, long used, long limit, long retryAfterSeconds, boolean degraded) {

        /**
         * 剩余可用次数。
         *
         * @return {@code limit - used}，不小于 0
         */
        public long remaining() {
            return Math.max(0L, limit - used);
        }
    }

    /**
     * 计算 {@code Retry-After}：到下一个整分钟的秒数，最小 1。
     *
     * @param windowStartEpochSecond 窗口起点
     * @param nowEpochSecond         当前 epoch 秒
     * @return 建议等待秒数
     */
    static long retryAfterSeconds(final long windowStartEpochSecond, final long nowEpochSecond) {
        return Math.max(1L, windowStartEpochSecond + WINDOW_SECONDS - nowEpochSecond);
    }
}
