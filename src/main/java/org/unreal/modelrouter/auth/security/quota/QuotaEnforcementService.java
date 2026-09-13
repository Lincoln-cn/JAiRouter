package org.unreal.modelrouter.auth.security.quota;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 配额限额判定与预留服务（v3.1 PR-2）。
 *
 * <p>把 PR-1 的旁路账本接入请求主链路：在实例选择之前判定“账本窗口用量 + 本次估算 token”
 * 是否越过既有 {@link ApiKey} 限额，未超限则记账预留（{@link QuotaLedgerService#reserve(QuotaRequest)}），
 * 结算由链路末端按实际用量冲正。</p>
 *
 * <p><b>限额映射</b>（限额来源是既有 API Key 配置，不新增配额字段）：</p>
 * <ul>
 *   <li>{@link QuotaWindow#DAY} 窗口 → {@code dailyRequestLimit}（请求数）与
 *       {@code dailyTokenLimit}（token 数）；</li>
 *   <li>{@link QuotaWindow#MINUTE} 窗口 → {@code rateLimitPerMinute}（请求数）。</li>
 * </ul>
 * <p>{@code 0} 表示不限制（与 {@link ApiKey} 既有语义一致）。判定顺序为
 * 日请求数 → 日 token 数 → 每分钟请求数，命中即返回（一次只报告一项，避免响应头自相矛盾）。</p>
 *
 * <p><b>维度取舍</b>：限额本身是 API Key 级配置，因此请求侧账本维度取 API Key 粒度
 * （{@link QuotaDimension#ofApiKey(String)}，其余维度留空），使 {@code usage(dimension, window)}
 * 既能命中账本内存热路径（不引入每请求一次聚合查询），又能按 Key 正确聚合窗口用量；
 * 服务类型 / 模型级细分留待后续 PR。</p>
 *
 * <p><b>不变量</b>：</p>
 * <ul>
 *   <li>账本未启用（{@code jairouter.quota.enabled=false}，默认）时本服务全部入口立即返回、
 *       不读写账本、不创建预留凭据——调用方行为与 v3.0.x 完全一致；</li>
 *   <li>判定 / 记账过程中的任何异常都按 fail-open 放行并打告警日志，绝不抛给调用方，
 *       因此配额链路不会产生 5xx；</li>
 *   <li><b>读不到用量不等于零用量</b>：窗口用量用 {@link QuotaLedgerService#usageStrict(QuotaDimension, QuotaWindow)}
 *       读取（读取失败直接抛异常），因此账本不可用时不会拿“默认 0 用量”去判定限额——那既可能凭空
 *       构造超限（错杀请求），也可能放过真实超限。此时统一按 fail-open 放行、不写任何 429 响应头；</li>
 *   <li>不调用也不修改既有限流组件（{@code RateLimitManager}），MINUTE 窗口判定是账本口径的
 *       独立视角，不存在双扣；</li>
 *   <li>限额对应的窗口未在 {@code jairouter.quota.windows} 中启用时，该项限额无法度量，
 *       按 fail-open 跳过（只记录 debug 日志）。</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@Slf4j
@Service
public class QuotaEnforcementService {

    private final QuotaLedgerService ledgerService;
    private final QuotaProperties properties;
    private final ApiKeyService apiKeyService;
    private final Clock clock;

    /**
     * Spring 使用的主构造器。
     *
     * @param ledgerService 账本服务
     * @param properties    账本配置（读取启用的窗口）
     * @param apiKeyService API Key 服务（读取限额配置，未装配时为 {@code null}，此时按无限制放行）
     */
    @Autowired
    public QuotaEnforcementService(final QuotaLedgerService ledgerService,
                                   final QuotaProperties properties,
                                   @Autowired(required = false) final ApiKeyService apiKeyService) {
        this(ledgerService, properties, apiKeyService, Clock.systemDefaultZone());
    }

    /**
     * 可注入时钟的构造器，便于测试固定窗口边界与 {@code Retry-After}。
     *
     * @param ledgerService 账本服务
     * @param properties    账本配置
     * @param apiKeyService API Key 服务，可为 {@code null}
     * @param clock         时钟
     */
    public QuotaEnforcementService(final QuotaLedgerService ledgerService,
                                   final QuotaProperties properties,
                                   final ApiKeyService apiKeyService,
                                   final Clock clock) {
        this.ledgerService = ledgerService;
        this.properties = properties;
        this.apiKeyService = apiKeyService;
        this.clock = clock;
    }

    /**
     * 配额能力是否启用（透传账本开关）。
     *
     * @return {@code jairouter.quota.enabled} 的值，默认 {@code false}
     */
    public boolean isEnabled() {
        return ledgerService.isEnabled();
    }

    /**
     * 判定本次请求是否已超限（只读，不记账、不挂载凭据）。
     *
     * <p>判定标准是 {@code 窗口已用量 + 本次估算 token > 限额}（严格大于：恰好用满不算超限）。
     * 窗口用量经 {@link QuotaLedgerService#usageStrict(QuotaDimension, QuotaWindow)} 读取，
     * 读不到用量（账本不可用）时不把缺失当成 0，而是直接降级放行。</p>
     *
     * @param apiKeyId        API Key ID，{@code null} 或空串时直接放行
     * @param estimatedTokens 本次请求估算 token 数（负数按 0 处理）
     * @return 超限时返回被命中的限额；未超限、账本未启用、Key 未知、账本读不到用量或判定异常
     *         （fail-open）时返回 {@link Optional#empty()}
     */
    public Optional<QuotaLimitViolation> evaluate(final String apiKeyId, final long estimatedTokens) {
        if (!isEnabled() || apiKeyId == null || apiKeyId.isEmpty()) {
            return Optional.empty();
        }
        try {
            final ApiKey apiKey = resolveApiKey(apiKeyId);
            if (apiKey == null) {
                log.debug("配额判定跳过：未找到 API Key 配置，按无限制放行: apiKeyId={}", apiKeyId);
                return Optional.empty();
            }
            final LocalDateTime now = LocalDateTime.now(clock);
            final List<QuotaWindow> windows = properties.enabledWindows();
            final QuotaDimension dimension = QuotaDimension.ofApiKey(apiKeyId);

            final Optional<QuotaLimitViolation> dailyViolation =
                evaluateDaily(apiKey, dimension, windows, now, Math.max(0L, estimatedTokens));
            if (dailyViolation.isPresent()) {
                return dailyViolation;
            }
            return evaluatePerMinute(apiKey, dimension, windows, now);
        } catch (Exception e) {
            log.warn("配额限额判定异常，按 fail-open 放行: apiKeyId={}, error={}", apiKeyId, e.toString());
            return Optional.empty();
        }
    }

    /**
     * 判定并在放行时完成预留（判定 + 记账 + 挂载结算凭据）。
     *
     * <p>返回空表示“放行且已完成预留”（或账本未启用 / 判定异常降级放行）；返回超限结果表示
     * 调用方应拒绝本次请求（429），本方法此时不记账。</p>
     *
     * @param request         请求（用于挂载结算凭据），可为 {@code null}
     * @param apiKeyId        API Key ID
     * @param estimatedTokens 本次请求估算 token 数
     * @return 超限结果；放行时为 {@link Optional#empty()}
     */
    public Optional<QuotaLimitViolation> tryReserve(final ServerHttpRequest request,
                                                    final String apiKeyId,
                                                    final long estimatedTokens) {
        if (!isEnabled() || apiKeyId == null || apiKeyId.isEmpty()) {
            return Optional.empty();
        }
        try {
            final Optional<QuotaLimitViolation> violation = evaluate(apiKeyId, estimatedTokens);
            if (violation.isPresent()) {
                log.warn("配额超限，拒绝请求: apiKeyId={}, {}", apiKeyId, violation.get().describe());
                return violation;
            }
            final QuotaDimension dimension = QuotaDimension.ofApiKey(apiKeyId);
            ledgerService.reserve(QuotaRequest.of(dimension, estimatedTokens));
            QuotaReservation.attach(request, new QuotaReservation(ledgerService, dimension, estimatedTokens));
            return Optional.empty();
        } catch (Exception e) {
            log.warn("配额预留异常，按 fail-open 放行: apiKeyId={}, error={}", apiKeyId, e.toString());
            return Optional.empty();
        }
    }

    /**
     * 日限额判定（请求数 + token 数）。
     *
     * @param apiKey          限额配置
     * @param dimension       账本维度
     * @param windows         启用的窗口
     * @param now             当前时间
     * @param estimatedTokens 本次估算 token 数
     * @return 超限结果，未超限或 DAY 窗口未启用时为空
     * @throws IllegalStateException 账本读不到 DAY 窗口用量（由 {@link #evaluate} 统一按 fail-open 处理）
     */
    private Optional<QuotaLimitViolation> evaluateDaily(final ApiKey apiKey,
                                                        final QuotaDimension dimension,
                                                        final List<QuotaWindow> windows,
                                                        final LocalDateTime now,
                                                        final long estimatedTokens) {
        if (!windows.contains(QuotaWindow.DAY)) {
            log.debug("DAY 窗口未启用，跳过日限额判定: apiKeyId={}", apiKey.getKeyId());
            return Optional.empty();
        }
        final Optional<QuotaUsage> usage = ledgerService.usageStrict(dimension, QuotaWindow.DAY);
        final long requests = usage.map(QuotaUsage::requestCount).orElse(0L);
        final long tokens = usage.map(QuotaUsage::tokenCount).orElse(0L);

        if (apiKey.getDailyRequestLimit() > 0 && requests + 1L > apiKey.getDailyRequestLimit()) {
            return Optional.of(violation(QuotaLimitViolation.METRIC_DAILY_REQUESTS, QuotaWindow.DAY,
                apiKey.getDailyRequestLimit(), requests, now));
        }
        if (apiKey.getDailyTokenLimit() > 0 && tokens + estimatedTokens > apiKey.getDailyTokenLimit()) {
            return Optional.of(violation(QuotaLimitViolation.METRIC_DAILY_TOKENS, QuotaWindow.DAY,
                apiKey.getDailyTokenLimit(), tokens, now));
        }
        return Optional.empty();
    }

    /**
     * 每分钟请求数限额判定。
     *
     * @param apiKey    限额配置
     * @param dimension 账本维度
     * @param windows   启用的窗口
     * @param now       当前时间
     * @return 超限结果，未超限或 MINUTE 窗口未启用时为空
     * @throws IllegalStateException 账本读不到 MINUTE 窗口用量（由 {@link #evaluate} 统一按 fail-open 处理）
     */
    private Optional<QuotaLimitViolation> evaluatePerMinute(final ApiKey apiKey,
                                                            final QuotaDimension dimension,
                                                            final List<QuotaWindow> windows,
                                                            final LocalDateTime now) {
        if (!windows.contains(QuotaWindow.MINUTE)) {
            log.debug("MINUTE 窗口未启用，跳过速率限额判定: apiKeyId={}", apiKey.getKeyId());
            return Optional.empty();
        }
        if (apiKey.getRateLimitPerMinute() <= 0) {
            return Optional.empty();
        }
        final long requests = ledgerService.usageStrict(dimension, QuotaWindow.MINUTE)
            .map(QuotaUsage::requestCount).orElse(0L);
        if (requests + 1L > apiKey.getRateLimitPerMinute()) {
            return Optional.of(violation(QuotaLimitViolation.METRIC_RATE_PER_MINUTE, QuotaWindow.MINUTE,
                apiKey.getRateLimitPerMinute(), requests, now));
        }
        return Optional.empty();
    }

    /**
     * 构造超限结果（含距下一窗口边界的秒数）。
     *
     * @param metric 限额指标名
     * @param window 窗口
     * @param limit  限额配置值
     * @param used   已用额度
     * @param now    当前时间
     * @return 超限结果
     */
    private QuotaLimitViolation violation(final String metric,
                                          final QuotaWindow window,
                                          final long limit,
                                          final long used,
                                          final LocalDateTime now) {
        return new QuotaLimitViolation(metric, window, limit, used, retryAfterSeconds(window, now));
    }

    /**
     * 计算距窗口重置的秒数（向上取整，最小 1 秒）。
     *
     * @param window 窗口
     * @param now    当前时间
     * @return 距下一窗口边界的秒数
     */
    private long retryAfterSeconds(final QuotaWindow window, final LocalDateTime now) {
        final LocalDateTime start = window.windowStart(now);
        final LocalDateTime next;
        switch (window) {
            case MINUTE:
                next = start.plusMinutes(1L);
                break;
            case HOUR:
                next = start.plusHours(1L);
                break;
            case MONTH:
                next = start.plusMonths(1L);
                break;
            default:
                next = start.plusDays(1L);
                break;
        }
        final long millis = Math.max(0L, Duration.between(now, next).toMillis());
        return Math.max(1L, (millis + 999L) / 1000L);
    }

    /**
     * 按 API Key ID 解析限额配置（沿用管理侧 {@code ApiKeyQuotaService} 的索引方式）。
     *
     * @param apiKeyId API Key ID
     * @return 限额配置；索引缺失或读取异常时返回 {@code null}（调用方按无限制放行）
     */
    private ApiKey resolveApiKey(final String apiKeyId) {
        if (apiKeyService == null) {
            return null;
        }
        try {
            final String keyHash = apiKeyService.getKeyIdIndex().get(apiKeyId);
            if (keyHash == null) {
                return null;
            }
            return apiKeyService.getApiKeyCache().get(keyHash);
        } catch (Exception e) {
            log.warn("读取 API Key 限额配置失败，按无限制放行: apiKeyId={}, error={}", apiKeyId, e.toString());
            return null;
        }
    }
}
