package org.unreal.modelrouter.router.ratelimit.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.router.ratelimit.RateLimitContext;
import org.unreal.modelrouter.router.ratelimit.RateLimiter;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 跨副本滑动窗口限流器（#161）：状态放在 Redis，各副本共享同一个窗口。
 *
 * <p>语义与本地 {@link SlidingWindowRateLimiter} <b>严格对齐</b>——窗口固定 1 秒、
 * 上限为 {@code config.rate} 次/秒、{@code capacity} 不参与。切换实现不应改变限流行为，
 * 只改变计数范围（本副本 → 全集群）。</p>
 *
 * <p>用 Redis sorted set 记录窗口内每次放行的时间戳，判定与记录在<b>同一段 Lua</b> 中完成，
 * 因此不会有「本副本加锁、跨副本超发」的问题：多个副本并发调用同一脚本时，
 * {@code ZCARD} 判定与 {@code ZADD} 记录之间不会插入其它副本的写入。</p>
 *
 * <p>三条边界语义：</p>
 * <ul>
 *   <li><b>fail-open</b>：Redis 不可用（连接失败 / 超时）时<b>放行</b>并告警。限流的目的是
 *       保护下游，而 Redis 故障时全站拒绝比放宽限流更糟——这与配额侧 {@code degradeToLocal}
 *       以及调度任务锁的取向一致。</li>
 *   <li><b>有界阻塞</b>：{@link RateLimiter} 是同步接口，故此处用
 *       {@code blockFirst(timeout)}。调用链已在 {@code Schedulers.boundedElastic()} 上
 *       （见 {@code ServiceRequestHandler} 的 {@code selectInstance} 调用），不会卡住
 *       Netty EventLoop；超时默认 50ms，避免 Redis 慢时拖长请求。</li>
 *   <li><b>桶自动过期</b>：每次脚本执行都刷新 key 的 TTL（2 个窗口长度），无需清理任务。</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 3.2.5
 */
@Slf4j
public class RedisSlidingWindowRateLimiter implements RateLimiter {

    /** 与本地实现一致的窗口长度。 */
    private static final long WINDOW_MS = 1000L;

    /**
     * 限流判定 + 记录（原子）。
     *
     * <p>KEYS[1]=限流 key；ARGV[1]=当前时间戳(ms)；ARGV[2]=窗口长度(ms)；ARGV[3]=上限；
     * ARGV[4]=本次请求的唯一成员；ARGV[5]=key 存活时间(ms)。返回 1 放行 / 0 拒绝。</p>
     *
     * <p>注意 {@code ZREMRANGEBYSCORE} 先剔除窗口外的旧时间戳，因此这是真正的滑动窗口，
     * 而不是固定窗口（固定窗口在两个窗口交界处可放行接近 2 倍）。</p>
     */
    private static final String ACQUIRE_LUA = String.join("\n",
            "local now = tonumber(ARGV[1])",
            "local window = tonumber(ARGV[2])",
            "local limit = tonumber(ARGV[3])",
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window)",
            "local count = redis.call('ZCARD', KEYS[1])",
            "if count < limit then",
            "  redis.call('ZADD', KEYS[1], now, ARGV[4])",
            "  redis.call('PEXPIRE', KEYS[1], ARGV[5])",
            "  return 1",
            "end",
            "return 0");

    private static final RedisScript<Long> ACQUIRE = RedisScript.of(ACQUIRE_LUA, Long.class);

    private final RateLimitConfig config;
    private final String redisKey;
    private final ReactiveRedisTemplate<String, String> template;
    private final Duration timeout;

    @Autowired(required = false)
    private MetricsCollector metricsCollector;

    public RedisSlidingWindowRateLimiter(final RateLimitConfig config,
                                         final String redisKey,
                                         final ReactiveRedisTemplate<String, String> template,
                                         final Duration timeout) {
        this.config = config;
        this.redisKey = redisKey;
        this.template = template;
        this.timeout = timeout;
    }

    @Override
    public boolean tryAcquire(final RateLimitContext context) {
        final boolean allowed = evaluate();
        recordRateLimitMetrics(context, allowed);
        return allowed;
    }

    /**
     * 执行限流判定。Redis 异常时按 fail-open 放行——不在异常路径上抛给调用方。
     */
    private boolean evaluate() {
        if (template == null) {
            return true;
        }
        try {
            final long now = System.currentTimeMillis();
            final Flux<Long> result = template.execute(ACQUIRE, List.of(redisKey), List.of(
                    String.valueOf(now),
                    String.valueOf(WINDOW_MS),
                    String.valueOf(config.getRate()),
                    now + "-" + ThreadLocalRandom.current().nextLong(Long.MAX_VALUE),
                    String.valueOf(WINDOW_MS * 2)));
            final Long allowed = result.blockFirst(timeout);
            // Redis 无返回（如脚本被中断）时按放行处理，与 fail-open 一致
            return allowed == null || allowed == 1L;
        } catch (Exception e) {
            log.warn("分布式限流 Redis 调用失败，按 fail-open 放行: key={}, error={}",
                    redisKey, e.getMessage());
            return true;
        }
    }

    @Override
    public RateLimitConfig getConfig() {
        return config;
    }

    private void recordRateLimitMetrics(final RateLimitContext context, final boolean allowed) {
        if (metricsCollector == null) {
            return;
        }
        try {
            final String serviceName = context != null && context.getServiceType() != null
                    ? context.getServiceType().name().toLowerCase() : "unknown";
            metricsCollector.recordRateLimit(serviceName, "redis_sliding_window", allowed);
        } catch (Exception e) {
            // 指标记录异常不影响业务判定
        }
    }
}
