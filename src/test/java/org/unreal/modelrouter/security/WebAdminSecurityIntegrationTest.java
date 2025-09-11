package org.unreal.modelrouter.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Web管理界面安全配置集成测试
 * 验证现有API接口的CORS配置和安全配置是否支持Web管理界面
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class WebAdminSecurityIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldAllowCorsPreflightRequests() {
        // 测试CORS预检请求
        webTestClient.options()
                .uri("/api/config/type")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("Access-Control-Allow-Origin");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminAccessToConfigApi() {
        // 测试管理员可以访问配置API
        webTestClient.get()
                .uri("/api/config/type")
                .header("Origin", "http://localhost:3000")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("Access-Control-Allow-Origin");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminAccessToMonitoringApi() {
        // 测试管理员可以访问监控API
        webTestClient.get()
                .uri("/api/monitoring")
                .header("Origin", "http://localhost:3000")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldDenyUserAccessToConfigApi() {
        // 测试普通用户无法访问配置API
        webTestClient.get()
                .uri("/api/config/type")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldDenyUnauthenticatedAccessToConfigApi() {
        // 测试未认证用户无法访问配置API
        webTestClient.get()
                .uri("/api/config/type")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldAllowAccessToHealthEndpoint() {
        // 测试健康检查端点允许匿名访问
        webTestClient.get()
                .uri("/actuator/health")
                .header("Origin", "http://localhost:3000")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowAccessToJwtLoginEndpoint() {
        // 测试JWT登录端点允许匿名访问
        webTestClient.post()
                .uri("/api/auth/jwt/login")
                .header("Origin", "http://localhost:3000")
                .header("Content-Type", "application/json")
                .bodyValue("{\"username\":\"test\",\"password\":\"test\"}")
                .exchange()
                .expectStatus().is4xxClientError(); // 预期认证失败，但不是403禁止访问
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminAccessToApiKeyManagement() {
        // 测试管理员可以访问API密钥管理
        webTestClient.get()
                .uri("/api/auth/api-keys")
                .header("Origin", "http://localhost:3000")
                .exchange()
                .expectStatus().isOk();
    }
}