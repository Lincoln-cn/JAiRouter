package org.unreal.modelrouter.router.adapter.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #257：流式失败的终止语义 —— 只对 {@code /v1} OpenAI 面补 error 帧，且<b>绝不</b>补 {@code [DONE]}。
 */
@DisplayName("#257：流式失败的终止语义")
class StreamingErrorTerminationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("/v1 OpenAI 面：先发协议形状 error 帧，再以原异常终止（且不含 [DONE]）")
    void openAiFace_emitsProtocolShapedFrameThenTerminatesWithError() {
        final WebClientResponseException tooManyRequests = WebClientResponseException.create(
                429, "Too Many Requests", HttpHeaders.EMPTY, null, null);

        final List<String> frames = new ArrayList<>();
        StepVerifier.create(StreamingErrorTermination.terminate(
                        tooManyRequests, request("/v1/chat/completions"), objectMapper))
                .consumeNextWith(frame -> frames.add(frame.data()))
                .expectErrorMatches(error -> error == tooManyRequests)
                .verify();

        assertEquals(1, frames.size(), "只应补一个 error 帧");
        final String payload = frames.get(0);
        assertTrue(payload.contains("\"error\""), payload);
        assertTrue(payload.contains("\"type\":\"rate_limit_error\""),
                () -> "下游 429 应保真映射为 rate_limit_error，实际: " + payload);
        assertFalse(payload.contains("[DONE]"), "错误路径绝不允许出现 [DONE]");
    }

    @Test
    @DisplayName("连接级失败（无下游状态码）：按 5xx 选型 → api_error")
    void connectionLevelFailure_mapsToApiError() {
        final IllegalStateException connectionReset = new IllegalStateException("Connection prematurely closed");

        final List<String> frames = new ArrayList<>();
        StepVerifier.create(StreamingErrorTermination.terminate(
                        connectionReset, request("/v1/chat/completions"), objectMapper))
                .consumeNextWith(frame -> frames.add(frame.data()))
                .expectErrorMatches(error -> error == connectionReset)
                .verify();

        assertEquals(1, frames.size());
        assertTrue(frames.get(0).contains("\"type\":\"api_error\""), frames.get(0));
    }

    @Test
    @DisplayName("读超时：504 → api_error")
    void timeout_mapsTo500Family() {
        final TimeoutException timeout = new TimeoutException("idle timeout");

        StepVerifier.create(StreamingErrorTermination.terminate(
                        timeout, request("/v1/chat/completions"), objectMapper))
                .consumeNextWith(frame -> assertTrue(frame.data().contains("\"type\":\"api_error\""), frame.data()))
                .expectErrorMatches(error -> error == timeout)
                .verify();
    }

    @Test
    @DisplayName("Anthropic 面（/v1/messages）：不补帧（该面由翻译器发协议定义的 event: error）")
    void anthropicFace_doesNotAddFrame() {
        assertNoFrame("/v1/messages");
    }

    @Test
    @DisplayName("legacy /api/** 面：不补帧，逐字节保持既有语义")
    void legacyFace_doesNotAddFrame() {
        assertNoFrame("/api/v1/chat/completions");
    }

    @Test
    @DisplayName("无路径信息：不补帧（无法判定协议面）")
    void missingPath_doesNotAddFrame() {
        final IllegalStateException failure = new IllegalStateException("boom");

        StepVerifier.create(StreamingErrorTermination.terminate(failure, null, objectMapper))
                .expectErrorMatches(error -> error == failure)
                .verify();
    }

    @Test
    @DisplayName("statusOf 从异常链里取下游状态码（含包装）")
    void statusOf_readsStatusFromCausedBy() {
        final WebClientResponseException unauthorized = WebClientResponseException.create(
                401, "Unauthorized", HttpHeaders.EMPTY, null, null);

        assertEquals(401, StreamingErrorTermination.statusOf(new RuntimeException("wrapped", unauthorized)));
        assertEquals(502, StreamingErrorTermination.statusOf(new IllegalStateException("no status")));
    }

    private void assertNoFrame(final String path) {
        final IllegalStateException failure = new IllegalStateException("boom");

        final Flux<?> terminated = StreamingErrorTermination.terminate(failure, request(path), objectMapper);
        StepVerifier.create(terminated)
                .expectErrorMatches(error -> error == failure)
                .verify();
    }

    private static ServerHttpRequest request(final String path) {
        return MockServerHttpRequest.get(path).build();
    }
}
