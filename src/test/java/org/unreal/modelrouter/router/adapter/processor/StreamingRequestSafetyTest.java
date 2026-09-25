package org.unreal.modelrouter.router.adapter.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.config.core.StreamingSafetyProperties;
import org.unreal.modelrouter.monitor.callhistory.ApiCallHistoryRecorder;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.config.RecordLevel;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO;
import org.unreal.modelrouter.monitor.dto.TokenUsageRecordDTO;
import org.unreal.modelrouter.monitor.service.TokenUsageRecorder;
import org.unreal.modelrouter.monitor.service.TokenUsageService;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.cache.CachedStreamingResponse;
import org.unreal.modelrouter.router.cache.ResponseCacheService;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * v3.2.3: 流式累积上界（#127）与上游块间空闲看门狗（#126）测试。
 *
 * <p>真实 {@link WebClient}（自定义 {@code ExchangeFunction}，无网络）驱动真实处理器；
 * 覆盖：超上界响应保留有界前缀 + 显式截断标记、token/配额数字仍按全量内容精确、
 * 超缓存预算不写缓存；上界之下行为逐字节不变。空闲看门狗：静默上游触发
 * cancel + 终止错误，长但持续产出的流不受影响。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.3
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StreamingRequestProcessor 流式安全（#126, #127）")
class StreamingRequestSafetyTest {

    static {
        StepVerifier.setDefaultTimeout(Duration.ofSeconds(10));
    }

    private static final String SSE_EVENT_TERMINATOR = "\n\n";

    @Mock
    private ResponseTransformer responseTransformer;

    @Mock
    private TokenUsageService tokenUsageService;

    @Mock
    private ResponseCacheService responseCacheService;

    @Mock
    private ApiCallHistoryRecorder callHistoryRecorder;

    private StreamingRequestProcessor processor;
    private StreamingSafetyProperties safetyProperties;
    private CallHistoryProperties callHistoryProperties;
    private ModelRouterProperties.ModelInstance instance;

