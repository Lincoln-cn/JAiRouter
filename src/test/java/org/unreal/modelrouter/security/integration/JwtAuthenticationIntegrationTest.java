package org.unreal.modelrouter.security.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
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
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.service.JwtTokenRefreshService;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT认证功能的集成测试
 * 
 * 测试范围：
 * - JWT令牌认证流程
 * - JWT令牌验证和刷新
 * - JWT认证失败场景
 * 
 * 需求覆盖：2.1, 2.2, 2.3, 2.4, 2.5, 2.6
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class JwtAuthenticationIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    @Autowired
    private JwtTokenRefreshService jwtTokenRefreshService;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_ENDPOINT = "/v1/chat/completions";
    private static final String JWT_REFRESH_ENDPOINT = "/v1/auth/refresh";
    private static final String TEST_SECRET = "test-jwt-secret-key-for-integration-testing-purposes-only";
    private static final String TEST_USER_ID = "test-user-001";
    private static final String TEST_USERNAME = "testuser";

    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        // 设置JWT配置
        securityProperties.getJwt().setEnabled(true);
        securityProperties.getJwt().setSecret(TEST_SECRET);
        securityProperties.getJwt().setAlgorithm("HS256");
        securityProperties.getJwt().setExpirationMinutes(60);
        securityProperties.getJwt().setRefreshExpirationDays(7);

        secretKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes());
    }

    /**
     * 测试有效JWT令牌的认证流程
     * 需求：2.1 - 当客户端提供JWT令牌时，系统应当验证令牌的签名和有效性
     * 需求：2.2 - 当JWT令牌有效时，系统应当从令牌中提取用户信息并允许访问
     */
    @Test
    void testValidJwtTokenAuthentication() {
        String validToken = createValidJwtToken();

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * 测试无效JWT令牌的认证失败
     * 需求：2.3 - 当JWT令牌无效或过期时，系统应当返回401未授权错误
     */
    @Test
    void testInvalidJwtTokenAuthentication() {
        String invalidToken = "invalid.jwt.token";

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("unauthorized")
                .jsonPath("$.message").value(message -> 
                    assertThat(message.toString()).contains("Invalid JWT token"));
    }

    /**
     * 测试过期JWT令牌的认证失败
     * 需求：2.3 - 当JWT令牌无效或过期时，系统应当返回401未授权错误
     */
    @Test
    void testExpiredJwtTokenAuthentication() {
        String expiredToken = createExpiredJwtToken();

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
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
     * 测试JWT令牌刷新机制
     * 需求：2.4 - 系统应当支持JWT令牌的刷新机制
     * 需求：2.5 - 系统应当支持令牌黑名单管理机制
     */
    @Test
    void testJwtTokenRefresh() {
        String originalToken = createValidJwtToken();

        // 测试令牌刷新
        Map<String, String> refreshRequest = new HashMap<>();
        refreshRequest.put("token", originalToken);

        webTestClient.post()
                .uri(JWT_REFRESH_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(refreshRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.access_token").exists()
                .jsonPath("$.refresh_token").exists()
                .jsonPath("$.expires_in").exists();
    }

    /**
     * 测试JWT令牌验证器的直接调用
     * 需求：2.6 - 系统应当验证JWT令牌中的必要声明（如exp、iat等）
     */
    @Test
    void testJwtTokenValidatorDirectly() {
        String validToken = createValidJwtToken();

        StepVerifier.create(jwtTokenValidator.validateToken(validToken))
                .assertNext(authentication -> {
                    assertThat(authentication).isNotNull();
                    assertThat(authentication.isAuthenticated()).isTrue();
                    assertThat(authentication.getName()).isEqualTo(TEST_USERNAME);
                })
                .verifyComplete();
    }

    /**
     * 测试JWT令牌中缺少必要声明的情况
     * 需求：2.6 - 系统应当验证JWT令牌中的必要声明（如exp、iat等）
     */
    @Test
    void testJwtTokenWithMissingClaims() {
        String tokenWithoutExp = createJwtTokenWithoutExpiration();

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithoutExp)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    /**
     * 测试不同签名算法的JWT令牌
     * 需求：2.4 - 系统应当支持配置JWT签名密钥和算法
     */
    @Test
    void testDifferentSignatureAlgorithms() {
        // 测试HS256算法（默认）
        String hs256Token = createJwtTokenWithAlgorithm(SignatureAlgorithm.HS256);
        
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hs256Token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * 测试JWT认证的并发性能
     */
    @Test
    void testConcurrentJwtAuthentication() throws InterruptedException {
        String validToken = createValidJwtToken();
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
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
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
     * 测试JWT令牌刷新服务的性能
     */
    @Test
    void testJwtRefreshPerformance() {
        String originalToken = createValidJwtToken();
        int refreshCount = 50;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < refreshCount; i++) {
            StepVerifier.create(jwtTokenRefreshService.refreshToken(originalToken))
                    .assertNext(refreshedToken -> {
                        assertThat(refreshedToken).isNotNull();
                        assertThat(refreshedToken).isNotEqualTo(originalToken);
                    })
                    .verifyComplete();
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double avgTime = (double) totalTime / refreshCount;

        // 平均每次刷新时间应该小于100ms
        assertThat(avgTime).isLessThan(100.0);
    }

    /**
     * 测试Bearer令牌格式验证
     */
    @Test
    void testBearerTokenFormatValidation() {
        String validToken = createValidJwtToken();

        // 测试正确的Bearer格式
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isOk();

        // 测试错误的格式（缺少Bearer前缀）
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isUnauthorized();

        // 测试错误的格式（错误的前缀）
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createTestChatRequest())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private String createValidJwtToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(TEST_USERNAME)
                .setIssuer("jairouter")
                .setAudience("jairouter-client")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .claim("userId", TEST_USER_ID)
                .claim("username", TEST_USERNAME)
                .claim("roles", new String[]{"user"})
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private String createExpiredJwtToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(TEST_USERNAME)
                .setIssuer("jairouter")
                .setAudience("jairouter-client")
                .setIssuedAt(Date.from(now.minus(2, ChronoUnit.HOURS)))
                .setExpiration(Date.from(now.minus(1, ChronoUnit.HOURS)))
                .claim("userId", TEST_USER_ID)
                .claim("username", TEST_USERNAME)
                .claim("roles", new String[]{"user"})
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private String createJwtTokenWithoutExpiration() {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(TEST_USERNAME)
                .setIssuer("jairouter")
                .setAudience("jairouter-client")
                .setIssuedAt(Date.from(now))
                // 故意不设置过期时间
                .claim("userId", TEST_USER_ID)
                .claim("username", TEST_USERNAME)
                .claim("roles", new String[]{"user"})
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private String createJwtTokenWithAlgorithm(SignatureAlgorithm algorithm) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(TEST_USERNAME)
                .setIssuer("jairouter")
                .setAudience("jairouter-client")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .claim("userId", TEST_USER_ID)
                .claim("username", TEST_USERNAME)
                .claim("roles", new String[]{"user"})
                .signWith(secretKey, algorithm)
                .compact();
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