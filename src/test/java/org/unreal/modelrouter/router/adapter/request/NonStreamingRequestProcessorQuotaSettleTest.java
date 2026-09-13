package org.unreal.modelrouter.router.adapter.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.quota.InMemoryQuotaLedgerRepository;
import org.unreal.modelrouter.auth.security.quota.QuotaDimension;
import org.unreal.modelrouter.auth.security.quota.QuotaEnforcementService;
import org.unreal.modelrouter.auth.security.quota.QuotaLedgerService;
import org.unreal.modelrouter.auth.security.quota.QuotaProperties;
import org.unreal.modelrouter.auth.security.quota.QuotaReservation;
import org.unreal.modelrouter.auth.security.quota.QuotaUsage;
import org.unreal.modelrouter.auth.security.quota.QuotaWindow;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.monitor.service.TokenUsageRecorder;
import org.unreal.modelrouter.monitor.service.TokenUsageService;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * v3.1 PR-2: {@link NonStreamingRequestProcessor} 配额结算集成测试。
 *
 * <p>用真实 {@link WebClient}（自定义 {@code ExchangeFunction}，无网络）驱动真实处理器 +
 * 真实 {@link TokenUsageExtractor} + 真实账本，验证非流式在 token 落库点结算、
 * 下游错误时回滚，以及无 usage 响应时保留预留估算值（不错误冲正为 0）。</p>
 *
 * <p>仅 {@link TokenUsageService}（DB 落库协作方）为 Mockito 桩。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NonStreamingRequestProcessor 配额结算集成测试")
class NonStreamingRequestProcessorQuotaSettleTest {

