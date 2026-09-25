package org.unreal.modelrouter.router.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.auth.security.quota.InMemoryQuotaLedgerRepository;
import org.unreal.modelrouter.auth.security.quota.QuotaDimension;
import org.unreal.modelrouter.auth.security.quota.QuotaEnforcementService;
import org.unreal.modelrouter.auth.security.quota.QuotaLedgerService;
import org.unreal.modelrouter.auth.security.quota.QuotaProperties;
import org.unreal.modelrouter.auth.security.quota.QuotaRequest;
import org.unreal.modelrouter.auth.security.quota.QuotaReservation;
import org.unreal.modelrouter.auth.security.quota.QuotaSettlement;
import org.unreal.modelrouter.auth.security.quota.QuotaUsage;
import org.unreal.modelrouter.auth.security.quota.QuotaWindow;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.exceptionhandler.ReactiveGlobalExceptionHandler;
import org.unreal.modelrouter.config.core.ResponseCacheProperties;
import org.unreal.modelrouter.persistence.jpa.repository.QuotaLedgerRepository;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.cache.CaffeineCacheStore;
import org.unreal.modelrouter.router.cache.ResponseCacheService;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * v3.1 PR-2: {@link ServiceRequestHandler} 配额主链接入集成测试。
 *
 * <p>走真实 {@code handleRequest(exchange)} 流程（真实安全上下文 + 真实 WebFlux 交换对象），
 * 配额侧使用真实 {@link QuotaEnforcementService} / {@link QuotaLedgerService} /
 * {@link InMemoryQuotaLedgerRepository}（固定时钟）；仅认证/路由/适配器协作方用 Mockito
 * 桩，与仓库既有 handler 集成测试写法一致。</p>
 *
 * <p>覆盖：未超限放行（预留 + 无配额头）、三类超限 429 + {@code Retry-After} /
 * {@code X-Quota-*} 头（不触实例选择与下游）、账本异常 fail-open 无 5xx、
 * {@code enabled=false} 零行为变更、缓存命中不消耗配额、429 响应体由全局异常处理器
 * 按既有 {@link RouterResponse} 格式渲染。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceRequestHandler 配额接入集成测试")
class ServiceRequestHandlerQuotaIntegrationTest {

