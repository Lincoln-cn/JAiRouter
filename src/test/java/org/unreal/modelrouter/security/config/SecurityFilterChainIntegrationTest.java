package org.unreal.modelrouter.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.audit.SecurityAuditService;

/**
 * 安全过滤器链集成测试
 * 测试Spring Security配置和RBAC功能
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "jairouter.security.enabled=true",
        "jairouter.security.api-key.enabled=true"
})
class SecurityFilterChainIntegrationTest {
    
    @Autowired
    private WebTestClient webTestClient;
    
    @MockBean
    private ApiKeyService apiKeyService;
    
    @MockBean
    private SecurityAuditService auditService;
    
    @Test
    void testHealthEndpointPermitAll() {
        // 健康检查端点应该允许匿名访问
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
    
    @Test
    void testSwaggerEndpointPermitAll() {
        // API文档端点应该允许匿名访问
        webTestClient.get()
                .uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().isNotFound(); // 404是正常的，说明没有被安全拦截
    }
    
    @Test
    void testV1EndpointRequiresAuthentication() {
        // AI服务端点应该需要认证
        webTestClient.get()
                .uri("/v1/chat/completions")
                .exchange()
                .expectStatus().isUnauthorized();
    }
    
    @Test
    void testActuatorEndpointRequiresAdminRole() {
        // 监控端点应该需要管理员权限
        webTestClient.get()
                .uri("/actuator/metrics")
                .exchange()
                .expectStatus().isUnauthorized();
    }
    
    @Test
    @WithMockUser(roles = "READ")
    void testV1GetEndpointWithReadRole() {
        // 具有READ角色的用户应该能访问GET端点
        webTestClient.get()
                .uri("/v1/models")
                .exchange()
                .expectStatus().isNotFound(); // 404是正常的，说明通过了安全检查
    }
    
    @Test
    @WithMockUser(roles = "READ")
    void testV1PostEndpointWithReadRoleShouldFail() {
        // 具有READ角色的用户不应该能访问POST端点
        webTestClient.post()
                .uri("/v1/chat/completions")
                .exchange()
                .expectStatus().isForbidden();
    }
    
    @Test
    @WithMockUser(roles = "WRITE")
    void testV1PostEndpointWithWriteRole() {
        // 具有WRITE角色的用户应该能访问POST端点
        webTestClient.post()
                .uri("/v1/chat/completions")
                .exchange()
                .expectStatus().isNotFound(); // 404是正常的，说明通过了安全检查
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void testActuatorEndpointWithAdminRole() {
        // 具有ADMIN角色的用户应该能访问监控端点
        webTestClient.get()
                .uri("/actuator/metrics")
                .exchange()
                .expectStatus().isOk();
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void testConfigEndpointWithAdminRole() {
        // 具有ADMIN角色的用户应该能访问配置管理端点
        webTestClient.get()
                .uri("/api/config/services")
                .exchange()
                .expectStatus().isNotFound(); // 404是正常的，说明通过了安全检查
    }
    
    @Test
    @WithMockUser(roles = "USER")
    void testConfigEndpointWithUserRoleShouldFail() {
        // 普通用户不应该能访问配置管理端点
        webTestClient.get()
                .uri("/api/config/services")
                .exchange()
                .expectStatus().isForbidden();
    }
}