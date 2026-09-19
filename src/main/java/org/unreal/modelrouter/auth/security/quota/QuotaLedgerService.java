package org.unreal.modelrouter.auth.security.quota;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.persistence.jpa.entity.QuotaLedgerEntity;
import org.unreal.modelrouter.persistence.jpa.repository.QuotaLedgerRepository;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 配额账本服务（v3.1 PR-1 内存账本 + PR-3 Redis 分布式计数）。
 *
 * <p>设计取舍：</p>
 * <ul>
 *   <li><b>热路径纯内存 / Redis</b>：{@link #reserve(QuotaRequest)} / {@link #settle(QuotaSettlement)}
 *       只操作计数后端（{@link LocalCounterBackend} 内存 {@code LongAdder} 或
 *       {@link RedisCounterBackend} 的 Lua 原子累加），不做任何数据库写入，避免给请求链路增加延迟。</li>
 *   <li><b>数据库仅作快照</b>：{@link #flush()} 定时（默认 60s）与 {@link #shutdown()}
 *       （{@code @PreDestroy}）时把本地计数增量落库。落库按“增量累加 + 行不存在则插入绝对值”实现，
 *       失败时保留增量待下一轮重试，不会重复计数。Redis 模式下快照<b>仍然写</b>，记录的是本节点
 *       贡献量：它既是重启 / 审计所需的旁路证据，也是降级期间本地视图的基线来源；集群权威计数值
 *       以 Redis 为准（见 {@link #usageStrict(QuotaDimension, QuotaWindow)}）。</li>
 *   <li><b>冷启动续算</b>：本地槽位首次被访问时从数据库读取基线（该维度的历史累计值），
 *       使重启后用量连续；读取失败会抛异常，由 {@link #reserve(QuotaRequest)} 捕获并按 fail-open 处理。</li>
 *   <li><b>分布式计数与降级</b>（v3.1 PR-3）：{@code jairouter.quota.distributed.enabled=true} 且
 *       装配了 {@link QuotaCounterBackend} 时，Redis 为权威计数，本地后端退化为“镜像 + 降级兜底”：
 *       <ul>
 *         <li>写入路径：先记本地镜像（保证降级视图与快照完整），再以 Lua 原子累加写 Redis；
 *             Redis 失败时把增量登记为“待补写”，下一次成功写入时合并补写，保证降级期间计数不丢；</li>
 *         <li>读取路径：优先读 Redis（跨实例一致），Redis 不可用时按
 *             {@code jairouter.quota.distributed.degrade-to-local} 决定是否回退到本地视图 / 数据库快照；</li>
 *         <li>任何 Redis 异常都不会抛给调用方：只打告警日志、累加降级指标并标记
 *             {@link #isDegraded() degraded}，放行/拒绝仍由 {@code jairouter.quota.fail-open} 决定；</li>
 *         <li>超时属“结果未知”（命令可能已生效）：不重试同一增量，宁可少计也不重复计——
 *             少计偏向放行，与默认 {@code fail-open=true} 的安全取向一致。</li>
 *       </ul>
 *   </li>
 *   <li><b>同步阻塞的代价</b>：PR-2 已把 {@code reserve}/{@code settle} 钉在同步签名上，因此
 *       Redis 调用使用 {@code Mono.block(timeout)}（默认 50ms）。这是本 PR 的已知取舍：
 *       热路径最坏阻塞 {@code distributed.timeout}，代价换来“零签名变更 + 确定性降级”；
 *       彻底异步化（把账本调用并入 Reactor 链路）留待后续 PR。</li>
 *   <li><b>绝不抛出</b>：{@link #reserve(QuotaRequest)} / {@link #settle(QuotaSettlement)} /
 *       查询 / 清理接口都不会把异常抛给调用方；账本不可用时按
 *       {@code jairouter.quota.fail-open}（默认 true）放行并标记 {@code degraded=true}。
 *       唯一例外是 {@link #usageStrict(QuotaDimension, QuotaWindow)}（v3.1 PR-2 新增，供限额判定
 *       区分“零用量”与“读不到用量”），它把读取异常抛给调用方，由调用方按 fail-open 处理。</li>
 *   <li><b>多实例语义</b>：默认（{@code distributed.enabled=false}）仅保证单实例内计数精确；
 *       开启分布式计数后额度由 Redis 共享，本地计数仅在降级期间作为兜底视图。</li>
 * </ul>
 *
 * <p>{@code jairouter.quota.enabled=false}（默认）时所有方法立即返回、不访问数据库与 Redis。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@Slf4j
@Service
public class QuotaLedgerService {

    /** 过期行清理调度表达式：每小时第 23 分钟执行（避开整点高峰） */
    private static final String CLEANUP_CRON = "0 23 * * * ?";

    /** Redis 计数后端不可用（连接失败 / 命令错误），已降级为本地计数 */
    public static final String REASON_REDIS_UNAVAILABLE = "quota-counter-redis-unavailable";

    /** Redis 计数命令超时（结果未知），已降级为本地计数 */
    public static final String REASON_REDIS_TIMEOUT = "quota-counter-redis-timeout";

    /** 开启分布式计数但未装配计数后端 Bean（配置错误），已降级为本地计数 */
    public static final String REASON_REDIS_NOT_CONFIGURED = "quota-counter-redis-not-configured";

    private final QuotaLedgerRepository repository;
    private final QuotaProperties properties;
    private final Clock clock;

    /** 本地计数后端：始终存在（本地模式为唯一后端，分布式模式下为镜像 + 降级兜底） */
    private final LocalCounterBackend localBackend;

    /** 分布式计数后端；{@code null} 表示未启用或未装配 */
    private final QuotaCounterBackend distributedBackend;

    /** 是否使用分布式后端作为权威计数 */
    private final boolean distributed;

    private final QuotaCounterMetrics metrics;

    /** 是否处于降级状态（最近一次分布式操作失败） */
    private volatile boolean degraded;

    /** 降级原因，未降级时为空串 */
    private volatile String degradedReason = "";

    /**
     * Spring 使用的主构造器。
     *
     * @param repository         账本仓库
     * @param properties         账本配置
     * @param metrics            配额计数指标（记录后端类型 / 降级次数 / Redis 命令耗时）
     * @param distributedBackends 分布式计数后端提供者（仅在
     *                            {@code jairouter.quota.distributed.enabled=true} 时存在 Bean）
     */
    @Autowired
    public QuotaLedgerService(final QuotaLedgerRepository repository,
                              final QuotaProperties properties,
                              final QuotaCounterMetrics metrics,
                              final ObjectProvider<QuotaCounterBackend> distributedBackends) {
        this(repository, properties, Clock.systemDefaultZone(),
            distributedBackends == null ? null : distributedBackends.getIfAvailable(), metrics);
    }

    /**
     * 全量构造器（测试与显式装配使用）。
     *
     * @param repository          账本仓库
     * @param properties          账本配置
     * @param clock               时钟
     * @param distributedBackend  分布式计数后端，{@code null} 表示只用本地后端
     * @param metrics             指标收集器，{@code null} 时使用空实现
     */
    public QuotaLedgerService(final QuotaLedgerRepository repository,
                              final QuotaProperties properties,
                              final Clock clock,
                              final QuotaCounterBackend distributedBackend,
                              final QuotaCounterMetrics metrics) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.metrics = metrics == null ? QuotaCounterMetrics.noop() : metrics;
        this.localBackend = new LocalCounterBackend(this::loadBaseline);
        this.distributedBackend = distributedBackend;
        this.distributed = properties.isEnabled() && properties.distributedEnabled() && distributedBackend != null;
        if (properties.isEnabled() && properties.distributedEnabled() && distributedBackend == null) {
            this.degraded = true;
            this.degradedReason = REASON_REDIS_NOT_CONFIGURED;
            this.metrics.recordDegradation(REASON_REDIS_NOT_CONFIGURED);
            log.warn("已开启配额分布式计数但未装配 QuotaCounterBackend，账本保持本地模式: reason={}",
                REASON_REDIS_NOT_CONFIGURED);
        }
        this.metrics.recordBackend(this.distributed);
        log.info("配额账本初始化: enabled={}, backend={}, failOpen={}, windows={}", properties.isEnabled(),
            backendName(), properties.isFailOpen(), properties.enabledWindows());
    }

    /**
     * 可注入时钟的构造器（本地模式），便于测试固定时间窗边界。
     *
     * @param repository 账本仓库
     * @param properties 账本配置
     * @param clock      时钟
     */
    public QuotaLedgerService(final QuotaLedgerRepository repository,
                              final QuotaProperties properties,
                              final Clock clock) {
        this(repository, properties, clock, null, null);
    }

    /**
     * 本地模式构造器（使用系统时钟）。
     *
     * @param repository 账本仓库
     * @param properties 账本配置
     */
    public QuotaLedgerService(final QuotaLedgerRepository repository, final QuotaProperties properties) {
        this(repository, properties, Clock.systemDefaultZone(), null, null);
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
     * 当前是否使用分布式（Redis）计数后端作为权威计数。
     *
     * @return 启用且已装配分布式后端时返回 {@code true}
     */
    public boolean isDistributed() {
        return distributed;
    }

    /**
     * 当前权威计数后端名称。
     *
     * @return {@code redis}（分布式模式）或 {@code local}
     */
    public String backendName() {
        return distributed ? distributedBackend.name() : LocalCounterBackend.NAME;
    }

    /**
     * 是否处于降级状态（最近一次分布式计数操作失败，或开启分布式计数但未装配后端）。
     *
     * @return 降级时返回 {@code true}
     */
    public boolean isDegraded() {
        return degraded;
    }

    /**
     * 降级原因。
     *
     * @return 降级原因，未降级时为空串
     */
    public String degradedReason() {
        return degradedReason;
    }

    /**
     * 预留一次配额：对全部已启用窗口原子累加请求数（+1）与预估 token。
     *
     * <p>本方法不会抛出异常：账本未启用时直接返回“未启用”决策；账本读写异常时按
     * {@code jairouter.quota.fail-open} 返回放行（默认）或拒绝（fail-closed）并标记 {@code degraded=true}；
     * 分布式计数不可用时降级为本地计数，原因见 {@link #degradedReason()}。</p>
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
            final long tokens = request.estimatedTokens();
            if (!distributed) {
                for (final QuotaWindow window : windows) {
                    localBackend.incrementLocal(keyOf(dimension, window, now), 1L, tokens);
                }
                return QuotaDecision.allow();
            }
            String failure = null;
            for (final QuotaWindow window : windows) {
                final QuotaCounterKey key = keyOf(dimension, window, now);
                if (properties.isFailOpen()) {
                    // fail-open：本次请求一定放行，先记本地镜像（降级读数、快照与补偿记账都依赖它）
                    localBackend.incrementLocal(key, 1L, tokens);
                }
                if (failure != null) {
                    // 本轮已确认 Redis 不可用：剩余窗口不再逐条尝试，避免超时叠加放大请求延迟；
                    // 增量登记为待补写，Redis 恢复后合并补写（不丢计数）
                    if (properties.isFailOpen()) {
                        localBackend.addPending(key, 1L, tokens);
                    }
                    continue;
                }
                try {
                    publish(key, 1L, tokens, properties.isFailOpen());
                } catch (Exception e) {
                    failure = classify(e);
                    if (degraded) {
                        log.debug("Redis 计数写入持续失败: apiKeyId={}, window={}, reason={}, error={}",
                            dimension.apiKeyId(), window, failure, e.toString());
                    } else {
                        log.warn("Redis 计数写入失败，降级为本地计数: apiKeyId={}, window={}, reason={}, error={}",
                            dimension.apiKeyId(), window, failure, e.toString());
                    }
                    continue;
                }
                if (!properties.isFailOpen()) {
                    // fail-closed：只有权威计数写入成功才记本地账，保证“被拒绝的请求不计账”
                    localBackend.incrementLocal(key, 1L, tokens);
                }
            }
            if (failure != null) {
                metrics.recordDegradation(failure);
                markDegraded(failure);
                return degrade(failure);
            }
            clearDegraded();
            return QuotaDecision.allow();
        } catch (Exception e) {
            log.warn("配额账本 reserve 异常，按 fail-open={} 处理: apiKeyId={}, error={}",
                properties.isFailOpen(), request.dimension().apiKeyId(), e.toString());
            return degrade(QuotaDecision.REASON_LEDGER_UNAVAILABLE);
        }
    }

    /**
     * 限额 CAS 预留：对每个启用窗口原子「检查 + 累加」，任一窗口超限则返回该窗口的违规结果。
     *
     * <p>本地模式使用 {@link LocalCounterBackend#incrementLocalWithLimit}（槽位锁）；
     * 分布式模式使用 Redis Lua {@link RedisCounterBackend#INCREMENT_WITH_LIMIT_SCRIPT}
     * （超限由脚本回滚，返回空结果）。Redis 失败时按 fail-open 降级为普通预留。</p>
     *
     * @param request          预留请求
     * @param dayMaxRequests   日请求数限额（0=不限）
     * @param dayMaxTokens     日 token 限额（0=不限）
     * @param minuteMaxRequests 每分钟请求数限额（0=不限）
     * @return 超限结果；成功预留或账本未启用时为空
     */
    public Optional<QuotaLimitViolation> reserveWithLimits(final QuotaRequest request,
                                                           final long dayMaxRequests,
                                                           final long dayMaxTokens,
                                                           final long minuteMaxRequests) {
        if (!isEnabled() || request == null) {
            return Optional.empty();
        }
        try {
            final List<QuotaWindow> windows = properties.enabledWindows();
            if (windows.isEmpty()) {
                return Optional.empty();
            }
            final LocalDateTime now = LocalDateTime.now(clock);
            final QuotaDimension dimension = request.dimension();
            final long tokens = Math.max(0L, request.estimatedTokens());
            for (final QuotaWindow window : windows) {
                final long maxRequests = maxRequestsFor(window, dayMaxRequests, minuteMaxRequests);
                final long maxTokens = maxTokensFor(window, dayMaxTokens);
                if (maxRequests <= 0L && maxTokens <= 0L) {
                    // 该窗口无有效限额：普通累加，保证计数连续
                    if (!distributed) {
                        localBackend.incrementLocal(keyOf(dimension, window, now), 1L, tokens);
                    } else {
                        try {
                            publish(keyOf(dimension, window, now), 1L, tokens, true);
                        } catch (Exception e) {
                            markDegraded(classify(e));
                        }
                    }
                    continue;
                }
                final QuotaCounterKey key = keyOf(dimension, window, now);
                if (!distributed) {
                    final long[] totals =
                            localBackend.incrementLocalWithLimit(key, 1L, tokens, maxRequests, maxTokens);
                    if (totals == null) {
                        return buildLimitViolation(window, maxRequests, maxTokens, key, now);
                    }
                    continue;
                }
                try {
                    final long[] result = executeRedis(() ->
                            distributedBackend.incrementWithLimit(key, 1L, tokens, maxRequests, maxTokens));
                    if (result == null) {
                        // Lua 已回滚
                        return buildLimitViolation(window, maxRequests, maxTokens, key, now);
                    }
                    localBackend.incrementLocal(key, 1L, tokens);
                    clearDegraded();
                } catch (Exception e) {
                    final String reason = classify(e);
                    markDegraded(reason);
                    metrics.recordDegradation(reason);
                    if (properties.isFailOpen()) {
                        // 降级：回退普通预留，避免 Redis 故障时误杀
                        localBackend.incrementLocal(key, 1L, tokens);
                        localBackend.addPending(key, 1L, tokens);
                    } else {
                        return buildLimitViolation(window, maxRequests, maxTokens, key, now);
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("配额 CAS reserveWithLimits 异常，按 fail-open 处理: apiKeyId={}, error={}",
                    request.dimension().apiKeyId(), e.toString());
            return Optional.empty();
        }
    }

    private static long maxRequestsFor(final QuotaWindow window,
                                       final long dayMaxRequests,
                                       final long minuteMaxRequests) {
        // 与 QuotaEnforcementService.evaluate 一致：仅 DAY / MINUTE 参与限额判定
        return switch (window) {
            case MINUTE -> minuteMaxRequests;
            case DAY -> dayMaxRequests;
            case HOUR, MONTH -> 0L;
        };
    }

    private static long maxTokensFor(final QuotaWindow window, final long dayMaxTokens) {
        return window == QuotaWindow.DAY ? dayMaxTokens : 0L;
    }

    private Optional<QuotaLimitViolation> buildLimitViolation(final QuotaWindow window,
                                                              final long maxRequests,
                                                              final long maxTokens,
                                                              final QuotaCounterKey key,
                                                              final LocalDateTime now) {
        // CAS 拒绝时计数未包含本笔，used 即当前窗口累计值
        final long[] usage = localBackend.totals(key);
        if (maxRequests > 0L) {
            final long used = usage == null ? maxRequests : usage[0];
            final String metric = window == QuotaWindow.MINUTE
                    ? QuotaLimitViolation.METRIC_RATE_PER_MINUTE
                    : QuotaLimitViolation.METRIC_DAILY_REQUESTS;
            return Optional.of(new QuotaLimitViolation(metric, window, maxRequests,
                    Math.max(0L, used), retryAfterSeconds(window, now)));
        }
        final long usedTokens = usage == null ? maxTokens : usage[1];
        return Optional.of(new QuotaLimitViolation(QuotaLimitViolation.METRIC_DAILY_TOKENS,
                window, maxTokens, Math.max(0L, usedTokens), retryAfterSeconds(window, now)));
    }

    private long retryAfterSeconds(final QuotaWindow window, final LocalDateTime now) {
        final LocalDateTime start = window.windowStart(now);
        final LocalDateTime next = switch (window) {
            case MINUTE -> start.plusMinutes(1L);
            case HOUR -> start.plusHours(1L);
            case MONTH -> start.plusMonths(1L);
            default -> start.plusDays(1L);
        };
        final long millis = Math.max(0L, java.time.Duration.between(now, next).toMillis());
        return Math.max(1L, (millis + 999L) / 1000L);
    }

    /**
     * 结算一次配额：按 {@code actual - estimated} 冲正 token，调用失败时回滚整笔预留。
     *
     * <p>只冲正已存在的本地槽位（不会为结算新建槽位、不访问数据库）；异常被吞掉并记录日志，
     * 不会影响调用方结果。分布式模式下同时把冲正量补写到 Redis（失败仅告警 + 降级标记）。</p>
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
            String failure = null;
            for (final QuotaWindow window : properties.enabledWindows()) {
                final QuotaCounterKey key = keyOf(dimension, window, now);
                if (localBackend.incrementLocalIfPresent(key, requestDelta, tokenDelta) == null) {
                    continue;
                }
                if (!distributed) {
                    continue;
                }
                try {
                    publish(key, requestDelta, tokenDelta, true);
                } catch (Exception e) {
                    failure = classify(e);
                    log.warn("Redis 计数结算补写失败，降级为本地计数: apiKeyId={}, window={}, reason={}, error={}",
                        dimension.apiKeyId(), window, failure, e.toString());
                }
            }
            if (failure != null) {
                metrics.recordDegradation(failure);
                markDegraded(failure);
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
     * @return 用量；账本未启用、均无数据或读取失败时返回 {@link Optional#empty()}（不会抛出异常）
     */
    public Optional<QuotaUsage> usage(final QuotaDimension dimension, final QuotaWindow window) {
        if (!isEnabled() || dimension == null || window == null) {
            return Optional.empty();
        }
        try {
            return usageStrict(dimension, window);
        } catch (Exception e) {
            log.warn("配额账本 usage 查询失败: apiKeyId={}, error={}", dimension.apiKeyId(), e.toString());
            return Optional.empty();
        }
    }

    /**
     * 查询某一维度在当前窗口内的用量，读取失败时抛出异常（v3.1 PR-2 限额判定专用）。
     *
     * <p>与 {@link #usage(QuotaDimension, QuotaWindow)} 的唯一差异是故障语义：后者把“账本不可用”
     * 折叠成 {@link Optional#empty()}，调用方无法区分“窗口内确实零用量”与“读不到用量”。限额判定
     * 必须区分这两者——把读取失败当成零用量会凭空得出“仍有额度”的结论（甚至反过来误判超限），
     * 因此这里把异常抛给调用方，由 {@code QuotaEnforcementService} 统一按 fail-open 放行。</p>
     *
     * <p>分布式模式下优先读 Redis（跨实例权威值，并加上本节点尚未补写的增量）；Redis 无该桶时
     * 回退数据库快照；Redis 读取失败时按 {@code degrade-to-local} 回退本地视图或数据库快照，
     * 不降级（{@code false}）时抛出异常。</p>
     *
     * @param dimension 维度
     * @param window    窗口类型
     * @return 用量；无记录时返回 {@link Optional#empty()}
     * @throws IllegalStateException 账本未启用，或参数缺失，或内存 / Redis / 数据库读取失败
     */
    public Optional<QuotaUsage> usageStrict(final QuotaDimension dimension, final QuotaWindow window) {
        if (!isEnabled()) {
            throw new IllegalStateException("配额账本未启用: " + QuotaDecision.REASON_DISABLED);
        }
        if (dimension == null || window == null) {
            throw new IllegalStateException("配额账本查询缺少维度或窗口: " + QuotaDecision.REASON_INVALID_REQUEST);
        }
        final LocalDateTime windowStart = window.windowStart(LocalDateTime.now(clock));
        final QuotaCounterKey key = new QuotaCounterKey(dimension, window, windowStart);
        if (!distributed) {
            return usageFromLocal(key);
        }
        try {
            final Optional<long[]> remote = executeRedis(() -> distributedBackend.read(key));
            clearDegraded();
            if (remote.isPresent()) {
                return Optional.of(toUsage(key, withPending(key, remote.get())));
            }
            // Redis 无该桶（未记账 / 已过期 / 数据被清）→ 与本地模式一致回退数据库快照
            return usageFromDatabase(key);
        } catch (Exception e) {
            final String reason = classify(e);
            metrics.recordDegradation(reason);
            if (degraded) {
                log.debug("Redis 计数读取持续失败: apiKeyId={}, window={}, reason={}, error={}",
                    dimension.apiKeyId(), window, reason, e.toString());
            } else {
                log.warn("Redis 计数读取失败，降级读数: apiKeyId={}, window={}, windowStart={}, reason={},"
                    + " degradeToLocal={}, error={}", dimension.apiKeyId(), window, windowStart, reason,
                    properties.distributedDegradeToLocal(), e.toString());
            }
            markDegraded(reason);
            if (!properties.distributedDegradeToLocal()) {
                throw new IllegalStateException("配额分布式计数读取失败（degrade-to-local=false）: " + reason, e);
            }
            return usageFromLocal(key);
        }
    }

    /**
     * 查询某个 API Key 的全部账本用量（所有维度、所有窗口）。
     *
     * <p><b>数据来源取舍</b>：这是管理侧 / 审计视图，需要一次列出某个 Key 的所有窗口与维度，
     * 而在 Redis 中按维度枚举需要全量 {@code SCAN}（成本不可控），因此分布式模式下仍以
     * “本地镜像 + 数据库快照”为数据源；需要跨实例权威值时使用
     * {@link #usage(QuotaDimension, QuotaWindow)}（单窗口精确读 Redis）。</p>
     *
     * <p>内存态优先：与本地槽位同键的数据库快照会被跳过，避免同一窗口重复计数。
     * 数据库读取失败时降级为“仅返回内存态”并记录日志，不抛出异常。</p>
     *
     * @param apiKeyId API Key ID，{@code null} 或空串、账本未启用时返回空列表
     * @return 用量列表（按窗口类型、窗口起点降序排序）
     */
    public List<QuotaUsage> usageAll(final String apiKeyId) {
        if (!isEnabled() || apiKeyId == null || apiKeyId.isEmpty()) {
            return List.of();
        }
        final List<QuotaUsage> result = usageAllFromMemory(apiKeyId);
        final Set<QuotaCounterKey> inMemoryKeys = new LinkedHashSet<>();
        for (final QuotaUsage usage : result) {
            inMemoryKeys.add(new QuotaCounterKey(usage.dimension(), usage.window(), usage.windowStart()));
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
                final QuotaCounterKey key = new QuotaCounterKey(dimensionOf(row), window.get(), row.getWindowStart());
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
     * 仅从本地内存计数聚合用量（不查库）.
     *
     * <p>管理台告警/概览使用：避免 Redis/H2 异常时同步读库拖死 HTTP 线程。</p>
     *
     * @param apiKeyId API Key ID
     * @return 内存态用量列表
     */
    public List<QuotaUsage> usageAllFromMemory(final String apiKeyId) {
        if (!isEnabled() || apiKeyId == null || apiKeyId.isEmpty()) {
            return List.of();
        }
        final List<QuotaUsage> result = new ArrayList<>();
        for (final QuotaCounterKey key : localBackend.keys()) {
            if (!apiKeyId.equals(key.dimension().apiKeyId())) {
                continue;
            }
            final long[] totals = localBackend.totals(key);
            if (totals == null) {
                continue;
            }
            result.add(toUsage(key, totals));
        }
        return result;
    }

    /**
     * 重置某个 API Key 的账本（本地槽位 + 数据库快照 + 分布式计数），用于配额重置。
     *
     * <p>清理会覆盖该 Key 的全部窗口（比“仅重置当日”更彻底），异常只记录日志不抛出；
     * 账本未启用时为无操作。</p>
     *
     * @param apiKeyId API Key ID，{@code null}、空串或账本未启用时不做任何处理
     * @return 清理条数（本地槽位 + 数据库行 + 分布式计数桶）
     */
    public int reset(final String apiKeyId) {
        if (!isEnabled() || apiKeyId == null || apiKeyId.isEmpty()) {
            return 0;
        }
        int cleared = localBackend.resetLocal(apiKeyId);
        try {
            cleared += repository.deleteByApiKeyId(apiKeyId);
        } catch (Exception e) {
            log.warn("配额账本 reset 删除数据库快照失败（本地槽位已清空）: apiKeyId={}, error={}",
                apiKeyId, e.toString());
        }
        if (distributed) {
            try {
                cleared += (int) nullToZero(executeRedis(() -> distributedBackend.reset(apiKeyId)));
            } catch (Exception e) {
                final String reason = classify(e);
                metrics.recordDegradation(reason);
                markDegraded(reason);
                log.warn("配额账本 reset 删除分布式计数失败（本地槽位与快照已清空）: apiKeyId={}, reason={}, error={}",
                    apiKeyId, reason, e.toString());
            }
        }
        log.info("配额账本已重置: apiKeyId={}, 清理条数={}, backend={}", apiKeyId, cleared, backendName());
        return cleared;
    }

    /**
     * 按各级保留期清理过期账本行与计数（默认 minute=1d / hour=2d / day=35d / month=13mo）。
     *
     * <p>同时清理已过期且增量已全部落库（且无待补写 Redis 增量）的本地计数槽位，避免计数无限
     * 增长；仍持有未落库增量的槽位会留到下一轮 {@link #flush()} 之后再清理。Redis 模式下额外
     * 删除过期的 Redis 计数桶。异常只记录日志不抛出。</p>
     *
     * @return 清理的数据库行数（计数清理数量见日志）
     */
    @Scheduled(cron = CLEANUP_CRON)
    public int cleanupExpired() {
        if (!isEnabled()) {
            return 0;
        }
        final LocalDateTime now = LocalDateTime.now(clock);
        // 已启用窗口 + 仍存在计数槽位的窗口（配置变更后可能残留），保证不与 PR-1 的清理范围退化
        final Set<QuotaWindow> windows = new LinkedHashSet<>(properties.enabledWindows());
        for (final QuotaCounterKey key : localBackend.keys()) {
            windows.add(key.window());
        }
        int deleted = 0;
        long purged = 0L;
        for (final QuotaWindow window : windows) {
            deleted += deleteExpiredRows(window, now);
            purged += purgeCounters(window, now);
        }
        if (deleted > 0 || purged > 0) {
            log.info("配额账本保留期清理完成: 数据库行={}, 计数槽位={}, backend={}", deleted, purged, backendName());
        }
        return deleted;
    }

    /**
     * 把本地计数增量落库（定时快照，默认每 60 秒一次）。
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
        for (final QuotaCounterKey key : localBackend.keys()) {
            if (writeSnapshot(key, now)) {
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
        localBackend.clear();
    }

    /**
     * 读取本地计数（内存镜像优先），无本地计数时回退数据库快照。
     *
     * @param key 计数键
     * @return 用量；无数据时返回 {@link Optional#empty()}
     */
    private Optional<QuotaUsage> usageFromLocal(final QuotaCounterKey key) {
        final long[] totals = localBackend.totals(key);
        if (totals != null) {
            return Optional.of(toUsage(key, totals));
        }
        return usageFromDatabase(key);
    }

    /**
     * 读取数据库快照（读库异常原样抛出，由调用方按 fail-open 处理）。
     *
     * @param key 计数键
     * @return 用量；无记录时返回 {@link Optional#empty()}
     */
    private Optional<QuotaUsage> usageFromDatabase(final QuotaCounterKey key) {
        final QuotaDimension dimension = key.dimension();
        return repository.findByTenantIdAndApiKeyIdAndUserIdAndServiceTypeAndModelAndWindowTypeAndWindowStart(
                dimension.tenantId(), dimension.apiKeyId(), dimension.userId(), dimension.serviceType(),
                dimension.model(), key.window().name(), key.windowStart())
            .map(row -> toUsage(key, row));
    }

    /**
     * 计算窗口起点与计数键。
     *
     * @param dimension 维度
     * @param window    窗口类型
     * @param now       当前时间
     * @return 计数键
     */
    private static QuotaCounterKey keyOf(final QuotaDimension dimension,
                                         final QuotaWindow window,
                                         final LocalDateTime now) {
        return new QuotaCounterKey(dimension, window, window.windowStart(now));
    }

    /**
     * 把增量（含降级期间累积的待补写增量）发布到分布式后端。
     *
     * <p>成功即返回；失败时向上抛异常由调用方按 fail-open 降级，并恢复此前取出的待补写增量：
     * 明确失败（连接失败 / 脚本错误）时连同本次增量一起放回待补写队列；超时属“结果未知”，
     * 整批放弃以避免重复计数。</p>
     *
     * @param key             计数键
     * @param requests        本次请求数增量
     * @param tokens          本次 token 增量
     * @param registerPending 失败时是否把本次增量登记为待补写（fail-closed 拒绝的请求不计账时为 false）
     */
    private void publish(final QuotaCounterKey key,
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
            executeRedis(() -> distributedBackend.increment(key, requestDelta, tokenDelta));
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
     * @param command 命令工厂
     * @param <T>     结果类型
     * @return 命令结果
     */
    private <T> T executeRedis(final Supplier<Mono<T>> command) {
        final long start = System.nanoTime();
        boolean success = false;
        try {
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
    private long[] withPending(final QuotaCounterKey key, final long[] remote) {
        final long[] pending = localBackend.peekPending(key);
        if (pending == null) {
            return remote;
        }
        return new long[]{remote[0] + pending[0], remote[1] + pending[1]};
    }

    /**
     * 初始化本地计数槽位基线（数据库历史累计值）。
     *
     * @param key 计数键
     * @return {@code [requests, tokens]} 基线
     * @throws RuntimeException 读取数据库失败时（由 {@link #reserve(QuotaRequest)} 按 fail-open 处理）
     */
    private long[] loadBaseline(final QuotaCounterKey key) {
        final QuotaDimension dimension = key.dimension();
        final Optional<QuotaLedgerEntity> row =
            repository.findByTenantIdAndApiKeyIdAndUserIdAndServiceTypeAndModelAndWindowTypeAndWindowStart(
                dimension.tenantId(), dimension.apiKeyId(), dimension.userId(), dimension.serviceType(),
                dimension.model(), key.window().name(), key.windowStart());
        final long baselineRequests = row.isPresent() ? nullToZero(row.get().getRequestCount()) : 0L;
        final long baselineTokens = row.isPresent() ? nullToZero(row.get().getTokenCount()) : 0L;
        return new long[]{baselineRequests, baselineTokens};
    }

    /**
     * 写入单个槽位的增量快照（累计值与增量来自同一次读取，避免重复落库）。
     *
     * @param key 计数键
     * @param now 当前时间
     * @return 是否成功落库
     */
    private boolean writeSnapshot(final QuotaCounterKey key, final LocalDateTime now) {
        final long[] snapshot = localBackend.snapshotDelta(key);
        if (snapshot == null) {
            return false;
        }
        final QuotaDimension dimension = key.dimension();
        try {
            final int updated = repository.accumulate(
                dimension.tenantId(), dimension.apiKeyId(), dimension.userId(), dimension.serviceType(),
                dimension.model(), key.window().name(), key.windowStart(), snapshot[2], snapshot[3], now);
            final boolean persisted = updated > 0 || insertRow(key, snapshot[0], snapshot[1], now);
            if (!persisted) {
                return false;
            }
            localBackend.markPublished(key, snapshot[0], snapshot[1]);
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
     * @param key      计数键
     * @param requests 累计请求数
     * @param tokens   累计 token 数
     * @param now      当前时间
     * @return 是否插入成功
     */
    private boolean insertRow(final QuotaCounterKey key,
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
     * 清理指定窗口的过期计数（Redis 桶 + 本地槽位），失败只告警不抛出。
     *
     * @param window 窗口类型
     * @param now    当前时间
     * @return 清理的计数数量
     */
    private long purgeCounters(final QuotaWindow window, final LocalDateTime now) {
        final LocalDateTime cutoff = properties.retentionCutoff(window, now);
        long purged = 0L;
        if (distributed) {
            try {
                purged += nullToZero(executeRedis(() -> distributedBackend.deleteExpired(window, cutoff)));
            } catch (Exception e) {
                final String reason = classify(e);
                metrics.recordDegradation(reason);
                markDegraded(reason);
                log.warn("Redis 过期计数清理失败（忽略，下轮重试）: window={}, reason={}, error={}",
                    window, reason, e.toString());
            }
        }
        return purged + localBackend.deleteExpiredLocal(window, cutoff);
    }

    /**
     * 标记降级状态。
     *
     * @param reason 降级原因
     */
    private void markDegraded(final String reason) {
        this.degradedReason = reason;
        this.degraded = true;
    }

    /**
     * 清除降级状态（一次成功的分布式操作即视为恢复）。
     */
    private void clearDegraded() {
        if (this.degraded) {
            log.info("配额分布式计数已恢复: backend={}", backendName());
        }
        this.degraded = false;
        this.degradedReason = "";
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
     * 判断异常是否为超时（Reactor {@code block(timeout)} 把超时包装成
     * {@link IllegalStateException}，真实超时在因果链中）。
     *
     * @param error 异常
     * @return 因果链中存在 {@link TimeoutException} 时返回 {@code true}
     */
    private static boolean isTimeout(final Throwable error) {
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
     * @return {@link #REASON_REDIS_TIMEOUT} 或 {@link #REASON_REDIS_UNAVAILABLE}
     */
    private static String classify(final Throwable error) {
        return isTimeout(error) ? REASON_REDIS_TIMEOUT : REASON_REDIS_UNAVAILABLE;
    }

    /**
     * 计数键 → 用量读数（非负钳制）。
     *
     * @param key    计数键
     * @param totals 计数值
     * @return 用量读数
     */
    private static QuotaUsage toUsage(final QuotaCounterKey key, final long[] totals) {
        return new QuotaUsage(key.dimension(), key.window(), key.windowStart(),
            Math.max(0L, totals[0]), Math.max(0L, totals[1]));
    }

    /**
     * 数据库行 → 用量读数。
     *
     * @param key 计数键
     * @param row 数据库行
     * @return 用量读数
     */
    private static QuotaUsage toUsage(final QuotaCounterKey key, final QuotaLedgerEntity row) {
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
}
