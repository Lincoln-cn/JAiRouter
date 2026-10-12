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
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * #257 A 部分：{@code /v1} 面在**订阅下游响应体之前**先看下游状态并按真实状态结束
 * （下游 4xx/5xx 不再被统一成「500 + 空体」），legacy {@code /api/**} 面保持「200 + 流内终止」不变。
 *
 * <p>夹具沿用 {@code StreamingRequestProcessorQuotaSettleTest}：真实 {@code StreamingRequestProcessor}
 * + 真实 {@code WebClient}（自定义 {@code ExchangeFunction} 提供桩响应，无网络），
 * 仅 {@link ResponseTransformer} 为桩。</p>
 *
 * <p>依据：{@code StreamingPreCommitErrorProbeWebFluxTest} 已实测 WebFlux 层在首块之前失败会返回 500
 * （不会先提交 200）⇒ 本改动能真正决定客户端看到的状态码。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("#257 A：/v1 面按下游状态保真")
class StreamingV1DownstreamStatusTest {

    static {
        StepVerifier.setDefaultTimeout(Duration.ofSeconds(10));
    }

    private static final String DOWNSTREAM_PATH = "/chat/completions";

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
        instance.setInstanceId("inst-v1");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://downstream.local");
    }

    @Test
    @DisplayName("/v1 面 + 下游 429 ⇒ 网关以 429 结束（保真，便于客户端退避重试）")
    void v1Face_downstream429_failsFastWithSameStatus() {
        StepVerifier.create(stream("/v1/chat/completions", errorResponse(HttpStatus.TOO_MANY_REQUESTS)))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(ResponseStatusException.class, error);
                    assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                            ((ResponseStatusException) error).getStatusCode());
                })
                .verify();
    }

    @Test
    @DisplayName("/v1 面 + 下游 401 ⇒ 网关以 401 结束（客户端可据此换密钥而非盲目重试）")
    void v1Face_downstream401_failsFastWithSameStatus() {
        StepVerifier.create(stream("/v1/chat/completions", errorResponse(HttpStatus.UNAUTHORIZED)))
                .expectErrorSatisfies(error -> assertEquals(HttpStatus.UNAUTHORIZED,
                        ((ResponseStatusException) error).getStatusCode()))
                .verify();
    }

    @Test
    @DisplayName("/v1/messages（Anthropic 面）同样保真状态：429 ⇒ 429")
    void anthropicFace_alsoFailsFastWithSameStatus() {
        StepVerifier.create(stream("/v1/messages", errorResponse(HttpStatus.TOO_MANY_REQUESTS)))
                .expectErrorSatisfies(error -> assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                        ((ResponseStatusException) error).getStatusCode()))
                .verify();
    }

    @Test
    @DisplayName("/v1 面 + 下游正常 ⇒ 仍是 200 + 流（快失败不误伤成功路径）")
    void v1Face_downstreamOk_stillStreams() {
        Mono<? extends ResponseEntity<?>> result = stream("/v1/chat/completions", sseResponse(
                "data: {\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}",
                "data: {\"model\":\"gpt-4\",\"choices\":[],\"usage\":{\"total_tokens\":3}}"));

        final ResponseEntity<?> response = result.block(Duration.ofSeconds(10));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        StepVerifier.create(sseBody(response)).expectNextCount(2).verifyComplete();
    }

    @Test
    @DisplayName("legacy /api/** 面不变：下游 429 仍返回 200，失败在流内终止")
    void legacyFace_keepsPreviousSemantics() {
        Mono<? extends ResponseEntity<?>> result = stream("/api/v1/chat/completions",
                errorResponse(HttpStatus.TOO_MANY_REQUESTS));

        final ResponseEntity<?> response = result.block(Duration.ofSeconds(10));
        assertEquals(HttpStatus.OK, response.getStatusCode(), "legacy 面契约不变：仍是 200 + 流");
        StepVerifier.create(sseBody(response)).expectError().verify();
    }

    private Mono<? extends ResponseEntity<?>> stream(final String clientPath, final ClientResponse downstream) {
        return processor.processStreamingRequest(
                chatRequest(), "Bearer downstream", clientOf(downstream), DOWNSTREAM_PATH,
                instance, ServiceType.chat, "openai-compat", clientRequest(clientPath));
    }

    private static WebClient clientOf(final ClientResponse response) {
        return WebClient.builder().exchangeFunction(ignored -> Mono.just(response)).build();
    }

    private static ClientResponse errorResponse(final HttpStatus status) {
        return ClientResponse.create(status)
                .header("Content-Type", "application/json")
                .body("{\"error\":{\"message\":\"downstream rejected\"}}")
                .build();
    }

    private static ClientResponse sseResponse(final String... chunks) {
        final Flux<DataBuffer> body = Flux.fromArray(chunks).map(chunk -> buffer(chunk + "\n\n"));
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "text/event-stream")
                .body(body)
                .build();
    }

    private static DataBuffer buffer(final String text) {
        return new DefaultDataBufferFactory().wrap(text.getBytes(StandardCharsets.UTF_8));
    }

    private static ServerHttpRequest clientRequest(final String path) {
        return MockServerHttpRequest.get(path).build();
    }

    @SuppressWarnings("unchecked")
    private static Flux<ServerSentEvent<String>> sseBody(final ResponseEntity<?> response) {
        return (Flux<ServerSentEvent<String>>) response.getBody();
    }

    private static ChatDTO.Request chatRequest() {
        return new ChatDTO.Request("gpt-4",
                List.of(new ChatDTO.Message("user", "hello", null)),
                true, 256, 0.0, null, null, null, null, null, null, null);
    }
}
