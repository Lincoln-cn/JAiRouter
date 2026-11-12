package org.unreal.modelrouter.router.ratelimit.enduser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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

    /**
     * 只读当前计数（不占名额、不续期）。
     *
     * <p>返回 {@code 0} 表示 key 不存在或已过期——两者对调用方的语义相同：本窗口没有计数。</p>
     */
    private static final String READ_LUA = String.join("\n",
            "local current = redis.call('GET', KEYS[1])",
            "if current == false then",
            "  return 0",
            "end",
            "return tonumber(current)");

    private static final RedisScript<Long> READ = RedisScript.of(READ_LUA, Long.class);

    private final ReactiveRedisTemplate<String, String> template;
    private final Duration timeout;
    private final String keyPrefix;

    /**
     * 构造计数器。
     *
     * @param template Redis 模板；{@code null} 表示不可用，所有请求按降级放行
     * @param timeout  单次调用超时；{@code null} 或非正数时用 {@link #DEFAULT_TIMEOUT}
     */
    public RedisEndUserRateLimitCounter(final ReactiveRedisTemplate<String, String> template,
                                        final Duration timeout) {
        this(template, timeout, KEY_PREFIX);
    }

    /**
     * 构造计数器（自定义 key 前缀）。
     *
     * <p>前缀参数化是为了让同一份实现同时服务两个互不相干的维度：终端用户级限流用
     * {@link #KEY_PREFIX}，API Key 级每分钟限流用 {@code jairouter:ratelimit:api-key:}。
     * 两者计数逻辑、窗口语义、fail-open 策略完全相同，只差命名空间——复制一份实现会
     * 让「窗口语义必须一致」这条约束失去守护。</p>
     *
     * @param template  Redis 模板；{@code null} 表示不可用，所有请求按降级放行
     * @param timeout   单次调用超时；{@code null} 或非正数时用 {@link #DEFAULT_TIMEOUT}
     * @param keyPrefix Redis key 前缀；{@code null} 或空白时用 {@link #KEY_PREFIX}
     */
    public RedisEndUserRateLimitCounter(final ReactiveRedisTemplate<String, String> template,
                                        final Duration timeout,
                                        final String keyPrefix) {
        this.template = template;
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? DEFAULT_TIMEOUT : timeout;
        this.keyPrefix = keyPrefix == null || keyPrefix.trim().isEmpty() ? KEY_PREFIX : keyPrefix;
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
        } catch (DataAccessException | IllegalStateException e) {
            // DataAccessException = 连接/序列化/脚本执行失败；IllegalStateException = blockFirst 超时。
            // 只捕获这两类可预期的故障，其余异常继续冒泡（#604 不以 catch(Exception) 兜底）。
            log.warn("终端用户限流 Redis 调用失败，按 fail-open 放行: key={}, error={}", key, e.getMessage());
            return new Decision(true, 0L, effectiveLimit, retryAfter, true);
        }
    }

    @Override
    public long currentUsage(final String bucketKey, final long windowStartEpochSecond) {
        if (template == null) {
            return 0L;
        }
        final String key = redisKey(bucketKey, windowStartEpochSecond);
        try {
            final Long used = template.execute(READ, List.of(key), List.of()).blockFirst(timeout);
            return used == null ? 0L : Math.max(0L, used);
        } catch (DataAccessException | IllegalStateException e) {
            // 读计数只服务管理面展示，故障时返回 0 而不是让查询接口失败；
            // 捕获面与 record(...) 一致（#604 不 catch(Exception)）。
            log.warn("读取限流计数失败，按 0 返回: key={}, error={}", key, e.getMessage());
            return 0L;
        }
    }

    /**
     * 删除指定桶的当前窗口计数（管理面「重置配额」用）。
     *
     * <p>只删「当前窗口」这一个 key：历史窗口靠 TTL 自然过期，不需要也不应该在这里清扫。</p>
     *
     * @param bucketKey              桶键
     * @param windowStartEpochSecond 窗口起点
     * @return 是否真的删掉了一个 key
     */
    @Override
    public boolean reset(final String bucketKey, final long windowStartEpochSecond) {
        if (template == null) {
            return false;
        }
        final String key = redisKey(bucketKey, windowStartEpochSecond);
        try {
            final Long deleted = template.delete(key).block(timeout);
            return deleted != null && deleted > 0L;
        } catch (DataAccessException | IllegalStateException e) {
            // 与读路径一致：捕获面只含可预期故障（#604 不 catch(Exception)）。
            log.warn("重置限流计数失败: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 全量清零<b>不支持</b>，固定返回 {@code 0}。
     *
     * <p>分布式后端要全清只能 SCAN 整个前缀再批量 DEL，成本与风险都远超管理面一个
     * 「重置全部」按钮应承担的范围；调用方拿到 0 就应当如实提示「分布式模式下未清零」，
     * 而不是以为成功了。单副本内存后端仍然支持（见 {@code LocalEndUserRateLimitCounter}）。</p>
     *
     * @return 固定 {@code 0}
     */
    @Override
    public int resetAll() {
        log.info("Redis 限流计数器不支持全量清零，忽略该操作: prefix={}", keyPrefix);
        return 0;
    }

    /**
     * 拼 Redis key：窗口起点参与 key，窗口切换即天然隔离。
     *
     * @param bucketKey              桶键
     * @param windowStartEpochSecond 窗口起点
     * @return Redis key
     */
    private String redisKey(final String bucketKey, final long windowStartEpochSecond) {
        return redisKeyWithPrefix(keyPrefix, bucketKey, windowStartEpochSecond);
    }

    /**
     * 按指定前缀拼 Redis key（供测试与跨维度复用）。
     *
     * @param keyPrefix              Redis key 前缀
     * @param bucketKey              桶键
     * @param windowStartEpochSecond 窗口起点
     * @return Redis key
     */
    public static String redisKeyWithPrefix(final String keyPrefix,
                                            final String bucketKey,
                                            final long windowStartEpochSecond) {
        final String prefix = keyPrefix == null || keyPrefix.trim().isEmpty() ? KEY_PREFIX : keyPrefix;
        return prefix + bucketKey + ":" + windowStartEpochSecond;
    }
}
