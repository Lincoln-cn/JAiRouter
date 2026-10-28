package org.unreal.modelrouter.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.ratelimit.LoginBruteForceGuard;
import org.unreal.modelrouter.auth.security.service.AccountManager;
import org.unreal.modelrouter.auth.security.service.JwtTokenRefreshService;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.dto.LoginRequest;
import org.unreal.modelrouter.common.exception.ApiException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录端点与失败频控的接线（issue #697）。
 *
 * <p>这里用**真实的** {@link LoginBruteForceGuard}（只把审计服务换成 mock），断言的是
 * 「{@code /api/auth/jwt/login} 确实走了频控」而不是「守卫类本身算得对」——
 * 后者由 {@code LoginBruteForceGuardTest} 覆盖。所以把 {@code JwtAuthController} 的改动回退后，
 * 本类必红。</p>
 */
@DisplayName("登录端点接入失败频控与暴力破解审计（issue #697）")
class JwtAuthLoginBruteForceTest {

    private static final String USERNAME = "alice";
    private static final String PASSWORD = "wrong-password";

    private JwtTokenRefreshService tokenRefreshService;
    private AccountManager accountManager;
    private SecurityProperties securityProperties;
    private ExtendedSecurityAuditService auditService;
    private LoginBruteForceGuard guard;
    private JwtAuthController controller;
    private LoginRequest request;

    @BeforeEach
    void setUp() {
        tokenRefreshService = mock(JwtTokenRefreshService.class);
        accountManager = mock(AccountManager.class);
        securityProperties = mock(SecurityProperties.class);
        auditService = mock(ExtendedSecurityAuditService.class);
        lenient().when(auditService.auditSecurityEvent(anyString(), anyString(), any(), any()))
                .thenReturn(Mono.empty());

        guard = new LoginBruteForceGuard(auditService);
        ReflectionTestUtils.setField(guard, "maxFailuresPerPrincipal", 3);
        ReflectionTestUtils.setField(guard, "maxFailuresPerIp", 100);

        controller = new JwtAuthController(tokenRefreshService, accountManager, securityProperties, guard);

        final JwtConfig jwtConfig = mock(JwtConfig.class);
        lenient().when(jwtConfig.getExpirationMinutes()).thenReturn(60L);
        lenient().when(securityProperties.getJwt()).thenReturn(jwtConfig);

        request = new LoginRequest();
        request.setUsername(USERNAME);
        request.setPassword(PASSWORD);
    }

    private void stubFailedAuthentication() {
        lenient().when(accountManager.authenticateAndGenerateToken(anyString(), anyString(), any(), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("Bad credentials")));
    }

    @Test
    @DisplayName("连续失败达到阈值：登录链发出 BRUTE_FORCE_ATTEMPT 审计事件")
    void repeatedFailuresEmitBruteForceAudit() {
        stubFailedAuthentication();

        for (int i = 0; i < 3; i++) {
            StepVerifier.create(controller.login(request, null))
                    .expectErrorSatisfies(ex -> assertEquals("LOGIN_FAILED",
                            ((ApiException) ex).getErrorCode()))
                    .verify();
        }

        final ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService, times(1)).auditSecurityEvent(
                typeCaptor.capture(), anyString(), eq(USERNAME), eq("unknown"));
        assertEquals(AuditEventType.BRUTE_FORCE_ATTEMPT, AuditEventType.valueOf(typeCaptor.getValue()));
    }

    @Test
    @DisplayName("锁定后第 4 次请求直接 429，且不再校验口令")
    void lockedLoginIsRejectedWith429() {
        stubFailedAuthentication();
        for (int i = 0; i < 3; i++) {
            StepVerifier.create(controller.login(request, null)).expectError().verify();
        }

        StepVerifier.create(controller.login(request, null))
                .expectErrorSatisfies(ex -> {
                    final ApiException apiEx = (ApiException) ex;
                    assertEquals("RATE_LIMITED_LOGIN", apiEx.getErrorCode());
                    assertEquals(HttpStatus.TOO_MANY_REQUESTS, apiEx.getStatus());
                })
                .verify();

        verify(accountManager, times(3))
                .authenticateAndGenerateToken(anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("登录成功清零计数：成功前后的失败不累计到阈值")
    void successfulLoginResetsCounter() {
        stubFailedAuthentication();
        for (int i = 0; i < 2; i++) {
            StepVerifier.create(controller.login(request, null)).expectError().verify();
        }

        when(accountManager.authenticateAndGenerateToken(anyString(), anyString(), any(), any(), any()))
                .thenReturn(Mono.just("token-ok"));
        StepVerifier.create(controller.login(request, null))
                .assertNext(response -> assertTrue(response.isSuccess()))
                .verifyComplete();

        stubFailedAuthentication();
        for (int i = 0; i < 2; i++) {
            StepVerifier.create(controller.login(request, null)).expectError().verify();
        }

        verify(auditService, never()).auditSecurityEvent(anyString(), anyString(), any(), any());
    }
}
