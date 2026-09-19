package org.unreal.modelrouter.auth.security.quota;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 本地（单进程内存）计数后端（v3.1 PR-3）。
 *
 * <p>由 PR-1 的 {@code QuotaLedgerService} 内部内存账本原样迁移而来：热路径
 * {@link #increment(QuotaCounterKey, long, long)} 只操作 {@link ConcurrentHashMap} +
 * {@link LongAdder}，不做任何 I/O；行为与 PR-1 完全一致。</p>
 *
 * <p><b>槽位三个计数器</b>（同一槽位内并存，互不干扰）：</p>
 * <ul>
 *   <li>{@code requests} / {@code tokens}：本进程累计计数（分布式模式下同时作为降级兜底视图）；</li>
 *   <li>{@code publishedRequests} / {@code publishedTokens}：最近一次成功落库（JPA 快照）的累计值，
 *       用于计算“未落库增量”，只由快照线程写入；</li>
 *   <li>{@code pendingRequests} / {@code pendingTokens}：降级期间累积、尚未补写到 Redis 的增量
 *       （仅分布式模式使用），写入与取走都持有槽位锁，保证同一 key 的补偿记账不重复。</li>
 * </ul>
 *
 * <p><b>冷启动基线</b>：槽位首次创建时通过 {@link BaselineLoader} 读取数据库快照作为基线，
 * 使重启后用量连续；基线读取异常会原样抛出（由 {@link QuotaLedgerService#reserve(QuotaRequest)}
 * 统一按 fail-open 处理），并且不会留下半个槽位。</p>
 *
 * <p><b>并发</b>：{@code requests} / {@code tokens} 用 {@link LongAdder} 无锁累加；
 * {@code pending*} 与 {@code published*} 的读写路径按上文加锁 / 单写者约束。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
public final class LocalCounterBackend implements QuotaCounterBackend {

    /** 后端名称 */
    public static final String NAME = "local";

    /** 槽位表：key = 维度 + 窗口 + 窗口起点 */
    private final ConcurrentHashMap<QuotaCounterKey, Slot> slots = new ConcurrentHashMap<>();

    /** 槽位首次创建时的基线加载器 */
    private final BaselineLoader baselineLoader;

    /**
     * 构造无基线（全 0）的本地后端。
     */
    public LocalCounterBackend() {
        this(null);
    }

    /**
     * 构造带基线加载器的本地后端。
     *
     * @param baselineLoader 槽位首次创建时的基线加载器，{@code null} 时按全 0 基线
     */
    public LocalCounterBackend(final BaselineLoader baselineLoader) {
        this.baselineLoader = baselineLoader == null ? key -> new long[]{0L, 0L} : baselineLoader;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Mono<long[]> increment(final QuotaCounterKey key, final long requests, final long tokens) {
        return Mono.just(incrementLocal(key, requests, tokens));
    }

    @Override
    public Mono<long[]> incrementIfPresent(final QuotaCounterKey key, final long requests, final long tokens) {
        final long[] totals = incrementLocalIfPresent(key, requests, tokens);
        return totals == null ? Mono.empty() : Mono.just(totals);
    }

    @Override
    public Mono<long[]> incrementWithLimit(final QuotaCounterKey key,
                                           final long requests,
                                           final long tokens,
                                           final long maxRequests,
                                           final long maxTokens) {
        final long[] totals = incrementLocalWithLimit(key, requests, tokens, maxRequests, maxTokens);
        return totals == null ? Mono.empty() : Mono.just(totals);
    }

    /**
     * 限额 CAS：检查「累加后」是否越限，越限不写入并返回 {@code null}。
     *
     * @return 累加后的 {@code [requests, tokens]}；越限时 {@code null}
     */
    public long[] incrementLocalWithLimit(final QuotaCounterKey key,
                                          final long requests,
                                          final long tokens,
                                          final long maxRequests,
                                          final long maxTokens) {
        final Slot slot = slotFor(key);
        synchronized (slot) {
            final long currentRequests = Math.max(0L, slot.requests.sum());
            final long currentTokens = Math.max(0L, slot.tokens.sum());
            final long nextRequests = Math.max(0L, currentRequests + requests);
            final long nextTokens = Math.max(0L, currentTokens + tokens);
            if ((maxRequests > 0L && nextRequests > maxRequests)
                    || (maxTokens > 0L && nextTokens > maxTokens)) {
                return null;
            }
            if (requests != 0L) {
                slot.requests.add(requests);
            }
            if (tokens != 0L) {
                slot.tokens.add(tokens);
            }
            return totals(slot);
        }
    }

    @Override
    public Mono<Optional<long[]>> read(final QuotaCounterKey key) {
        return Mono.just(Optional.ofNullable(totals(key)));
    }

    @Override
    public Mono<Long> reset(final String apiKeyId) {
        return Mono.just((long) resetLocal(apiKeyId));
    }

    @Override
    public Mono<Long> deleteExpired(final QuotaWindow window, final LocalDateTime cutoff) {
        return Mono.just((long) deleteExpiredLocal(window, cutoff));
    }

    /**
     * 同步累加计数（热路径使用，避免 {@link Mono} 包装开销）。
     *
     * @param key      计数键
     * @param requests 请求数增量
     * @param tokens   token 增量
     * @return 累加后的（非负钳制）{@code [requests, tokens]}
     * @throws RuntimeException 槽位首次创建且基线读取失败时
     */
    public long[] incrementLocal(final QuotaCounterKey key, final long requests, final long tokens) {
        final Slot slot = slotFor(key);
        slot.requests.add(requests);
        slot.tokens.add(tokens);
        return totals(slot);
    }

    /**
     * 同步累加已存在槽位的计数（结算语义：不新建槽位）。
     *
     * @param key      计数键
     * @param requests 请求数增量
     * @param tokens   token 增量
     * @return 累加后的 {@code [requests, tokens]}，槽位不存在时返回 {@code null}
     */
    public long[] incrementLocalIfPresent(final QuotaCounterKey key, final long requests, final long tokens) {
        final Slot slot = slots.get(key);
        if (slot == null) {
            return null;
        }
        if (requests != 0L) {
            slot.requests.add(requests);
        }
        if (tokens != 0L) {
            slot.tokens.add(tokens);
        }
        return totals(slot);
    }

    /**
     * 读取槽位累计计数（非负钳制）。
     *
     * @param key 计数键
     * @return {@code [requests, tokens]}，槽位不存在时返回 {@code null}
     */
    public long[] totals(final QuotaCounterKey key) {
        return totals(slots.get(key));
    }

    /**
     * 一次性读取快照落库所需的累计值与未落库增量（同一快照，避免“累计值”与“增量”两次读取之间
     * 的并发增量导致重复落库——与 PR-1 单次读取 {@code sum()} 的语义一致）。
     *
     * @param key 计数键
     * @return {@code [requests, tokens, requestDelta, tokenDelta]}（累计值已非负钳制）；
     *         槽位不存在或增量为 0 时返回 {@code null}
     */
    public long[] snapshotDelta(final QuotaCounterKey key) {
        final Slot slot = slots.get(key);
        if (slot == null) {
            return null;
        }
        final long requests = Math.max(0L, slot.requests.sum());
        final long tokens = Math.max(0L, slot.tokens.sum());
        final long requestDelta = requests - slot.publishedRequests;
        final long tokenDelta = tokens - slot.publishedTokens;
        if (requestDelta == 0L && tokenDelta == 0L) {
            return null;
        }
        return new long[]{requests, tokens, requestDelta, tokenDelta};
    }

    /**
     * 记录增量已落库（快照成功后调用）。
     *
     * @param key      计数键
     * @param requests 已落库的累计请求数
     * @param tokens   已落库的累计 token 数
     * @return 是否命中槽位
     */
    public boolean markPublished(final QuotaCounterKey key, final long requests, final long tokens) {
        final Slot slot = slots.get(key);
        if (slot == null) {
            return false;
        }
        slot.publishedRequests = requests;
        slot.publishedTokens = tokens;
        return true;
    }

    /**
     * 当前全部槽位键快照（用于快照落库与保留期清理遍历）。
     *
     * @return 槽位键集合（副本，弱一致）
     */
    public Set<QuotaCounterKey> keys() {
        return Set.copyOf(slots.keySet());
    }

    /**
     * 删除某个 API Key 的全部本地槽位。
     *
     * @param apiKeyId API Key ID
     * @return 删除的槽位数
     */
    public int resetLocal(final String apiKeyId) {
        if (apiKeyId == null || apiKeyId.isEmpty()) {
            return 0;
        }
        final List<QuotaCounterKey> keys = new ArrayList<>();
        for (final QuotaCounterKey key : slots.keySet()) {
            if (apiKeyId.equals(key.dimension().apiKeyId())) {
                keys.add(key);
            }
        }
        keys.forEach(slots::remove);
        return keys.size();
    }

    /**
     * 删除指定窗口内窗口起点早于 {@code cutoff} 的本地槽位。
     *
     * <p>仍持有未落库增量或待补写 Redis 增量的槽位会保留到下一次快照 / 补写之后，
     * 避免清理造成计数丢失。</p>
     *
     * @param window 窗口类型，不能为 {@code null}
     * @param cutoff 截止时间
     * @return 删除的槽位数
     */
    public int deleteExpiredLocal(final QuotaWindow window, final LocalDateTime cutoff) {
        final List<QuotaCounterKey> expired = new ArrayList<>();
        for (final Map.Entry<QuotaCounterKey, Slot> entry : slots.entrySet()) {
            final QuotaCounterKey key = entry.getKey();
            if (key.window() != window || !key.windowStart().isBefore(cutoff)) {
                continue;
            }
            final Slot slot = entry.getValue();
            if (!isFullyPublished(slot) || hasPending(slot)) {
                continue;
            }
            expired.add(key);
        }
        expired.forEach(slots::remove);
        return expired.size();
    }

    /**
     * 记录“降级期间未补写到 Redis”的增量（仅分布式模式使用）。
     *
     * @param key      计数键
     * @param requests 请求数增量
     * @param tokens   token 增量
     */
    public void addPending(final QuotaCounterKey key, final long requests, final long tokens) {
        if (requests == 0L && tokens == 0L) {
            return;
        }
        final Slot slot = slotFor(key);
        synchronized (slot) {
            slot.pendingRequests.add(requests);
            slot.pendingTokens.add(tokens);
        }
    }

    /**
     * 取走（并清零）待补写 Redis 的增量，用于下一次 Redis 写入时合并补偿。
     *
     * <p>取走与写入之间若进程崩溃会丢失该补偿增量（与 PR-1 快照“落库成功再标记”的
     * 崩溃窗口同类风险）；写入失败时应调用 {@link #addPending(QuotaCounterKey, long, long)}
     * 放回。</p>
     *
     * @param key 计数键
     * @return 待补写增量；无未补写增量或槽位不存在时返回 {@code null}
     */
    public long[] takePending(final QuotaCounterKey key) {
        final Slot slot = slots.get(key);
        if (slot == null || !hasPending(slot)) {
            return null;
        }
        synchronized (slot) {
            if (!hasPending(slot)) {
                return null;
            }
            final long requests = slot.pendingRequests.sum();
            final long tokens = slot.pendingTokens.sum();
            // 持有槽位锁时无并发写入，reset 与写入串行
            slot.pendingRequests.reset();
            slot.pendingTokens.reset();
            return new long[]{requests, tokens};
        }
    }

    /**
     * 只读查看待补写 Redis 的增量（不清零），用于读数时把未补写部分计入总量。
     *
     * @param key 计数键
     * @return 待补写增量；无未补写增量或槽位不存在时返回 {@code null}
     */
    public long[] peekPending(final QuotaCounterKey key) {
        final Slot slot = slots.get(key);
        if (slot == null) {
            return null;
        }
        final long requests = slot.pendingRequests.sum();
        final long tokens = slot.pendingTokens.sum();
        if (requests == 0L && tokens == 0L) {
            return null;
        }
        return new long[]{requests, tokens};
    }

    /**
     * 清空全部槽位（应用关闭时调用）。
     */
    public void clear() {
        slots.clear();
    }

    /**
     * 获取或创建槽位，首次创建时用基线初始化（基线异常向上抛出，不留下半个槽位）。
     *
     * @param key 计数键
     * @return 槽位
     */
    private Slot slotFor(final QuotaCounterKey key) {
        final Slot existing = slots.get(key);
        if (existing != null) {
            return existing;
        }
        return slots.computeIfAbsent(key, this::createSlot);
    }

    /**
     * 创建槽位。
     *
     * @param key 计数键
     * @return 已用基线初始化的槽位
     */
    private Slot createSlot(final QuotaCounterKey key) {
        final long[] baseline = baselineLoader.load(key);
        final long requests = baseline == null || baseline.length < 2 ? 0L : Math.max(0L, baseline[0]);
        final long tokens = baseline == null || baseline.length < 2 ? 0L : Math.max(0L, baseline[1]);
        final Slot slot = new Slot();
        slot.requests.add(requests);
        slot.tokens.add(tokens);
        slot.publishedRequests = requests;
        slot.publishedTokens = tokens;
        return slot;
    }

    /**
     * 槽位累计计数（非负钳制）。
     *
     * @param slot 槽位，可为 {@code null}
     * @return {@code [requests, tokens]}，槽位为 {@code null} 时返回 {@code null}
     */
    private static long[] totals(final Slot slot) {
        if (slot == null) {
            return null;
        }
        return new long[]{Math.max(0L, slot.requests.sum()), Math.max(0L, slot.tokens.sum())};
    }

    /**
     * 槽位增量是否已全部落库。
     *
     * @param slot 槽位
     * @return 累计值与已落库值一致时返回 {@code true}
     */
    private static boolean isFullyPublished(final Slot slot) {
        return slot.requests.sum() == slot.publishedRequests && slot.tokens.sum() == slot.publishedTokens;
    }

    /**
     * 槽位是否有待补写 Redis 的增量。
     *
     * @param slot 槽位
     * @return 存在非零待补写增量时返回 {@code true}
     */
    private static boolean hasPending(final Slot slot) {
        return slot.pendingRequests.sum() != 0L || slot.pendingTokens.sum() != 0L;
    }

    /**
     * 计数槽位。
     */
    private static final class Slot {

        /** 本进程累计请求数 */
        private final LongAdder requests = new LongAdder();

        /** 本进程累计 token 数 */
        private final LongAdder tokens = new LongAdder();

        /** 待补写 Redis 的请求数增量 */
        private final LongAdder pendingRequests = new LongAdder();

        /** 待补写 Redis 的 token 增量 */
        private final LongAdder pendingTokens = new LongAdder();

        /** 最近一次已落库的请求数（只由快照线程写入） */
        private long publishedRequests;

        /** 最近一次已落库的 token 数（只由快照线程写入） */
        private long publishedTokens;
    }

    /**
     * 槽位首次创建时的基线加载器（默认全 0）。
     */
    @FunctionalInterface
    public interface BaselineLoader {

        /**
         * 加载基线累计值。
         *
         * @param key 计数键
         * @return {@code [requests, tokens]} 基线；返回 {@code null} 或长度不足时按 0 处理
         */
        long[] load(QuotaCounterKey key);
    }
}
