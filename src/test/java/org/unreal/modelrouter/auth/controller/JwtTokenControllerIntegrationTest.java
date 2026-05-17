package org.unreal.modelrouter.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.unreal.modelrouter.auth.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.service.*;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtTokenController RESTful 接口测试
 * 
 * 测试范围：
 * - POST /api/auth/jwt/login - 登录
 * - POST /api/auth/jwt/refresh - 刷新令牌
 * - POST /api/auth/jwt/validate - 验证令牌
 * - POST /api/auth/jwt/revoke - 撤销令牌
 * - GET /api/auth/jwt/blacklist/stats - 黑名单统计
 * 
 * v2.7.6: 使用 Mockito 单元测试方式
 */
@DisplayName("JwtTokenController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
class JwtTokenControllerIntegrationTest {

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

    @Mock
    private JwtCleanupService jwtCleanupService;

    @Mock
    private JwtBlacklistService jwtBlacklistService;

    @Mock
    private JwtTokenManagementService jwtTokenManagementService;

    @Mock
    private JwtTokenQueryService jwtTokenQueryService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private JwtTokenController controller;

    private static final String VALID_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test";
    private static final String VALID_USERNAME = "admin";
    private static final String VALID_PASSWORD = "admin123";

    @BeforeEach
    void setUp() {
        // 配置 SecurityProperties（使用 lenient 避免不必要的 stubbing 警告）
        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setEnabled(true);
        jwtConfig.setExpirationMinutes(60);
        lenient().when(securityProperties.getJwt()).thenReturn(jwtConfig);
    }

    // ==================== 登录测试 ====================

    @Nested
    @DisplayName("POST /api/auth/jwt/login - 登录测试")
    class LoginTests {

        @Test
        @DisplayName("JWT-001: 登录成功 - 返回有效Token")
        void testLogin_success() {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername(VALID_USERNAME);
            request.setPassword(VALID_PASSWORD);

            when(accountManager.authenticateAndGenerateToken(
                    anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Mono.just(VALID_TOKEN));

            // When
            Mono<RouterResponse<LoginResponse>> result = controller.login(request, null);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(VALID_TOKEN, response.getData().getToken());
                        assertEquals("Bearer", response.getData().getTokenType());
                        assertEquals("登录成功", response.getMessage());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("JWT-002: 登录失败 - 用户名错误")
        void testLogin_wrongUsername() {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("wronguser");
            request.setPassword(VALID_PASSWORD);

            when(accountManager.authenticateAndGenerateToken(
                    anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Invalid credentials")));

            // When
            Mono<RouterResponse<LoginResponse>> result = controller.login(request, null);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertEquals("LOGIN_FAILED", response.getErrorCode());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("JWT-003: 登录失败 - 密码错误")
        void testLogin_wrongPassword() {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername(VALID_USERNAME);
            request.setPassword("wrongpassword");

            when(accountManager.authenticateAndGenerateToken(
                    anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Invalid credentials")));

            // When
            Mono<RouterResponse<LoginResponse>> result = controller.login(request, null);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertEquals("LOGIN_FAILED", response.getErrorCode());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 令牌刷新测试 ====================

    @Nested
    @DisplayName("POST /api/auth/jwt/refresh - 令牌刷新测试")
    class RefreshTokenTests {

        @Test
        @DisplayName("JWT-008: 刷新成功 - 返回新Token")
        void testRefresh_success() {
            // Given
            TokenRefreshRequest request = new TokenRefreshRequest();
            request.setToken(VALID_TOKEN);

            String newToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.newtoken";

            when(tokenRefreshService.refreshToken(anyString(), any(), any()))
                    .thenReturn(Mono.just(newToken));

            when(authentication.getName()).thenReturn("admin");

            // When
            Mono<RouterResponse<JwtTokenInfo>> result = controller.refreshToken(request, authentication, null);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(newToken, response.getData().getToken());
                        assertEquals("令牌刷新成功", response.getMessage());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("JWT-007: 刷新失败 - Token过期")
        void testRefresh_expiredToken() {
            // Given
            TokenRefreshRequest request = new TokenRefreshRequest();
            request.setToken("expired.token.here");

            when(tokenRefreshService.refreshToken(anyString(), any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Token expired")));

            when(authentication.getName()).thenReturn("admin");

            // When
            Mono<RouterResponse<JwtTokenInfo>> result = controller.refreshToken(request, authentication, null);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertEquals("TOKEN_REFRESH_FAILED", response.getErrorCode());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("JWT-006: 刷新失败 - Token无效")
        void testRefresh_invalidToken() {
            // Given
            TokenRefreshRequest request = new TokenRefreshRequest();
            request.setToken("invalid-token");

            when(tokenRefreshService.refreshToken(anyString(), any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Invalid token")));

            when(authentication.getName()).thenReturn("admin");

            // When
            Mono<RouterResponse<JwtTokenInfo>> result = controller.refreshToken(request, authentication, null);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertEquals("TOKEN_REFRESH_FAILED", response.getErrorCode());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 令牌验证测试 ====================

    @Nested
    @DisplayName("POST /api/auth/jwt/validate - 令牌验证测试")
    class ValidateTokenTests {

        @Test
        @DisplayName("JWT-005: 验证成功 - Token有效")
        void testValidate_validToken() {
            // Given
            TokenValidationRequest request = new TokenValidationRequest();
            request.setToken(VALID_TOKEN);

            when(tokenRefreshService.isTokenValid(anyString()))
                    .thenReturn(Mono.just(true));
            when(jwtTokenValidator.extractUserId(anyString()))
                    .thenReturn(Mono.just("admin"));

            // When
            Mono<RouterResponse<TokenValidationResponse>> result = controller.validateToken(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertTrue(response.getData().isValid());
                        assertEquals("admin", response.getData().getUserId());
                        assertEquals("令牌有效", response.getData().getMessage());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("JWT-006: 验证失败 - Token无效")
        void testValidate_invalidToken() {
            // Given
            TokenValidationRequest request = new TokenValidationRequest();
            request.setToken("invalid-token");

            when(tokenRefreshService.isTokenValid(anyString()))
                    .thenReturn(Mono.just(false));

            // When
            Mono<RouterResponse<TokenValidationResponse>> result = controller.validateToken(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertFalse(response.getData().isValid());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("JWT-007: 验证失败 - Token过期")
        void testValidate_expiredToken() {
            // Given
            TokenValidationRequest request = new TokenValidationRequest();
            request.setToken("expired-token");

            when(tokenRefreshService.isTokenValid(anyString()))
                    .thenReturn(Mono.just(false));

            // When
            Mono<RouterResponse<TokenValidationResponse>> result = controller.validateToken(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertFalse(response.getData().isValid());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 黑名单统计测试 ====================

    @Nested
    @DisplayName("GET /api/auth/jwt/blacklist/stats - 黑名单统计测试")
    class BlacklistStatsTests {

        @Test
        @DisplayName("获取黑名单统计成功")
        void testGetBlacklistStats_success() {
            // Given
            Map<String, Object> stats = Map.of(
                    "totalBlacklisted", 10L,
                    "totalRevoked", 5L
            );

            when(tokenRefreshService.getBlacklistStats())
                    .thenReturn(Mono.just(stats));

            when(authentication.getName()).thenReturn("admin");

            // When
            Mono<RouterResponse<Map<String, Object>>> result = controller.getBlacklistStats(authentication);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertNotNull(response.getData());
                        assertEquals(10L, response.getData().get("totalBlacklisted"));
                    })
                    .verifyComplete();
        }
    }
}
