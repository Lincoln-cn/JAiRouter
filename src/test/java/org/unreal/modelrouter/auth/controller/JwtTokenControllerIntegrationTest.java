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
import org.unreal.modelrouter.auth.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.auth.security.service.*;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtTokenController 集成测试
 *
 * 测试范围：
 * - POST /api/auth/jwt/validate - 验证令牌
 * - POST /api/auth/jwt/revoke - 撤销令牌
 * - GET /api/auth/jwt/blacklist/stats - 黑名单统计
 *
 * 登录和刷新令牌测试请参见 {@link JwtAuthControllerIntegrationTest}
 *
 * v2.7.4: 从原 JwtTokenControllerIntegrationTest 拆分
 */
@DisplayName("JwtTokenController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
class JwtTokenControllerIntegrationTest {

    @Mock
    private JwtTokenRefreshService tokenRefreshService;

    @Mock
    private JwtTokenValidator jwtTokenValidator;

    @Mock
    private JwtPersistenceService jwtPersistenceService;

    @Mock
    private JwtTokenQueryService jwtTokenQueryService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private JwtTokenController controller;

    private static final String VALID_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test";

    @BeforeEach
    void setUp() {
        // 默认配置
    }

    // ==================== 令牌验证测试 ====================

    @Nested
    @DisplayName("POST /api/auth/jwt/validate - 令牌验证测试")
    class ValidateTokenTests {

        @Test
        @DisplayName("JWT-005: 验证成功 - Token有效")
        void testValidate_validToken() {
            TokenValidationRequest request = new TokenValidationRequest();
            request.setToken(VALID_TOKEN);

            when(tokenRefreshService.isTokenValid(anyString()))
                    .thenReturn(Mono.just(true));
            when(jwtTokenValidator.extractUserId(anyString()))
                    .thenReturn(Mono.just("admin"));

            Mono<RouterResponse<TokenValidationResponse>> result = controller.validateToken(request);

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
            TokenValidationRequest request = new TokenValidationRequest();
            request.setToken("invalid-token");

            when(tokenRefreshService.isTokenValid(anyString()))
                    .thenReturn(Mono.just(false));

            Mono<RouterResponse<TokenValidationResponse>> result = controller.validateToken(request);

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
            TokenValidationRequest request = new TokenValidationRequest();
            request.setToken("expired-token");

            when(tokenRefreshService.isTokenValid(anyString()))
                    .thenReturn(Mono.just(false));

            Mono<RouterResponse<TokenValidationResponse>> result = controller.validateToken(request);

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
            Map<String, Object> stats = Map.of(
                    "totalBlacklisted", 10L,
                    "totalRevoked", 5L
            );

            when(tokenRefreshService.getBlacklistStats())
                    .thenReturn(Mono.just(stats));

            when(authentication.getName()).thenReturn("admin");

            Mono<RouterResponse<Map<String, Object>>> result = controller.getBlacklistStats(authentication);

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
