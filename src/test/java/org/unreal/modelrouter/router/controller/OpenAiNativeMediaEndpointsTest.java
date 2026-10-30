package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.router.handler.ServiceEndpoint;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import org.unreal.modelrouter.router.model.ModelCatalogService;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /v1} 原生媒体端点的测试（issue #835）.
 *
 * <p>断言四件事：四类媒体请求都落到 {@code /v1} 的映射上（不是 404/501）、
 * 分派到正确的 {@link ServiceEndpoint}、请求在 native 面被标记
 * （{@code NATIVE_RESPONSE_ATTRIBUTE}，否则响应会被包成 {@code RouterResponse}）、
 * 以及缺少必填部件时是 400 而不是 500。</p>
 *
 * <p>说明：这里不核对 DTO 内部字段——参数解析复用控制台面已有的实现，
 * 端到端需要真实音频/图像上游（本机 Ollama 不提供这两类能力）。</p>
 */
@DisplayName("#835 /v1 原生媒体端点")
class OpenAiNativeMediaEndpointsTest {

    private ServiceRequestHandler handler;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        handler = mock(ServiceRequestHandler.class);
        when(handler.handleRequest(any(ServiceEndpoint.class), any(), any(),
                any(ServerWebExchange.class), any()))
                .thenReturn(Mono.just((ResponseEntity<?>) ResponseEntity.ok(Map.of("stub", true))));

        final ModelCatalogService catalog = mock(ModelCatalogService.class);
        when(catalog.listAllModelsAsOpenAiList()).thenReturn(Map.of("object", "list", "data", List.of()));
        client = WebTestClient.bindToController(new OpenAiNativeController(handler, catalog)).build();
    }

    @Test
    @DisplayName("835-001 /v1/audio/speech 落到 TTS 且在 native 面")
    void audioSpeechMapsToTts() {
        client.post().uri("/v1/audio/speech")
                .bodyValue(Map.of("model", "cosyvoice-300m", "input", "你好", "voice", "alloy"))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.stub").isEqualTo(true);

        assertEquals(ServiceEndpoint.TTS, capturedEndpoint());
        assertTrue(capturedNativeFlag());
    }

    @Test
    @DisplayName("835-002 /v1/audio/transcriptions 落到 STT（multipart）")
    void audioTranscriptionsMapsToStt() {
        final MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("model", "faster-whisper-tiny");
        builder.part("file", new ByteArrayResource("fake-audio".getBytes(StandardCharsets.UTF_8)))
                .filename("sample.wav");
        builder.part("response_format", "json");

        client.post().uri("/v1/audio/transcriptions")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk();

        assertEquals(ServiceEndpoint.STT, capturedEndpoint());
    }

    @Test
    @DisplayName("835-003 /v1/images/generations 落到 IMAGE_GEN")
    void imageGenerationMapsToImageGen() {
        client.post().uri("/v1/images/generations")
                .bodyValue(Map.of("model", "stable-diffusion-2-1", "prompt", "a cat"))
                .exchange()
                .expectStatus().isOk();

        assertEquals(ServiceEndpoint.IMAGE_GEN, capturedEndpoint());
        assertTrue(capturedNativeFlag());
    }

    @Test
    @DisplayName("835-004 /v1/images/edits 落到 IMAGE_EDIT（multipart）")
    void imageEditsMapsToImageEdit() {
        final MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("model", "stable-diffusion-2-1");
        builder.part("prompt", "make it blue");
        builder.part("image", new ByteArrayResource("fake-png".getBytes(StandardCharsets.UTF_8)))
                .filename("source.png");

        client.post().uri("/v1/images/edits")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk();

        assertEquals(ServiceEndpoint.IMAGE_EDIT, capturedEndpoint());
    }

    @Test
    @DisplayName("835-005 /v1/images/edits 缺少 image 部件时是 400")
    void imageEditsWithoutImageIsBadRequest() {
        final MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("model", "stable-diffusion-2-1");
        builder.part("prompt", "make it blue");

        client.post().uri("/v1/images/edits")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    private ServiceEndpoint capturedEndpoint() {
        final ArgumentCaptor<ServiceEndpoint> captor = ArgumentCaptor.forClass(ServiceEndpoint.class);
        verify(handler).handleRequest(captor.capture(), any(), any(),
                any(ServerWebExchange.class), any());
        return captor.getValue();
    }

    private boolean capturedNativeFlag() {
        final ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(handler).handleRequest(any(ServiceEndpoint.class), any(), any(), captor.capture(), any());
        return Boolean.TRUE.equals(
                captor.getValue().getAttributes().get(ServiceRequestHandler.NATIVE_RESPONSE_ATTRIBUTE));
    }
}
