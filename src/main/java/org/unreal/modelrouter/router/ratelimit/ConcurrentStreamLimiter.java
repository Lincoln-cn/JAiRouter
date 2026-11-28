package org.unreal.modelrouter.router.ratelimit;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
 * <p><b>计数范围（{@code jairouter.ratelimit.concurrent-scope}）</b>：默认 {@code local}，
 * 名额按副本各算一份。这与要保护的资源口径一致 —— 出站连接池是每个副本各自的 reactor-netty
 * 池，本地计数正好守住自己那一份，且不需要为每次请求多付一次 Redis 往返。多副本部署若希望
 * 「整个集群总共只允许 N 条在途」，把它设为 {@code cluster}：计数改在 Redis 里做（单条 Lua
 * 脚本完成自增 + 首次设置 TTL + 超限回滚），名额跨副本共享。cluster 模式下 Redis 不可用时
 * 自动降级为本地计数并打 warn —— 宁可临时放宽限制，也不要让限流组件成为单点。</p>
 *
 * @since 本次审计（issue #1246）
 */
@Component
public class ConcurrentStreamLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentStreamLimiter.class);

    /** 跨副本计数的键前缀；与其它组件的 Redis 键空间隔离。 */
    static final String KEY_PREFIX = "jairouter:ratelimit:concurrent:";

    /** 计数范围取值：按副本各算一份。 */
    public static final String SCOPE_LOCAL = "local";

    /** 计数范围取值：跨副本共享一份。 */
    public static final String SCOPE_CLUSTER = "cluster";

    /**
     * 申请名额的脚本：自增 → 首次写入时设置过期时间 → 超限则回滚并返回 -1。
     *
     * <p>过期时间是必须的：副本崩溃或进程被 kill 时不会执行归还，计数若永不过期就会永久虚高，
     * 最终把该主体彻底锁死。TTL 取「流式响应超时 + 余量」，覆盖单条流可能存活的最长时间。</p>
     */
    static final String ACQUIRE_SCRIPT = String.join("\n",
            "local current = redis.call('INCR', KEYS[1])",
            "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end",
            "if current > tonumber(ARGV[1]) then",
            "  redis.call('DECR', KEYS[1])",
            "  return -1",
            "end",
            "return current");

    /** 归还名额的脚本：自减，减成负数说明有重复归还，就地归零。 */
    static final String RELEASE_SCRIPT = String.join("\n",
            "local current = redis.call('DECR', KEYS[1])",
            "if current < 0 then redis.call('SET', KEYS[1], 0) current = 0 end",
            "return current");

    private static final RedisScript<Long> ACQUIRE = RedisScript.of(ACQUIRE_SCRIPT, Long.class);

    private static final RedisScript<Long> RELEASE = RedisScript.of(RELEASE_SCRIPT, Long.class);

    /** 单主体允许的同时进行中请求数；{@code <= 0} 表示不限制。 */
    private final int maxConcurrentPerKey;

    private final ConcurrentHashMap<String, AtomicInteger> activeByKey = new ConcurrentHashMap<>();

    private final AtomicLong rejectedTotal = new AtomicLong();

    /** 最近一次告警的毫秒时间戳，用来把 warn 限流到每个主体一分钟一条。 */
    private final ConcurrentHashMap<String, Long> lastWarnAt = new ConcurrentHashMap<>();

    /** 告警抑制窗口：同一主体一分钟内最多一条 warn。 */
    private static final long WARN_INTERVAL_MS = 60_000L;

    /** 计数范围：{@code local}（默认）或 {@code cluster}。 */
    @Value("${jairouter.ratelimit.concurrent-scope:local}")
    private String concurrentScope = SCOPE_LOCAL;

    /** cluster 模式下名额键的存活时间（秒），需覆盖单条流的最长存活时间。 */
    @Value("${jairouter.ratelimit.concurrent-key-ttl-seconds:700}")
    private long keyTtlSeconds = 700L;

    private ObjectProvider<ReactiveRedisTemplate<String, String>> reactiveRedisTemplateProvider;

    public ConcurrentStreamLimiter(
            @Value("${jairouter.ratelimit.max-concurrent-per-key:100}") final int maxConcurrentPerKey) {
        this.maxConcurrentPerKey = maxConcurrentPerKey;
    }

    /**
     * 注入可选的 Redis 模板；容器里没有 Redis 时不注入，cluster 模式会自动退回本地计数。
     *
     * <p>用 {@link ObjectProvider} 而不是直接注入，是为了让「没配 Redis」的部署照常启动
     * （与 {@code RateLimitManager} 的可选 Redis 注入口径一致）。</p>
     */
    @Autowired(required = false)
    public void setReactiveRedisTemplateProvider(
            final ObjectProvider<ReactiveRedisTemplate<String, String>> reactiveRedisTemplateProvider) {
        this.reactiveRedisTemplateProvider = reactiveRedisTemplateProvider;
    }

    /**
     * 启动时检查 cluster 模式的依赖是否齐备。
     *
     * <p>{@code ReactiveRedisTemplate} 这个 bean 由 {@code jairouter.security.cache.redis.enabled=true}
     * 才会创建，因此只把 {@code concurrent-scope} 设成 cluster 并不会真的跨副本计数 —— 它会静默
     * 退回本地计数。这种情况必须在启动日志里说清楚，否则运维会以为自己已经开了集群级限制。</p>
     */
    @PostConstruct
    void warnIfClusterScopeWithoutRedis() {
        if (!SCOPE_CLUSTER.equalsIgnoreCase(concurrentScope)) {
            return;
        }
        if (reactiveRedisTemplateProvider != null && reactiveRedisTemplateProvider.getIfAvailable() != null) {
            LOGGER.info("并发名额按集群共享：jairouter.ratelimit.concurrent-scope=cluster，上限 {}，名额键 TTL {} 秒",
                    maxConcurrentPerKey, keyTtlSeconds);
            return;
        }
        LOGGER.error("jairouter.ratelimit.concurrent-scope=cluster 需要 Redis，但容器里没有 ReactiveRedisTemplate，"
                + "并发名额已退回本地计数（按副本各算一份）。请同时启用 jairouter.security.cache.redis.enabled"
                + " 并配置 Redis 连接（REDIS_HOST / REDIS_PORT / REDIS_PASSWORD）");
    }

    /**
     * 申请一个并发名额（同步、本地计数）。
     *
     * <p>返回的 {@link Lease} 必须在不晚于响应终止时关闭；关闭是幂等的，重复调用不会把计数减两次。
     * 未限额（配置为 0，或调用主体为空）时返回的是一个不占名额、关闭无副作用的租约。</p>
     *
     * <p>本方法始终使用进程内计数，不读 {@code concurrent-scope}；响应式链路请改用
     * {@link #acquireMono(String)}。</p>
     *
     * @param callerId 调用主体标识（API Key ID 或 {@code jwt:<用户名>}），可为 {@code null}
     * @return 租约；{@link Lease#granted()} 为 {@code false} 表示已超限
     */
    public Lease acquire(final String callerId) {
        if (maxConcurrentPerKey <= 0 || callerId == null || callerId.isBlank()) {
            return Lease.unlimited();
        }
        return acquireLocal(callerId);
    }

    /**
     * 申请一个并发名额（响应式），按 {@code jairouter.ratelimit.concurrent-scope} 选择计数位置。
     *
     * <p>cluster 模式下 Redis 报错或未配置时会降级为本地计数并打 warn：限流器本身不能成为新的
     * 单点，宁可临时放宽限制也不能让请求全被拒。</p>
     *
     * @param callerId 调用主体标识，可为 {@code null}
     * @return 单条租约；永不返回空
     */
    public Mono<Lease> acquireMono(final String callerId) {
        if (maxConcurrentPerKey <= 0 || callerId == null || callerId.isBlank()) {
            return Mono.just(Lease.unlimited());
        }
        if (!SCOPE_CLUSTER.equalsIgnoreCase(concurrentScope)) {
            return Mono.just(acquireLocal(callerId));
        }
        final ReactiveRedisTemplate<String, String> template =
                reactiveRedisTemplateProvider == null ? null : reactiveRedisTemplateProvider.getIfAvailable();
        if (template == null) {
            return Mono.just(acquireLocal(callerId));
        }

        final String redisKey = KEY_PREFIX + callerId;
        return template.execute(ACQUIRE, List.of(redisKey),
                        List.of(String.valueOf(maxConcurrentPerKey), String.valueOf(keyTtlSeconds)))
                .next()
                .map(current -> toLease(callerId, current, template, redisKey))
                // 必须延迟求值：defaultIfEmpty 的实参在方法调用时就执行，会让每次申请都白占一个本地名额
                .switchIfEmpty(Mono.defer(() -> Mono.just(acquireLocal(callerId))))
                .onErrorResume(error -> {
                    LOGGER.warn("Redis 并发计数不可用，本次降级为本地计数（{}）：{}",
                            callerId, error.toString());
                    return Mono.just(acquireLocal(callerId));
                });
    }

    private Lease toLease(final String callerId, final Long current,
                          final ReactiveRedisTemplate<String, String> template, final String redisKey) {
        if (current == null || current < 0) {
            final long rejected = rejectedTotal.incrementAndGet();
            warnThrottled(callerId, maxConcurrentPerKey, rejected);
            return Lease.rejected(maxConcurrentPerKey);
        }
        return Lease.distributed(() -> releaseRemote(template, redisKey));
    }

    /** 归还跨副本名额：fire-and-forget 到 boundedElastic，失败只记日志（不能阻塞响应终止路径）。 */
    private void releaseRemote(final ReactiveRedisTemplate<String, String> template, final String redisKey) {
        try {
            template.execute(RELEASE, List.of(redisKey), List.of())
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            value -> {
                            },
                            error -> LOGGER.warn("归还跨副本并发名额失败（{}）：{}，该键将在 TTL 后自然过期",
                                    redisKey, error.toString()));
        } catch (RuntimeException error) {
            LOGGER.warn("归还跨副本并发名额时抛出异常（{}）：{}", redisKey, error.toString());
        }
    }

    private Lease acquireLocal(final String callerId) {
        final AtomicInteger counter = activeByKey.computeIfAbsent(callerId, key -> new AtomicInteger());
        final int current = counter.incrementAndGet();
        if (current <= maxConcurrentPerKey) {
            return Lease.local(counter);
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

    /** 当前计数范围（{@code local} / {@code cluster}）。供健康检查与测试读取。 */
    public String concurrentScope() {
        return concurrentScope;
    }

    /** 当前该主体进行中的请求数（只看本地计数；cluster 模式下其它副本占用的名额不计入）。 */
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
            LOGGER.warn("调用主体 {} 的并发请求数已达上限 {}（当前 {}，范围 {}），本次请求被拒绝；"
                            + "累计拒绝 {} 次。如需放行更多并发，调高 jairouter.ratelimit.max-concurrent-per-key，"
                            + "设为 0 表示不限制",
                    callerId, maxConcurrentPerKey, active, concurrentScope, rejected);
        }
    }

    /**
     * 一个并发名额。{@link #close()} 幂等。
     */
    public static final class Lease {

        private static final Lease UNLIMITED = new Lease(null, null, true);
        private static final Lease REJECTED = new Lease(null, null, false);

        private final AtomicInteger counter;
        private final Runnable remoteRelease;
        private final boolean granted;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private Lease(final AtomicInteger counter, final Runnable remoteRelease, final boolean granted) {
            this.counter = counter;
            this.remoteRelease = remoteRelease;
            this.granted = granted;
        }

        /** 不占名额、关闭无副作用的租约（不限额或调用主体为空）。 */
        public static Lease unlimited() {
            return UNLIMITED;
        }

        static Lease rejected(final int ignoredActive) {
            return REJECTED;
        }

        static Lease local(final AtomicInteger counter) {
            return new Lease(counter, null, true);
        }

        static Lease distributed(final Runnable remoteRelease) {
            return new Lease(null, remoteRelease, true);
        }

        /** 是否拿到了名额。 */
        public boolean granted() {
            return granted;
        }

        /** 归还名额；重复调用无副作用，未拿到名额时也不做任何事。 */
        public void close() {
            if (!granted || !released.compareAndSet(false, true)) {
                return;
            }
            if (remoteRelease != null) {
                remoteRelease.run();
                return;
            }
            if (counter == null) {
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