    private static final String KEY_ID = "key-nonstream";
    private static final String KEY_HASH = "hash-key-nonstream";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);

    /** 预留时使用的估算 token 数 */
    private static final long ESTIMATED_TOKENS = 10L;

    /** 下游上报的实际 token 数 */
    private static final long REPORTED_TOKENS = 7L;

    @Mock
    private TokenUsageService tokenUsageService;

    private NonStreamingRequestProcessor processor;
    private InMemoryQuotaLedgerRepository repository;
    private QuotaProperties quotaProperties;
    private QuotaLedgerService ledgerService;
    private QuotaEnforcementService enforcementService;
    private ModelRouterProperties.ModelInstance instance;

    @BeforeEach
    void setUp() {
        final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        repository = InMemoryQuotaLedgerRepository.create();
        quotaProperties = new QuotaProperties();
        quotaProperties.setEnabled(true);

        final Map<String, ApiKey> apiKeyCache = new HashMap<>();
        final Map<String, String> keyIdIndex = new HashMap<>();
        apiKeyCache.put(KEY_HASH, ApiKey.builder().keyId(KEY_ID).keyHash(KEY_HASH).enabled(true).build());
        keyIdIndex.put(KEY_ID, KEY_HASH);
        final ApiKeyService apiKeyService = mock(ApiKeyService.class);
        when(apiKeyService.getApiKeyCache()).thenReturn(apiKeyCache);
        when(apiKeyService.getKeyIdIndex()).thenReturn(keyIdIndex);

        ledgerService = new QuotaLedgerService(repository.proxy(), quotaProperties, clock);
        enforcementService = new QuotaEnforcementService(ledgerService, quotaProperties, apiKeyService, clock);

        final ObjectMapper objectMapper = new ObjectMapper();
        // apiKeyService / poolSelector / metricsCollector 传 null：配额结算不依赖它们
        final TokenUsageExtractor tokenUsageExtractor = new TokenUsageExtractor(objectMapper,
            new TokenUsageRecorder(tokenUsageService), null, null, null);
        processor = new NonStreamingRequestProcessor(objectMapper, null, null, tokenUsageExtractor);

        instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-nonstream");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://downstream.local");
    }

    // ==================== 成功路径：按实际用量冲正 ====================

    @Test
    @DisplayName("非流式成功：token 落库点按 实际 − 估算 冲正，请求数不变")
    void nonStreamingSuccess_shouldReconcileTokens() {
        final ServerHttpRequest httpRequest = requestWithReservation();
        assertEquals(ESTIMATED_TOKENS, tokenCount(QuotaWindow.MINUTE), "预留时应记估算值");

        final WebClient client = clientOf(jsonResponse(HttpStatus.OK, chatJson(REPORTED_TOKENS)));
        final ResponseEntity<?> response = invoke(client, httpRequest).block(java.time.Duration.ofSeconds(10));

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(REPORTED_TOKENS, tokenCount(QuotaWindow.MINUTE), "结算后应为实际用量");
        assertEquals(1L, requestCount(QuotaWindow.MINUTE), "成功结算不改请求数");
        assertEquals(REPORTED_TOKENS, tokenCount(QuotaWindow.DAY));

        final QuotaReservation reservation = QuotaReservation.from(httpRequest);
        assertTrue(reservation.isSettled());
        assertFalse(reservation.isFailed());
    }

    @Test
    @DisplayName("响应无 usage：保留预留估算值（不冲正为 0），请求数照记")
    void responseWithoutUsage_shouldKeepReservation() {
        final ServerHttpRequest httpRequest = requestWithReservation();

        final WebClient client = clientOf(jsonResponse(HttpStatus.OK,
            "{\"model\":\"gpt-4\",\"choices\":[]}"));
        final ResponseEntity<?> response = invoke(client, httpRequest).block(java.time.Duration.ofSeconds(10));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ESTIMATED_TOKENS, tokenCount(QuotaWindow.MINUTE), "无实际用量时保留估算值");
        assertEquals(1L, requestCount(QuotaWindow.MINUTE));
        assertFalse(QuotaReservation.from(httpRequest).isSettled(), "未取到实际用量则不结算");
    }

    // ==================== 失败路径：整笔回滚 ====================

    @Test
    @DisplayName("下游 5xx：回滚整笔预留（请求数与 token 归零）")
    void downstreamServerError_shouldRollbackReservation() {
        final ServerHttpRequest httpRequest = requestWithReservation();
        final WebClient client = clientOf(jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
            "{\"error\":\"boom\"}"));

        StepVerifier.create(invoke(client, httpRequest))
            .expectErrorMatches(error -> error instanceof org.springframework.web.server.ResponseStatusException)
            .verify(java.time.Duration.ofSeconds(10));

        final QuotaReservation reservation = QuotaReservation.from(httpRequest);
        assertTrue(reservation.isSettled());
        assertTrue(reservation.isFailed(), "下游错误应按失败回滚");
        assertEquals(0L, requestCount(QuotaWindow.MINUTE), "回滚后请求数归零");
        assertEquals(0L, tokenCount(QuotaWindow.MINUTE), "回滚后 token 归零");
    }

    @Test
    @DisplayName("传输异常：回滚整笔预留且不抛出额外异常")
    void transportError_shouldRollbackReservation() {
        final ServerHttpRequest httpRequest = requestWithReservation();
        final WebClient client = WebClient.builder()
            .exchangeFunction(ignored -> Mono.error(new IllegalStateException("下游不可用")))
            .build();

        StepVerifier.create(invoke(client, httpRequest))
            .expectErrorMatches(error -> error instanceof IllegalStateException)
            .verify(java.time.Duration.ofSeconds(10));

        final QuotaReservation reservation = QuotaReservation.from(httpRequest);
        assertTrue(reservation.isFailed());
        assertEquals(0L, requestCount(QuotaWindow.MINUTE));
        assertEquals(0L, tokenCount(QuotaWindow.MINUTE));
    }

    // ==================== 不变量 ====================

    @Test
    @DisplayName("未预扣的请求（无凭据）：结算路径完全空操作，零账本访问")
    void withoutReservation_shouldBeNoOp() {
        final ServerHttpRequest httpRequest = mutableAttributeRequest();
        httpRequest.getAttributes().put(ServiceRequestHandler.API_KEY_ID_ATTRIBUTE, KEY_ID);

        final WebClient client = clientOf(jsonResponse(HttpStatus.OK, chatJson(REPORTED_TOKENS)));
        final ResponseEntity<?> response = invoke(client, httpRequest).block(java.time.Duration.ofSeconds(10));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, repository.invocationCount(), "无预留时结算不得访问账本");
        assertEquals(0L, requestCount(QuotaWindow.MINUTE));
    }

    @Test
    @DisplayName("账本不可用：结算异常被吞掉，不影响非流式响应")
    void ledgerUnavailable_shouldNotAffectResponse() {
        final ServerHttpRequest httpRequest = requestWithReservation();
        repository.setFailing(true);

        final WebClient client = clientOf(jsonResponse(HttpStatus.OK, chatJson(REPORTED_TOKENS)));
        final ResponseEntity<?> response = invoke(client, httpRequest).block(java.time.Duration.ofSeconds(10));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(QuotaReservation.from(httpRequest).isSettled());
    }

    // ==================== 辅助方法 ====================

    /**
     * 执行非流式请求（String 响应类型，无 multipart）。
     *
     * @param client      WebClient
     * @param httpRequest 请求
     * @return 响应 Mono
     */
    private Mono<? extends ResponseEntity<?>> invoke(final WebClient client, final ServerHttpRequest httpRequest) {
        return processor.processRequest(
            chatRequest(),
            "Bearer downstream",
            client,
            "/v1/chat/completions",
            instance,
            ServiceType.chat,
            String.class,
            "openai-compat",
            request -> request,
            response -> response,
            null,
            httpRequest,
            null);
    }

    /**
     * 生成带配额预留凭据的请求（走真实预留入口）。
     *
     * @return 请求对象
     */
    private ServerHttpRequest requestWithReservation() {
        final ServerHttpRequest httpRequest = mutableAttributeRequest();
        httpRequest.getAttributes().put(ServiceRequestHandler.API_KEY_ID_ATTRIBUTE, KEY_ID);
        assertTrue(enforcementService.tryReserve(httpRequest, KEY_ID, ESTIMATED_TOKENS).isEmpty());
        assertNotNull(QuotaReservation.from(httpRequest));
        return httpRequest;
    }

    /**
     * 构建属性可写的 WebFlux 请求（独立 MockServerHttpRequest 的属性表只读，
     * 与真实 WebFlux 请求不同，故经 MockServerWebExchange 获取）。
     *
     * @return 请求
     */
    private ServerHttpRequest mutableAttributeRequest() {
        return MockServerWebExchange.from(MockServerHttpRequest
            .post("/api/v1/chat/completions").build()).getRequest();
    }

    /**
     * 构建带固定响应的真实 WebClient（无网络）。
     *
     * @param response 预置响应
     * @return WebClient
     */
    private WebClient clientOf(final ClientResponse response) {
        return WebClient.builder().exchangeFunction(ignored -> Mono.just(response)).build();
    }

    /**
     * 构建 JSON 响应。
     *
     * @param status 状态码
     * @param body   响应体
     * @return 响应
     */
    private ClientResponse jsonResponse(final HttpStatus status, final String body) {
        return ClientResponse.create(status)
            .header("Content-Type", "application/json")
            .body(body)
            .build();
    }

    /**
     * 构建带 usage 的 chat 响应 JSON。
     *
     * @param totalTokens 总 token 数
     * @return JSON
     */
    private String chatJson(final long totalTokens) {
        return "{\"id\":\"chatcmpl-1\",\"model\":\"gpt-4\",\"choices\":[{\"index\":0,"
            + "\"message\":{\"role\":\"assistant\",\"content\":\"hi\"},\"finish_reason\":\"stop\"}],"
            + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4,\"total_tokens\":" + totalTokens + "}}";
    }

    /**
     * 构建 chat 请求对象。
     *
     * @return 请求
     */
    private ChatDTO.Request chatRequest() {
        return new ChatDTO.Request("gpt-4",
            List.of(new ChatDTO.Message("user", "hello", null)),
            false, 256, 0.0, null, null, null, null, null, null, null);
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
}
