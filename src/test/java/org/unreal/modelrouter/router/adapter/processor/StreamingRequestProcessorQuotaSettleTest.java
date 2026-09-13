package org.unreal.modelrouter.router.adapter.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
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
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * v3.1 PR-2: {@link StreamingRequestProcessor} 配额结算集成测试。
 *
 * <p>用真实 {@link WebClient}（自定义 {@code ExchangeFunction} 提供真实 SSE 响应体，无网络）
 * 驱动真实处理器与真实账本：验证流式成功路径在 token 落库点按 {@code 实际 − 估算} 冲正、
 * 中断/异常路径整笔回滚，以及结算恰一次。</p>
 *
 * <p>仅 {@link ResponseTransformer}（保持真实透传语义的桩）与 {@link TokenUsageService}
 * （DB 落库协作方）为 Mockito 桩，其余均为真实实现。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StreamingRequestProcessor 配额结算集成测试")
class StreamingRequestProcessorQuotaSettleTest {

    static {
        // v3.1 PR-2: 为所有无参验证设默认超时——否则 SSE 流不完成时 StepVerifier 会无限挂起
        StepVerifier.setDefaultTimeout(java.time.Duration.ofSeconds(10));
    }

    private static final String KEY_ID = "key-stream";
    private static final String KEY_HASH = "hash-key-stream";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 14, 13, 45, 30);

    /** 预留时使用的估算 token 数（结算后应被实际值替换） */
    private static final long ESTIMATED_TOKENS = 10L;

    /** 后端上报的实际 token 数 */
    private static final long REPORTED_TOKENS = 7L;

    /** SSE 事件结束符：空行分隔事件，缺省时相邻块会被合并为同一事件 */
    private static final String SSE_EVENT_TERMINATOR = "\n\n";

    @Mock
    private ResponseTransformer responseTransformer;

    @Mock
    private TokenUsageService tokenUsageService;

    private StreamingRequestProcessor processor;
    private InMemoryQuotaLedgerRepository repository;
    private QuotaProperties quotaProperties;
    private QuotaLedgerService ledgerService;
    private QuotaEnforcementService enforcementService;
    private ModelRouterProperties.ModelInstance instance;

    @BeforeEach
    void setUp() {
        when(responseTransformer.transformStreamChunk(anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        repository = InMemoryQuotaLedgerRepository.create();
        quotaProperties = new QuotaProperties();
        quotaProperties.setEnabled(true);

        final Map<String, ApiKey> apiKeyCache = new HashMap<>();
        final Map<String, String> keyIdIndex = new HashMap<>();
        apiKeyCache.put(KEY_HASH, ApiKey.builder().keyId(KEY_ID).keyHash(KEY_HASH).enabled(true).build());
        keyIdIndex.put(KEY_ID, KEY_HASH);
        final ApiKeyService apiKeyService = org.mockito.Mockito.mock(ApiKeyService.class);
        when(apiKeyService.getApiKeyCache()).thenReturn(apiKeyCache);
        when(apiKeyService.getKeyIdIndex()).thenReturn(keyIdIndex);

        ledgerService = new QuotaLedgerService(repository.proxy(), quotaProperties, clock);
        enforcementService = new QuotaEnforcementService(ledgerService, quotaProperties, apiKeyService, clock);

        processor = new StreamingRequestProcessor(responseTransformer);
        ReflectionTestUtils.setField(processor, "tokenUsageRecorder", new TokenUsageRecorder(tokenUsageService));
        ReflectionTestUtils.setField(processor, "apiKeyService", apiKeyService);

        instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-stream");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://downstream.local");
    }

    // ==================== 成功路径：按实际用量冲正 ====================

    @Test
    @DisplayName("流式成功：在 token 落库点按 实际 − 估算 冲正，且只结算一次")
    void streamingSuccess_shouldReconcileTokensOnce() {
        final ServerHttpRequest httpRequest = requestWithReservation(ESTIMATED_TOKENS);
        assertEquals(ESTIMATED_TOKENS, tokenCount(QuotaWindow.MINUTE), "预留时应记估算值");

        final WebClient client = clientOf(sseResponse(
            "data: {\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}",
            "data: {\"model\":\"gpt-4\",\"choices\":[],\"usage\":{\"prompt_tokens\":3,"
                + "\"completion_tokens\":4,\"total_tokens\":7}}"));
        final ResponseEntity<?> response = processor.processStreamingRequest(
            chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
            instance, ServiceType.chat, "openai-compat", httpRequest).block(java.time.Duration.ofSeconds(10));

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        StepVerifier.create(sseBody(response))
            .expectNextCount(2)
            .verifyComplete();

        final QuotaReservation reservation = QuotaReservation.from(httpRequest);
        assertTrue(reservation.isSettled(), "流式完成后应已结算");
        assertFalse(reservation.isFailed(), "正常完成应为成功结算");
        assertEquals(REPORTED_TOKENS, tokenCount(QuotaWindow.MINUTE), "结算后 token 应为实际用量");
        assertEquals(1L, requestCount(QuotaWindow.MINUTE), "成功结算不改请求数");
        assertEquals(REPORTED_TOKENS, tokenCount(QuotaWindow.DAY), "四级窗口同步冲正");
    }

    @Test
    @DisplayName("流式无 usage：回退内容估算并冲正（实际 = 估算字符数）")
    void streamingWithoutUsage_shouldReconcileWithEstimatedContent() {
        final ServerHttpRequest httpRequest = requestWithReservation(ESTIMATED_TOKENS);
        final WebClient client = clientOf(sseResponse(
            "data: {\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"你好世界\"}}]}"));

        final ResponseEntity<?> response = processor.processStreamingRequest(
            chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
            instance, ServiceType.chat, "openai-compat", httpRequest).block(java.time.Duration.ofSeconds(10));

        StepVerifier.create(sseBody(response)).expectNextCount(1).verifyComplete();

        // 4 个中文字符 → ceil(4 / 2) = 2 token
        assertEquals(2L, tokenCount(QuotaWindow.MINUTE));
        assertEquals(1L, requestCount(QuotaWindow.MINUTE));
        assertFalse(QuotaReservation.from(httpRequest).isFailed());
    }

    // ==================== 失败 / 中断路径：整笔回滚 ====================

    @Test
    @DisplayName("流式异常：回滚整笔预留（请求数与 token 归零），且不抛出异常")
    void streamingError_shouldRollbackReservation() {
        final ServerHttpRequest httpRequest = requestWithReservation(ESTIMATED_TOKENS);
        final WebClient client = WebClient.builder()
            .exchangeFunction(ignored -> Mono.error(new IllegalStateException("下游不可用")))
            .build();

        final ResponseEntity<?> response = processor.processStreamingRequest(
            chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
            instance, ServiceType.chat, "openai-compat", httpRequest).block(java.time.Duration.ofSeconds(10));

        StepVerifier.create(sseBody(response))
            .expectErrorMatches(error -> error instanceof IllegalStateException)
            .verify();

        final QuotaReservation reservation = QuotaReservation.from(httpRequest);
        assertTrue(reservation.isSettled());
        assertTrue(reservation.isFailed(), "异常路径应按失败回滚");
        assertEquals(0L, requestCount(QuotaWindow.MINUTE), "回滚后请求数归零");
        assertEquals(0L, tokenCount(QuotaWindow.MINUTE), "回滚后 token 归零");
    }

    @Test
    @DisplayName("流式中断（订阅取消）：回滚整笔预留")
    void streamingCancelled_shouldRollbackReservation() {
        final ServerHttpRequest httpRequest = requestWithReservation(ESTIMATED_TOKENS);
        final WebClient client = WebClient.builder()
            .exchangeFunction(ignored -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "text/event-stream")
                .body(Flux.concat(
                    Flux.just(buffer("data: {\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}"
                        + SSE_EVENT_TERMINATOR)),
                    Flux.never()))
                .build()))
            .build();

        final ResponseEntity<?> response = processor.processStreamingRequest(
            chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
            instance, ServiceType.chat, "openai-compat", httpRequest).block(java.time.Duration.ofSeconds(10));

        StepVerifier.create(sseBody(response))
            .expectNextCount(1)
            .thenCancel()
            .verify();

        final QuotaReservation reservation = QuotaReservation.from(httpRequest);
        assertTrue(reservation.isSettled());
        assertTrue(reservation.isFailed(), "中断应按失败回滚");
        assertEquals(0L, requestCount(QuotaWindow.MINUTE));
        assertEquals(0L, tokenCount(QuotaWindow.MINUTE));
    }

    // ==================== 不变量 ====================

    @Test
    @DisplayName("未预扣的请求（无凭据）：结算路径完全空操作，零账本访问")
    void withoutReservation_shouldBeNoOp() {
        final ServerHttpRequest httpRequest = mutableAttributeRequest();
        httpRequest.getAttributes().put(ServiceRequestHandler.API_KEY_ID_ATTRIBUTE, KEY_ID);
        assertNull(QuotaReservation.from(httpRequest));

        final WebClient client = clientOf(sseResponse(
            "data: {\"model\":\"gpt-4\",\"choices\":[],\"usage\":{\"prompt_tokens\":1,"
                + "\"completion_tokens\":1,\"total_tokens\":2}}"));
        final ResponseEntity<?> response = processor.processStreamingRequest(
            chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
            instance, ServiceType.chat, "openai-compat", httpRequest).block(java.time.Duration.ofSeconds(10));

        StepVerifier.create(sseBody(response)).expectNextCount(1).verifyComplete();

        assertEquals(0, repository.invocationCount(), "无预留时结算不得访问账本");
        assertEquals(0L, requestCount(QuotaWindow.MINUTE));
    }

    @Test
    @DisplayName("账本不可用：结算异常被吞掉，不影响流式响应")
    void ledgerUnavailable_shouldNotAffectStream() {
        final ServerHttpRequest httpRequest = requestWithReservation(ESTIMATED_TOKENS);
        repository.setFailing(true);

        final WebClient client = clientOf(sseResponse(
            "data: {\"model\":\"gpt-4\",\"choices\":[],\"usage\":{\"prompt_tokens\":1,"
                + "\"completion_tokens\":1,\"total_tokens\":2}}"));
        final ResponseEntity<?> response = processor.processStreamingRequest(
            chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
            instance, ServiceType.chat, "openai-compat", httpRequest).block(java.time.Duration.ofSeconds(10));

        StepVerifier.create(sseBody(response)).expectNextCount(1).verifyComplete();

        assertTrue(QuotaReservation.from(httpRequest).isSettled());
    }

    // ==================== 辅助方法 ====================

    /**
     * 生成带配额预留凭据的请求（走真实预留入口）。
     *
     * @param estimatedTokens 估算 token 数
     * @return 请求对象
     */
    private ServerHttpRequest requestWithReservation(final long estimatedTokens) {
        final ServerHttpRequest httpRequest = mutableAttributeRequest();
        httpRequest.getAttributes().put(ServiceRequestHandler.API_KEY_ID_ATTRIBUTE, KEY_ID);
        assertTrue(enforcementService.tryReserve(httpRequest, KEY_ID, estimatedTokens).isEmpty(),
            "预留应放行");
        assertNotNull(QuotaReservation.from(httpRequest));
        return httpRequest;
    }

    /**
     * 构建属性可写的 WebFlux 请求。
     *
     * <p>注意：独立构建的 {@code MockServerHttpRequest} 属性表是只读的（与真实 WebFlux 请求不同），
     * 因此必须经 {@code MockServerWebExchange} 获取请求，才能复现 handler 写入请求属性的行为
     * （仓库既有集成测试同样经由 exchange 取请求）。</p>
     *
     * @return 请求
     */
    private ServerHttpRequest mutableAttributeRequest() {
        return MockServerWebExchange.from(MockServerHttpRequest
            .post("/api/v1/chat/completions").build()).getRequest();
    }

    /**
     * 构建带固定 SSE 响应体的真实 WebClient（无网络，自定义 ExchangeFunction）。
     *
     * @param response 预置响应
     * @return WebClient
     */
    private WebClient clientOf(final ClientResponse response) {
        return WebClient.builder().exchangeFunction(ignored -> Mono.just(response)).build();
    }

    /**
     * 构建真实 SSE 响应（多块）。
     *
     * <p>每个块补齐 SSE 事件结束的空行：真机下游同样以空行分隔事件，缺少分隔符时相邻块会被
     * {@code ServerSentEventHttpMessageReader} 合并成一个事件（多块断言与“取消前先收到首块”
     * 的前提都会失效）。</p>
     *
     * @param chunks SSE 文本块
     * @return 响应
     */
    private ClientResponse sseResponse(final String... chunks) {
        final Flux<DataBuffer> body = Flux.fromArray(chunks).map(chunk -> buffer(chunk + SSE_EVENT_TERMINATOR));
        return ClientResponse.create(HttpStatus.OK)
            .header("Content-Type", "text/event-stream")
            .body(body)
            .build();
    }

    /**
     * 文本转 DataBuffer。
     *
     * @param text 文本
     * @return 数据缓冲
     */
    private DataBuffer buffer(final String text) {
        return new DefaultDataBufferFactory().wrap(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 取出流式响应体。
     *
     * @param response 响应实体
     * @return SSE 流
     */
    @SuppressWarnings("unchecked")
    private Flux<ServerSentEvent<String>> sseBody(final ResponseEntity<?> response) {
        return (Flux<ServerSentEvent<String>>) response.getBody();
    }

    /**
     * 构建 chat 请求对象。
     *
     * @return 请求
     */
    private ChatDTO.Request chatRequest() {
        return new ChatDTO.Request("gpt-4",
            List.of(new ChatDTO.Message("user", "hello", null)),
            true, 256, 0.0, null, null, null, null, null, null, null);
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
