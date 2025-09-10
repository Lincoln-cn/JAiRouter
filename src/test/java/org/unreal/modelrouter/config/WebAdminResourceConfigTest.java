package org.unreal.modelrouter.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Web管理界面静态资源配置测试
 */
@ExtendWith(SpringExtension.class)
@WebFluxTest
@Import(WebAdminResourceConfig.class)
class WebAdminResourceConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldServeAdminIndexPage() {
        // 测试访问管理界面首页
        webTestClient.get()
                .uri("/admin/")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldServeAdminIndexPageForSubPaths() {
        // 测试SPA路由 - 所有未匹配的路径应该返回index.html
        webTestClient.get()
                .uri("/admin/dashboard")
                .exchange()
                .expectStatus().isOk();
        
        webTestClient.get()
                .uri("/admin/services")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldServeStaticAssets() {
        // 测试静态资源访问
        webTestClient.get()
                .uri("/admin/assets/test.js")
                .exchange()
                .expectStatus().isNotFound(); // 文件不存在时应该返回404
    }

    @Test
    void shouldHandleNonExistentPaths() {
        // 测试不存在的路径
        webTestClient.get()
                .uri("/admin/nonexistent")
                .exchange()
                .expectStatus().isOk(); // SPA应该返回index.html
    }
}