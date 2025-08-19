package org.unreal.modelrouter.security.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证错误处理的集成测试
 * 
 * 测试范围：
 * - 各种认证失败场景的错误处理
 * - 错误响应格式的一致性
 * - 安全审计日志的记录
 * - 异常处理的完整性
 * 
 * 需求覆盖：1.3, 1.4, 2.3, 3.5, 3.6, 7.1, 7.2
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class AuthenticationErrorHandlingIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private SecurityAuditService securityAuditService;

    @Autowired
    private SecurityProperties securityProperties;

    private static final String TEST_ENDPOINT = "/v1/chat/completions";
    private static final String VALID_API_KEY = "test-api-key-001";
    private static final String DISABLED_API_KEY = "disabled-api-key-001";

    @BeforeEach
    void setUp() {
        setupTestApiKeys();
    }

    /**
     * 测试缺失认证信息的错误处理
     * 需求：1.3 - 当API Key无效或缺失时，系统应当返回401未授权错误
     */
    @Test
    void testMissingAuthenticationError() {
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.error").isEqualTo("unauthorized")
                .jsonPath("$.message").exists()
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.path").isEqualTo(TEST_ENDPOINT)
                .jsonPath("$.status").isEqualTo(401);
    }

    /**
     * 测试无效API Key的错误处理
     * 需求：1.3 - 当API Key无效或缺失时，系统应当返回401未授权错误
     */
    @Test
    void testInvalidApiKeyError() {
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", "invalid-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.error").isEqualTo("unauthorized")
                .jsonPath("$.message").value(message -> 
                    assertThat(message.toString()).contains("Invalid API Key"))
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.path").isEqualTo(TEST_ENDPOINT)
                .jsonPath("$.status").isEqualTo(401);
    }

    /**
     * 测试禁用API Key的错误处理
     * 需求：1.3 - 当API Key无效或缺失时，系统应当返回401未授权错误
     */
    @Test
    void testDisabledApiKeyError() {
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", DISABLED_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.error").isEqualTo("unauthorized")
                .jsonPath("$.message").value(message -> 
                    assertThat(message.toString()).contains("disabled"))
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.path").isEqualTo(TEST_ENDPOINT)
                .jsonPath("$.status").isEqualTo(401);
    }

    /**
     * 测试过期API Key的错误处理
     * 需求：1.4 - 当API Key过期时，系统应当返回401未授权错误并提示过期信息
     */
    @Test
    void testExpiredApiKeyError() {
        // 创建过期的API Key
        ApiKeyInfo expiredKey = ApiKeyInfo.builder()
                .keyId("expired-key-001")
                .keyValue("expired-api-key-001")
                .description("过期的测试API密钥")
                .createdAt(LocalDateTime.now().minusDays(60))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .enabled(true)
                .permissions(List.of("read"))
                .build();

        StepVerifier.create(apiKeyService.saveApiKey(expiredKey))
                .expectNext(expiredKey)
                .verifyComplete();

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", "expired-api-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.error").isEqualTo("unauthorized")
                .jsonPath("$.message").value(message -> 
                    assertThat(message.toString()).contains("expired"))
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.path").isEqualTo(TEST_ENDPOINT)
                .jsonPath("$.status").isEqualTo(401);
    }

    /**
     * 测试无效JWT令牌的错误处理
     * 需求：2.3 - 当JWT令牌无效或过期时，系统应当返回401未授权错误
     */
    @Test
    void testInvalidJwtTokenError() {
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.error").isEqualTo("unauthorized")
                .jsonPath("$.message").value(message -> 
                    assertThat(message.toString()).contains("Invalid JWT token"))
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.path").isEqualTo(TEST_ENDPOINT)
                .jsonPath("$.status").isEqualTo(401);
    }

    /**
     * 测试格式错误的Authorization头的错误处理
     */
    @Test
    void testMalformedAuthorizationHeaderError() {
        // 测试缺少Bearer前缀
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "some-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody()
                .jsonPath("$.error").isEqualTo("unauthorized")
                .jsonPath("$.message").value(message -> 
                    assertThat(message.toString()).contains("Invalid authorization header format"));

        // 测试空的Bearer令牌
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody()
                .jsonPath("$.error").isEqualTo("unauthorized")
                .jsonPath("$.message").value(message -> 
                    assertThat(message.toString()).contains("Empty token"));
    }

    /**
     * 测试权限不足的错误处理
     * 需求：3.3 - 系统应当支持基于角色的访问控制（RBAC）
     */
    @Test
    void testInsufficientPermissionsError() {
        // 创建只有读权限的API Key
        ApiKeyInfo readOnlyKey = ApiKeyInfo.builder()
                .keyId("readonly-key-001")
                .keyValue("readonly-api-key-001")
                .description("只读权限的测试API密钥")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .enabled(true)
                .permissions(List.of("read"))
                .build();

        StepVerifier.create(apiKeyService.saveApiKey(readOnlyKey))
                .expectNext(readOnlyKey)
                .verifyComplete();

        // 尝试访问需要写权限的端点
        webTestClient.post()
                .uri("/v1/admin/config")
                .header("X-API-Key", "readonly-api-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"config\": \"test\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
                .expectBody()
                .jsonPath("$.error").isEqualTo("forbidden")
                .jsonPath("$.message").value(message -> 
                    assertThat(message.toString()).contains("Insufficient permissions"));
    }

    /**
     * 测试认证失败的审计日志记录
     * 需求：7.1 - 系统应当记录所有认证尝试的详细日志
     * 需求：7.2 - 系统应当记录数据脱敏操作的审计信息
     */
    @Test
    void testAuthenticationFailureAuditLogging() {
        String invalidApiKey = "invalid-audit-test-key";
        String clientIp = "192.168.1.100";

        // 发送认证失败的请求
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", invalidApiKey)
                .header("X-Forwarded-For", clientIp)
                .header("User-Agent", "Test-Client/1.0")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isUnauthorized();

        // 验证审计日志是否记录
        StepVerifier.create(securityAuditService.getAuditEvents(
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(1),
                0, 10))
                .assertNext(auditEvents -> {
                    assertThat(auditEvents).isNotEmpty();
                    SecurityAuditEvent event = auditEvents.get(0);
                    assertThat(event.getEventType()).isEqualTo("AUTHENTICATION_FAILURE");
                    assertThat(event.getClientIp()).isEqualTo(clientIp);
                    assertThat(event.getUserAgent()).isEqualTo("Test-Client/1.0");
                    assertThat(event.getResource()).isEqualTo(TEST_ENDPOINT);
                    assertThat(event.getAction()).isEqualTo("POST");
                    assertThat(event.isSuccess()).isFalse();
                    assertThat(event.getFailureReason()).contains("Invalid API Key");
                })
                .verifyComplete();
    }

    /**
     * 测试多种认证方式同时存在时的错误处理
     */
    @Test
    void testMultipleAuthenticationMethodsError() {
        // 同时提供API Key和JWT令牌
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", "some-api-key")
                .header(HttpHeaders.AUTHORIZATION, "Bearer some.jwt.token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
                .expectBody()
                .jsonPath("$.error").isEqualTo("bad_request")
                .jsonPath("$.message").value(message -> 
                    assertThat(message.toString()).contains("Multiple authentication methods"));
    }

    /**
     * 测试错误响应的一致性
     * 需求：3.5 - 系统应当集成Spring Security的异常处理机制
     */
    @Test
    void testErrorResponseConsistency() {
        // 测试多个不同的认证错误，验证响应格式一致性
        String[] invalidCredentials = {
            null, // 无认证信息
            "invalid-api-key", // 无效API Key
            "Bearer invalid.jwt.token" // 无效JWT令牌
        };

        for (String credential : invalidCredentials) {
            WebTestClient.RequestHeadersSpec<?> request = webTestClient.post()
                    .uri(TEST_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(createTestChatRequest());

            if (credential != null) {
                if (credential.startsWith("Bearer ")) {
                    request = request.header(HttpHeaders.AUTHORIZATION, credential);
                } else {
                    request = request.header("X-API-Key", credential);
                }
            }

            request.exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.error").exists()
                    .jsonPath("$.message").exists()
                    .jsonPath("$.timestamp").exists()
                    .jsonPath("$.path").exists()
                    .jsonPath("$.status").exists();
        }
    }

    /**
     * 测试认证错误的国际化支持
     */
    @Test
    void testAuthenticationErrorInternationalization() {
        // 测试中文错误消息
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("Accept-Language", "zh-CN")
                .header("X-API-Key", "invalid-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.message").value(message -> 
                    assertThat(message.toString()).contains("无效"));

        // 测试英文错误消息
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("Accept-Language", "en-US")
                .header("X-API-Key", "invalid-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.message").value(message -> 
                    assertThat(message.toString()).contains("Invalid"));
    }

    private void setupTestApiKeys() {
        // 创建有效的API Key
        ApiKeyInfo validKey = ApiKeyInfo.builder()
                .keyId("test-key-001")
                .keyValue(VALID_API_KEY)
                .description("测试API密钥")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .enabled(true)
                .permissions(List.of("read", "write"))
                .build();

        // 创建禁用的API Key
        ApiKeyInfo disabledKey = ApiKeyInfo.builder()
                .keyId("disabled-key-001")
                .keyValue(DISABLED_API_KEY)
                .description("禁用的测试API密钥")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .enabled(false)
                .permissions(List.of("read"))
                .build();

        // 保存测试API Key
        StepVerifier.create(apiKeyService.saveApiKey(validKey))
                .expectNext(validKey)
                .verifyComplete();

        StepVerifier.create(apiKeyService.saveApiKey(disabledKey))
                .expectNext(disabledKey)
                .verifyComplete();
    }

    private String createTestChatRequest() {
        return """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "Hello, world!"
                        }
                    ],
                    "max_tokens": 100
                }
                """;
    }
}