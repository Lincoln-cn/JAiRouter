package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 * <p>断言三件事：仍未映射的媒体路径返回 501 且错误体里带对应的 {@code /api/v1/...} 路径、
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
    @DisplayName("MEDIA-001: /v1/images/variations 返回 501 并指向 /api/v1 控制台面")
    void imagesVariationsPointsToConsoleSurface() {
        client().post().uri("/v1/images/variations")
                .exchange()
                .expectStatus().isEqualTo(501)
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("not_implemented_on_v1")
                .jsonPath("$.error.message").value(message -> {
                    org.junit.jupiter.api.Assertions.assertTrue(
                            String.valueOf(message).contains("/api/v1/images/variations"),
                            "错误信息必须给出可直接替换的路径，实际: " + message);
                });
    }

    @Test
    @DisplayName("MEDIA-002: /v1/audio/translations 返回 501 并指向 /api/v1/audio/translations")
    void audioTranslationsPointsToConsoleSurface() {
        client().post().uri("/v1/audio/translations")
                .exchange()
                .expectStatus().isEqualTo(501)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("not_implemented_on_v1")
                .jsonPath("$.error.message").value(message -> {
                    org.junit.jupiter.api.Assertions.assertTrue(
                            String.valueOf(message).contains("/api/v1/audio/translations"),
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

    @Test
    @DisplayName("MEDIA-004: /v1/images/edits 用 JSON 提交时是 415，并说明只接受 multipart")
    void imageEditJsonBodyIsUnsupportedMediaType() {
        client().post().uri("/v1/images/edits")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("model", "stable-diffusion-2-1", "prompt", "a dog"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("unsupported_media_type")
                .jsonPath("$.error.message").value(message -> {
                    final String text = String.valueOf(message);
                    // 必须指出正确提交形式，而不是笼统的「未提供」
                    org.junit.jupiter.api.Assertions.assertTrue(
                            text.contains("multipart/form-data"), "应说明正确媒体类型，实际: " + text);
                    org.junit.jupiter.api.Assertions.assertTrue(
                            text.contains("image"), "应指出 image 文件字段，实际: " + text);
                    org.junit.jupiter.api.Assertions.assertFalse(
                            text.contains("not_implemented"), "已提供映射的端点不能报「未提供」，实际: " + text);
                });
    }

    @Test
    @DisplayName("MEDIA-005: 501 文案不再声明该路径之外的能力「已提供 /v1 原生映射」")
    void notImplementedHintStaysFactual() {
        client().post().uri("/v1/images/variations")
                .exchange()
                .expectStatus().isEqualTo(501)
                .expectBody()
                .jsonPath("$.error.message").value(message -> {
                    final String text = String.valueOf(message);
                    org.junit.jupiter.api.Assertions.assertTrue(
                            text.contains("/api/v1/images/variations"), "应给出控制台面路径，实际: " + text);
                    // 旧文案在拒绝请求的同时声称「图像生成/编辑已提供 /v1 原生映射」，自相矛盾
                    org.junit.jupiter.api.Assertions.assertFalse(
                            text.contains("已提供 /v1 原生映射"), "不应再出现自相矛盾的整体性声明，实际: " + text);
                });
    }
}
