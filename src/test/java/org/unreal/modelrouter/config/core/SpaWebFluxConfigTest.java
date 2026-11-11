package org.unreal.modelrouter.config.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * {@link SpaWebFluxConfig} 的路由与缓存策略测试.
 *
 * <p>断言四件事：带哈希的构建产物拿到一年期 immutable 缓存、缺失的构建产物是 404（不再伪装成
 * index.html）、SPA 兜底页面是 no-store、{@code /admin} 直接给出入口页。</p>
 *
 * <p>依赖 {@code src/test/resources/static/admin/**} 下的测试专用占位文件，
 * 因此不依赖真实前端构建产物是否存在于 classpath。</p>
 */
class SpaWebFluxConfigTest {

    private WebTestClient client() {
        return WebTestClient.bindToRouterFunction(new SpaWebFluxConfig().spaRouterFunction()).build();
    }

    @Test
    @DisplayName("SPA-001: 带哈希的构建产物返回一年期 immutable 缓存")
    void hashedAssetIsCacheableForOneYear() {
        client().get().uri("/admin/assets/probe-abcdefgh.js")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.parseMediaType("application/javascript"))
                .expectHeader().value("Cache-Control", value -> {
                    final String header = String.valueOf(value);
                    org.junit.jupiter.api.Assertions.assertTrue(
                            header.contains("max-age=31536000"), "应有一年期缓存，实际: " + header);
                    org.junit.jupiter.api.Assertions.assertTrue(
                            header.contains("immutable"), "构建产物应标记 immutable，实际: " + header);
                });
    }

    @Test
    @DisplayName("SPA-002: 缺失的构建产物是 404，且响应体指明具体文件")
    void missingBuildAssetIsNotFound() {
        client().get().uri("/admin/assets/missing-chunk-xyz.js")
                .exchange()
                .expectStatus().isNotFound()
                // Micrometer 把 URI 归一成 /admin/**，响应体是调用方唯一能直接看到具体路径的地方（issue #1079）
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
                .expectBody(String.class)
                .value(body -> org.junit.jupiter.api.Assertions.assertTrue(
                        body.contains("/admin/assets/missing-chunk-xyz.js"),
                        "404 响应体应含具体路径，实际: " + body));
    }

    @Test
    @DisplayName("SPA-003: 前端路由兜底返回 index.html 且为 no-store")
    void spaFallbackIsNotCached() {
        client().get().uri("/admin/monitoring/slow-queries")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectHeader().value("Cache-Control", value -> {
                    final String header = String.valueOf(value);
                    // index.html 必须回源校验，否则发版后仍指向旧 chunk
                    org.junit.jupiter.api.Assertions.assertTrue(
                            header.contains("no-store"), "入口页不得被缓存，实际: " + header);
                    org.junit.jupiter.api.Assertions.assertFalse(
                            header.contains("immutable"), "入口页不得标 immutable，实际: " + header);
                });
    }

    @Test
    @DisplayName("SPA-004: /admin 直接返回入口页")
    void adminRootServesIndex() {
        client().get().uri("/admin")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    @DisplayName("SPA-005: 根路径 favicon.ico 返回 204 而不是认证错误")
    void faviconReturnsNoContent() {
        client().get().uri("/favicon.ico")
                .exchange()
                .expectStatus().isNoContent();
    }
}
