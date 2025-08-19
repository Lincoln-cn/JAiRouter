on;

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
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.config.SecurityConfigurationService;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.monitoring.security.SecurityMetrics;
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
 * 安全功能的端到端集成测试
 * 
 * 测试范围：
 * - 完整的安全功能测试套件
 * - 安全配置的动态更新
 * - 审计日志和监控指标验证
 * - 安全渗透测试和漏洞扫描
 * 
 * 需求覆盖：6.2, 7.1, 7.3
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class SecurityEndToEndIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private SecurityAuditService securityAuditService;

    @Autowired
    private SecurityConfigurationService securityConfigurationService;

    @Autowired
    private SecurityMetrics securityMetrics;

    @Autowired
    private SecurityProperties securityProperties;

    private static final String ADMIN_API_KEY = "admin-e2e-key-001";
    private static final String USER_API_KEY = "user-e2e-key-001";
    private static final String TEST_ENDPOINT = "/v1/chat/completions";
    private static final String ADMIN_ENDPOINT = "/v1/admin/security/config";

    @BeforeEach
    void setUp() {
        setupTestEnvironment();
    }

    /**
     * 测试完整的安全功能流程
     * 包括认证、脱敏、审计的端到端测试
     */
    @Test
    void testCompleteSecurityWorkflow() {
        String sensitiveRequest = """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "My password is secret123, phone is 13812345678, email is user@example.com"
                        }
                    ],
                    "max_tokens": 100
                }
                """;

        // 记录初始指标
        long initialAuthAttempts = securityMetrics.getAuthenticationAttemptsTotal();
        long initialAuthSuccesses = securityMetrics.getAuthenticationSuccessesTotal();
        long initialSanitizationOps = securityMetrics.getSanitizationOperationsTotal();

        // 执行完整的安全流程
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", USER_API_KEY)
                .header("User-Agent", "SecurityTest/1.0")
                .header("X-Forwarded-For", "192.168.1.100")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sensitiveRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(response -> {
                    String responseBody = new String(response.getResponseBody());
                    // 验证敏感数据已被脱敏
                    assertThat(responseBody).doesNotContain("secret123");
                    assertThat(responseBody).doesNotContain("13812345678");
                    assertThat(responseBody).doesNotContain("user@example.com");
                });

        // 验证指标更新
        assertThat(securityMetrics.getAuthenticationAttemptsTotal()).isGreaterThan(initialAuthAttempts);
        assertThat(securityMetrics.getAuthenticationSuccessesTotal()).isGreaterThan(initialAuthSuccesses);
        assertThat(securityMetrics.getSanitizationOperationsTotal()).isGreaterThan(initialSanitizationOps);

        // 验证审计日志记录
        StepVerifier.create(securityAuditService.getAuditEvents(
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(1),
                0, 10))
                .assertNext(auditEvents -> {
                    assertThat(auditEvents).isNotEmpty();
                    SecurityAuditEvent event = auditEvents.get(0);
                    assertThat(event.getEventType()).isEqualTo("AUTHENTICATION_SUCCESS");
                    assertThat(event.getClientIp()).isEqualTo("192.168.1.100");
                    assertThat(event.getUserAgent()).isEqualTo("SecurityTest/1.0");
                    assertThat(event.isSuccess()).isTrue();
                })
                .verifyComplete();
    }

    /**
     * 测试安全配置的动态更新
     * 需求：6.2 - 系统应当支持安全配置的热更新，无需重启服务
     */
    @Test
    void testDynamicSecurityConfigurationUpdate() {
        // 获取当前配置
        StepVerifier.create(securityConfigurationService.getCurrentConfiguration())
                .assertNext(currentConfig -> {
                    assertThat(currentConfig).isNotNull();
                    assertThat(currentConfig.isEnabled()).isTrue();
                })
                .verifyComplete();

        // 更新脱敏配置
        SecurityProperties.SanitizationConfig newSanitizationConfig = 
                new SecurityProperties.SanitizationConfig();
        newSanitizationConfig.getRequest().setEnabled(true);
        newSanitizationConfig.getRequest().setSensitiveWords(
                List.of("password", "secret", "token", "newSensitiveWord"));
        newSanitizationConfig.getRequest().setPiiPatterns(
                List.of("\\d{11}", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "\\d{18}"));

        // 执行动态更新
        StepVerifier.create(securityConfigurationService.updateSanitizationRules(newSanitizationConfig))
                .verifyComplete();

        // 验证新配置生效
        String requestWithNewSensitiveWord = """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "This contains newSensitiveWord that should be sanitized"
                        }
                    ]
                }
                """;

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", USER_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestWithNewSensitiveWord)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(response -> {
                    String responseBody = new String(response.getResponseBody());
                    // 验证新的敏感词被脱敏
                    assertThat(responseBody).doesNotContain("newSensitiveWord");
                });
    }

    /**
     * 测试API Key的动态管理
     * 需求：1.6 - 系统应当支持API Key的动态添加、删除和更新操作
     */
    @Test
    void testDynamicApiKeyManagement() {
        String newApiKey = "dynamic-test-key-001";
        
        // 创建新的API Key
        ApiKeyInfo newKey = ApiKeyInfo.builder()
                .keyId("dynamic-key-001")
                .keyValue(newApiKey)
                .description("动态创建的测试API密钥")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .enabled(true)
                .permissions(List.of("read"))
                .build();

        // 动态添加API Key
        StepVerifier.create(apiKeyService.saveApiKey(newKey))
                .expectNext(newKey)
                .verifyComplete();

        // 验证新API Key可以使用
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", newApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createSimpleChatRequest())
                .exchange()
                .expectStatus().isOk();

        // 动态禁用API Key
        newKey.setEnabled(false);
        StepVerifier.create(apiKeyService.saveApiKey(newKey))
                .expectNext(newKey)
                .verifyComplete();

        // 验证禁用的API Key无法使用
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", newApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createSimpleChatRequest())
                .exchange()
                .expectStatus().isUnauthorized();

        // 动态删除API Key
        StepVerifier.create(apiKeyService.deleteApiKey(newKey.getKeyId()))
                .verifyComplete();

        // 验证删除的API Key无法使用
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", newApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createSimpleChatRequest())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    /**
     * 测试审计日志的完整性和查询功能
     * 需求：7.1 - 系统应当记录所有认证尝试的详细日志
     * 需求：7.3 - 系统应当提供安全相关的监控指标
     */
    @Test
    void testComprehensiveAuditLogging() {
        String testUserAgent = "AuditTest/1.0";
        String testClientIp = "192.168.1.200";

        // 执行成功的认证
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", USER_API_KEY)
                .header("User-Agent", testUserAgent)
                .header("X-Forwarded-For", testClientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createSimpleChatRequest())
                .exchange()
                .expectStatus().isOk();

        // 执行失败的认证
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", "invalid-key")
                .header("User-Agent", testUserAgent)
                .header("X-Forwarded-For", testClientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createSimpleChatRequest())
                .exchange()
                .expectStatus().isUnauthorized();

        // 查询审计日志
        StepVerifier.create(securityAuditService.getAuditEvents(
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusMinutes(1),
                0, 20))
                .assertNext(auditEvents -> {
                    assertThat(auditEvents).hasSizeGreaterThanOrEqualTo(2);
                    
                    // 验证成功认证事件
                    SecurityAuditEvent successEvent = auditEvents.stream()
                            .filter(event -> event.getEventType().equals("AUTHENTICATION_SUCCESS"))
                            .filter(event -> testClientIp.equals(event.getClientIp()))
                            .findFirst()
                            .orElse(null);
                    
                    assertThat(successEvent).isNotNull();
                    assertThat(successEvent.getUserAgent()).isEqualTo(testUserAgent);
                    assertThat(successEvent.isSuccess()).isTrue();
                    assertThat(successEvent.getResource()).isEqualTo(TEST_ENDPOINT);
                    assertThat(successEvent.getAction()).isEqualTo("POST");

                    // 验证失败认证事件
                    SecurityAuditEvent failureEvent = auditEvents.stream()
                            .filter(event -> event.getEventType().equals("AUTHENTICATION_FAILURE"))
                            .filter(event -> testClientIp.equals(event.getClientIp()))
                            .findFirst()
                            .orElse(null);
                    
                    assertThat(failureEvent).isNotNull();
                    assertThat(failureEvent.getUserAgent()).isEqualTo(testUserAgent);
                    assertThat(failureEvent.isSuccess()).isFalse();
                    assertThat(failureEvent.getFailureReason()).contains("Invalid API Key");
                })
                .verifyComplete();
    }

    /**
     * 测试监控指标的准确性
     * 需求：7.3 - 系统应当提供安全相关的监控指标（认证成功率、失败次数等）
     */
    @Test
    void testSecurityMetricsAccuracy() {
        // 记录初始指标
        long initialAuthAttempts = securityMetrics.getAuthenticationAttemptsTotal();
        long initialAuthSuccesses = securityMetrics.getAuthenticationSuccessesTotal();
        long initialAuthFailures = securityMetrics.getAuthenticationFailuresTotal();

        int successfulRequests = 5;
        int failedRequests = 3;

        // 执行成功的认证请求
        for (int i = 0; i < successfulRequests; i++) {
            webTestClient.post()
                    .uri(TEST_ENDPOINT)
                    .header("X-API-Key", USER_API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(createSimpleChatRequest())
                    .exchange()
                    .expectStatus().isOk();
        }

        // 执行失败的认证请求
        for (int i = 0; i < failedRequests; i++) {
            webTestClient.post()
                    .uri(TEST_ENDPOINT)
                    .header("X-API-Key", "invalid-key-" + i)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(createSimpleChatRequest())
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        // 验证指标准确性
        assertThat(securityMetrics.getAuthenticationAttemptsTotal())
                .isEqualTo(initialAuthAttempts + successfulRequests + failedRequests);
        assertThat(securityMetrics.getAuthenticationSuccessesTotal())
                .isEqualTo(initialAuthSuccesses + successfulRequests);
        assertThat(securityMetrics.getAuthenticationFailuresTotal())
                .isEqualTo(initialAuthFailures + failedRequests);

        // 验证成功率计算
        double successRate = securityMetrics.getAuthenticationSuccessRate();
        double expectedSuccessRate = (double) (initialAuthSuccesses + successfulRequests) / 
                (initialAuthAttempts + successfulRequests + failedRequests);
        assertThat(successRate).isCloseTo(expectedSuccessRate, org.assertj.core.data.Offset.offset(0.01));
    }

    /**
     * 测试安全功能的高并发性能
     */
    @Test
    void testHighConcurrencySecurityPerformance() throws InterruptedException {
        int threadCount = 20;
        int requestsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        String sensitiveRequest = """
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

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        try {
                            webTestClient.post()
                                    .uri(TEST_ENDPOINT)
                                    .header("X-API-Key", USER_API_KEY)
                                    .header("Thread-ID", String.valueOf(threadId))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(sensitiveRequest)
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

        boolean completed = latch.await(120, TimeUnit.SECONDS);
        executor.shutdown();
        long endTime = System.currentTimeMillis();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount * requestsPerThread);
        assertThat(failureCount.get()).isEqualTo(0);

        // 验证平均响应时间
        long totalTime = endTime - startTime;
        double avgTimePerRequest = (double) totalTime / (threadCount * requestsPerThread);
        assertThat(avgTimePerRequest).isLessThan(200.0); // 平均响应时间应小于200ms
    }

    /**
     * 测试安全渗透场景
     * 验证系统对常见攻击的防护能力
     */
    @Test
    void testSecurityPenetrationScenarios() {
        // 测试SQL注入尝试
        testSqlInjectionAttempt();
        
        // 测试XSS攻击尝试
        testXssAttackAttempt();
        
        // 测试认证绕过尝试
        testAuthenticationBypassAttempt();
        
        // 测试敏感信息泄露尝试
        testSensitiveInformationLeakageAttempt();
    }

    private void testSqlInjectionAttempt() {
        String sqlInjectionPayload = """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "'; DROP TABLE users; --"
                        }
                    ]
                }
                """;

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", USER_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sqlInjectionPayload)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(response -> {
                    String responseBody = new String(response.getResponseBody());
                    // 验证SQL注入代码被脱敏或处理
                    assertThat(responseBody).doesNotContain("DROP TABLE");
                });
    }

    private void testXssAttackAttempt() {
        String xssPayload = """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "<script>alert('XSS')</script>"
                        }
                    ]
                }
                """;

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", USER_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(xssPayload)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(response -> {
                    String responseBody = new String(response.getResponseBody());
                    // 验证XSS代码被脱敏或转义
                    assertThat(responseBody).doesNotContain("<script>");
                });
    }

    private void testAuthenticationBypassAttempt() {
        // 尝试使用各种无效的认证方式
        String[] invalidAuthHeaders = {
                "Bearer fake-jwt-token",
                "Basic dGVzdDp0ZXN0", // test:test in base64
                "ApiKey invalid-key",
                "Token malicious-token"
        };

        for (String authHeader : invalidAuthHeaders) {
            webTestClient.post()
                    .uri(TEST_ENDPOINT)
                    .header("Authorization", authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(createSimpleChatRequest())
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    private void testSensitiveInformationLeakageAttempt() {
        // 尝试通过错误消息获取敏感信息
        webTestClient.post()
                .uri("/v1/admin/debug/config")
                .header("X-API-Key", "invalid-admin-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .consumeWith(response -> {
                    String responseBody = new String(response.getResponseBody());
                    // 验证错误消息不包含敏感信息
                    assertThat(responseBody).doesNotContain("database");
                    assertThat(responseBody).doesNotContain("password");
                    assertThat(responseBody).doesNotContain("secret");
                    assertThat(responseBody).doesNotContain("internal");
                });
    }

    private void setupTestEnvironment() {
        // 创建管理员API Key
        ApiKeyInfo adminKey = ApiKeyInfo.builder()
                .keyId("admin-e2e-key-001")
                .keyValue(ADMIN_API_KEY)
                .description("端到端测试管理员API密钥")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .enabled(true)
                .permissions(List.of("admin", "read", "write"))
                .build();

        // 创建普通用户API Key
        ApiKeyInfo userKey = ApiKeyInfo.builder()
                .keyId("user-e2e-key-001")
                .keyValue(USER_API_KEY)
                .description("端到端测试用户API密钥")
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

        // 配置安全设置
        securityProperties.setEnabled(true);
        securityProperties.getApiKey().setEnabled(true);
        securityProperties.getSanitization().getRequest().setEnabled(true);
        securityProperties.getSanitization().getResponse().setEnabled(true);
        securityProperties.getAudit().setEnabled(true);
    }

    private String createSimpleChatRequest() {
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