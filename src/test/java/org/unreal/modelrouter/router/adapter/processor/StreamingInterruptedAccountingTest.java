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
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.monitor.callhistory.ApiCallHistoryRecorder;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.config.RecordLevel;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO;
import org.unreal.modelrouter.monitor.service.TokenUsageRecorder;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #710：流被打断（客户端取消 / 流内异常）时的入账测试。
 *
 * <p>中断路径此前只在 {@code doOnComplete} 才写 {@code token_usage} 与 {@code call_history}，
 * 断开在账本上等于「这次请求没发生过」，而上游已经产生了 token 成本。本类钉住三件事：</p>
 * <ul>
 *   <li>取消与异常都必须落一行用量（{@code isSuccess=false}）；</li>
 *   <li>取消与异常都必须落一条调用历史（{@code isSuccess=false} + 错误码）；</li>
 *   <li>配额由 {@code QuotaReservation} 决定，本类只管用量与历史（配额语义由
 *       {@code StreamingRequestProcessorQuotaSettleTest} 覆盖）。</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("#710 流式中断入账测试")
class StreamingInterruptedAccountingTest {

    private static final String SSE_EVENT_TERMINATOR = "\n\n";

    @Mock
    private ResponseTransformer responseTransformer;

    @Mock
    private TokenUsageRecorder tokenUsageRecorder;

    @Mock
    private ApiCallHistoryRecorder callHistoryRecorder;

    private StreamingRequestProcessor processor;
    private ModelRouterProperties.ModelInstance instance;

    @BeforeEach
    void setUp() {
        when(responseTransformer.transformStreamChunk(anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        final CallHistoryProperties callHistoryProperties = new CallHistoryProperties();
        callHistoryProperties.setRecordLevel(RecordLevel.SUMMARY);

        processor = new StreamingRequestProcessor(responseTransformer);
        ReflectionTestUtils.setField(processor, "tokenUsageRecorder", tokenUsageRecorder);
        ReflectionTestUtils.setField(processor, "callHistoryRecorder", callHistoryRecorder);
        ReflectionTestUtils.setField(processor, "callHistoryProperties", callHistoryProperties);

        instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-710");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://downstream.local");
    }

    @Test
    @DisplayName("ACC-710-001 客户端取消：落一行 isSuccess=false 的用量与一条 CLIENT_CANCELLED 调用历史")
    void clientCancel_recordsUsageAndHistory() {
        final WebClient client = streamingClient(
            "data: {\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}" + SSE_EVENT_TERMINATOR,
            Flux.never());

        final ResponseEntity<?> response = processor.processStreamingRequest(
            chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
            instance, ServiceType.chat, "openai-compat", request()).block(Duration.ofSeconds(10));

        StepVerifier.create(sseBody(response))
            .expectNextCount(1)
            .thenCancel()
            .verify();

        final ArgumentCaptor<Boolean> successCaptor = ArgumentCaptor.forClass(Boolean.class);
        final ArgumentCaptor<Long> totalCaptor = ArgumentCaptor.forClass(Long.class);
        verify(tokenUsageRecorder, timeout(3000)).recordTokenUsageNoAuth(
            any(), any(), any(), any(), any(), any(), any(), any(), totalCaptor.capture(),
            any(), any(), successCaptor.capture(), any(), any(), any());
        assertEquals(Boolean.FALSE, successCaptor.getValue(), "中断的流必须记为失败");
        assertTrue(totalCaptor.getValue() != null && totalCaptor.getValue() > 0,
            "已流出内容应计入用量，实际: " + totalCaptor.getValue());

        final ArgumentCaptor<CallHistoryRecordDTO> captor = ArgumentCaptor.forClass(CallHistoryRecordDTO.class);
        verify(callHistoryRecorder, timeout(3000)).record(captor.capture());
        final CallHistoryRecordDTO record = captor.getValue();
        assertEquals(Boolean.FALSE, record.getIsSuccess(), "取消的调用必须记为失败");
        assertEquals("CLIENT_CANCELLED", record.getErrorCode());
        assertEquals("gpt-4", record.getModelName());
        assertNotNull(record.getResponseTimeMs());
        assertTrue(record.getCompletionTokens() != null && record.getCompletionTokens() > 0,
            "应把已流出内容估算成 token 并记录，实际: " + record.getCompletionTokens());
    }

    @Test
    @DisplayName("ACC-710-002 流内异常：落一行 isSuccess=false 的用量与带异常类名的调用历史")
    void streamError_recordsUsageAndHistory() {
        final WebClient client = WebClient.builder()
            .exchangeFunction(ignored -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "text/event-stream")
                .body(Flux.concat(
                    Flux.just(buffer("data: {\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}"
                        + SSE_EVENT_TERMINATOR)),
                    Flux.error(new IllegalStateException("下游中途断开"))))
                .build()))
            .build();

