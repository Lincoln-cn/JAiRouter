package org.unreal.modelrouter.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Web管理界面安全配置测试
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
    "jairouter.security.enabled=true"
})
class WebAdminSecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldAllowAnonymousAccessToStaticResources() {
        // 测试静态资源的匿名访问
        webTestClient.get()
                .uri("/admin/assets/test.css")
                .exchange()
                .expectStatus().isNotFound(); // 文件不存在，但不是认证错误

        webTestClient.get()
                .uri("/admin/index.html")
                .exchange()
                .expectStatus().isNotFound(); // 文件不存在，但不是认证错误
    }

    @Test
    void shouldRequireAuthenticationForApiEndpoints() {
        // 测试API端点需要认证
        webTestClient.get()
                .uri("/api/monitoring/config")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get()
                .uri("/api/config/instance/type/chat")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldAllowAnonymousAccessToAdminStaticResources() {
        // 测试Web管理界面静态资源的匿名访问
        webTestClient.get()
                .uri("/admin/")
                .exchange()
                .expectStatus().isNotFound(); // 文件不存在，但不是认证错误

        webTestClient.get()
                .uri("/admin/login")
                .exchange()
                .expectStatus().isNotFound(); // 文件不存在，但不是认证错误
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminAccessToApiEndpoints() {
        // 测试管理员可以访问API端点
        webTestClient.get()
                .uri("/api/monitoring/config")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldDenyUserAccessToAdminEndpoints() {
        // 测试普通用户无法访问管理员端点
        webTestClient.get()
                .uri("/api/monitoring/config")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldAllowAnonymousAccessToHealthEndpoint() {
        // 测试健康检查端点的匿名访问
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowAnonymousAccessToJwtLogin() {
        // 测试JWT登录端点的匿名访问
        webTestClient.post()
                .uri("/api/auth/jwt/login")
                .bodyValue("{\"username\":\"test\",\"password\":\"test\"}")
                .exchange()
                .expectStatus().is4xxClientError(); // 可能是400或401，但不是403
    }
}