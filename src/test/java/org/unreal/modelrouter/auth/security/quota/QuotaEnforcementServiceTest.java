package org.unreal.modelrouter.auth.security.quota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link QuotaEnforcementService} 限额判定与预留测试（v3.1 PR-2）。
 *
 * <p>被测对象是真实 {@link QuotaEnforcementService} + 真实 {@link QuotaLedgerService} +
 * 手写内存仓库（{@link InMemoryQuotaLedgerRepository}），时钟固定；仅 {@link ApiKeyService}
 * 沿 PR-1 既有测试夹具做法用 Mockito 桩（它由 JPA 存储支撑，无法在单测中真实构造），
 * 并返回真实 {@link HashMap} 作为缓存与索引。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QuotaEnforcementService 限额判定测试")
class QuotaEnforcementServiceTest {

    private static final String KEY_ID = "key-1";
    private static final String KEY_HASH = "hash-key-1";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 固定时刻 2026-03-14 13:45:30（分钟窗口剩 30 秒、当天剩 10h14m30s） */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);

    private static final long SECONDS_TO_NEXT_MINUTE = 30L;
    private static final long SECONDS_TO_NEXT_DAY = 36870L;

    @Mock
    private ApiKeyService apiKeyService;

    private InMemoryQuotaLedgerRepository repository;
    private QuotaProperties properties;
    private QuotaLedgerService ledgerService;
    private QuotaEnforcementService enforcementService;
    private Map<String, ApiKey> apiKeyCache;
    private Map<String, String> keyIdIndex;
    private ServerHttpRequest request;

    @BeforeEach
    void setUp() {
        final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

        repository = InMemoryQuotaLedgerRepository.create();
        properties = new QuotaProperties();
        properties.setEnabled(true);

        apiKeyCache = new HashMap<>();
        keyIdIndex = new HashMap<>();
        when(apiKeyService.getApiKeyCache()).thenReturn(apiKeyCache);
        when(apiKeyService.getKeyIdIndex()).thenReturn(keyIdIndex);

        ledgerService = new QuotaLedgerService(repository.proxy(), properties, clock);
        enforcementService = new QuotaEnforcementService(ledgerService, properties, apiKeyService, clock);
        // 结算凭据挂在请求属性上：独立构建的 MockServerHttpRequest 属性表只读（与真实 WebFlux 请求
        // 不同），因此必须经 MockServerWebExchange 取请求，才能复现“挂载—回取”的真实行为
        // （与 StreamingRequestProcessorQuotaSettleTest / NonStreamingRequestProcessorQuotaSettleTest 一致）。
        request = MockServerWebExchange.from(MockServerHttpRequest
            .post("/api/v1/chat/completions").build()).getRequest();
    }

    // ==================== 限额三态：日请求数 / 日 token / 每分钟请求 ====================

    @Test
    @DisplayName("日请求限额：预留请求数触达 dailyRequestLimit 时拒绝并给出 DAY 窗口信息")
    void dailyRequestLimit_shouldRejectWhenReached() {
        seedUsage(3, 0L);
        registerApiKey(3L, 0L, 0);

        final Optional<QuotaLimitViolation> violation = enforcementService.tryReserve(request, KEY_ID, 0L);

        assertTrue(violation.isPresent(), "第 4 个请求应被拒绝");
        final QuotaLimitViolation result = violation.get();
        assertEquals(QuotaLimitViolation.METRIC_DAILY_REQUESTS, result.metric());
        assertEquals(QuotaWindow.DAY, result.window());
        assertEquals(3L, result.limit());
        assertEquals(3L, result.used());
        assertEquals(0L, result.remaining());
        assertEquals(SECONDS_TO_NEXT_DAY, result.retryAfterSeconds(), "Retry-After 应指向下一个自然日");
        assertTrue(result.describe().contains("dailyRequestLimit"));
        assertEquals(3L, requestCount(QuotaWindow.DAY), "拒绝时不得记账");
    }

    @Test
    @DisplayName("日请求限额：未触达时放行并完成预留")
    void dailyRequestLimit_shouldAllowBelowLimit() {
        seedUsage(2, 0L);
        registerApiKey(3L, 0L, 0);

        final Optional<QuotaLimitViolation> violation = enforcementService.tryReserve(request, KEY_ID, 0L);

        assertTrue(violation.isEmpty());
        assertEquals(3L, requestCount(QuotaWindow.DAY), "放行时应完成预留（+1 请求）");
    }

    @Test
    @DisplayName("日 token 限额：账本用量 + 本次估算 token 超限时拒绝（含估算参与判定）")
    void dailyTokenLimit_shouldAccountForEstimatedTokens() {
        seedUsage(1, 900L);
        registerApiKey(0L, 1000L, 0);

        final Optional<QuotaLimitViolation> exceeded = enforcementService.evaluate(KEY_ID, 150L);

        assertTrue(exceeded.isPresent(), "900 + 150 > 1000 应拒绝");
        assertEquals(QuotaLimitViolation.METRIC_DAILY_TOKENS, exceeded.get().metric());
        assertEquals(QuotaWindow.DAY, exceeded.get().window());
        assertEquals(1000L, exceeded.get().limit());
        assertEquals(900L, exceeded.get().used());
        assertEquals(100L, exceeded.get().remaining());

        // 边界：恰好用满不算超限（> 才拒绝）
        assertTrue(enforcementService.evaluate(KEY_ID, 100L).isEmpty(), "900 + 100 == 1000 应放行");
    }

    @Test
    @DisplayName("每分钟限额：MINUTE 窗口请求数触达 rateLimitPerMinute 时拒绝并给出分钟窗口信息")
    void rateLimitPerMinute_shouldRejectInMinuteWindow() {
        seedUsage(2, 0L);
        registerApiKey(0L, 0L, 2);

        final Optional<QuotaLimitViolation> violation = enforcementService.tryReserve(request, KEY_ID, 0L);

        assertTrue(violation.isPresent());
        final QuotaLimitViolation result = violation.get();
        assertEquals(QuotaLimitViolation.METRIC_RATE_PER_MINUTE, result.metric());
        assertEquals(QuotaWindow.MINUTE, result.window());
        assertEquals(2L, result.limit());
        assertEquals(2L, result.used());
        assertEquals(SECONDS_TO_NEXT_MINUTE, result.retryAfterSeconds(), "Retry-After 应指向下一分钟边界");
    }

    @Test
    @DisplayName("判定优先级：日请求数 → 日 token → 每分钟，命中即返回")
    void evaluation_shouldReportFirstHitByPriority() {
        seedUsage(5, 5000L);
        registerApiKey(1L, 1L, 1);

        final QuotaLimitViolation violation = enforcementService.evaluate(KEY_ID, 10L).orElseThrow();

        assertEquals(QuotaLimitViolation.METRIC_DAILY_REQUESTS, violation.metric());
    }

    @Test
    @DisplayName("全部限额为 0（不限）：放行且不产生超限结果")
    void zeroLimits_shouldAllowEverything() {
        seedUsage(50, 1000000L);
        registerApiKey(0L, 0L, 0);

        assertTrue(enforcementService.evaluate(KEY_ID, 100000L).isEmpty());
    }

    // ==================== 预留凭据 ====================

    @Test
    @DisplayName("放行时挂载预留凭据：含维度与估算 token，供链路末端结算")
    void allow_shouldAttachReservation() {
        registerApiKey(0L, 0L, 0);

        assertTrue(enforcementService.tryReserve(request, KEY_ID, 42L).isEmpty());

        final QuotaReservation reservation = QuotaReservation.from(request);
        assertNotNull(reservation, "放行后应挂载结算凭据");
        assertEquals(42L, reservation.estimatedTokens());
        assertEquals(QuotaDimension.ofApiKey(KEY_ID), reservation.dimension());
        assertFalse(reservation.isSettled());
        assertEquals(42L, tokenCount(QuotaWindow.DAY), "预留应计入估算 token");
    }

    @Test
    @DisplayName("账本未启用：零行为变更（不判定、不记账、不挂载凭据、零数据库访问）")
    void disabled_shouldBeNoOp() {
        properties.setEnabled(false);
        registerApiKey(1L, 1L, 1);
        seedUsage(1, 1L);
        final int invocationsBefore = repository.invocationCount();

        assertFalse(enforcementService.isEnabled());
        assertTrue(enforcementService.evaluate(KEY_ID, 999999L).isEmpty());
        assertTrue(enforcementService.tryReserve(request, KEY_ID, 999999L).isEmpty());

        assertNull(QuotaReservation.from(request), "未启用时不得挂载凭据");
        assertEquals(invocationsBefore, repository.invocationCount(), "未启用时不得访问账本");
    }

    @Test
    @DisplayName("API Key 未找到：按无限制 fail-open 放行")
    void unknownApiKey_shouldFailOpen() {
        assertTrue(enforcementService.evaluate(KEY_ID, 100L).isEmpty());
        assertTrue(enforcementService.tryReserve(request, KEY_ID, 100L).isEmpty());
    }

    @Test
    @DisplayName("apiKeyId 为空：放行且零账本访问")
    void blankApiKeyId_shouldFailOpen() {
        assertTrue(enforcementService.evaluate(null, 100L).isEmpty());
        assertTrue(enforcementService.tryReserve(request, "", 100L).isEmpty());
        assertEquals(0, repository.invocationCount());
    }

    @Test
    @DisplayName("账本读写异常：fail-open 放行、不抛异常（不得产生 5xx）")
    void ledgerUnavailable_shouldFailOpen() {
        registerApiKey(1L, 1L, 1);
        repository.setFailing(true);

        assertTrue(enforcementService.evaluate(KEY_ID, 100L).isEmpty(), "账本不可用时按 fail-open 放行");
        assertTrue(enforcementService.tryReserve(request, KEY_ID, 100L).isEmpty(), "账本不可用时不得拒绝请求");
    }

    @Test
    @DisplayName("限额配置读取异常：fail-open 放行、不抛异常")
    void apiKeyLookupFailure_shouldFailOpen() {
        when(apiKeyService.getKeyIdIndex()).thenThrow(new IllegalStateException("模拟索引不可用"));

        assertTrue(enforcementService.evaluate(KEY_ID, 100L).isEmpty());
        assertTrue(enforcementService.tryReserve(request, KEY_ID, 100L).isEmpty());
    }

    // ==================== 窗口未启用 ====================

    @Test
    @DisplayName("DAY 窗口未启用：日限额无法度量，按 fail-open 跳过")
    void dayWindowDisabled_shouldSkipDailyLimits() {
        properties.setWindows(List.of(QuotaWindow.MINUTE));
        seedUsage(5, 0L);
        registerApiKey(1L, 1L, 0);

        assertTrue(enforcementService.evaluate(KEY_ID, 1000L).isEmpty());
    }

    @Test
    @DisplayName("MINUTE 窗口未启用：速率限额无法度量，按 fail-open 跳过")
    void minuteWindowDisabled_shouldSkipRateLimit() {
        properties.setWindows(List.of(QuotaWindow.DAY));
        seedUsage(5, 0L);
        registerApiKey(0L, 0L, 1);

        assertTrue(enforcementService.evaluate(KEY_ID, 0L).isEmpty());
    }

    // ==================== 结算（冲正 / 回滚 / 恰一次） ====================

    @Test
    @DisplayName("成功结算：token 按 实际 − 估算 冲正，请求数不变")
    void settleSuccess_shouldReconcileTokensToActual() {
        registerApiKey(0L, 0L, 0);
        assertTrue(enforcementService.tryReserve(request, KEY_ID, 100L).isEmpty());
        final QuotaReservation reservation = QuotaReservation.from(request);
        assertEquals(100L, tokenCount(QuotaWindow.MINUTE), "预留时记估算值");

        QuotaReservation.settleSuccess(reservation, 30L);

        assertEquals(30L, tokenCount(QuotaWindow.MINUTE), "结算后应为实际用量");
        assertEquals(1L, requestCount(QuotaWindow.MINUTE), "成功结算不改请求数");
        assertTrue(reservation.isSettled());
        assertFalse(reservation.isFailed());
        assertEquals(30L, tokenCount(QuotaWindow.DAY));
    }

    @Test
    @DisplayName("失败结算：整笔回滚（请求数 −1、token −估算）")
    void settleFailure_shouldRollbackReservation() {
        registerApiKey(0L, 0L, 0);
        assertTrue(enforcementService.tryReserve(request, KEY_ID, 100L).isEmpty());
        final QuotaReservation reservation = QuotaReservation.from(request);

        QuotaReservation.settleFailure(reservation);

        assertEquals(0L, requestCount(QuotaWindow.MINUTE), "失败应回滚预留的请求数");
        assertEquals(0L, tokenCount(QuotaWindow.MINUTE), "失败应回滚预留的 token");
        assertEquals(0L, requestCount(QuotaWindow.DAY));
        assertTrue(reservation.isSettled());
        assertTrue(reservation.isFailed());
    }

    @Test
    @DisplayName("结算恰一次：重复结算（成功后再回滚 / 连续回滚）不产生额外冲正")
    void settle_shouldBeExactlyOnce() {
        registerApiKey(0L, 0L, 0);
        assertTrue(enforcementService.tryReserve(request, KEY_ID, 100L).isEmpty());
        final QuotaReservation reservation = QuotaReservation.from(request);

        QuotaReservation.settleSuccess(reservation, 30L);
        QuotaReservation.settleFailure(reservation);
        QuotaReservation.settleSuccess(reservation, 999L);

        assertEquals(30L, tokenCount(QuotaWindow.MINUTE), "只有第一次结算生效");
        assertEquals(1L, requestCount(QuotaWindow.MINUTE));
        assertFalse(reservation.isFailed(), "回滚调用被忽略，状态保持首次结算");
    }

    @Test
    @DisplayName("未预扣的请求（凭据为 null / 属性缺失）：结算与回滚均为空操作")
    void settleWithoutReservation_shouldBeNoOp() {
        assertNull(QuotaReservation.from(request));
        assertNull(QuotaReservation.from(null));

        QuotaReservation.settleSuccess(null, 100L);
        QuotaReservation.settleFailure(null);

        assertEquals(0L, requestCount(QuotaWindow.MINUTE));
    }

    @Test
    @DisplayName("账本不可用时的结算：不抛异常（结算异常被吞掉）")
    void settleWhenLedgerFailing_shouldNotThrow() {
        registerApiKey(0L, 0L, 0);
        final QuotaReservation reservation = new QuotaReservation(ledgerService, QuotaDimension.ofApiKey(KEY_ID), 10L);
        repository.setFailing(true);

        QuotaReservation.settleSuccess(reservation, 5L);

        assertTrue(reservation.isSettled());
    }

    // ==================== 维度与预留的对应关系 ====================

    @Test
    @DisplayName("账本维度为 API Key 粒度：预留与读数使用同一维度键")
    void reservationDimension_shouldBeApiKeyScoped() {
        registerApiKey(0L, 0L, 0);

        assertTrue(enforcementService.tryReserve(request, KEY_ID, 7L).isEmpty());

        final QuotaReservation reservation = QuotaReservation.from(request);
        assertEquals("", reservation.dimension().serviceType(), "请求侧维度取 Key 粒度（服务类型留空）");
        assertEquals("", reservation.dimension().model());
        assertEquals(KEY_ID, reservation.dimension().apiKeyId());
    }

    // ==================== 辅助方法 ====================

    /**
     * 直接向账本写入窗口用量（绕开限额判定，便于构造“已接近限额”的前置状态）。
     *
     * @param requests        请求次数
     * @param tokensPerRequest 每次预留的 token 数
     */
    private void seedUsage(final long requests, final long tokensPerRequest) {
        for (long i = 0; i < requests; i++) {
            ledgerService.reserve(QuotaRequest.of(QuotaDimension.ofApiKey(KEY_ID), tokensPerRequest));
        }
    }

    /**
     * 注册一个带限额的 API Key（写入 mock 返回的真实缓存与索引 Map）。
     *
     * @param dailyRequestLimit 日请求限额（0 = 不限）
     * @param dailyTokenLimit   日 token 限额（0 = 不限）
     * @param rateLimitPerMinute 每分钟请求限额（0 = 不限）
     */
    private void registerApiKey(final long dailyRequestLimit,
                                final long dailyTokenLimit,
                                final int rateLimitPerMinute) {
        apiKeyCache.put(KEY_HASH, ApiKey.builder()
            .keyId(KEY_ID)
            .keyHash(KEY_HASH)
            .dailyRequestLimit(dailyRequestLimit)
            .dailyTokenLimit(dailyTokenLimit)
            .rateLimitPerMinute(rateLimitPerMinute)
            .enabled(true)
            .build());
        keyIdIndex.put(KEY_ID, KEY_HASH);
    }

    /**
     * 读取账本中该 Key 在指定窗口的请求数。
     *
     * @param window 窗口
     * @return 请求数，无记录时为 0
     */
    private long requestCount(final QuotaWindow window) {
        return ledgerService.usage(QuotaDimension.ofApiKey(KEY_ID), window)
            .map(QuotaUsage::requestCount).orElse(0L);
    }

    /**
     * 读取账本中该 Key 在指定窗口的 token 数。
     *
     * @param window 窗口
     * @return token 数，无记录时为 0
     */
    private long tokenCount(final QuotaWindow window) {
        return ledgerService.usage(QuotaDimension.ofApiKey(KEY_ID), window)
            .map(QuotaUsage::tokenCount).orElse(0L);
    }
}
