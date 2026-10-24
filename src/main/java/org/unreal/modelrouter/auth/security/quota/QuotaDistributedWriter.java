package org.unreal.modelrouter.auth.security.quota;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 分布式计数后端的写路径（补写合并、阻塞执行、超时归类）。
 *
 * <p>从 {@code QuotaLedgerService} 抽出（issue #604 批次 B）。抽出的三类逻辑都与「怎么把增量
 * 可靠地写到 Redis」有关，与配额窗口、限额判定、快照落库无关：</p>
 * <ul>
 *   <li>{@link #publish} —— 把降级期间累积的待补写增量与本次增量合并后一次写出；</li>
 *   <li>{@link #execute} —— 在可阻塞线程上以 {@code block(timeout)} 执行一次 Redis 命令并统计耗时；</li>
 *   <li>{@link #isTimeout} / {@link #classify} —— 超时属「结果未知」，与「明确失败」的处置不同。</li>
 * </ul>
 *
 * <p><b>线程契约不变</b>（issue #105）：{@link #execute} 只允许在可阻塞线程上调用，遇到 Reactor
 * 非阻塞线程时抛带线程名的 {@link IllegalStateException}，由调用方归类为降级原因。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.1
 */
@Slf4j
final class QuotaDistributedWriter {

    private final QuotaCounterBackend backend;
    private final QuotaProperties properties;
    private final QuotaCounterMetrics metrics;
    private final LocalCounterBackend localBackend;
    private final QuotaDegradationState degradation;
    private final String backendName;

    /**
     * 构造写路径。
     *
     * @param backend      分布式计数后端（非 {@code null}；调用方只在分布式模式装配本对象）
     * @param properties   账本配置（超时、fail-open）
     * @param metrics      指标收集器
     * @param localBackend 本地计数后端（待补写增量存放处）
     * @param degradation  降级状态
     * @param backendName  权威后端名称（用于异常信息与日志）
     */
    QuotaDistributedWriter(final QuotaCounterBackend backend,
                           final QuotaProperties properties,
                           final QuotaCounterMetrics metrics,
                           final LocalCounterBackend localBackend,
                           final QuotaDegradationState degradation,
                           final String backendName) {
        this.backend = backend;
        this.properties = properties;
        this.metrics = metrics;
        this.localBackend = localBackend;
        this.degradation = degradation;
        this.backendName = backendName;
    }

    /**
     * 把增量（含降级期间累积的待补写增量）发布到分布式后端。
     *
     * <p>成功即返回；失败时向上抛异常由调用方按 fail-open 降级，并恢复此前取出的待补写增量：
     * 明确失败（连接失败 / 脚本错误）时连同本次增量一起放回待补写队列；超时属「结果未知」，
     * 整批放弃以避免重复计数。</p>
     *
     * @param key             计数键
     * @param requests        本次请求数增量
     * @param tokens          本次 token 增量
     * @param registerPending 失败时是否把本次增量登记为待补写（fail-closed 拒绝的请求不计账时为 false）
     */
    void publish(final QuotaCounterKey key,
                 final long requests,
                 final long tokens,
                 final boolean registerPending) {
        final long[] pending = localBackend.takePending(key);
        final long requestDelta = requests + (pending == null ? 0L : pending[0]);
        final long tokenDelta = tokens + (pending == null ? 0L : pending[1]);
        if (requestDelta == 0L && tokenDelta == 0L) {
            return;
        }
        try {
            execute(() -> backend.increment(key, requestDelta, tokenDelta));
        } catch (RuntimeException e) {
            if (isTimeout(e)) {
                log.warn("Redis 计数命令超时（结果未知），放弃补写该增量以避免重复计数: window={}, windowStart={},"
                    + " requests={}, tokens={}", key.window(), key.windowStart(), requestDelta, tokenDelta);
            } else {
                final long restoreRequests = pending == null ? 0L : pending[0];
                final long restoreTokens = pending == null ? 0L : pending[1];
                localBackend.addPending(key,
                    restoreRequests + (registerPending ? requests : 0L),
                    restoreTokens + (registerPending ? tokens : 0L));
            }
            throw e;
        }
    }

    /**
     * 阻塞执行一次 Redis 命令并统计耗时（超时由 {@code distributed.timeout} 控制）。
     *
     * <p>本方法只能运行在可阻塞线程上：Reactor 会在非阻塞线程上直接拒绝 {@code Mono.block()}，
     * 其异常信息只说明「block 不被支持」，不含任何账本上下文（issue #105 中表现为 Redis 冲正
     * 静默丢失）。因此这里先做显式守卫，抛出可定位的异常——异常信息带上当前线程名与后端名，
     * 调用方的 {@link #classify} 会把它归类为
     * {@link QuotaLedgerService#REASON_REDIS_UNAVAILABLE} 并打出降级告警，
     * 使「误在事件循环上调用」与「Redis 真的不可用」至少在日志里可区分。</p>
     *
     * @param command 命令工厂
     * @param <T>     结果类型
     * @return 命令结果
     * @throws IllegalStateException 当前线程为 Reactor 非阻塞线程（事件循环无法阻塞等待 Redis）
     */
    <T> T execute(final Supplier<Mono<T>> command) {
        final long start = System.nanoTime();
        boolean success = false;
        try {
            if (Schedulers.isInNonBlockingThread()) {
                throw new IllegalStateException("配额账本 Redis 命令不能在 Reactor 非阻塞线程上同步阻塞执行"
                    + "（否则 block() 会被拒绝且冲正量静默丢失）: thread=" + Thread.currentThread().getName()
                    + ", backend=" + backendName);
            }
            final T value = command.get().block(properties.distributedTimeout());
            success = true;
            return value;
        } finally {
            metrics.recordRedisCommand(Duration.ofNanos(System.nanoTime() - start), success);
        }
    }

    /**
     * 把 Redis 权威读数与本节点尚未补写的增量合并（避免降级期间低估用量）。
     *
     * @param key    计数键
     * @param remote Redis 读到的 {@code [requests, tokens]}
     * @return 合并后的 {@code [requests, tokens]}
     */
    long[] withPending(final QuotaCounterKey key, final long[] remote) {
        final long[] pending = localBackend.peekPending(key);
        if (pending == null) {
            return remote;
        }
        return new long[]{remote[0] + pending[0], remote[1] + pending[1]};
    }

    /**
     * 判断异常是否为超时（Reactor {@code block(timeout)} 把超时包装成
     * {@link IllegalStateException}，真实超时在因果链中）。
     *
     * @param error 异常
     * @return 因果链中存在 {@link TimeoutException} 时返回 {@code true}
     */
    static boolean isTimeout(final Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 把异常归类为降级原因。
     *
     * @param error 异常
     * @return {@link QuotaLedgerService#REASON_REDIS_TIMEOUT} 或
     *         {@link QuotaLedgerService#REASON_REDIS_UNAVAILABLE}
     */
    static String classify(final Throwable error) {
        return isTimeout(error)
            ? QuotaLedgerService.REASON_REDIS_TIMEOUT
            : QuotaLedgerService.REASON_REDIS_UNAVAILABLE;
    }

    /**
     * 降级状态（供账本服务查询 / 在其它路径上复用）。
     *
     * @return 降级状态
     */
    QuotaDegradationState degradation() {
        return degradation;
    }
}
