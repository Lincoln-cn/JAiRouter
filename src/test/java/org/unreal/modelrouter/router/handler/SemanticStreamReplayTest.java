package org.unreal.modelrouter.router.handler;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.unreal.modelrouter.router.cache.SemanticCacheService;
import org.unreal.modelrouter.router.cache.SemanticAdmissionPolicy;

import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 语义缓存命中在流式请求下的回放格式.
 *
 * <p>语义缓存只存非流式响应体。客户端发 {@code stream=true} 时若直接返回
 * JSON，按 SSE 解析的 SDK 会失败，所以命中后必须包成一帧 data 与一帧 [DONE]。</p>
 */
@DisplayName("语义缓存流式回放")
class SemanticStreamReplayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ResponseCacheResolver resolver(final ObjectMapper mapper) {
        return new ResponseCacheResolver(
                mock(org.unreal.modelrouter.router.cache.ResponseCacheService.class),
                mapper,
                mock(SemanticCacheService.class),
                mock(SemanticAdmissionPolicy.class),
                null);
    }

    @Test
    @DisplayName("命中值被包成 SSE：一帧 data + 一帧 [DONE]")
    @SuppressWarnings("unchecked")
    void semanticValueIsReplayedAsSse() {
        final ResponseCacheResolver resolver = resolver(objectMapper);

        final ResponseEntity<?> response = resolver.replaySemanticAsStream(Map.of("id", "x"));

        assertThat(response).isNotNull();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);

        final List<ServerSentEvent<String>> events =
                ((Flux<ServerSentEvent<String>>) response.getBody()).collectList().block();
        assertThat(events).isNotNull();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).data()).contains("\"id\"").contains("\"x\"");
        assertThat(events.get(1).data()).isEqualTo("[DONE]");
    }

    @Test
    @DisplayName("序列化失败时返回 null，让调用方回退 JSON")
    void serializationFailureFallsBack() {
        final ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("boom"));

        final ResponseCacheResolver resolver = resolver(broken);

        assertThat(resolver.replaySemanticAsStream(Map.of("id", "x"))).isNull();
    }
}
