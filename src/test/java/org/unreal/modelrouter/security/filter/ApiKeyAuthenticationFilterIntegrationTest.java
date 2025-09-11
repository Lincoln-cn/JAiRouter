package org.unreal.modelrouter.security.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.unreal.modelrouter.filter.SpringSecurityAuthenticationFilter;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.exception.AuthenticationException;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import reactor.core.publisher.Mono;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * API Key认证过滤器的集成测试
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "jairouter.security.api-key.enabled=true",
        "jairouter.security.api-key.header-name=X-API-Key"
})
class ApiKeyAuthenticationFilterIntegrationTest {

    @MockBean
    private ApiKeyService apiKeyService;

    @MockBean
    private SecurityAuditService auditService;

    @Autowired
    private SecurityProperties securityProperties;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        // 创建测试路由
        RouterFunction<ServerResponse> route = RouterFunctions.route()
                .GET("/api/test", request -> ServerResponse.ok().bodyValue("success"))
                .POST("/api/test", request -> ServerResponse.ok().bodyValue("created"))
                .DELETE("/api/test", request -> ServerResponse.ok().bodyValue("deleted"))
                .build();

        // 创建WebTestClient并添加过滤器
        SpringSecurityAuthenticationFilter filter = new SpringSecurityAuthenticationFilter(
                securityProperties, null, null);

        webTestClient = WebTestClient.bindToRouterFunction(route)
                .webFilter(filter)
                .build();

        // 模拟审计服务
        when(auditService.recordEvent(any(SecurityAuditEvent.class))).thenReturn(Mono.empty());
    }

    @Test
    void testSuccessfulAuthentication() {
        // 准备测试数据
        ApiKeyInfo apiKeyInfo = createTestApiKey("test-key", Arrays.asList("read"));
        when(apiKeyService.validateApiKey("valid-api-key")).thenReturn(Mono.just(apiKeyInfo));
        when(apiKeyService.updateUsageStatistics("test-key", true)).thenReturn(Mono.empty());

        // 执行测试
        webTestClient.get()
                .uri("/api/test")
                .header("X-API-Key", "valid-api-key")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("success");
    }

    @Test
    void testMissingApiKey() {
        webTestClient.get()
                .uri("/api/test")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("MISSING_API_KEY")
                .jsonPath("$.message").isEqualTo("缺少API Key");
    }

    @Test
    void testInvalidApiKey() {
        when(apiKeyService.validateApiKey("invalid-api-key"))
                .thenReturn(Mono.error(AuthenticationException.invalidApiKey()));

        webTestClient.get()
                .uri("/api/test")
                .header("X-API-Key", "invalid-api-key")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("INVALID_API_KEY");
    }

    @Test
    void testInsufficientPermissions() {
        // 只有read权限的API Key尝试POST请求
        ApiKeyInfo apiKeyInfo = createTestApiKey("read-only-key", Arrays.asList("read"));
        when(apiKeyService.validateApiKey("read-only-api-key")).thenReturn(Mono.just(apiKeyInfo));

        webTestClient.post()
                .uri("/api/test")
                .header("X-API-Key", "read-only-api-key")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("INVALID_API_KEY")
                .jsonPath("$.message").isEqualTo("权限不足");
    }

    @Test
    void testAdminPermissionAllowsAll() {
        // admin权限应该允许所有操作
        ApiKeyInfo apiKeyInfo = createTestApiKey("admin-key", Arrays.asList("admin"));
        when(apiKeyService.validateApiKey("admin-api-key")).thenReturn(Mono.just(apiKeyInfo));
        when(apiKeyService.updateUsageStatistics("admin-key", true)).thenReturn(Mono.empty());

        webTestClient.delete()
                .uri("/api/test")
                .header("X-API-Key", "admin-api-key")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("deleted");
    }

    @Test
    void testAuthorizationHeaderSupport() {
        // 测试通过Authorization头传递API Key
        ApiKeyInfo apiKeyInfo = createTestApiKey("bearer-key", Arrays.asList("read"));
        when(apiKeyService.validateApiKey("bearer-api-key")).thenReturn(Mono.just(apiKeyInfo));
        when(apiKeyService.updateUsageStatistics("bearer-key", true)).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/test")
                .header("Authorization", "Bearer bearer-api-key")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("success");
    }

    @Test
    void testExcludedPaths() {
        // 健康检查端点应该被排除，不需要认证
        RouterFunction<ServerResponse> healthRoute = RouterFunctions.route()
                .GET("/actuator/health", request -> ServerResponse.ok().bodyValue("UP"))
                .build();

        SpringSecurityAuthenticationFilter filter = new SpringSecurityAuthenticationFilter(
                securityProperties, null, null);

        WebTestClient healthClient = WebTestClient.bindToRouterFunction(healthRoute)
                .webFilter(filter)
                .build();

        healthClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("UP");
    }

    @Test
    void testServiceException() {
        // 模拟服务异常
        when(apiKeyService.validateApiKey(anyString()))
                .thenReturn(Mono.error(new RuntimeException("Database connection failed")));

        webTestClient.get()
                .uri("/api/test")
                .header("X-API-Key", "any-key")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.message").isEqualTo("认证服务内部错误");
    }

    @Test
    void testPermissionMapping() {
        // 测试不同HTTP方法的权限要求
        ApiKeyInfo writeKey = createTestApiKey("write-key", Arrays.asList("write"));
        when(apiKeyService.validateApiKey("write-api-key")).thenReturn(Mono.just(writeKey));
        when(apiKeyService.updateUsageStatistics("write-key", true)).thenReturn(Mono.empty());

        // write权限应该允许POST请求
        webTestClient.post()
                .uri("/api/test")
                .header("X-API-Key", "write-api-key")
                .exchange()
                .expectStatus().isOk();

        // 但不应该允许GET请求（需要read权限）
        webTestClient.get()
                .uri("/api/test")
                .header("X-API-Key", "write-api-key")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testEmptyPermissionsList() {
        // 空权限列表应该允许所有操作
        ApiKeyInfo noPermissionsKey = createTestApiKey("no-perms-key", Arrays.asList());
        when(apiKeyService.validateApiKey("no-perms-api-key")).thenReturn(Mono.just(noPermissionsKey));
        when(apiKeyService.updateUsageStatistics("no-perms-key", true)).thenReturn(Mono.empty());

        // 应该允许所有HTTP方法
        webTestClient.get()
                .uri("/api/test")
                .header("X-API-Key", "no-perms-api-key")
                .exchange()
                .expectStatus().isOk();

        webTestClient.post()
                .uri("/api/test")
                .header("X-API-Key", "no-perms-api-key")
                .exchange()
                .expectStatus().isOk();

        webTestClient.delete()
                .uri("/api/test")
                .header("X-API-Key", "no-perms-api-key")
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * 创建测试用的API Key信息
     */
    private ApiKeyInfo createTestApiKey(String keyId, java.util.List<String> permissions) {
        return ApiKeyInfo.builder()
                .keyId(keyId)
                .keyValue("test-api-key-value")
                .description("测试API Key")
                .enabled(true)
                .permissions(permissions)
                .build();
    }
}