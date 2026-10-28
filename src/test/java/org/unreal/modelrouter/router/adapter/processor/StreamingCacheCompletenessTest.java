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
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.common.dto.ChatDTO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #721：只有「带终止标记」的流才允许写流式缓存。
 *
 * <p>修复前写入条件只有「{@code doOnComplete} 触发 + 块非空 + 未超预算」。上游被反向代理干净截断
 * （连接正常关闭、但没发 {@code [DONE]} / {@code finish_reason}）时，半截内容会被按默认 TTL 缓存，
 * 后续相同请求回放的都是缺尾输出，且没有任何日志或指标。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StreamingRequestProcessor 流式缓存完整性（#721）")
class StreamingCacheCompletenessTest {

    private static final String SSE_EVENT_TERMINATOR = "\n\n";

    private static final String CACHE_KEY = "stream-cache-key-721";

    private static final String FIRST_CHUNK = "{\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}";

    private static final String FINAL_CHUNK =
            "{\"model\":\"gpt-4\",\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}";

    private static final String TRUNCATED_CHUNK =
            "{\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"cut off mid-\"}}]}";

    private static final String DONE = "[DONE]";

    @Mock
    private ResponseTransformer responseTransformer;

    @Mock
    private ResponseCacheService responseCacheService;

    private StreamingRequestProcessor processor;
    private ModelRouterProperties.ModelInstance instance;

    @BeforeEach
    void setUp() {
        when(responseTransformer.transformStreamChunk(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        processor = new StreamingRequestProcessor(responseTransformer);
        ReflectionTestUtils.setField(processor, "responseCacheService", responseCacheService);

        instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-721");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://downstream.local");
    }

    @Test
    @DisplayName("#721 上游干净关闭但没有终止标记：不得写缓存")
    void truncatedStreamIsNotCached() {
        final ResponseEntity<?> response = invoke(FIRST_CHUNK, TRUNCATED_CHUNK);
        StepVerifier.create(sseBody(response)).expectNextCount(2).verifyComplete();

        verify(responseCacheService, after(500).never()).store(any(), any());
    }

    @Test
    @DisplayName("#721 带 [DONE] 的完整流：写缓存且 complete=true")
    void completeStreamIsCached() {
        final ResponseEntity<?> response = invoke(FIRST_CHUNK, FINAL_CHUNK, DONE);
        StepVerifier.create(sseBody(response)).expectNextCount(3).verifyComplete();

        final CachedStreamingResponse cached = captureCached();
        assertTrue(cached.complete(), "出现 [DONE] 的流必须标记 complete");
        assertEquals(DONE, cached.chunks().get(cached.chunks().size() - 1), "缓存块必须包含 [DONE]");
        assertEquals("stop", cached.finishReason(), "finish_reason 仍要照旧记下");
    }

    @Test
    @DisplayName("#721 只有 finish_reason 没有 [DONE]：同样视为完整")
    void finishReasonAloneCountsAsTerminator() {
        final ResponseEntity<?> response = invoke(FIRST_CHUNK, FINAL_CHUNK);
        StepVerifier.create(sseBody(response)).expectNextCount(2).verifyComplete();

        assertTrue(captureCached().complete(), "finish_reason 是协议级终止标记，等价于 [DONE]");
    }

    @Test
    @DisplayName("#721 Anthropic 的 message_stop：也认作终止标记")
    void anthropicMessageStopCountsAsTerminator() {
        final String stopEvent = "{\"type\":\"message_stop\"}";
        final ResponseEntity<?> response = invoke(FIRST_CHUNK, stopEvent);
        StepVerifier.create(sseBody(response)).expectNextCount(2).verifyComplete();

        assertTrue(captureCached().complete(), "message_stop 必须被识别为终止标记");
    }

    // ==================== 辅助 ====================

    private CachedStreamingResponse captureCached() {
        final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(responseCacheService, timeout(3000)).store(eq(CACHE_KEY), captor.capture());
        final Object value = captor.getValue();
        assertNotNull(value, "缓存值不应为空");
        assertTrue(value instanceof CachedStreamingResponse, "缓存值必须是 CachedStreamingResponse");
        return (CachedStreamingResponse) value;
    }

    private ResponseEntity<?> invoke(final String... chunks) {
        final WebClient client = clientOf(sseResponse(chunks));
        final ResponseEntity<?> response = processor.processStreamingRequest(
                chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
                instance, ServiceType.chat, "openai-compat", requestWithCacheKey())
                .block(Duration.ofSeconds(10));
        assertNotNull(response, "流式响应不应为空");
        return response;
    }

    private ClientResponse sseResponse(final String... chunks) {
        final Flux<DataBuffer> body = Flux.fromArray(chunks)
                .map(payload -> buffer("data: " + payload + SSE_EVENT_TERMINATOR));
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "text/event-stream")
                .body(body)
                .build();
    }

    private WebClient clientOf(final ClientResponse response) {
        return WebClient.builder().exchangeFunction(ignored -> Mono.just(response)).build();
    }

    private DataBuffer buffer(final String text) {
        return new DefaultDataBufferFactory().wrap(text.getBytes(StandardCharsets.UTF_8));
    }

    private ServerHttpRequest requestWithCacheKey() {
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put(ServiceRequestHandler.CACHE_KEY_ATTRIBUTE, CACHE_KEY);
        return new ServerHttpRequestDecorator(
                MockServerHttpRequest.post("/v1/chat/completions").build()) {
            @Override
            public Map<String, Object> getAttributes() {
                return attributes;
            }
        };
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
