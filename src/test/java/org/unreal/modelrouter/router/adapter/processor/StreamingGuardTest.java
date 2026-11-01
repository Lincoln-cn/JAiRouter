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
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.cache.ResponseCacheService;
import org.unreal.modelrouter.router.guard.GuardProperties;
import org.unreal.modelrouter.router.guard.StreamGuardService;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流式护栏在真实转发链路上的接线（#961）。
 *
 * <p>守住三件事：命中阻断时客户端收到的是终止帧而不是敏感内容；命中脱敏时替换后的文本才落到客户端；
 * 被阻断的流即便上游正常结束也不写缓存 —— 否则半截内容会被回放给后续请求，把「拦截」变成「静默丢失」。</p>
 *
 * @since 3.6.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StreamingRequestProcessor 流式护栏")
class StreamingGuardTest {

    private static final String SSE_EVENT_TERMINATOR = "\n\n";

    private static final String CACHE_KEY = "stream-cache-key-guard";

    private static final String FIRST_CHUNK =
            "{\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}";

    private static final String FINAL_CHUNK =
            "{\"model\":\"gpt-4\",\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}";

    private static final String PHONE_CHUNK =
            "{\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"call 13800138000\"}}]}";

    private static final String SECRET_CHUNK =
            "{\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"key sk-abcdef123456\"}}]}";

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
        instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-guard");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://downstream.local");
    }

    private StreamGuardService guard(final boolean enabled,
                                     final List<String> redactRegexes,
                                     final List<String> blockRegexes) {
        final GuardProperties properties = new GuardProperties();
        properties.setEnabled(enabled);
        properties.getRedact().setPatterns(patterns("phone", redactRegexes, "[PHONE]"));
        properties.getBlock().setPatterns(patterns("secret", blockRegexes, null));
        final StreamGuardService service = new StreamGuardService(properties, null);
        service.rebuild();
        ReflectionTestUtils.setField(processor, "streamGuardService", service);
        return service;
    }

    private static List<GuardProperties.Pattern> patterns(final String name,
                                                          final List<String> regexes,
                                                          final String replacement) {
        final List<GuardProperties.Pattern> list = new java.util.ArrayList<>();
        for (final String regex : regexes) {
            final GuardProperties.Pattern p = new GuardProperties.Pattern();
            p.setName(name);
            p.setRegex(regex);
            if (replacement != null) {
                p.setReplacement(replacement);
            }
            list.add(p);
        }
        return list;
    }

    @Test
    @DisplayName("命中阻断：客户端只收到终止帧，后续 chunk 不再下发")
    void abortReplacesRemainingStreamWithDoneFrame() {
        guard(true, List.of(), List.of("sk-[a-z0-9]{8,}"));

        final List<String> data = invokeData(FIRST_CHUNK, SECRET_CHUNK, FINAL_CHUNK);

        assertThat(data)
                .as("阻断块整块替换为终止帧，其后不再有内容")
                .hasSize(2)
                .last().isEqualTo(DONE);
        assertThat(data).noneMatch(chunk -> chunk.contains("sk-abcdef123456"));
    }

    @Test
    @DisplayName("命中脱敏：客户端拿到的是替换后的文本")
    void redactRewritesChunk() {
        guard(true, List.of("\\d{11}"), List.of());

        final List<String> data = invokeData(FIRST_CHUNK, PHONE_CHUNK, FINAL_CHUNK);

        assertThat(data).hasSize(3);
        assertThat(data.get(1))
                .as("手机号应被占位符替换")
                .contains("[PHONE]")
                .doesNotContain("13800138000");
    }

    @Test
    @DisplayName("被阻断的流不写缓存（对照组：无护栏时同一夹具确实会写）")
    void abortedStreamIsNotCached() {
        // 对照组先跑：证明这套夹具本身能触发写缓存，否则「没写」可能只是夹具没走到写入分支。
        ReflectionTestUtils.setField(processor, "responseCacheService", responseCacheService);
        ReflectionTestUtils.setField(processor, "streamGuardService", null);
        assertThat(invokeData(FIRST_CHUNK, FINAL_CHUNK, DONE)).hasSize(3);
        verify(responseCacheService, timeout(3000)).store(any(), any());

        // 正式场景：终止标记在阻断之前就已经出现（terminated=true），此时若没有
        // guardAborted 门控，doOnComplete 会照常写缓存 —— 这个顺序才是能区分两者的形态。
        reset(responseCacheService);
        guard(true, List.of(), List.of("sk-[a-z0-9]{8,}"));
        assertThat(invokeData(FINAL_CHUNK, SECRET_CHUNK)).isNotEmpty();

        verify(responseCacheService, after(500).never()).store(any(), any());
    }

    @Test
    @DisplayName("护栏未装配时流原样透传")
    void passesThroughWhenGuardAbsent() {
        ReflectionTestUtils.setField(processor, "streamGuardService", null);

        final List<String> data = invokeData(PHONE_CHUNK);

        assertThat(data).hasSize(1);
        assertThat(data.get(0)).contains("13800138000");
    }

    @Test
    @DisplayName("护栏关闭时流原样透传")
    void passesThroughWhenGuardDisabled() {
        guard(false, List.of("\\d{11}"), List.of("sk-[a-z0-9]{8,}"));

        final List<String> data = invokeData(PHONE_CHUNK);

        assertThat(data.get(0)).contains("13800138000");
    }

    // ==================== 辅助 ====================

    @SuppressWarnings("unchecked")
    private List<String> invokeData(final String... chunks) {
        final ResponseEntity<?> response = invokeResponse(chunks);
        final Flux<org.springframework.http.codec.ServerSentEvent<String>> body =
                (Flux<org.springframework.http.codec.ServerSentEvent<String>>) response.getBody();
        return body.map(event -> event.data()).collectList().block(Duration.ofSeconds(10));
    }

    private ResponseEntity<?> invokeResponse(final String... chunks) {
        final WebClient client = WebClient.builder()
                .exchangeFunction(ignored -> Mono.just(sseResponse(chunks)))
                .build();
        return processor.processStreamingRequest(
                        chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
                        instance, ServiceType.chat, "openai-compat", requestWithCacheKey())
                .block(Duration.ofSeconds(10));
    }

    private ClientResponse sseResponse(final String... chunks) {
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

    private ChatDTO.Request chatRequest() {
        return new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", "hello", null)),
                true, 256, 0.0, null, null, null, null, null, null, null);
    }
}
