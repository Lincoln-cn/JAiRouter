package org.unreal.modelrouter.auth.security.authentication.impl;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.service.EnhancedJwtBlacklistService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DefaultJwtTokenValidator 单元测试
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultJwtTokenValidator 测试")
class DefaultJwtTokenValidatorTest {

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private JwtConfig jwtProperties;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private EnhancedJwtBlacklistService enhancedBlacklistService;

    @InjectMocks
    private DefaultJwtTokenValidator validator;

    private static final String TEST_SECRET = "test-secret-key-for-jwt-validation-min-32-chars";
    private SecretKey testKey;

    @BeforeEach
    void setUp() {
        testKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        when(securityProperties.getJwt()).thenReturn(jwtProperties);
        when(jwtProperties.getSecret()).thenReturn(TEST_SECRET);
        when(jwtProperties.getIssuer()).thenReturn("jairouter-test");
        when(jwtProperties.getExpirationMinutes()).thenReturn(30L);
        when(jwtProperties.getRefreshExpirationDays()).thenReturn(7L);
        when(jwtProperties.isBlacklistEnabled()).thenReturn(false); // 禁用黑名单简化测试
    }

    @Nested
    @DisplayName("令牌验证测试")
    class ValidateTokenTests {

        @Test
        @DisplayName("VAL-049: 验证有效令牌 - 成功")
        void testValidateTokenSuccess() {
            String token = generateTestToken("user123", Arrays.asList("USER", "ADMIN"));

            Mono<?> result = validator.validateToken(token);

            StepVerifier.create(result)
                .expectNextMatches(auth -> auth != null)
                .verifyComplete();
        }

        @Test
        @DisplayName("VAL-050: 验证令牌 - 无效令牌格式")
        void testValidateTokenInvalidFormat() {
            String invalidToken = "invalid.token.format";

            Mono<?> result = validator.validateToken(invalidToken);

            StepVerifier.create(result)
                .expectError()
                .verify();
        }
    }

    @Nested
    @DisplayName("黑名单检查测试")
    class IsTokenBlacklistedTests {

        @Test
        @DisplayName("VAL-055: 黑名单检查 - 功能禁用")
        void testIsTokenBlacklistedDisabled() {
            when(jwtProperties.isBlacklistEnabled()).thenReturn(false);
            String token = generateTestToken("user123", Arrays.asList("USER"));

            Mono<Boolean> result = validator.isTokenBlacklisted(token);

            StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("添加黑名单测试")
    class BlacklistTokenTests {

        @Test
        @DisplayName("VAL-059: 添加黑名单 - 功能禁用")
        void testBlacklistTokenDisabled() {
            when(jwtProperties.isBlacklistEnabled()).thenReturn(false);
            String token = generateTestToken("user123", Arrays.asList("USER"));

            Mono<Void> result = validator.blacklistToken(token);

            StepVerifier.create(result)
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("提取用户ID测试")
    class ExtractUserIdTests {

        @Test
        @DisplayName("VAL-060: 提取用户ID - 成功")
        void testExtractUserIdSuccess() {
            String token = generateTestToken("user123", Arrays.asList("USER"));

            Mono<String> result = validator.extractUserId(token);

            StepVerifier.create(result)
                .expectNext("user123")
                .verifyComplete();
        }

        @Test
        @DisplayName("VAL-061: 提取用户ID - 无效令牌")
        void testExtractUserIdInvalidToken() {
            String invalidToken = "invalid.token";

            Mono<String> result = validator.extractUserId(invalidToken);

            StepVerifier.create(result)
                .expectError()
                .verify();
        }
    }

    @Nested
    @DisplayName("令牌生成测试")
    class GenerateTokenTests {

        @Test
        @DisplayName("VAL-062: 生成令牌 - 基本生成")
        void testGenerateTokenBasic() {
            String subject = "user123";
            List<String> roles = Arrays.asList("USER", "ADMIN");
            Map<String, Object> claims = new HashMap<>();

            String token = validator.generateToken(subject, roles, claims);

            assertNotNull(token);
            assertTrue(token.split("\\.").length == 3);
        }

        @Test
        @DisplayName("VAL-063: 生成令牌 - 带额外声明")
        void testGenerateTokenWithClaims() {
            String subject = "user123";
            List<String> roles = Arrays.asList("USER");
            Map<String, Object> claims = Map.of("customClaim", "customValue");

            String token = validator.generateToken(subject, roles, claims);

            assertNotNull(token);
            assertTrue(token.split("\\.").length == 3);
        }

        @Test
        @DisplayName("VAL-064: 生成令牌 - 无角色")
        void testGenerateTokenNoRoles() {
            String subject = "user123";
            List<String> roles = Collections.emptyList();
            Map<String, Object> claims = new HashMap<>();

            String token = validator.generateToken(subject, roles, claims);

            assertNotNull(token);
        }
    }

    // Helper method
    private String generateTestToken(String subject, List<String> roles) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + Duration.ofMinutes(30).toMillis());

        return Jwts.builder()
            .subject(subject)
            .issuer("jairouter-test")
            .issuedAt(now)
            .expiration(expiration)
            .id(UUID.randomUUID().toString())
            .claim("roles", roles)
            .signWith(testKey)
            .compact();
    }
}
