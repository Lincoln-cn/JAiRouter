package org.unreal.modelrouter.security.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API Key认证功能的集成测试
 * 
 * 测试范围：
 * - API Key认证的端到端流程
 * - 认证失败场景和错误处理
 * - 认证性能和并发访问
 * 
 * 需求覆盖：1.1, 1.2, 1.3, 2.1, 2.2, 2.3
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class AuthenticationIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private SecurityProperties securityProperties;

    private static final String VALID_API_KEY = "test-api-key-001";
    private static final String EXPIRED_API_KEY = "expired-api-key-001";
    private static final String INVALID_API_KEY = "invalid-api-key";
    private static final String TEST_ENDPOINT = "/v1/chat/completions";

    @BeforeEach
    void setUp() {
        // 设置测试用的API Key
        setupTestApiKeys();
    }

    /**
     * 测试有效API Key的认证流程
     * 需求：1.1 - 当客户端发送请求时，系统应当验证请求头中的API Key
     * 需求：1.2 - 当API Key有效时，系统应当允许请求继续处理
     */
    @Test
    void testValidApiKeyAuthentication() {
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", VALID_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * 测试缺失API Key的认证失败场景
     * 需求：1.3 - 当API Key无效或缺失时，系统应当返回401未授权错误
     */
    @Test
    void testMissingApiKeyAuthentication() {
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("unauthorized")
                .jsonPath("$.message").value(message -> 
                    assertThat(message.toString()).contains("API Key"));
    }

    /**
     * 测试无效API Key的认证失败场景
     * 需求：1.3 - 当API Key无效或缺失时，系统应当返回401未授权错误
     */
    @Test
    void testInvalidApiKeyAuthentication() {
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", INVALID_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("unauthorized")
                .jsonPath("$.message").value(message -> 
                    assertThat(message.toString()).contains("Invalid API Key"));
    }

    /**
     * 测试过期API Key的认证失败场景
     * 需求：1.4 - 当API Key过期时，系统应当返回401未授权错误并提示过期信息
     */
    @Test
    void testExpiredApiKeyAuthentication() {
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", EXPIRED_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("unauthorized")
                .jsonPath("$.message").value(message -> 
                    assertThat(message.toString()).contains("expired"));
    }

    /**
     * 测试多个有效API Key的支持
     * 需求：1.5 - 系统应当支持多个有效的API Key同时存在
     */
    @Test
    void testMultipleValidApiKeys() {
        String secondApiKey = "test-api-key-002";
        
        // 添加第二个API Key
        ApiKeyInfo secondKey = ApiKeyInfo.builder()
                .keyId("test-key-002")
                .keyValue(secondApiKey)
                .description("第二个测试API密钥")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .enabled(true)
                .permissions(List.of("read"))
                .build();
        
        StepVerifier.create(apiKeyService.saveApiKey(secondKey))
                .expectNext(secondKey)
                .verifyComplete();

        // 测试第一个API Key
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", VALID_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isOk();

        // 测试第二个API Key
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", secondApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * 测试API Key使用情况记录
     * 需求：1.7 - 系统应当记录API Key的使用情况用于审计
     */
    @Test
    void testApiKeyUsageTracking() {
        // 发送请求
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", VALID_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isOk();

        // 验证使用统计
        StepVerifier.create(apiKeyService.getApiKeyByValue(VALID_API_KEY))
                .assertNext(apiKey -> {
                    assertThat(apiKey.getUsage()).isNotNull();
                    assertThat(apiKey.getUsage().getTotalRequests()).isGreaterThan(0);
                    assertThat(apiKey.getUsage().getLastUsedAt()).isNotNull();
                })
                .verifyComplete();
    }

    /**
     * 测试认证性能
     * 验证认证过程不会显著影响请求处理性能
     */
    @Test
    void testAuthenticationPerformance() {
        int requestCount = 100;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < requestCount; i++) {
            webTestClient.post()
                    .uri(TEST_ENDPOINT)
                    .header("X-API-Key", VALID_API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(createTestChatRequest())
                    .exchange()
                    .expectStatus().isOk();
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double avgTime = (double) totalTime / requestCount;

        // 平均每个请求的认证时间应该小于50ms
        assertThat(avgTime).isLessThan(50.0);
    }

    /**
     * 测试并发认证访问
     * 验证系统在高并发情况下的认证稳定性
     */
    @Test
    void testConcurrentAuthentication() throws InterruptedException {
        int threadCount = 10;
        int requestsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        try {
                            webTestClient.post()
                                    .uri(TEST_ENDPOINT)
                                    .header("X-API-Key", VALID_API_KEY)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(createTestChatRequest())
                                    .exchange()
                                    .expectStatus().isOk();
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount * requestsPerThread);
        assertThat(failureCount.get()).isEqualTo(0);
    }

    /**
     * 测试不同HTTP方法的认证
     */
    @Test
    void testAuthenticationForDifferentHttpMethods() {
        // GET请求
        webTestClient.get()
                .uri("/v1/models")
                .header("X-API-Key", VALID_API_KEY)
                .exchange()
                .expectStatus().isOk();

        // POST请求
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", VALID_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * 测试自定义API Key头名称
     */
    @Test
    void testCustomApiKeyHeaderName() {
        // 临时修改配置中的头名称
        String originalHeaderName = securityProperties.getApiKey().getHeaderName();
        securityProperties.getApiKey().setHeaderName("Custom-API-Key");

        try {
            webTestClient.post()
                    .uri(TEST_ENDPOINT)
                    .header("Custom-API-Key", VALID_API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(createTestChatRequest())
                    .exchange()
                    .expectStatus().isOk();
        } finally {
            // 恢复原始配置
            securityProperties.getApiKey().setHeaderName(originalHeaderName);
        }
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

        // 创建过期的API Key
        ApiKeyInfo expiredKey = ApiKeyInfo.builder()
                .keyId("expired-key-001")
                .keyValue(EXPIRED_API_KEY)
                .description("过期的测试API密钥")
                .createdAt(LocalDateTime.now().minusDays(60))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .enabled(true)
                .permissions(List.of("read"))
                .build();

        // 保存测试API Key
        StepVerifier.create(apiKeyService.saveApiKey(validKey))
                .expectNext(validKey)
                .verifyComplete();

        StepVerifier.create(apiKeyService.saveApiKey(expiredKey))
                .expectNext(expiredKey)
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