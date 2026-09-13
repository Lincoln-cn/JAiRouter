package org.unreal.modelrouter.auth.security.quota;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 配额计数后端指标（v3.1 PR-3）。
 *
 * <p>暴露三类指标（沿用仓库既有的 Micrometer 计数器 / 计时器风格）：</p>
 * <ul>
 *   <li>{@code jairouter.quota.counter.degraded}（tag {@code reason}）：降级次数，
 *       含 Redis 不可用 / 超时 / 未装配客户端等；</li>
 *   <li>{@code jairouter.quota.counter.redis.enabled}：当前计数后端是否为 Redis
 *       （1 = Redis 权威计数，0 = 本地计数），用于确认开关是否生效；</li>
 *   <li>{@code jairouter.quota.counter.redis.commands} 与
 *       {@code jairouter.quota.counter.redis.command.duration}（tag {@code outcome}）：
 *       Redis 命令次数与耗时，用于定位“变慢 → 超时 → 降级”的因果链。</li>
 * </ul>
 *
 * <p>指标记录本身绝不抛出：测试与无监控环境下可通过 {@link #noop()} 获取空实现。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@Slf4j
@Component
public class QuotaCounterMetrics {

    /** 降级计数器名 */
    private static final String DEGRADED_METER = "jairouter.quota.counter.degraded";

    /** Redis 命令计数器名 */
    private static final String COMMAND_METER = "jairouter.quota.counter.redis.commands";

    /** Redis 命令耗时计时器名 */
    private static final String COMMAND_DURATION_METER = "jairouter.quota.counter.redis.command.duration";

    /** 空实现单例 */
    private static final QuotaCounterMetrics NOOP = new QuotaCounterMetrics();

    /** 注册表，{@code null} 表示空实现 */
    private final MeterRegistry meterRegistry;

    /** 当前是否为 Redis 后端（1/0） */
    private final AtomicLong redisBackend = new AtomicLong(0L);

    /** 按原因缓存的降级计数器 */
    private final Map<String, Counter> degradations = new ConcurrentHashMap<>();

    /** Redis 命令成功计数器 */
    private final Counter commandSuccess;

    /** Redis 命令失败计数器 */
    private final Counter commandFailure;

    /** Redis 命令成功耗时 */
    private final Timer commandSuccessDuration;

    /** Redis 命令失败耗时 */
    private final Timer commandFailureDuration;

    /**
     * Spring 主构造器。
     *
     * @param meterRegistry 指标注册表
     */
    @Autowired
    public QuotaCounterMetrics(final MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.commandSuccess = Counter.builder(COMMAND_METER)
            .description("配额 Redis 计数命令成功次数")
            .tag("outcome", "success")
            .register(meterRegistry);
        this.commandFailure = Counter.builder(COMMAND_METER)
            .description("配额 Redis 计数命令失败次数")
            .tag("outcome", "failure")
            .register(meterRegistry);
        this.commandSuccessDuration = Timer.builder(COMMAND_DURATION_METER)
            .description("配额 Redis 计数命令耗时")
            .tag("outcome", "success")
            .register(meterRegistry);
        this.commandFailureDuration = Timer.builder(COMMAND_DURATION_METER)
            .description("配额 Redis 计数命令耗时")
            .tag("outcome", "failure")
            .register(meterRegistry);
        Gauge.builder("jairouter.quota.counter.redis.enabled", redisBackend, AtomicLong::doubleValue)
            .description("配额计数是否使用 Redis（1=是，0=本地）")
            .register(meterRegistry);
    }

    /**
     * 空实现构造器（指标字段全部为 {@code null}，各方法直接跳过）。
     */
    private QuotaCounterMetrics() {
        this.meterRegistry = null;
        this.commandSuccess = null;
        this.commandFailure = null;
        this.commandSuccessDuration = null;
        this.commandFailureDuration = null;
    }

    /**
     * 获取空实现（不注册任何指标）。
     *
     * @return 空实现
     */
    public static QuotaCounterMetrics noop() {
        return NOOP;
    }

    /**
     * 记录当前计数后端类型（启动时与切换时调用）。
     *
     * @param redis true = Redis 权威计数
     */
    public void recordBackend(final boolean redis) {
        redisBackend.set(redis ? 1L : 0L);
    }

    /**
     * 记录一次降级。
     *
     * @param reason 降级原因（见 {@link QuotaLedgerService} 的 {@code REASON_REDIS_*} 常量）
     */
    public void recordDegradation(final String reason) {
        if (meterRegistry == null) {
            return;
        }
        degradations.computeIfAbsent(reason == null ? "unknown" : reason, key -> Counter.builder(DEGRADED_METER)
            .description("配额计数后端降级次数")
            .tag("reason", key)
            .register(meterRegistry))
            .increment();
        log.debug("配额计数后端降级计数 +1: reason={}", reason);
    }

    /**
     * 记录一次 Redis 命令的耗时。
     *
     * @param duration 耗时，可为 {@code null}（忽略）
     * @param success  是否成功
     */
    public void recordRedisCommand(final Duration duration, final boolean success) {
        if (duration == null) {
            return;
        }
        if (success) {
            if (commandSuccess != null) {
                commandSuccess.increment();
                commandSuccessDuration.record(duration);
            }
            return;
        }
        if (commandFailure != null) {
            commandFailure.increment();
            commandFailureDuration.record(duration);
        }
    }

    /**
     * 读取某个原因的累计降级次数。
     *
     * @param reason 降级原因
     * @return 次数，未记录过时返回 0
     */
    public double degradationCount(final String reason) {
        final Counter counter = degradations.get(reason == null ? "unknown" : reason);
        return counter == null ? 0.0 : counter.count();
    }
}