        final ResponseEntity<?> response = processor.processStreamingRequest(
            chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
            instance, ServiceType.chat, "openai-compat", request()).block(Duration.ofSeconds(10));

        // 错误帧之后 WebClient 仍会把原始异常抛出（既有行为），本类只关心入账
        sseBody(response).onErrorResume(ignored -> Flux.empty()).blockLast(Duration.ofSeconds(10));

        final ArgumentCaptor<Boolean> errorSuccessCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(tokenUsageRecorder, timeout(3000)).recordTokenUsageNoAuth(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            errorSuccessCaptor.capture(), any(), any(), any());
        assertEquals(Boolean.FALSE, errorSuccessCaptor.getValue(), "异常终态的流必须记为失败");

        final ArgumentCaptor<CallHistoryRecordDTO> captor = ArgumentCaptor.forClass(CallHistoryRecordDTO.class);
        verify(callHistoryRecorder, timeout(3000)).record(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getIsSuccess());
        assertNotNull(captor.getValue().getErrorCode(), "异常终态必须带错误码");
        assertTrue(!captor.getValue().getErrorCode().isEmpty());
    }

    @Test
    @DisplayName("ACC-710-003 一个块都没流出就取消：不写用量（没有 token 成本），但仍落一条失败调用历史")
    void cancelBeforeAnyChunk_recordsOnlyHistory() {
        final WebClient client = streamingClient(null, Flux.never());

        final ResponseEntity<?> response = processor.processStreamingRequest(
            chatRequest(), "Bearer downstream", client, "/v1/chat/completions",
            instance, ServiceType.chat, "openai-compat", request()).block(Duration.ofSeconds(10));

        StepVerifier.create(sseBody(response)).thenCancel().verify();

        verify(callHistoryRecorder, timeout(3000)).record(any());
        verify(tokenUsageRecorder, org.mockito.Mockito.after(300).never()).recordTokenUsageNoAuth(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ==================== helpers ====================

    private WebClient streamingClient(final String firstChunk, final Flux<DataBuffer> tail) {
        return WebClient.builder()
            .exchangeFunction(ignored -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "text/event-stream")
                .body(firstChunk == null ? tail : Flux.concat(Flux.just(buffer(firstChunk)), tail))
                .build()))
            .build();
    }

    private MockServerHttpRequest request() {
        return MockServerHttpRequest.post("/v1/chat/completions").build();
    }

    private ChatDTO.Request chatRequest() {
        return new ChatDTO.Request("gpt-4",
            List.of(new ChatDTO.Message("user", "hi", null)),
            true, 100, 0.7, null, null, null, null, null, null, null, null);
    }

    private DataBuffer buffer(final String text) {
        return new DefaultDataBufferFactory().wrap(text.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private Flux<ServerSentEvent<String>> sseBody(final ResponseEntity<?> response) {
        assertNotNull(response);
        assertNotNull(response.getBody());
        return (Flux<ServerSentEvent<String>>) response.getBody();
    }
}
