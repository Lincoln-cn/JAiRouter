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
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #716：流式调用的成败必须在流终端回报给记账层。
 *
 * <p>修复前 {@code BaseAdapter.doOnSuccess} 在建流时就按「ResponseEntity 已创建」记成功，
 * 于是首块之后才失败的实例被记成成功（熔断失败计数被清零），持续失败的实例永远进不了 OPEN。</p>
 *
 * <p>覆盖四条终止路径：正常结束 → SUCCESS；首块后失败 → INSTANCE_FAILURE；
 * 下游 4xx → CLIENT_ERROR（不计熔断失败）；客户端主动断开 → CLIENT_ERROR 而不是实例故障。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StreamingRequestProcessor 流式结果记账（#716）")
class StreamingRequestOutcomeAccountingTest {

    private static final String SSE_EVENT_TERMINATOR = "\n\n";

    private static final String FIRST_CHUNK =
            "data: {\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}";

    private static final String SECOND_CHUNK =
            "data: {\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"there\"}}]}";

    @Mock
    private ResponseTransformer responseTransformer;

    @Mock
    private AdapterMetricsRecorder metricsRecorder;

    private StreamingRequestProcessor processor;
    private ModelRouterProperties.ModelInstance instance;
    private ServerHttpRequest httpRequest;

    @BeforeEach
    void setUp() {
        when(responseTransformer.transformStreamChunk(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        processor = new StreamingRequestProcessor(responseTransformer);
        ReflectionTestUtils.setField(processor, "adapterMetricsRecorder", metricsRecorder);

        instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-716");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://downstream.local");

        httpRequest = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/chat/completions").build()).getRequest();
    }

    @Test
    @DisplayName("#716 正常结束：只在流完成后记一次 SUCCESS")
    void normalCompletion_recordsSuccess() {
        final ResponseEntity<?> response = invoke(clientOf(sseResponse(FIRST_CHUNK, SECOND_CHUNK)));
        StepVerifier.create(sseBody(response)).expectNextCount(2).verifyComplete();

        final Accounted accounted = accountOnce();
        assertEquals(AdapterMetricsRecorder.CallOutcome.SUCCESS, accounted.outcome, "正常结束应记成功");
        assertNull(accounted.errorCode, "成功时不应带错误码");
    }

    @Test
    @DisplayName("#716 首块之后失败：记 INSTANCE_FAILURE（熔断器能看到失败）")
    void failureAfterFirstChunk_recordsInstanceFailure() {
        final Flux<DataBuffer> body = Flux.concat(
                Flux.just(buffer(FIRST_CHUNK + SSE_EVENT_TERMINATOR)),
                Flux.error(new IllegalStateException("下游首块后断开")));
        final WebClient client = clientOf(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "text/event-stream")
                .body(body)
                .build());

        final ResponseEntity<?> response = invoke(client);
        StepVerifier.create(sseBody(response)).expectNextCount(1).thenConsumeWhile(ignored -> true)
                .expectError().verify(Duration.ofSeconds(5));

        final Accounted accounted = accountOnce();
        assertEquals(AdapterMetricsRecorder.CallOutcome.INSTANCE_FAILURE, accounted.outcome,
                "首块之后的失败必须计入实例故障，否则熔断永不动作");
        assertNotNull(accounted.errorCode, "失败必须带错误码，便于在监控里区分终止原因");
    }

    @Test
    @DisplayName("#716 下游 4xx：记 CLIENT_ERROR，不计熔断失败")
    void downstream4xx_recordsClientError() {
        final WebClient client = clientOf(ClientResponse.create(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("{\"error\":\"bad request\"}")
                .build());

        final ResponseEntity<?> response = invoke(client);
        StepVerifier.create(sseBody(response)).expectError().verify(Duration.ofSeconds(5));

        assertEquals(AdapterMetricsRecorder.CallOutcome.CLIENT_ERROR, accountOnce().outcome,
                "4xx 属调用方问题，与实例健康无关（沿用 #647 口径）");
    }

    @Test
    @DisplayName("#716 客户端断开：记 CLIENT_ERROR（释放流式计数，不算实例故障）")
    void clientCancellation_recordsClientErrorNotInstanceFailure() {
        final WebClient client = clientOf(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "text/event-stream")
                .body(Flux.concat(
                        Mono.just(buffer(FIRST_CHUNK + SSE_EVENT_TERMINATOR)),
                        Flux.never()))
                .build());

        final ResponseEntity<?> response = invoke(client);
        StepVerifier.create(sseBody(response))
                .expectNextCount(1)
                .thenCancel()
                .verify(Duration.ofMillis(500));

        final Accounted accounted = accountOnce();
        assertEquals(AdapterMetricsRecorder.CallOutcome.CLIENT_ERROR, accounted.outcome,
                "客户端主动断开不得累加实例熔断失败");
        assertEquals("CLIENT_CANCELLED", accounted.errorCode);
    }

    // ==================== 辅助 ====================

    /** 取这次流式调用唯一的一次记账结果（结果分类 + 错误码）。 */
    private Accounted accountOnce() {
        final ArgumentCaptor<AdapterMetricsRecorder.CallOutcome> outcomeCaptor =
                ArgumentCaptor.forClass(AdapterMetricsRecorder.CallOutcome.class);
        final ArgumentCaptor<String> errorCodeCaptor = ArgumentCaptor.forClass(String.class);
        verify(metricsRecorder, timeout(3000)).recordCompleteCallStats(anyString(), anyString(), anyLong(),
                outcomeCaptor.capture(), errorCodeCaptor.capture(), nullable(String.class), any(), any());
        return new Accounted(outcomeCaptor.getValue(), errorCodeCaptor.getValue());
    }

    private ResponseEntity<?> invoke(final WebClient client) {
        final ResponseEntity<?> response = processor.processStreamingRequest(
                chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
                instance, ServiceType.chat, "openai-compat", httpRequest).block(Duration.ofSeconds(10));
        assertNotNull(response, "流式响应不应为空");
        return response;
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

    /** 一次记账结果的快照。 */
    private static final class Accounted {
        private final AdapterMetricsRecorder.CallOutcome outcome;
        private final String errorCode;

        Accounted(final AdapterMetricsRecorder.CallOutcome outcome, final String errorCode) {
            this.outcome = outcome;
            this.errorCode = errorCode;
        }
    }
}