    @BeforeEach
    void setUp() {
        when(responseTransformer.transformStreamChunk(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        safetyProperties = new StreamingSafetyProperties();
        callHistoryProperties = new CallHistoryProperties();
        callHistoryProperties.setRecordLevel(RecordLevel.FULL);
        callHistoryProperties.setMaxContentLength(1 << 20);

        processor = new StreamingRequestProcessor(responseTransformer);
        ReflectionTestUtils.setField(processor, "tokenUsageRecorder", new TokenUsageRecorder(tokenUsageService));
        ReflectionTestUtils.setField(processor, "responseCacheService", responseCacheService);
        ReflectionTestUtils.setField(processor, "callHistoryRecorder", callHistoryRecorder);
        ReflectionTestUtils.setField(processor, "callHistoryProperties", callHistoryProperties);
        ReflectionTestUtils.setField(processor, "streamingSafety", safetyProperties);

        instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-safety");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://downstream.local");
    }

    // ==================== #127: 累积上界 ====================

    @Test
    @DisplayName("#127 超上界：保留有界前缀 + 显式截断标记，token/配额仍按全量精确，超预算不缓存")
    void overCap_boundsContent_marksTruncation_keepsAccountingExact_andSkipsCache() {
        safetyProperties.setMaxContentChars(200);
        safetyProperties.setMaxCacheChars(50);

        // 40 块 × 20 英文字符 = 800 字符全量内容 ≈ 200 tokens（800/4）
        final String[] chunks = contentChunks(40, 20);
        final long expectedTokens = 200L;

        final ServerHttpRequest httpRequest = requestWithCacheKey();
        final WebClient client = clientOf(sseResponse(chunks));

        final ResponseEntity<?> response = processor.processStreamingRequest(
                chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
                instance, ServiceType.chat, "openai-compat", httpRequest).block(Duration.ofSeconds(10));
        assertNotNull(response);
        StepVerifier.create(sseBody(response)).expectNextCount(chunks.length).verifyComplete();

        // (i) retained content bounded + (ii) truncation marked
        final String recordedBody = recordedResponseBody();
        assertNotNull(recordedBody, "FULL 记录级别下应记录响应体");
        assertTrue(recordedBody.length() <= 200 + StreamingRequestProcessor.TRUNCATION_MARKER.length(),
                "记录内容应有界: " + recordedBody.length());
        assertTrue(recordedBody.contains(StreamingRequestProcessor.TRUNCATION_MARKER),
                "截断应显式标记");
        assertTrue(recordedBody.startsWith("a".repeat(20)), "应保留有界前缀");

        // (iii) accounting exact — 估算来自独立计数（全量 800 字符 → 200 token），不受截断影响
        final ArgumentCaptor<TokenUsageRecordDTO> usageCaptor = ArgumentCaptor.forClass(TokenUsageRecordDTO.class);
        verify(tokenUsageService, timeout(3000)).recordTokenUsage(usageCaptor.capture());
        assertEquals(expectedTokens, usageCaptor.getValue().getCompletionTokens(),
                "completion 估算必须等于全量内容估算（截断不得压低账单）");
        assertEquals(expectedTokens, usageCaptor.getValue().getTotalTokens());

        // (iv) over-budget responses are not cached
        verifyNoInteractions(responseCacheService);
    }

    @Test
    @DisplayName("#127 上界之下：行为不变（内容/缓存/估算与截断前逐字节一致）")
    void underCap_isUnchanged_cachesAllChunks_estimationMatchesFullContent() {
        safetyProperties.setMaxContentChars(10_000);
        safetyProperties.setMaxCacheChars(10_000);

        final String[] chunks = contentChunks(3, 20);
        final String fullContent = "a".repeat(60);
        final long expectedTokens = 15L; // 60 / 4

        final ServerHttpRequest httpRequest = requestWithCacheKey();
        final WebClient client = clientOf(sseResponse(chunks));

        final ResponseEntity<?> response = processor.processStreamingRequest(
                chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
                instance, ServiceType.chat, "openai-compat", httpRequest).block(Duration.ofSeconds(10));
        assertNotNull(response);
        StepVerifier.create(sseBody(response)).expectNextCount(chunks.length).verifyComplete();

        // 记录内容 = 完整内容，无截断标记
        final String recordedBody = recordedResponseBody();
        assertEquals(fullContent, recordedBody, "上界之下记录内容应与全量一致");
        assertFalse(recordedBody.contains(StreamingRequestProcessor.TRUNCATION_MARKER));

        // 估算与 estimateTokens(全量) 一致
        final ArgumentCaptor<TokenUsageRecordDTO> usageCaptor = ArgumentCaptor.forClass(TokenUsageRecordDTO.class);
        verify(tokenUsageService, timeout(3000)).recordTokenUsage(usageCaptor.capture());
        assertEquals(expectedTokens, usageCaptor.getValue().getCompletionTokens());
        assertEquals(estimateTokens(fullContent), usageCaptor.getValue().getCompletionTokens(),
                "独立计数估算必须与完整内容估算逐字节一致");

        // 全部块都写入缓存
        final ArgumentCaptor<Object> cacheCaptor = ArgumentCaptor.forClass(Object.class);
        verify(responseCacheService).store(eq("cache-key-safety"), cacheCaptor.capture());
        final CachedStreamingResponse cached = (CachedStreamingResponse) cacheCaptor.getValue();
        assertEquals(chunks.length, cached.chunks().size(), "上界之下应缓存全部块");
    }

    @Test
    @DisplayName("#127 后端自带 usage：账单数字直接来自 usage，与累积缓冲无关")
    void withBackendUsage_accountingUnaffectedByTruncation() {
        safetyProperties.setMaxContentChars(50);
        safetyProperties.setMaxCacheChars(1 << 20);

        final String[] chunks = new String[] {
            "data: {\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"" + "b".repeat(200) + "\"}}]}",
            "data: {\"model\":\"gpt-4\",\"choices\":[],\"usage\":{\"prompt_tokens\":10,"
                + "\"completion_tokens\":42,\"total_tokens\":52}}"
        };

        final ServerHttpRequest httpRequest = requestWithCacheKey();
        final WebClient client = clientOf(sseResponse(chunks));

        final ResponseEntity<?> response = processor.processStreamingRequest(
                chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
                instance, ServiceType.chat, "openai-compat", httpRequest).block(Duration.ofSeconds(10));
        assertNotNull(response);
        StepVerifier.create(sseBody(response)).expectNextCount(2).verifyComplete();

        final ArgumentCaptor<TokenUsageRecordDTO> usageCaptor = ArgumentCaptor.forClass(TokenUsageRecordDTO.class);
        verify(tokenUsageService, timeout(3000)).recordTokenUsage(usageCaptor.capture());
        assertEquals(42L, usageCaptor.getValue().getCompletionTokens(), "usage.completion_tokens 原样记账");
        assertEquals(52L, usageCaptor.getValue().getTotalTokens(), "usage.total_tokens 原样记账");
    }

    // ==================== #126: 块间空闲看门狗 ====================

    @Test
    @DisplayName("#126 静默上游：空闲超时后发出终止错误并回滚配额（不挂死）")
    void silentUpstream_timesOut_emitsTerminalError() {
        safetyProperties.setIdleTimeout(Duration.ofMillis(200));

        final ServerHttpRequest httpRequest = mutableAttributeRequest();
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
                instance, ServiceType.chat, "openai-compat", httpRequest).block(Duration.ofSeconds(10));
        assertNotNull(response);

        StepVerifier.create(sseBody(response))
                .expectNextCount(1)
                .expectErrorMatches(error -> error instanceof java.util.concurrent.TimeoutException)
                .verify();
    }

