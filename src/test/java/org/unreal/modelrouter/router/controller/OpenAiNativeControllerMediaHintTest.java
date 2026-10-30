package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import org.unreal.modelrouter.router.model.ModelCatalogService;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code /v1} 媒体能力兜底入口的测试（issue #830）.
 *
 * <p>断言三件事：音频/图像路径返回 501 且错误体里带对应的 {@code /api/v1/...} 路径、
 * 错误类型是 OpenAI 的 {@code invalid_request_error}、既有映射（{@code GET /v1/models}）不受影响。</p>
 */
class OpenAiNativeControllerMediaHintTest {

    private WebTestClient client() {
        final ModelCatalogService catalog = mock(ModelCatalogService.class);
        when(catalog.listAllModelsAsOpenAiList()).thenReturn(Map.of("object", "list",
                "data", List.of()));
        final OpenAiNativeController controller = new OpenAiNativeController(
                mock(ServiceRequestHandler.class), catalog);
        return WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("MEDIA-001: /v1/images/generations 返回 501 并指向 /api/v1 控制台面")
    void imagesGenerationPointsToConsoleSurface() {
        client().post().uri("/v1/images/generations")
                .exchange()
                .expectStatus().isEqualTo(501)
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("not_implemented_on_v1")
                .jsonPath("$.error.message").value(message -> {
                    org.junit.jupiter.api.Assertions.assertTrue(
                            String.valueOf(message).contains("/api/v1/images/generations"),
                            "错误信息必须给出可直接替换的路径，实际: " + message);
                });
    }

    @Test
    @DisplayName("MEDIA-002: /v1/audio/speech 返回 501 并指向 /api/v1/audio/speech")
    void audioSpeechPointsToConsoleSurface() {
        client().post().uri("/v1/audio/speech")
                .exchange()
                .expectStatus().isEqualTo(501)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("not_implemented_on_v1")
                .jsonPath("$.error.message").value(message -> {
                    org.junit.jupiter.api.Assertions.assertTrue(
                            String.valueOf(message).contains("/api/v1/audio/speech"),
                            "错误信息必须给出可直接替换的路径，实际: " + message);
                });
    }

    @Test
    @DisplayName("MEDIA-003: 兜底入口不影响既有 /v1/models 映射")
    void existingModelsEndpointStillWorks() {
        client().get().uri("/v1/models")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list");
    }
}
