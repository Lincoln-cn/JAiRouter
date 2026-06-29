package org.unreal.modelrouter.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.service.*;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtAuthController 集成测试
 *
 * 测试范围：
 * - POST /api/auth/jwt/login - 登录
 * - POST /api/auth/jwt/refresh - 刷新令牌
 *
 * v2.7.4: 从 JwtTokenControllerIntegrationTest 拆分
 */
@DisplayName("JwtAuthController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
class JwtAuthControllerIntegrationTest {

    @Mock
    private JwtTokenRefreshService tokenRefreshService;

    @Mock
    private AccountManager accountManager;

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private JwtPersistenceService jwtPersistenceService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private JwtAuthController controller;

    private static final String VALID_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test";
    private static final String VALID_USERNAME = "admin";
    private static final String VALID_PASSWORD = "admin123";

    @BeforeEach
    void setUp() {
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
            LoginRequest request = new LoginRequest();
            request.setUsername(VALID_USERNAME);
            request.setPassword(VALID_PASSWORD);

            when(accountManager.authenticateAndGenerateToken(
                    anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Mono.just(VALID_TOKEN));

            Mono<RouterResponse<LoginResponse>> result = controller.login(request, null);

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
            LoginRequest request = new LoginRequest();
            request.setUsername("wronguser");
            request.setPassword(VALID_PASSWORD);

            when(accountManager.authenticateAndGenerateToken(
                    anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Invalid credentials")));

            Mono<RouterResponse<LoginResponse>> result = controller.login(request, null);

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
            LoginRequest request = new LoginRequest();
            request.setUsername(VALID_USERNAME);
            request.setPassword("wrongpassword");

            when(accountManager.authenticateAndGenerateToken(
                    anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Invalid credentials")));

            Mono<RouterResponse<LoginResponse>> result = controller.login(request, null);

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
            TokenRefreshRequest request = new TokenRefreshRequest();
            request.setToken(VALID_TOKEN);

            String newToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.newtoken";

            when(tokenRefreshService.refreshToken(anyString(), any(), any()))
                    .thenReturn(Mono.just(newToken));

            when(authentication.getName()).thenReturn("admin");

            Mono<RouterResponse<JwtTokenInfo>> result = controller.refreshToken(request, authentication, null);

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
            TokenRefreshRequest request = new TokenRefreshRequest();
            request.setToken("expired.token.here");

            when(tokenRefreshService.refreshToken(anyString(), any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Token expired")));

            when(authentication.getName()).thenReturn("admin");

            Mono<RouterResponse<JwtTokenInfo>> result = controller.refreshToken(request, authentication, null);

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
            TokenRefreshRequest request = new TokenRefreshRequest();
            request.setToken("invalid-token");

            when(tokenRefreshService.refreshToken(anyString(), any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Invalid token")));

            when(authentication.getName()).thenReturn("admin");

            Mono<RouterResponse<JwtTokenInfo>> result = controller.refreshToken(request, authentication, null);

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertEquals("TOKEN_REFRESH_FAILED", response.getErrorCode());
                    })
                    .verifyComplete();
        }
    }
}