    @Test
    @DisplayName("#126 长但持续产出：块间空闲不超时，流完整送达")
    void longActiveStream_isNotCut() {
        safetyProperties.setIdleTimeout(Duration.ofMillis(400));

        // 20 块 × 50ms 间隔 = 总时长 ~1s > idleTimeout，但块间 ~50ms << 400ms
        final Flux<DataBuffer> body = Flux.interval(Duration.ofMillis(50))
                .take(20)
                .map(i -> buffer("data: {\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"t\"}}]}"
                        + SSE_EVENT_TERMINATOR));

        final ServerHttpRequest httpRequest = mutableAttributeRequest();
        final WebClient client = WebClient.builder()
                .exchangeFunction(ignored -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "text/event-stream")
                        .body(body)
                        .build()))
                .build();

        final ResponseEntity<?> response = processor.processStreamingRequest(
                chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
                instance, ServiceType.chat, "openai-compat", httpRequest).block(Duration.ofSeconds(10));
        assertNotNull(response);

        StepVerifier.create(sseBody(response))
                .expectNextCount(20)
                .verifyComplete();
    }

    @Test
    @DisplayName("#126 空闲超时禁用（PT0S）：静默上游不因看门狗被切断")
    void idleTimeoutDisabled_doesNotFire() {
        safetyProperties.setIdleTimeout(Duration.ZERO);

        final ServerHttpRequest httpRequest = mutableAttributeRequest();
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
                instance, ServiceType.chat, "openai-compat", httpRequest).block(Duration.ofSeconds(10));
        assertNotNull(response);

        StepVerifier.create(sseBody(response))
                .expectNextCount(1)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    // ==================== 辅助 ====================

    private String[] contentChunks(final int count, final int charsPerChunk) {
        final String[] chunks = new String[count];
        for (int i = 0; i < count; i++) {
            chunks[i] = "data: {\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\""
                    + "a".repeat(charsPerChunk) + "\"}}]}";
        }
        return chunks;
    }

    /** 与 StreamingRequestProcessor.estimateTokens 同系数的参考实现（断言用） */
    private long estimateTokens(final String content) {
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : content.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }
        return (long) Math.ceil(chineseChars / 2.0 + otherChars / 4.0);
    }

    private String recordedResponseBody() {
        final ArgumentCaptor<CallHistoryRecordDTO> captor = ArgumentCaptor.forClass(CallHistoryRecordDTO.class);
        // 落库在 boundedElastic 上异步执行（不得阻塞 EventLoop），用 timeout 等待
        verify(callHistoryRecorder, timeout(3000)).record(captor.capture());
        return captor.getValue().getResponseBody();
    }

    private ServerHttpRequest requestWithCacheKey() {
        final ServerHttpRequest httpRequest = mutableAttributeRequest();
        httpRequest.getAttributes().put(ServiceRequestHandler.CACHE_KEY_ATTRIBUTE, "cache-key-safety");
        return httpRequest;
    }

    private ServerHttpRequest mutableAttributeRequest() {
        return MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/chat/completions").build()).getRequest();
    }

    private WebClient clientOf(final ClientResponse response) {
        return WebClient.builder().exchangeFunction(ignored -> Mono.just(response)).build();
    }

    private ClientResponse sseResponse(final String... chunks) {
        final Flux<DataBuffer> body = Flux.fromArray(chunks).map(chunk -> buffer(chunk + SSE_EVENT_TERMINATOR));
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "text/event-stream")
                .body(body)
                .build();
    }

    private DataBuffer buffer(final String text) {
        return new DefaultDataBufferFactory().wrap(text.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private Flux<ServerSentEvent<String>> sseBody(final ResponseEntity<?> response) {
        return (Flux<ServerSentEvent<String>>) response.getBody();
    }

    private ChatDTO.Request chatRequest() {
        return new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", "hello", null)),
                true, 256, 0.0, null, null, null, null, null, null, null);
    }
}
