package org.unreal.modelrouter.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JwtAuthController 单元测试
 * 测试认证相关功能（登录、刷新令牌）
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthControllerTest {

    @Mock
    private JwtTokenRefreshService tokenRefreshService;

    @Mock
    private AccountManager accountManager;

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private JwtPersistenceService jwtPersistenceService;

    @InjectMocks
    private JwtAuthController controller;

    private LoginRequest testLoginRequest;
    private TokenRefreshRequest testRefreshRequest;

    @BeforeEach
    void setUp() {
        testLoginRequest = new LoginRequest();
        testLoginRequest.setUsername("testuser");
        testLoginRequest.setPassword("password");

        testRefreshRequest = new TokenRefreshRequest();
        testRefreshRequest.setToken("test-token");

        JwtConfig jwtConfig = mock(JwtConfig.class);
        lenient().when(jwtConfig.getExpirationMinutes()).thenReturn(60L);
        lenient().when(securityProperties.getJwt()).thenReturn(jwtConfig);
    }

    // ==================== 登录测试 ====================

    @Nested
    @DisplayName("POST /api/auth/jwt/login - 用户登录测试")
    class LoginTests {

        @Test
        @DisplayName("登录成功")
        void testLogin_success() {
            // Given
            lenient().when(accountManager.authenticateAndGenerateToken(anyString(), anyString(), any(), any(), any()))
                    .thenReturn(Mono.just("test-jwt-token"));

            // When
            Mono<RouterResponse<LoginResponse>> result = controller.login(testLoginRequest, null);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess()
                            && response.getData().getToken().equals("test-jwt-token"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("登录失败")
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

    // ==================== 刷新令牌测试 ====================

    @Nested
    @DisplayName("POST /api/auth/jwt/refresh - 令牌刷新测试")
    class RefreshTokenTests {

        @Test
        @DisplayName("刷新成功")
        void testRefreshToken_success() {
            // Given
            lenient().when(tokenRefreshService.refreshToken(anyString(), any(), any()))
                    .thenReturn(Mono.just("new-jwt-token"));

            // When
            Mono<RouterResponse<JwtTokenInfo>> result = controller.refreshToken(testRefreshRequest, null, null);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.isSuccess()
                            && response.getData().getToken().equals("new-jwt-token"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("刷新失败")
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
}
