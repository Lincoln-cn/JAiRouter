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
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * #720：客户端要求 {@code stream_options.include_usage} 时，网关必须在 {@code [DONE]} 之前补一个
 * usage 帧（OpenAI 语义：{@code choices} 为空数组）。
 *
 * <p>修复前 {@code ChatDTO.Request} 没有该字段，Jackson 的
 * {@code FAIL_ON_UNKNOWN_PROPERTIES=false} 让它被静默丢弃，客户端永远拿不到 usage，
 * 只能在本地按字估算。</p>
 *
 * <p>覆盖三条：开启时补帧且只补一次；未开启时一个字节都不加；上游自带 usage 帧时不重复补。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StreamingRequestProcessor 流式 usage 帧（#720）")
class StreamingIncludeUsageFrameTest {

    private static final String SSE_EVENT_TERMINATOR = "\n\n";

    private static final String DONE = "[DONE]";

    private static final String FIRST_CHUNK =
            "{\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}";

    private static final String SECOND_CHUNK =
            "{\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"there\"}}]}";

    private static final String UPSTREAM_USAGE_CHUNK =
            "{\"model\":\"gpt-4\",\"choices\":[],\"usage\":{\"prompt_tokens\":9,"
                    + "\"completion_tokens\":4,\"total_tokens\":13}}";

    @Mock
    private ResponseTransformer responseTransformer;

    private StreamingRequestProcessor processor;
    private ModelRouterProperties.ModelInstance instance;

    @BeforeEach
    void setUp() {
        when(responseTransformer.transformStreamChunk(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        processor = new StreamingRequestProcessor(responseTransformer);

        instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-720");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://downstream.local");
    }

    @Test
    @DisplayName("#720 include_usage=true：chunk 之后、[DONE] 之前补一帧 usage")
    void includeUsageAddsFrameBeforeDone() {
        final List<String> data = collect(true, FIRST_CHUNK, SECOND_CHUNK, DONE);

        assertEquals(4, data.size(), "两个内容块 + usage 帧 + [DONE]");
        assertEquals(FIRST_CHUNK, data.get(0));
        assertEquals(SECOND_CHUNK, data.get(1));
        assertTrue(data.get(2).contains("\"usage\""), "第三帧必须是 usage 帧，实际: " + data.get(2));
        assertTrue(data.get(2).contains("\"choices\":[]"), "usage 帧的 choices 必须是空数组");
        assertTrue(data.get(2).contains("\"prompt_tokens\""), "usage 帧必须带 prompt_tokens");
        assertEquals(DONE, data.get(3), "[DONE] 必须排在最后");
    }

    @Test
    @DisplayName("#720 include_usage 未开启：流内容与改动前完全一致")
    void withoutIncludeUsageLeavesStreamUntouched() {
        final List<String> data = collect(false, FIRST_CHUNK, SECOND_CHUNK, DONE);

        assertEquals(3, data.size(), "未开启时不得新增任何帧");
        assertEquals(FIRST_CHUNK, data.get(0));
        assertEquals(SECOND_CHUNK, data.get(1));
        assertEquals(DONE, data.get(2));
        assertFalse(data.stream().anyMatch(item -> item.contains("\"usage\"")),
                "未开启时不得出现 usage 帧");
    }

    @Test
    @DisplayName("#720 上游已自带 usage 帧：只透传，不重复补，[DONE] 仍只出现一次")
    void upstreamUsageFrameIsNotDuplicated() {
        final List<String> data = collect(true, FIRST_CHUNK, UPSTREAM_USAGE_CHUNK, DONE);

        assertEquals(3, data.size(), "内容块 + 上游 usage 帧 + [DONE]");
        assertEquals(1, data.stream().filter(item -> item.contains("\"usage\"")).count(),
                "usage 帧不得重复");
        assertEquals(DONE, data.get(data.size() - 1), "[DONE] 必须只出现一次且在最后");
    }

    // ==================== 辅助 ====================

    private List<String> collect(final boolean includeUsage, final String... chunks) {
        final ServerHttpRequest request = requestWithAttribute(includeUsage);
        final WebClient client = clientOf(sseResponse(chunks));

        final ResponseEntity<?> response = processor.processStreamingRequest(
                chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
                instance, ServiceType.chat, "openai-compat", request).block(Duration.ofSeconds(10));

        assertNotNull(response, "流式响应不应为空");
        final List<String> data = new ArrayList<>();
        StepVerifier.create(sseBody(response))
                .recordWith(ArrayList::new)
                .thenConsumeWhile(ignored -> true)
                .consumeRecordedWith(recorded -> recorded.forEach(event -> data.add(event.data())))
                .verifyComplete();
        return data;
    }

    private ServerHttpRequest requestWithAttribute(final boolean includeUsage) {
        final Map<String, Object> attributes = new HashMap<>();
        if (includeUsage) {
            attributes.put(StreamingRequestProcessor.INCLUDE_USAGE_ATTRIBUTE, Boolean.TRUE);
        }
        return new ServerHttpRequestDecorator(
                MockServerHttpRequest.post("/v1/chat/completions").build()) {
            @Override
            public Map<String, Object> getAttributes() {
                return attributes;
            }
        };
    }

    private WebClient clientOf(final ClientResponse response) {
        return WebClient.builder().exchangeFunction(ignored -> Mono.just(response)).build();
    }

    private ClientResponse sseResponse(final String... chunks) {
        // SSE 帧必须是 `data: <payload>` 形式；事件里的 data() 拿到的是 payload 本身
        final Flux<DataBuffer> body = Flux.fromArray(chunks)
                .map(payload -> buffer("data: " + payload + SSE_EVENT_TERMINATOR));
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
