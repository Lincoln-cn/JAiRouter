package org.unreal.modelrouter.auth.controller;

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
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.auth.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.service.AccountManager;
import org.unreal.modelrouter.auth.security.service.JwtPersistenceService;
import org.unreal.modelrouter.auth.security.service.JwtTokenRefreshService;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.JwtTokenInfo;
import org.unreal.modelrouter.common.dto.LoginRequest;
import org.unreal.modelrouter.common.dto.LoginResponse;
import org.unreal.modelrouter.common.dto.TokenRefreshRequest;
import org.unreal.modelrouter.common.dto.TokenValidationRequest;
import org.unreal.modelrouter.common.dto.TokenValidationResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JwtTokenController 单元测试
 *
 * <p>使用 StepVerifier 测试响应式 API</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("JwtTokenController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtTokenControllerTest {

    @Mock
    private JwtTokenRefreshService tokenRefreshService;

    @Mock
    private JwtTokenValidator jwtTokenValidator;

    @Mock
    private AccountManager accountManager;

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private JwtPersistenceService jwtPersistenceService;

    @InjectMocks
    private JwtTokenController controller;

    private LoginRequest testLoginRequest;
    private TokenRefreshRequest testRefreshRequest;
    private TokenValidationRequest testValidationRequest;

    @BeforeEach
    void setUp() {
        testLoginRequest = new LoginRequest();
        testLoginRequest.setUsername("testuser");
        testLoginRequest.setPassword("password123");

        testRefreshRequest = new TokenRefreshRequest();
        testRefreshRequest.setToken("test.jwt.token");

        testValidationRequest = new TokenValidationRequest();
        testValidationRequest.setToken("test.jwt.token");

        // 设置 SecurityProperties mock
        JwtConfig jwtConfig = mock(JwtConfig.class);
        lenient().when(jwtConfig.isEnabled()).thenReturn(true);
        lenient().when(jwtConfig.getExpirationMinutes()).thenReturn(60L);
        lenient().when(jwtConfig.getAccounts()).thenReturn(java.util.List.of());
        lenient().when(securityProperties.getJwt()).thenReturn(jwtConfig);
        
        // 不注入 jwtPersistenceService，让 Controller 进入 else 分支
    }

    // ==================== 登录测试 ====================

    @Nested
    @DisplayName("POST /api/auth/jwt/login - 用户登录测试")
    class LoginTests {

        @Test
        @DisplayName("JWT-001: 成功登录")
        void testLogin_success() {
            // Given
            lenient().when(accountManager.authenticateAndGenerateToken(anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Mono.just("generated.jwt.token"));
            lenient().when(tokenRefreshService.saveTokenMetadata(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            // When
            Mono<RouterResponse<LoginResponse>> result = controller.login(testLoginRequest, null);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> {
                        // 登录成功后，RouterResponse 应该是成功的
                        return response.isSuccess();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("JWT-002: 登录失败")
        void testLogin_failed() {
            // Given
            lenient().when(accountManager.authenticateAndGenerateToken(anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Invalid credentials")));

            // When
            Mono<RouterResponse<LoginResponse>> result = controller.login(testLoginRequest, null);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> !response.isSuccess())
                    .verifyComplete();
        }
    }

    // ==================== 令牌刷新测试 ====================

    @Nested
    @DisplayName("POST /api/auth/jwt/refresh - 令牌刷新测试")
    class RefreshTokenTests {

        @Test
        @DisplayName("JWT-003: 成功刷新令牌")
        void testRefreshToken_success() {
            // Given
            lenient().when(tokenRefreshService.refreshToken(anyString(), any(), any()))
                    .thenReturn(Mono.just("new.jwt.token"));
            lenient().when(tokenRefreshService.saveTokenOnRefreshWithContext(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            // When
            Mono<RouterResponse<JwtTokenInfo>> result = controller.refreshToken(testRefreshRequest, null, null);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess())
                    .verifyComplete();
        }

        @Test
        @DisplayName("JWT-004: 刷新失败")
        void testRefreshToken_failed() {
            // Given
            lenient().when(tokenRefreshService.refreshToken(anyString(), any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Token expired")));

            // When
            Mono<RouterResponse<JwtTokenInfo>> result = controller.refreshToken(testRefreshRequest, null, null);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> !response.isSuccess())
                    .verifyComplete();
        }
    }

    // ==================== 令牌验证测试 ====================

    @Nested
    @DisplayName("POST /api/auth/jwt/validate - 令牌验证测试")
    class ValidateTokenTests {

        @Test
        @DisplayName("JWT-005: 令牌有效")
        void testValidateToken_valid() {
            // Given
            lenient().when(tokenRefreshService.isTokenValid(anyString())).thenReturn(Mono.just(true));
            lenient().when(jwtPersistenceService.findByTokenHash(anyString())).thenReturn(Mono.empty());
            lenient().when(jwtTokenValidator.extractUserId(anyString())).thenReturn(Mono.just("testuser"));

            // When
            Mono<RouterResponse<TokenValidationResponse>> result = controller.validateToken(testValidationRequest);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess()
                            && response.getData().isValid())
                    .verifyComplete();
        }

        @Test
        @DisplayName("JWT-006: 令牌无效")
        void testValidateToken_invalid() {
            // Given
            lenient().when(tokenRefreshService.isTokenValid(anyString())).thenReturn(Mono.just(false));

            // When
            Mono<RouterResponse<TokenValidationResponse>> result = controller.validateToken(testValidationRequest);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess()
                            && !response.getData().isValid())
                    .verifyComplete();
        }
    }

    // ==================== 黑名单统计测试 ====================

    @Nested
    @DisplayName("GET /api/auth/jwt/blacklist/stats - 黑名单统计测试")
    class GetBlacklistStatsTests {

        @Test
        @DisplayName("JWT-007: 成功获取黑名单统计")
        void testGetBlacklistStats_success() {
            // Given
            lenient().when(tokenRefreshService.getBlacklistStats())
                    .thenReturn(Mono.just(java.util.Map.of("blacklistedCount", 5L)));
            lenient().when(jwtPersistenceService.countActiveTokens()).thenReturn(Mono.just(100L));

            // When
            Mono<RouterResponse<java.util.Map<String, Object>>> result = controller.getBlacklistStats(null);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess())
                    .verifyComplete();
        }
    }
}
