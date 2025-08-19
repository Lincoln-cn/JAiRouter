package org.unreal.modelrouter.security.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.sanitization.SanitizationService;
import org.unreal.modelrouter.security.sanitization.SanitizationMetrics;
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
 * 数据脱敏功能的集成测试
 * 
 * 测试范围：
 * - 不同内容类型的脱敏测试
 * - 脱敏规则组合和优先级
 * - 脱敏功能的性能影响
 * - 白名单用户的跳过逻辑
 * 
 * 需求覆盖：4.1, 4.2, 4.7, 5.1, 5.2
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class SanitizationIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private SanitizationService sanitizationService;

    @Autowired
    private SanitizationMetrics sanitizationMetrics;

    @Autowired
    private SecurityProperties securityProperties;

    private static final String ADMIN_API_KEY = "admin-api-key-001";
    private static final String USER_API_KEY = "user-api-key-001";
    private static final String TEST_ENDPOINT = "/v1/chat/completions";

    @BeforeEach
    void setUp() {
        setupTestApiKeys();
        setupSanitizationConfiguration();
    }

    /**
     * 测试请求数据中敏感词的脱敏处理
     * 需求：4.1 - 当请求包含敏感词汇时，系统应当将其替换为占位符或删除
     * 需求：4.2 - 当请求包含PII数据时，系统应当进行脱敏处理
     */
    @Test
    void testRequestSensitiveWordSanitization() {
        String requestWithSensitiveWords = """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "My password is secret123 and my email is user@example.com"
                        }
                    ],
                    "max_tokens": 100
                }
                """;

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", USER_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestWithSensitiveWords)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(response -> {
                    String responseBody = new String(response.getResponseBody());
                    // 验证敏感词已被脱敏
                    assertThat(responseBody).doesNotContain("secret123");
                    assertThat(responseBody).doesNotContain("user@example.com");
                });
    }

    /**
     * 测试请求数据中PII数据的脱敏处理
     * 需求：4.2 - 当请求包含PII数据（如电话号码、邮箱、身份证号）时，系统应当进行脱敏处理
     */
    @Test
    void testRequestPiiDataSanitization() {
        String requestWithPiiData = """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "请联系我，手机号：13812345678，邮箱：test@example.com，身份证：123456789012345678"
                        }
                    ],
                    "max_tokens": 100
                }
                """;

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", USER_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestWithPiiData)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(response -> {
                    String responseBody = new String(response.getResponseBody());
                    // 验证PII数据已被脱敏
                    assertThat(responseBody).doesNotContain("13812345678");
                    assertThat(responseBody).doesNotContain("test@example.com");
                    assertThat(responseBody).doesNotContain("123456789012345678");
                    // 验证脱敏后的格式
                    assertThat(responseBody).contains("138****5678");
                    assertThat(responseBody).contains("****@example.com");
                    assertThat(responseBody).contains("123456********5678");
                });
    }

    /**
     * 测试响应数据的脱敏处理
     * 需求：5.1 - 当AI模型响应包含敏感信息时，系统应当进行脱敏处理
     * 需求：5.2 - 系统应当支持对响应中的PII数据进行识别和脱敏
     */
    @Test
    void testResponseDataSanitization() {
        String normalRequest = """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "Generate a sample user profile"
                        }
                    ],
                    "max_tokens": 200
                }
                """;

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", USER_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(normalRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(response -> {
                    String responseBody = new String(response.getResponseBody());
                    // 验证响应中的敏感信息已被脱敏
                    // 这里假设AI模型返回的响应包含了一些敏感信息
                    assertThat(responseBody).doesNotContainPattern("\\d{11}"); // 手机号
                    assertThat(responseBody).doesNotContainPattern("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"); // 邮箱
                });
    }

    /**
     * 测试不同内容类型的脱敏处理
     * 需求：4.2 - 系统应当支持正则表达式模式匹配进行数据识别
     */
    @Test
    void testDifferentContentTypeSanitization() {
        // 测试JSON内容类型
        testJsonContentSanitization();
        
        // 测试纯文本内容类型
        testPlainTextContentSanitization();
        
        // 测试XML内容类型
        testXmlContentSanitization();
    }

    private void testJsonContentSanitization() {
        String jsonRequest = """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "My secret token is abc123 and phone is 13812345678"
                        }
                    ]
                }
                """;

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", USER_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonRequest)
                .exchange()
                .expectStatus().isOk();
    }

    private void testPlainTextContentSanitization() {
        String textRequest = "My password is secret123 and email is user@example.com";

        webTestClient.post()
                .uri("/v1/embeddings")
                .header("X-API-Key", USER_API_KEY)
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(textRequest)
                .exchange()
                .expectStatus().isOk();
    }

    private void testXmlContentSanitization() {
        String xmlRequest = """
                <request>
                    <user>
                        <email>user@example.com</email>
                        <phone>13812345678</phone>
                        <password>secret123</password>
                    </user>
                </request>
                """;

        webTestClient.post()
                .uri("/v1/custom")
                .header("X-API-Key", USER_API_KEY)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(xmlRequest)
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * 测试脱敏规则的组合和优先级
     * 需求：4.3 - 系统应当支持可配置的敏感词词库
     * 需求：4.5 - 系统应当支持不同类型数据的不同脱敏策略
     */
    @Test
    void testSanitizationRulePriorityAndCombination() {
        // 配置多个脱敏规则
        securityProperties.getSanitization().getRequest().setSensitiveWords(
                List.of("password", "secret", "token", "key"));
        securityProperties.getSanitization().getRequest().setPiiPatterns(
                List.of("\\d{11}", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "\\d{18}"));

        String complexRequest = """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "My password is secret123, token is abc456, phone is 13812345678, email is user@example.com, and ID is 123456789012345678"
                        }
                    ]
                }
                """;

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", USER_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(complexRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(response -> {
                    String responseBody = new String(response.getResponseBody());
                    // 验证所有敏感信息都被脱敏
                    assertThat(responseBody).doesNotContain("secret123");
                    assertThat(responseBody).doesNotContain("abc456");
                    assertThat(responseBody).doesNotContain("13812345678");
                    assertThat(responseBody).doesNotContain("user@example.com");
                    assertThat(responseBody).doesNotContain("123456789012345678");
                });
    }

    /**
     * 测试白名单用户的跳过逻辑
     * 需求：4.7 - 系统应当支持白名单机制，允许特定用户跳过脱敏处理
     */
    @Test
    void testWhitelistUserSkipSanitization() {
        // 配置白名单用户
        securityProperties.getSanitization().getRequest().setWhitelistUsers(
                List.of("admin-api-key-001"));

        String requestWithSensitiveData = """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "My password is secret123 and phone is 13812345678"
                        }
                    ]
                }
                """;

        // 测试白名单用户（管理员）- 应该跳过脱敏
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", ADMIN_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestWithSensitiveData)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(response -> {
                    String responseBody = new String(response.getResponseBody());
                    // 白名单用户的敏感数据不应被脱敏
                    assertThat(responseBody).contains("secret123");
                    assertThat(responseBody).contains("13812345678");
                });

        // 测试普通用户 - 应该进行脱敏
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", USER_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestWithSensitiveData)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(response -> {
                    String responseBody = new String(response.getResponseBody());
                    // 普通用户的敏感数据应被脱敏
                    assertThat(responseBody).doesNotContain("secret123");
                    assertThat(responseBody).doesNotContain("13812345678");
                });
    }

    /**
     * 测试脱敏功能的性能影响
     * 验证脱敏处理不会显著影响请求处理性能
     */
    @Test
    void testSanitizationPerformanceImpact() {
        String requestWithSensitiveData = """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "My password is secret123, email is user@example.com, phone is 13812345678"
                        }
                    ]
                }
                """;

        int requestCount = 100;
        
        // 测试启用脱敏的性能
        long startTimeWithSanitization = System.currentTimeMillis();
        for (int i = 0; i < requestCount; i++) {
            webTestClient.post()
                    .uri(TEST_ENDPOINT)
                    .header("X-API-Key", USER_API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestWithSensitiveData)
                    .exchange()
                    .expectStatus().isOk();
        }
        long endTimeWithSanitization = System.currentTimeMillis();
        long timeWithSanitization = endTimeWithSanitization - startTimeWithSanitization;

        // 测试白名单用户（跳过脱敏）的性能
        long startTimeWithoutSanitization = System.currentTimeMillis();
        for (int i = 0; i < requestCount; i++) {
            webTestClient.post()
                    .uri(TEST_ENDPOINT)
                    .header("X-API-Key", ADMIN_API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestWithSensitiveData)
                    .exchange()
                    .expectStatus().isOk();
        }
        long endTimeWithoutSanitization = System.currentTimeMillis();
        long timeWithoutSanitization = endTimeWithoutSanitization - startTimeWithoutSanitization;

        // 脱敏处理的性能开销应该在合理范围内（不超过50%）
        double performanceOverhead = (double) (timeWithSanitization - timeWithoutSanitization) / timeWithoutSanitization;
        assertThat(performanceOverhead).isLessThan(0.5);
    }

    /**
     * 测试并发脱敏处理的稳定性
     */
    @Test
    void testConcurrentSanitizationProcessing() throws InterruptedException {
        String requestWithSensitiveData = """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "My password is secret123 and phone is 13812345678"
                        }
                    ]
                }
                """;

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
                                    .header("X-API-Key", USER_API_KEY)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(requestWithSensitiveData)
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

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount * requestsPerThread);
        assertThat(failureCount.get()).isEqualTo(0);
    }

    /**
     * 测试脱敏服务的直接调用
     */
    @Test
    void testSanitizationServiceDirectly() {
        String sensitiveContent = "My password is secret123, email is user@example.com, phone is 13812345678";

        StepVerifier.create(sanitizationService.sanitizeRequest(sensitiveContent, "text/plain"))
                .assertNext(sanitizedContent -> {
                    assertThat(sanitizedContent).doesNotContain("secret123");
                    assertThat(sanitizedContent).doesNotContain("user@example.com");
                    assertThat(sanitizedContent).doesNotContain("13812345678");
                    assertThat(sanitizedContent).contains("****");
                })
                .verifyComplete();
    }

    /**
     * 测试脱敏指标的收集
     */
    @Test
    void testSanitizationMetricsCollection() {
        String requestWithSensitiveData = """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "My password is secret123 and phone is 13812345678"
                        }
                    ]
                }
                """;

        // 记录初始指标
        long initialOperations = sanitizationMetrics.getSanitizationOperationsTotal();
        long initialRuleMatches = sanitizationMetrics.getSanitizationRuleMatchesTotal();

        // 发送请求触发脱敏
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", USER_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestWithSensitiveData)
                .exchange()
                .expectStatus().isOk();

        // 验证指标增加
        assertThat(sanitizationMetrics.getSanitizationOperationsTotal()).isGreaterThan(initialOperations);
        assertThat(sanitizationMetrics.getSanitizationRuleMatchesTotal()).isGreaterThan(initialRuleMatches);
    }

    private void setupTestApiKeys() {
        // 创建管理员API Key（白名单用户）
        ApiKeyInfo adminKey = ApiKeyInfo.builder()
                .keyId("admin-key-001")
                .keyValue(ADMIN_API_KEY)
                .description("管理员API密钥")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .enabled(true)
                .permissions(List.of("admin", "read", "write"))
                .build();

        // 创建普通用户API Key
        ApiKeyInfo userKey = ApiKeyInfo.builder()
                .keyId("user-key-001")
                .keyValue(USER_API_KEY)
                .description("普通用户API密钥")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .enabled(true)
                .permissions(List.of("read", "write"))
                .build();

        // 保存测试API Key
        StepVerifier.create(apiKeyService.saveApiKey(adminKey))
                .expectNext(adminKey)
                .verifyComplete();

        StepVerifier.create(apiKeyService.saveApiKey(userKey))
                .expectNext(userKey)
                .verifyComplete();
    }

    private void setupSanitizationConfiguration() {
        // 配置请求脱敏
        securityProperties.getSanitization().getRequest().setEnabled(true);
        securityProperties.getSanitization().getRequest().setSensitiveWords(
                List.of("password", "secret", "token"));
        securityProperties.getSanitization().getRequest().setPiiPatterns(
                List.of("\\d{11}", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "\\d{18}"));
        securityProperties.getSanitization().getRequest().setMaskingChar("*");

        // 配置响应脱敏
        securityProperties.getSanitization().getResponse().setEnabled(true);
        securityProperties.getSanitization().getResponse().setSensitiveWords(
                List.of("internal", "debug", "error"));
        securityProperties.getSanitization().getResponse().setPiiPatterns(
                List.of("\\d{11}", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"));
        securityProperties.getSanitization().getResponse().setMaskingChar("*");
    }
}