    private static final String BEARER = "Bearer downstream-token";
    private static final String KEY_ID = "key-quota";
    private static final String KEY_HASH = "hash-key-quota";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 固定时刻 2026-03-14 13:45:30（分钟窗口剩 30 秒、当天剩 36870 秒） */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);
    private static final String SECONDS_TO_NEXT_MINUTE = "30";
    private static final String SECONDS_TO_NEXT_DAY = "36870";

    /** chat 请求 "hello!" 的估算 token：7 英文非空白字符 → ceil(7 / 4) = 2 */
    private static final long ESTIMATED_TOKENS = 2L;

    @Mock
    private AdapterRegistry adapterRegistry;

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private ServiceCapability adapter;

    @Mock
    private ApiKeyService apiKeyService;

    private InMemoryQuotaLedgerRepository repository;
    private QuotaProperties quotaProperties;
    private CountingQuotaLedgerService ledgerService;
    private QuotaEnforcementService enforcementService;
    private ServiceRequestHandler handler;
    private Map<String, ApiKey> apiKeyCache;
    private Map<String, String> keyIdIndex;

    @BeforeEach
    void setUp() throws Exception {
        when(serviceStateManager.isServiceHealthy(anyString())).thenReturn(true);

        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-1");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://downstream.local");
        instance.setAdapter("openai-compat");
        when(registry.selectInstance(any(), anyString(), anyString(), any())).thenReturn(instance);
        when(adapterRegistry.getAdapter(any(ServiceType.class), any())).thenReturn(adapter);

        final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        repository = InMemoryQuotaLedgerRepository.create();
        quotaProperties = new QuotaProperties();
        quotaProperties.setEnabled(true);
        apiKeyCache = new HashMap<>();
        keyIdIndex = new HashMap<>();
        when(apiKeyService.getApiKeyCache()).thenReturn(apiKeyCache);
        when(apiKeyService.getKeyIdIndex()).thenReturn(keyIdIndex);

        ledgerService = new CountingQuotaLedgerService(repository.proxy(), quotaProperties, clock);
        enforcementService = new QuotaEnforcementService(ledgerService, quotaProperties, apiKeyService, clock);

        handler = new ServiceRequestHandler(adapterRegistry, registry, serviceStateManager, null, null);
        injectQuotaService(enforcementService);
        registerApiKey(0L, 0L, 0);
    }

    // ==================== 放行路径 ====================

    @Test
    @DisplayName("未超限：200 放行 + 预留一次 + 无配额响应头")
    void underLimit_shouldPassThroughAndReserve() throws Exception {
        final ServerWebExchange exchange = buildExchange(deterministicChat("hello!"));
        final ServiceRequestExecutor executor = downstreamExecutor();

        final ResponseEntity<?> response = invoke(exchange, executor).block(java.time.Duration.ofSeconds(10));

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("downstream-ok", response.getBody());
        verify(executor, times(1)).execute(any(), any(), any());
        verify(registry, times(1)).selectInstance(any(), anyString(), anyString(), any());

        // 预留凭据已挂载（供流式/非流式结算点回取），估算来自请求 DTO
        final QuotaReservation reservation = QuotaReservation.from(exchange.getRequest());
        assertNotNull(reservation);
        assertEquals(ESTIMATED_TOKENS, reservation.estimatedTokens());
        assertEquals(1L, requestCount(QuotaWindow.DAY), "放行请求应完成一次预留");

        // 放行时不得出现配额响应头
        assertNull(exchange.getResponse().getHeaders().getFirst(ServiceRequestHandler.QUOTA_HEADER_LIMIT));
        assertNull(exchange.getResponse().getHeaders().getFirst(ServiceRequestHandler.QUOTA_HEADER_WINDOW));
        assertNull(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    @DisplayName("不可估算的请求：按 0 估算预留（只计请求数）")
    void nonEstimableRequest_shouldReserveRequestCountOnly() throws Exception {
        final ServerWebExchange exchange = buildExchange(null);
        final ServiceRequestExecutor executor = downstreamExecutor();

        assertEquals(HttpStatus.OK, invoke(exchange, executor).block().getStatusCode());

        final QuotaReservation reservation = QuotaReservation.from(exchange.getRequest());
        assertNotNull(reservation);
        assertEquals(0L, reservation.estimatedTokens());
        assertEquals(0L, tokenCount(QuotaWindow.DAY));
    }

    // ==================== 超限三态 429 ====================

    @Test
    @DisplayName("日请求超限：429 + Retry-After/X-Quota-* 头，且不触实例选择与下游")
    void dailyRequestLimitExceeded_shouldReturn429() throws Exception {
        seedUsage(3, 0L);
        registerApiKey(3L, 0L, 0);
        final ServerWebExchange exchange = buildExchange(deterministicChat("hello!"));
        final ServiceRequestExecutor executor = downstreamExecutor();

        final Mono<ResponseEntity<?>> mono = invoke(exchange, executor);
        StepVerifier.create(mono)
                .expectErrorMatches(error -> error instanceof ResponseStatusException statusException
                        && statusException.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS)
                .verify(java.time.Duration.ofSeconds(10));

        assertQuotaHeaders(exchange, "3", "0", "DAY", SECONDS_TO_NEXT_DAY);
        verify(registry, never()).selectInstance(any(), anyString(), anyString(), any());
        verify(executor, never()).execute(any(), any(), any());
        assertEquals(3L, requestCount(QuotaWindow.DAY), "超限拒绝不得记账");
    }

    @Test
    @DisplayName("日 token 超限：429 + DAY 窗口头（估算参与判定）")
    void dailyTokenLimitExceeded_shouldReturn429() throws Exception {
        // 用量 999 / 限额 1000：仅剩 1 token，本次估算 2 token 使判定越界（估算必须参与判定）
        seedUsage(1, 999L);
        registerApiKey(0L, 1000L, 0);
        final ServerWebExchange exchange = buildExchange(deterministicChat("hello!"));
        final ServiceRequestExecutor executor = downstreamExecutor();

        StepVerifier.create(invoke(exchange, executor))
                .expectErrorMatches(error -> error instanceof ResponseStatusException statusException
                        && statusException.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS)
                .verify(java.time.Duration.ofSeconds(10));

        // 999 + 2（估算）> 1000 → 命中 token 限额；剩余 = 1000 − 999 = 1
        assertQuotaHeaders(exchange, "1000", "1", "DAY", SECONDS_TO_NEXT_DAY);
        verify(executor, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("每分钟超限：429 + MINUTE 窗口头（Retry-After 指向下一分钟）")
    void rateLimitPerMinuteExceeded_shouldReturn429() throws Exception {
        seedUsage(2, 0L);
        registerApiKey(0L, 0L, 2);
        final ServerWebExchange exchange = buildExchange(deterministicChat("hello!"));
        final ServiceRequestExecutor executor = downstreamExecutor();

        StepVerifier.create(invoke(exchange, executor))
                .expectErrorMatches(error -> error instanceof ResponseStatusException statusException
                        && statusException.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS)
                .verify(java.time.Duration.ofSeconds(10));

        assertQuotaHeaders(exchange, "2", "0", "MINUTE", SECONDS_TO_NEXT_MINUTE);
        verify(executor, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("429 响应体：由全局异常处理器按既有 RouterResponse 错误格式渲染")
    void quotaExceeded_shouldRenderLegacyErrorBody() throws Exception {
        seedUsage(1, 0L);
        registerApiKey(1L, 0L, 0);
        final ServerWebExchange exchange = buildExchange(deterministicChat("hello!"));
        final ServiceRequestExecutor executor = downstreamExecutor();

        final Throwable error = invoke(exchange, executor)
                .map(response -> (Throwable) null)
                .onErrorResume(throwable -> Mono.just(throwable))
                .block(java.time.Duration.ofSeconds(10));
        assertNotNull(error, "超限应以异常形式上抛给全局异常处理器");

        // 真实 WebFlux 响应写入（真实 ErrorWebExceptionHandler，未装配监控追踪）
        new ReactiveGlobalExceptionHandler(null).handle(exchange, error).block(java.time.Duration.ofSeconds(10));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        final String body = ((MockServerHttpResponse) exchange.getResponse()).getBodyAsString().block(java.time.Duration.ofSeconds(10));
        assertNotNull(body);
        assertTrue(body.contains("\"success\":false"), "响应体应沿用 RouterResponse 错误格式: " + body);
        assertTrue(body.contains("\"errorCode\":\"429\""), "错误码应为 429: " + body);
        assertTrue(body.contains("dailyRequestLimit"), "响应体应说明命中的限额: " + body);
        // 头部在异常渲染后依然保留
        assertQuotaHeaders(exchange, "1", "0", "DAY", SECONDS_TO_NEXT_DAY);
    }

    // ==================== 不变量 ====================

    @Test
    @DisplayName("enabled=false：零行为变更（零账本访问、零响应头、零凭据）")
    void quotaDisabled_shouldBehaveAsBefore() throws Exception {
        quotaProperties.setEnabled(false);
        registerApiKey(1L, 1L, 1);
        seedUsage(10, 100000L);
        final int invocationsBefore = repository.invocationCount();

        final ServerWebExchange exchange = buildExchange(deterministicChat("hello!"));
        final ServiceRequestExecutor executor = downstreamExecutor();
        final ResponseEntity<?> response = invoke(exchange, executor).block(java.time.Duration.ofSeconds(10));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("downstream-ok", response.getBody());
        assertNull(QuotaReservation.from(exchange.getRequest()), "未启用时不得挂载凭据");
        assertEquals(invocationsBefore, repository.invocationCount(), "未启用时不得访问账本");
        assertTrue(exchange.getResponse().getHeaders().isEmpty(), "未启用时不得新增任何响应头");
    }

    @Test
    @DisplayName("账本不可用：fail-open 放行（200，不产生 5xx、无配额头）")
    void ledgerUnavailable_shouldFailOpen() throws Exception {
        registerApiKey(1L, 1L, 1);
        repository.setFailing(true);

        final ServerWebExchange exchange = buildExchange(deterministicChat("hello!"));
        final ServiceRequestExecutor executor = downstreamExecutor();
        final ResponseEntity<?> response = invoke(exchange, executor).block(java.time.Duration.ofSeconds(10));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("downstream-ok", response.getBody());
        assertNull(exchange.getResponse().getHeaders().getFirst(ServiceRequestHandler.QUOTA_HEADER_LIMIT));
        assertNull(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        verify(executor, times(1)).execute(any(), any(), any());
    }

    @Test
    @DisplayName("配额服务未装配：行为与 v3.0.x 一致（放行、无头、无凭据）")
    void quotaServiceAbsent_shouldBehaveAsBefore() throws Exception {
        injectQuotaService(null);
        registerApiKey(1L, 1L, 1);
        seedUsage(10, 100000L);

        final ServerWebExchange exchange = buildExchange(deterministicChat("hello!"));
        final ServiceRequestExecutor executor = downstreamExecutor();

        assertEquals(HttpStatus.OK, invoke(exchange, executor).block().getStatusCode());
        assertNull(QuotaReservation.from(exchange.getRequest()));
        assertTrue(exchange.getResponse().getHeaders().isEmpty());
    }

    @Test
    @DisplayName("缓存命中：在配额判定前短路，不消耗配额、不产生凭据")
    void cacheHit_shouldNotConsumeQuota() throws Exception {
        registerApiKey(1L, 0L, 0);
        final ResponseCacheService cacheService = buildResponseCache();
        injectResponseCacheService(cacheService);
        final ChatDTO.Request dto = deterministicChat("hello!");
        final String cacheKey = cacheService.buildKey(KEY_ID, ServiceType.chat, dto);
        assertNotNull(cacheKey, "确定性请求应生成缓存键");
        cacheService.store(cacheKey, Map.of("cached", "answer"));

        final ServerWebExchange exchange = buildExchange(dto);
        final ServiceRequestExecutor executor = downstreamExecutor();
        final ResponseEntity<?> response = invoke(exchange, executor).block(java.time.Duration.ofSeconds(10));

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(executor, never()).execute(any(), any(), any());
        assertNull(QuotaReservation.from(exchange.getRequest()), "缓存命中不应产生配额预留");
        assertEquals(0L, requestCount(QuotaWindow.DAY), "缓存命中不消耗日请求配额");
    }

    // ==================== issue #119: 预留中段失败回滚 ====================

    @Test
    @DisplayName("issue #119: selectInstance 失败：预留必须恰好回滚一次，不得泄漏额度")
    void selectInstanceFailure_shouldRollbackReservationExactlyOnce() throws Exception {
        registerApiKey(1L, 0L, 0);
        when(registry.selectInstance(any(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("no instance available"));
        final ServerWebExchange exchange = buildExchange(deterministicChat("hello!"));
        final ServiceRequestExecutor executor = downstreamExecutor();

        StepVerifier.create(invoke(exchange, executor))
                .expectError(IllegalStateException.class)
                .verify(Duration.ofSeconds(10));

        assertEquals(1, ledgerService.settleInvocations(), "预留回滚必须恰好触发一次");
        assertTrue(ledgerService.lastSettlement().failed(), "必须是失败回滚而非成功结算");
        assertEquals(0L, requestCount(QuotaWindow.DAY), "失败请求不得泄漏 DAY 请求数");
        assertEquals(0L, tokenCount(QuotaWindow.DAY), "失败请求不得泄漏 DAY token");
        final QuotaReservation reservation = QuotaReservation.from(exchange.getRequest());
        assertNotNull(reservation);
        assertTrue(reservation.isSettled(), "凭据应已结算");
        assertTrue(reservation.isFailed(), "凭据应标记为失败回滚");
    }

    @Test
    @DisplayName("issue #119: 处理器路径 settleFailure 与兜底 net 叠加仍只结算一次")
    void processorFailure_withSafetyNet_shouldSettleExactlyOnce() throws Exception {
        registerApiKey(1L, 0L, 0);
        final ServiceRequestExecutor executor = mock(ServiceRequestExecutor.class);
        // 模拟 NonStreamingRequestProcessor 既有的 doOnError 回滚（处理器内结算点）
        when(executor.execute(any(), any(), any())).thenAnswer(invocation -> {
            final ServerHttpRequest request = invocation.getArgument(2);
            return Mono.<ResponseEntity<?>>error(new IllegalStateException("downstream failed"))
                    .doOnError(error -> QuotaReservation.settleFailure(QuotaReservation.from(request)));
        });
        final ServerWebExchange exchange = buildExchange(deterministicChat("hello!"));

        StepVerifier.create(invoke(exchange, executor))
                .expectError(IllegalStateException.class)
                .verify(Duration.ofSeconds(10));

        assertEquals(1, ledgerService.settleInvocations(),
                "处理器回滚 + 处理器外兜底 net 不得重复冲正（恰一次结算）");
        assertEquals(0L, requestCount(QuotaWindow.DAY));
        assertEquals(0L, tokenCount(QuotaWindow.DAY));
    }

    // ==================== 辅助方法 ====================

    /**
     * 执行完整 handleRequest 流程（认证 + 配额 + 缓存 + 实例选择）。
     *
     * @param exchange 交换对象
     * @param executor 下游执行器
     * @return 响应 Mono
     */
    private Mono<ResponseEntity<?>> invoke(final ServerWebExchange exchange,
                                           final ServiceRequestExecutor executor) {
        return handler.handleRequest(ServiceEndpoint.CHAT, "gpt-4", BEARER, exchange, executor)
                .contextWrite(ReactiveSecurityContextHolder
                        .withSecurityContext(Mono.just(securityContext(KEY_ID, List.of("chat")))));
    }

    /**
     * 断言 429 的四个响应头。
     *
     * @param exchange   交换对象
     * @param limit      期望的 X-Quota-Limit
     * @param remaining  期望的 X-Quota-Remaining
     * @param window     期望的 X-Quota-Window
     * @param retryAfter 期望的 Retry-After
     */
    private void assertQuotaHeaders(final ServerWebExchange exchange, final String limit,
                                    final String remaining, final String window, final String retryAfter) {
        final HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals(limit, headers.getFirst(ServiceRequestHandler.QUOTA_HEADER_LIMIT));
        assertEquals(remaining, headers.getFirst(ServiceRequestHandler.QUOTA_HEADER_REMAINING));
        assertEquals(window, headers.getFirst(ServiceRequestHandler.QUOTA_HEADER_WINDOW));
        assertEquals(retryAfter, headers.getFirst(HttpHeaders.RETRY_AFTER));
    }

    /**
     * 直接向账本写入用量（构造“已接近限额”的前置状态）。
     *
     * @param requests         请求次数
     * @param tokensPerRequest 每次预留的 token 数
     */
    private void seedUsage(final long requests, final long tokensPerRequest) {
        for (long i = 0; i < requests; i++) {
            ledgerService.reserve(QuotaRequest.of(QuotaDimension.ofApiKey(KEY_ID), tokensPerRequest));
        }
    }

    /**
     * 注册带限额的 API Key。
     *
     * @param dailyRequestLimit  日请求限额（0 = 不限）
     * @param dailyTokenLimit    日 token 限额（0 = 不限）
     * @param rateLimitPerMinute 每分钟限额（0 = 不限）
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
     * 读取账本窗口请求数。
     *
     * @param window 窗口
     * @return 请求数
     */
    private long requestCount(final QuotaWindow window) {
        return ledgerService.usage(QuotaDimension.ofApiKey(KEY_ID), window)
            .map(QuotaUsage::requestCount).orElse(0L);
    }

    /**
     * 读取账本窗口 token 数。
     *
     * @param window 窗口
     * @return token 数
     */
    private long tokenCount(final QuotaWindow window) {
        return ledgerService.usage(QuotaDimension.ofApiKey(KEY_ID), window)
            .map(QuotaUsage::tokenCount).orElse(0L);
    }

    /**
     * 构建真实响应缓存组件（Caffeine 存储）。
     *
     * @return 缓存服务
     */
    private ResponseCacheService buildResponseCache() {
        final ResponseCacheProperties properties = new ResponseCacheProperties();
        properties.setEnabled(true);
        properties.setTtl(Duration.ofMinutes(5));
        return new ResponseCacheService(new CaffeineCacheStore(properties), properties, null);
    }

    /**
     * 反射注入可选依赖 quotaEnforcementService（沿用既有测试注入方式）。
     *
     * @param service 配额判定服务，可为 null
     */
    private void injectQuotaService(final QuotaEnforcementService service) throws Exception {
        final Field field = ServiceRequestHandler.class.getDeclaredField("quotaEnforcementService");
        field.setAccessible(true);
        field.set(handler, service);
    }

    /**
     * 反射注入可选依赖 responseCacheService（沿用既有测试注入方式）。
     *
     * @param service 响应缓存服务
     */
    private void injectResponseCacheService(final ResponseCacheService service) throws Exception {
        final Field field = ServiceRequestHandler.class.getDeclaredField("responseCacheService");
        field.setAccessible(true);
        field.set(handler, service);
    }

    /**
     * 构建真实 WebFlux 交换对象（含原始 DTO 属性）。
     *
     * @param dto 原始请求 DTO，可为 null
     * @return 交换对象
     */
    private ServerWebExchange buildExchange(final ChatDTO.Request dto) {
        final MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/v1/chat/completions")
                .build();
        final ServerWebExchange exchange = MockServerWebExchange.from(request);
        if (dto != null) {
            exchange.getAttributes().put(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE, dto);
        }
        return exchange;
    }

    /**
     * 构建真实安全上下文。
     *
     * @param keyId       API Key ID
     * @param permissions 权限列表
     * @return 安全上下文
     */
    private SecurityContextImpl securityContext(final String keyId, final List<String> permissions) {
        final ApiKeyAuthentication authentication = new ApiKeyAuthentication(keyId, "sk-" + keyId, permissions);
        authentication.setAuthenticated(true);
        final SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(authentication);
        return securityContext;
    }

    /**
     * 下游执行器（返回固定 200 响应体）。
     *
     * @return 执行器
     */
    private ServiceRequestExecutor downstreamExecutor() throws Exception {
        final ServiceRequestExecutor executor = mock(ServiceRequestExecutor.class);
        when(executor.execute(any(), any(), any()))
                .thenReturn(Mono.just(ResponseEntity.ok("downstream-ok")));
        return executor;
    }

    /**
     * 确定性 chat 请求（temperature=0、stream=false，满足响应缓存键生成条件）。
     *
     * @param content 用户消息
     * @return 请求 DTO
     */
    private ChatDTO.Request deterministicChat(final String content) {
        return new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", content, null)),
                false, 256, 0.0, null, null, null, null, null, null, null);
    }

    /**
     * 统计 settle 调用次数的真实账本（issue #119：验证回滚恰好一次）。
     *
     * <p>结算本身由 {@link org.unreal.modelrouter.auth.security.quota.QuotaReservation} 的
     * {@code AtomicBoolean} 保证恰一次，这里只数真正到达账本的调用次数。</p>
     */
    private static final class CountingQuotaLedgerService extends QuotaLedgerService {

        private final AtomicInteger settleInvocations = new AtomicInteger();

        private final AtomicReference<QuotaSettlement> lastSettlement = new AtomicReference<>();

        private CountingQuotaLedgerService(final QuotaLedgerRepository repository,
                                           final QuotaProperties properties,
                                           final Clock clock) {
            super(repository, properties, clock);
        }

        @Override
        public void settle(final QuotaSettlement settlement) {
            settleInvocations.incrementAndGet();
            lastSettlement.set(settlement);
            super.settle(settlement);
        }

        private int settleInvocations() {
            return settleInvocations.get();
        }

        private QuotaSettlement lastSettlement() {
            return lastSettlement.get();
        }
    }
}
