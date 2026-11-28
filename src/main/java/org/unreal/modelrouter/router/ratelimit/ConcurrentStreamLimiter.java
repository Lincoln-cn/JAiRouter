package org.unreal.modelrouter.router.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 按调用主体限制**同时进行中**的流式请求数。
 *
 * <p>issue #1246：每分钟请求数配额管的是「打了多少次」，管不住「同时挂着多少条」。一个租户可以
 * 用极低的目标 QPS 起几百条长连接流式请求，把 {@code WebClientPool} 的连接池
 * （{@code jairouter.webclient.max-connections}，默认 500）占满；连接池是全站共享的，于是其它
 * 租户的请求要排队到 {@code pending-acquire-timeout} 之后才拿到连接，表现为大范围 504 / 502。
 * 网关侧既没有日志也没有指标，运维只能看到「突然变慢」。</p>
 *
 * <p>本类只解决「单主体不能独占出站连接池」这一件事，不复刻限流体系的窗口与配额语义：
 * 它不做时间窗口统计，只在请求进入时占一个名额、在响应真正终止时归还。</p>
 *
 * <p><b>默认值为 100</b>（{@code jairouter.ratelimit.max-concurrent-per-key}）。选这个值是因为
 * 它远低于连接池容量、又不至于拦住正常的批量调用；需要更高并发或想完全关掉时把它设为 0。</p>
 *
 * @since 本次审计（issue #1246）
 */
@Component
public class ConcurrentStreamLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentStreamLimiter.class);

    /** 单主体允许的同时进行中请求数；{@code <= 0} 表示不限制。 */
    private final int maxConcurrentPerKey;

    private final ConcurrentHashMap<String, AtomicInteger> activeByKey = new ConcurrentHashMap<>();

    private final AtomicLong rejectedTotal = new AtomicLong();

    /** 最近一次告警的毫秒时间戳，用来把 warn 限流到每个主体一分钟一条。 */
    private final ConcurrentHashMap<String, Long> lastWarnAt = new ConcurrentHashMap<>();

    /** 告警抑制窗口：同一主体一分钟内最多一条 warn。 */
    private static final long WARN_INTERVAL_MS = 60_000L;

    public ConcurrentStreamLimiter(
            @Value("${jairouter.ratelimit.max-concurrent-per-key:100}") final int maxConcurrentPerKey) {
        this.maxConcurrentPerKey = maxConcurrentPerKey;
    }

    /**
     * 申请一个并发名额。
     *
     * <p>返回的 {@link Lease} 必须在不晚于响应终止时关闭；关闭是幂等的，重复调用不会把计数减两次。
     * 未限额（配置为 0，或调用主体为空）时返回的是一个不占名额、关闭无副作用的租约。</p>
     *
     * @param callerId 调用主体标识（API Key ID 或 {@code jwt:<用户名>}），可为 {@code null}
     * @return 租约；{@link Lease#granted()} 为 {@code false} 表示已超限
     */
    public Lease acquire(final String callerId) {
        if (maxConcurrentPerKey <= 0 || callerId == null || callerId.isBlank()) {
            return Lease.unlimited();
        }

        final AtomicInteger counter = activeByKey.computeIfAbsent(callerId, key -> new AtomicInteger());
        final int current = counter.incrementAndGet();
        if (current <= maxConcurrentPerKey) {
            return new Lease(counter, true);
        }

        counter.decrementAndGet();
        final long rejected = rejectedTotal.incrementAndGet();
        warnThrottled(callerId, current - 1, rejected);
        return Lease.rejected(current - 1);
    }

    /** 配置里的上限；{@code <= 0} 表示不限制。供健康检查与测试读取。 */
    public int maxConcurrentPerKey() {
        return maxConcurrentPerKey;
    }

    /** 当前该主体进行中的请求数（未限额或主体为空时为 0）。 */
    public int activeCount(final String callerId) {
        if (callerId == null) {
            return 0;
        }
        final AtomicInteger counter = activeByKey.get(callerId);
        return counter == null ? 0 : Math.max(0, counter.get());
    }

    /** 进程启动以来被本限制器拒绝的次数，用来判断阈值是不是设得太紧。 */
    public long rejectedTotal() {
        return rejectedTotal.get();
    }

    private void warnThrottled(final String callerId, final int active, final long rejected) {
        final long now = System.currentTimeMillis();
        final boolean shouldLog = lastWarnAt.compute(callerId, (key, previous) -> {
            if (previous != null && now - previous < WARN_INTERVAL_MS) {
                return previous;
            }
            return now;
        }) == now;
        if (shouldLog) {
            LOGGER.warn("调用主体 {} 的并发请求数已达上限 {}（当前 {}），本次请求被拒绝；"
                    + "累计拒绝 {} 次。如需放行更多并发，调高 jairouter.ratelimit.max-concurrent-per-key，"
                    + "设为 0 表示不限制",
                    callerId, maxConcurrentPerKey, active, rejected);
        }
    }

    /**
     * 一个并发名额。{@link #close()} 幂等。
     */
    public static final class Lease {

        private static final Lease UNLIMITED = new Lease(null, true);
        private static final Lease REJECTED = new Lease(null, false);

        private final AtomicInteger counter;
        private final boolean granted;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private Lease(final AtomicInteger counter, final boolean granted) {
            this.counter = counter;
            this.granted = granted;
        }

        static Lease unlimited() {
            return UNLIMITED;
        }

        static Lease rejected(final int ignoredActive) {
            return REJECTED;
        }

        /** 是否拿到了名额。 */
        public boolean granted() {
            return granted;
        }

        /** 归还名额；重复调用无副作用，未拿到名额时也不做任何事。 */
        public void close() {
            if (counter == null || !released.compareAndSet(false, true)) {
                return;
            }
            if (counter.decrementAndGet() < 0) {
                // 计数被减到负数说明有路径关闭了不止一次；修正回 0 并留下痕迹，便于定位泄漏。
                counter.set(0);
                LOGGER.error("并发计数被减到负数并已归零：这说明有租约被重复关闭，请检查响应终止路径");
            }
        }
    }
}
