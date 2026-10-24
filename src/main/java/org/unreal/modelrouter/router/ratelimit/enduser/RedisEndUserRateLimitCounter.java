package org.unreal.modelrouter.router.ratelimit.enduser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

/**
 * 跨副本共享的终端用户限流计数器（需 Redis）。
 *
 * <p>单个 {@code EVAL} 内完成「自增 → 首次写入设置 TTL → 超限回滚」：</p>
 * <pre>
 * local current = redis.call('INCR', KEYS[1])
 * if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
 * if current &gt; tonumber(ARGV[1]) then redis.call('DECR', KEYS[1]); return 0 end
 * return current
 * </pre>
 *
 * <p>超限时 {@code DECR} 回滚，保证被拒绝的请求不占名额（否则客户端重试会把窗口越推越满）。</p>
 *
 * <p><b>三条边界语义</b>（与 {@code RedisSlidingWindowRateLimiter} 保持一致）：</p>
 * <ul>
 *   <li><b>fail-open</b>：Redis 不可用时放行并标记 {@code degraded}——限流是为了保护下游，
 *       Redis 故障时全站拒绝比放宽更糟。</li>
 *   <li><b>有界阻塞</b>：同步接口用 {@code blockFirst(timeout)}，默认 50ms；
 *       调用点位于 {@code Schedulers.boundedElastic()} 上，不会卡住 Netty EventLoop。</li>
 *   <li><b>桶自动过期</b>：key 内含窗口起点，TTL 为 2 个窗口，无需清理任务。</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 3.5.2
 */
@Slf4j
public final class RedisEndUserRateLimitCounter implements EndUserRateLimitCounter {

    /** Redis key 前缀（与既有 {@code jairouter:ratelimit:} 命名空间并列）。 */
    public static final String KEY_PREFIX = "jairouter:ratelimit:end-user:";

    /** 默认单次 Redis 调用超时。 */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(50L);

    /**
     * 自增 + 超限回滚（原子）。
     *
     * <p>KEYS[1]=桶 key；ARGV[1]=本窗口上限；ARGV[2]=key 存活毫秒数。
     * 返回递增后的计数；超限返回 0。</p>
     */
    private static final String INCREMENT_LUA = String.join("\n",
            "local current = redis.call('INCR', KEYS[1])",
            "if current == 1 then",
            "  redis.call('PEXPIRE', KEYS[1], ARGV[2])",
            "end",
            "if current > tonumber(ARGV[1]) then",
            "  redis.call('DECR', KEYS[1])",
            "  return 0",
            "end",
            "return current");

    private static final RedisScript<Long> INCREMENT = RedisScript.of(INCREMENT_LUA, Long.class);

    private final ReactiveRedisTemplate<String, String> template;
    private final Duration timeout;

    /**
     * 构造计数器。
     *
     * @param template Redis 模板；{@code null} 表示不可用，所有请求按降级放行
     * @param timeout  单次调用超时；{@code null} 或非正数时用 {@link #DEFAULT_TIMEOUT}
     */
    public RedisEndUserRateLimitCounter(final ReactiveRedisTemplate<String, String> template,
                                        final Duration timeout) {
        this.template = template;
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? DEFAULT_TIMEOUT : timeout;
    }

    @Override
    public Decision record(final String bucketKey,
                           final long windowStartEpochSecond,
                           final int limit,
                           final long nowEpochSecond) {
        final long retryAfter = EndUserRateLimitCounter.retryAfterSeconds(windowStartEpochSecond, nowEpochSecond);
        final int effectiveLimit = Math.max(1, limit);
        if (template == null) {
            return new Decision(true, 0L, effectiveLimit, retryAfter, true);
        }
        final String key = redisKey(bucketKey, windowStartEpochSecond);
        try {
            final Long used = template.execute(INCREMENT, List.of(key), List.of(
                    String.valueOf(effectiveLimit),
                    String.valueOf(WINDOW_SECONDS * 2L * 1000L))).blockFirst(timeout);
            if (used == null) {
                log.warn("终端用户限流 Redis 无返回，按 fail-open 放行: key={}", key);
                return new Decision(true, 0L, effectiveLimit, retryAfter, true);
            }
            if (used <= 0L) {
                return new Decision(false, effectiveLimit, effectiveLimit, retryAfter, false);
            }
            return new Decision(true, used, effectiveLimit, retryAfter, false);
        } catch (Exception e) {
            log.warn("终端用户限流 Redis 调用失败，按 fail-open 放行: key={}, error={}", key, e.getMessage());
            return new Decision(true, 0L, effectiveLimit, retryAfter, true);
        }
    }

    /**
     * 拼 Redis key：窗口起点参与 key，窗口切换即天然隔离。
     *
     * @param bucketKey              桶键
     * @param windowStartEpochSecond 窗口起点
     * @return Redis key
     */
    public static String redisKey(final String bucketKey, final long windowStartEpochSecond) {
        return KEY_PREFIX + bucketKey + ":" + windowStartEpochSecond;
    }
}
