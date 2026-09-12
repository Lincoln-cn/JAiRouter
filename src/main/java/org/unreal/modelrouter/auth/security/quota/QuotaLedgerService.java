package org.unreal.modelrouter.auth.security.quota;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.persistence.jpa.entity.QuotaLedgerEntity;
import org.unreal.modelrouter.persistence.jpa.repository.QuotaLedgerRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * 配额账本服务（v3.1 PR-1，旁路账本）。
 *
 * <p>设计取舍：</p>
 * <ul>
 *   <li><b>热路径纯内存</b>：{@link #reserve(QuotaRequest)} / {@link #settle(QuotaSettlement)} 只操作
 *       {@link ConcurrentHashMap} + {@link LongAdder}，不做任何数据库写入，避免给请求链路增加延迟。</li>
 *   <li><b>数据库仅作快照</b>：{@link #flush()} 定时（默认 60s）与 {@link #shutdown()}（{@code @PreDestroy}）
 *       时把内存增量落库。落库按“增量累加 + 行不存在则插入绝对值”实现，失败时保留增量待下一轮重试，
 *       不会重复计数。</li>
 *   <li><b>冷启动续算</b>：内存槽位首次被访问时从数据库读取基线（该维度的历史累计值），
 *       使重启后用量连续；读取失败会抛异常，由 {@link #reserve(QuotaRequest)} 捕获并按 fail-open 处理。</li>
 *   <li><b>绝不抛出</b>：{@link #reserve(QuotaRequest)} / {@link #settle(QuotaSettlement)} /
 *       查询 / 清理接口都不会把异常抛给调用方；账本不可用时按
 *       {@code jairouter.quota.fail-open}（默认 true）放行并标记 {@code degraded=true}。</li>
 *   <li><b>单节点语义</b>：仅保证单实例内计数精确，多实例共享额度需 Redis 方案（PR-3）。</li>
 * </ul>
 *
 * <p>{@code jairouter.quota.enabled=false}（默认）时所有方法立即返回、不访问数据库。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@Slf4j
@Service
public class QuotaLedgerService {

    /** 过期行清理调度表达式：每小时第 23 分钟执行（避开整点高峰） */
    private static final String CLEANUP_CRON = "0 23 * * * ?";

    private final QuotaLedgerRepository repository;
    private final QuotaProperties properties;
    private final Clock clock;

    /** 内存账本：key = 维度 + 窗口 + 窗口起点 */
    private final ConcurrentHashMap<LedgerKey, LedgerSlot> slots = new ConcurrentHashMap<>();

    /**
     * Spring 使用的主构造器。
     *
     * @param repository 账本仓库
     * @param properties 账本配置
     */
    @Autowired
    public QuotaLedgerService(final QuotaLedgerRepository repository, final QuotaProperties properties) {
        this(repository, properties, Clock.systemDefaultZone());
    }

    /**
     * 可注入时钟的构造器，便于测试固定时间窗边界。
     *
     * @param repository 账本仓库
     * @param properties 账本配置
     * @param clock      时钟
     */
    public QuotaLedgerService(final QuotaLedgerRepository repository,
                              final QuotaProperties properties,
                              final Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 账本是否启用。
     *
     * @return {@code jairouter.quota.enabled} 的值，默认 {@code false}
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 预留一次配额：对全部已启用窗口原子累加请求数（+1）与预估 token。
     *
     * <p>本方法不会抛出异常：账本未启用时直接返回“未启用”决策；账本读写异常时按
     * {@code jairouter.quota.fail-open} 返回放行（默认）或拒绝（fail-closed）并标记 {@code degraded=true}。</p>
     *
     * @param request 预留请求，可为 {@code null}
     * @return 预留决策
     */
    public QuotaDecision reserve(final QuotaRequest request) {
        if (!isEnabled()) {
            return QuotaDecision.disabled();
        }
        if (request == null) {
            return degrade(QuotaDecision.REASON_INVALID_REQUEST);
        }
        try {
            final List<QuotaWindow> windows = properties.enabledWindows();
            if (windows.isEmpty()) {
                // 启用但未配置任何窗口：等同于未启用，不产生任何记账
                return QuotaDecision.disabled();
            }
            final LocalDateTime now = LocalDateTime.now(clock);
            final QuotaDimension dimension = request.dimension();
            for (final QuotaWindow window : windows) {
                final LedgerSlot slot = slotFor(dimension, window, window.windowStart(now));
                slot.requests.increment();
                slot.tokens.add(request.estimatedTokens());
            }
            return QuotaDecision.allow();
        } catch (Exception e) {
            log.warn("配额账本 reserve 异常，按 fail-open={} 处理: apiKeyId={}, error={}",
                properties.isFailOpen(), request.dimension().apiKeyId(), e.toString());
            return degrade(QuotaDecision.REASON_LEDGER_UNAVAILABLE);
        }
    }

    /**
     * 结算一次配额：按 {@code actual - estimated} 冲正 token，调用失败时回滚整笔预留。
     *
     * <p>只冲正已存在的内存槽位（不会为结算新建槽位、不访问数据库）；异常被吞掉并记录日志，
     * 不会影响调用方结果。</p>
     *
     * @param settlement 结算信息，可为 {@code null}（忽略）
     */
    public void settle(final QuotaSettlement settlement) {
        if (!isEnabled() || settlement == null) {
            return;
        }
        try {
            final LocalDateTime now = LocalDateTime.now(clock);
            final QuotaDimension dimension = settlement.dimension();
            final long requestDelta = settlement.requestDelta();
            final long tokenDelta = settlement.tokenDelta();
            if (requestDelta == 0L && tokenDelta == 0L) {
                return;
            }
            for (final QuotaWindow window : properties.enabledWindows()) {
                final LedgerSlot slot = slots.get(new LedgerKey(dimension, window, window.windowStart(now)));
                if (slot == null) {
                    continue;
                }
                if (requestDelta != 0L) {
                    slot.requests.add(requestDelta);
                }
                if (tokenDelta != 0L) {
                    slot.tokens.add(tokenDelta);
                }
            }
        } catch (Exception e) {
            log.warn("配额账本 settle 异常（忽略，不影响请求结果）: apiKeyId={}, error={}",
                settlement.dimension().apiKeyId(), e.toString());
        }
    }

    /**
     * 查询某一维度在当前窗口内的用量。
     *
     * @param dimension 维度，{@code null} 时返回 {@link Optional#empty()}
     * @param window    窗口类型，{@code null} 时返回 {@link Optional#empty()}
     * @return 内存态优先的用量；内存无数据时读数据库快照；账本未启用、均无数据或读取失败时返回
     *         {@link Optional#empty()}（不会抛出异常、不会访问数据库）
     */
    public Optional<QuotaUsage> usage(final QuotaDimension dimension, final QuotaWindow window) {
        if (!isEnabled() || dimension == null || window == null) {
            return Optional.empty();
        }
        try {
            final LocalDateTime windowStart = window.windowStart(LocalDateTime.now(clock));
            final LedgerKey key = new LedgerKey(dimension, window, windowStart);
            final LedgerSlot slot = slots.get(key);
            if (slot != null) {
                return Optional.of(toUsage(key, slot));
            }
            return repository.findByTenantIdAndApiKeyIdAndUserIdAndServiceTypeAndModelAndWindowTypeAndWindowStart(
                    dimension.tenantId(), dimension.apiKeyId(), dimension.userId(), dimension.serviceType(),
                    dimension.model(), window.name(), windowStart)
                .map(row -> toUsage(key, row));
        } catch (Exception e) {
            log.warn("配额账本 usage 查询失败: apiKeyId={}, error={}", dimension.apiKeyId(), e.toString());
            return Optional.empty();
        }
    }

    /**
     * 查询某个 API Key 的全部账本用量（所有维度、所有窗口）。
     *
     * <p>内存态优先：与内存槽位同键的数据库快照会被跳过，避免同一窗口重复计数。
     * 数据库读取失败时降级为“仅返回内存态”并记录日志，不抛出异常——因此无法区分
     * “无数据”与“读库失败”，调用方需要严格回退语义时应结合 {@link #usage} 判断。</p>
     *
     * @param apiKeyId API Key ID，{@code null} 或空串、账本未启用时返回空列表
     * @return 用量列表（按窗口类型、窗口起点降序排序）
     */
    public List<QuotaUsage> usageAll(final String apiKeyId) {
        if (!isEnabled() || apiKeyId == null || apiKeyId.isEmpty()) {
            return List.of();
        }
        final List<QuotaUsage> result = new ArrayList<>();
        final Set<LedgerKey> inMemoryKeys = new LinkedHashSet<>();
        for (final Map.Entry<LedgerKey, LedgerSlot> entry : slots.entrySet()) {
            final LedgerKey key = entry.getKey();
            if (apiKeyId.equals(key.dimension().apiKeyId())) {
                inMemoryKeys.add(key);
                result.add(toUsage(key, entry.getValue()));
            }
        }
        try {
            for (final QuotaLedgerEntity row : repository.findByApiKeyId(apiKeyId)) {
                if (row.getWindowStart() == null) {
                    continue;
                }
                final Optional<QuotaWindow> window = QuotaWindow.parse(row.getWindowType());
                if (window.isEmpty()) {
                    continue;
                }
                final LedgerKey key = new LedgerKey(dimensionOf(row), window.get(), row.getWindowStart());
                if (inMemoryKeys.contains(key)) {
                    continue;
                }
                result.add(toUsage(key, row));
            }
        } catch (Exception e) {
            log.warn("配额账本 usageAll 读库失败，降级为仅返回内存态: apiKeyId={}, error={}", apiKeyId, e.toString());
        }
        result.sort((left, right) -> {
            final int byWindow = Integer.compare(left.window().ordinal(), right.window().ordinal());
            if (byWindow != 0) {
                return byWindow;
            }
            return right.windowStart().compareTo(left.windowStart());
        });
        return result;
    }

    /**
     * 重置某个 API Key 的账本（内存槽位 + 数据库快照），用于配额重置。
     *
     * <p>清理会覆盖该 Key 的全部窗口（比“仅重置当日”更彻底），异常只记录日志不抛出；
     * 账本未启用时为无操作。</p>
     *
     * @param apiKeyId API Key ID，{@code null}、空串或账本未启用时不做任何处理
     * @return 清理条数（内存槽位 + 数据库行）
     */
    public int reset(final String apiKeyId) {
        if (!isEnabled() || apiKeyId == null || apiKeyId.isEmpty()) {
            return 0;
        }
        final int before = slots.size();
        slots.keySet().removeIf(key -> apiKeyId.equals(key.dimension().apiKeyId()));
        int cleared = before - slots.size();
        try {
            cleared += repository.deleteByApiKeyId(apiKeyId);
        } catch (Exception e) {
            log.warn("配额账本 reset 删除数据库快照失败（内存态已清空）: apiKeyId={}, error={}",
                apiKeyId, e.toString());
        }
        log.info("配额账本已重置: apiKeyId={}, 清理条数={}", apiKeyId, cleared);
        return cleared;
    }

    /**
     * 按各级保留期清理过期账本行（默认 minute=1d / hour=2d / day=35d / month=13mo）。
     *
     * <p>同时清理已过期且增量已全部落库的内存槽位，避免内存无限增长；仍持有未落库增量的槽位会
     * 留到下一轮 {@link #flush()} 之后再清理。异常只记录日志不抛出。</p>
     *
     * @return 清理的数据库行数
     */
    @Scheduled(cron = CLEANUP_CRON)
    public int cleanupExpired() {
        if (!isEnabled()) {
            return 0;
        }
        final LocalDateTime now = LocalDateTime.now(clock);
        int deleted = 0;
        for (final QuotaWindow window : properties.enabledWindows()) {
            deleted += deleteExpiredRows(window, now);
        }
        final int purged = purgeMemory(now);
        if (deleted > 0 || purged > 0) {
            log.info("配额账本保留期清理完成: 数据库行={}, 内存槽位={}", deleted, purged);
        }
        return deleted;
    }

    /**
     * 把内存账本增量落库（定时快照，默认每 60 秒一次）。
     *
     * <p>每个槽位独立短事务：先按增量原子累加，影响行数为 0 时说明行不存在，改为插入绝对值快照；
     * 单个槽位失败只影响该槽位，下一轮 flush 会重试（增量保留在内存中）。</p>
     *
     * @return 成功落库的槽位数
     */
    @Scheduled(fixedDelayString = "${jairouter.quota.flush-interval-seconds:60}", timeUnit = TimeUnit.SECONDS)
    public int flush() {
        if (!isEnabled()) {
            return 0;
        }
        final LocalDateTime now = LocalDateTime.now(clock);
        int written = 0;
        for (final Map.Entry<LedgerKey, LedgerSlot> entry : slots.entrySet()) {
            if (writeSnapshot(entry.getKey(), entry.getValue(), now)) {
                written++;
            }
        }
        if (written > 0) {
            log.debug("配额账本快照落库完成: 槽位数={}", written);
        }
        return written;
    }

    /**
     * 应用关闭前最后一次落库，尽量不丢失内存中的增量。
     */
    @PreDestroy
    public void shutdown() {
        if (!isEnabled()) {
            return;
        }
        try {
            final int written = flush();
            log.info("配额账本关闭前落库完成: 槽位数={}", written);
        } catch (Exception e) {
            log.warn("配额账本关闭前落库失败: {}", e.toString());
        }
        slots.clear();
    }

    /**
     * 获取或创建槽位。首次创建时从数据库读取基线（历史累计值），读取失败会抛异常，
     * 由 {@link #reserve(QuotaRequest)} 统一按 fail-open 处理。
     *
     * @param dimension   维度
     * @param window      窗口类型
     * @param windowStart 窗口起点
     * @return 内存槽位
     */
    private LedgerSlot slotFor(final QuotaDimension dimension,
                               final QuotaWindow window,
                               final LocalDateTime windowStart) {
        final LedgerKey key = new LedgerKey(dimension, window, windowStart);
        final LedgerSlot existing = slots.get(key);
        if (existing != null) {
            return existing;
        }
        return slots.computeIfAbsent(key, this::createSlot);
    }

    /**
     * 创建内存槽位并用数据库基线初始化（读库异常时向上抛出，由调用方 fail-open 兜底）。
     *
     * @param key 账本键
     * @return 已用基线初始化的槽位
     */
    private LedgerSlot createSlot(final LedgerKey key) {
        final QuotaDimension dimension = key.dimension();
        final Optional<QuotaLedgerEntity> row =
            repository.findByTenantIdAndApiKeyIdAndUserIdAndServiceTypeAndModelAndWindowTypeAndWindowStart(
                dimension.tenantId(), dimension.apiKeyId(), dimension.userId(), dimension.serviceType(),
                dimension.model(), key.window().name(), key.windowStart());
        final long baselineRequests = row.isPresent() ? nullToZero(row.get().getRequestCount()) : 0L;
        final long baselineTokens = row.isPresent() ? nullToZero(row.get().getTokenCount()) : 0L;
        return new LedgerSlot(baselineRequests, baselineTokens);
    }

    /**
     * 写入单个槽位的增量快照。
     *
     * @param key  账本键
     * @param slot 内存槽位
     * @param now  当前时间
     * @return 是否成功落库
     */
    private boolean writeSnapshot(final LedgerKey key, final LedgerSlot slot, final LocalDateTime now) {
        final long requests = Math.max(0L, slot.requests.sum());
        final long tokens = Math.max(0L, slot.tokens.sum());
        final long requestDelta = requests - slot.publishedRequests;
        final long tokenDelta = tokens - slot.publishedTokens;
        if (requestDelta == 0L && tokenDelta == 0L) {
            return false;
        }
        final QuotaDimension dimension = key.dimension();
        try {
            final int updated = repository.accumulate(
                dimension.tenantId(), dimension.apiKeyId(), dimension.userId(), dimension.serviceType(),
                dimension.model(), key.window().name(), key.windowStart(), requestDelta, tokenDelta, now);
            final boolean persisted = updated > 0 || insertRow(key, requests, tokens, now);
            if (!persisted) {
                return false;
            }
            slot.publishedRequests = requests;
            slot.publishedTokens = tokens;
            return true;
        } catch (Exception e) {
            log.warn("配额账本快照落库失败（增量保留待下轮重试）: apiKeyId={}, window={}, windowStart={}, error={}",
                dimension.apiKeyId(), key.window(), key.windowStart(), e.toString());
            return false;
        }
    }

    /**
     * 插入账本行（首次落库写入绝对值）。
     *
     * @param key      账本键
     * @param requests 累计请求数
     * @param tokens   累计 token 数
     * @param now      当前时间
     * @return 是否插入成功
     */
    private boolean insertRow(final LedgerKey key,
                              final long requests,
                              final long tokens,
                              final LocalDateTime now) {
        final QuotaDimension dimension = key.dimension();
        try {
            repository.save(QuotaLedgerEntity.builder()
                .tenantId(dimension.tenantId())
                .apiKeyId(dimension.apiKeyId())
                .userId(dimension.userId())
                .serviceType(dimension.serviceType())
                .model(dimension.model())
                .windowType(key.window().name())
                .windowStart(key.windowStart())
                .requestCount(requests)
                .tokenCount(tokens)
                .updatedAt(now)
                .build());
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("配额账本行已存在（并发插入），下轮以累加方式落库: apiKeyId={}", dimension.apiKeyId());
            return false;
        } catch (Exception e) {
            log.warn("配额账本行插入失败: apiKeyId={}, window={}, error={}",
                dimension.apiKeyId(), key.window(), e.toString());
            return false;
        }
    }

    /**
     * 删除指定窗口的过期数据库行。
     *
     * @param window 窗口类型
     * @param now    当前时间
     * @return 删除行数
     */
    private int deleteExpiredRows(final QuotaWindow window, final LocalDateTime now) {
        try {
            return repository.deleteExpired(window.name(), properties.retentionCutoff(window, now));
        } catch (Exception e) {
            log.warn("配额账本过期行清理失败: window={}, error={}", window, e.toString());
            return 0;
        }
    }

    /**
     * 清理已过期且增量已全部落库的内存槽位。
     *
     * @param now 当前时间
     * @return 清理的槽位数
     */
    private int purgeMemory(final LocalDateTime now) {
        final List<LedgerKey> expired = new ArrayList<>();
        for (final Map.Entry<LedgerKey, LedgerSlot> entry : slots.entrySet()) {
            final LedgerKey key = entry.getKey();
            final LocalDateTime cutoff = properties.retentionCutoff(key.window(), now);
            if (key.windowStart() == null || !key.windowStart().isBefore(cutoff)) {
                continue;
            }
            if (!entry.getValue().isFullyPublished()) {
                // 仍有未落库增量：留给下一轮 flush，避免丢失计数
                continue;
            }
            expired.add(key);
        }
        for (final LedgerKey key : expired) {
            slots.remove(key);
        }
        return expired.size();
    }

    /**
     * 按配置决定降级决策的放行 / 拒绝形态。
     *
     * @param reason 降级原因
     * @return fail-open 时放行、否则拒绝的降级决策
     */
    private QuotaDecision degrade(final String reason) {
        if (properties.isFailOpen()) {
            return QuotaDecision.failOpen(reason);
        }
        return QuotaDecision.failClosed(reason);
    }

    /**
     * 内存槽位 → 用量读数（非负钳制）。
     *
     * @param key  账本键
     * @param slot 内存槽位
     * @return 用量读数
     */
    private QuotaUsage toUsage(final LedgerKey key, final LedgerSlot slot) {
        return new QuotaUsage(key.dimension(), key.window(), key.windowStart(),
            Math.max(0L, slot.requests.sum()), Math.max(0L, slot.tokens.sum()));
    }

    /**
     * 数据库行 → 用量读数。
     *
     * @param key 账本键
     * @param row 数据库行
     * @return 用量读数
     */
    private QuotaUsage toUsage(final LedgerKey key, final QuotaLedgerEntity row) {
        return new QuotaUsage(key.dimension(), key.window(), key.windowStart(),
            Math.max(0L, nullToZero(row.getRequestCount())), Math.max(0L, nullToZero(row.getTokenCount())));
    }

    /**
     * 数据库行 → 维度（空值折叠为空串哨兵值）。
     *
     * @param row 数据库行
     * @return 维度
     */
    private static QuotaDimension dimensionOf(final QuotaLedgerEntity row) {
        return new QuotaDimension(row.getTenantId(), row.getApiKeyId(), row.getUserId(),
            row.getServiceType(), row.getModel());
    }

    /**
     * {@code Long} → {@code long} 空值兜底。
     *
     * @param value 可能为 {@code null} 的计数
     * @return 非 {@code null} 的计数
     */
    private static long nullToZero(final Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 账本内存键：维度 + 窗口类型 + 窗口起点。
     *
     * @param dimension   维度
     * @param window      窗口类型
     * @param windowStart 窗口起点
     */
    private record LedgerKey(QuotaDimension dimension, QuotaWindow window, LocalDateTime windowStart) {
    }

    /**
     * 账本内存槽位：累计计数器 + 最近一次已落库的值（用于计算未落库增量）。
     *
     * <p>累计计数用 {@link LongAdder} 支持高并发累加；{@code published*} 字段只由 flush 线程写入。</p>
     */
    private static final class LedgerSlot {

        /** 累计请求数 */
        private final LongAdder requests = new LongAdder();

        /** 累计 token 数 */
        private final LongAdder tokens = new LongAdder();

        /** 最近一次已落库的请求数 */
        private long publishedRequests;

        /** 最近一次已落库的 token 数 */
        private long publishedTokens;

        private LedgerSlot(final long baselineRequests, final long baselineTokens) {
            this.requests.add(baselineRequests);
            this.tokens.add(baselineTokens);
            this.publishedRequests = baselineRequests;
            this.publishedTokens = baselineTokens;
        }

        /**
         * 增量是否已全部落库。
         *
         * @return 累计值与已落库值一致时返回 {@code true}
         */
        private boolean isFullyPublished() {
            return requests.sum() == publishedRequests && tokens.sum() == publishedTokens;
        }
    }
}
