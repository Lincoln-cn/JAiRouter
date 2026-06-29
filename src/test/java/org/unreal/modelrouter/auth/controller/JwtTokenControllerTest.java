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
import org.unreal.modelrouter.auth.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.auth.security.service.JwtPersistenceService;
import org.unreal.modelrouter.auth.security.service.JwtTokenRefreshService;
import org.unreal.modelrouter.auth.security.service.JwtTokenQueryService;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
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
 * <p>测试令牌管理功能（验证、黑名单统计等）</p>
 * <p>登录和刷新令牌测试请参见 {@link JwtAuthControllerTest}</p>
 *
 * @version v2.7.4
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
    private JwtPersistenceService jwtPersistenceService;

    @Mock
    private JwtTokenQueryService jwtTokenQueryService;

    @InjectMocks
    private JwtTokenController controller;

    private TokenValidationRequest testValidationRequest;

    @BeforeEach
    void setUp() {
        testValidationRequest = new TokenValidationRequest();
        testValidationRequest.setToken("test.jwt.token");
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
