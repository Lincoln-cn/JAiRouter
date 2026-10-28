package org.unreal.modelrouter.auth.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.exception.ApiException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
 * 登录失败频控的计数语义（issue #697）。
 *
 * <p>阈值在 {@code setUp} 里用 {@link ReflectionTestUtils} 压到 3，避免用例依赖生产默认值 ——
 * 但默认值本身由 {@code LoginBruteForceGuard} 的字段声明守住（{@code @Value} 缺省值）。</p>
 */
@DisplayName("登录失败频控与暴力破解审计（issue #697）")
class LoginBruteForceGuardTest {

    private static final String IP = "203.0.113.7";
    private static final String USERNAME = "alice";

    private ExtendedSecurityAuditService auditService;
    private LoginBruteForceGuard guard;

    @BeforeEach
    void setUp() {
        auditService = mock(ExtendedSecurityAuditService.class);
        lenient().when(auditService.auditSecurityEvent(anyString(), anyString(), any(), any()))
                .thenReturn(Mono.empty());
        guard = new LoginBruteForceGuard(auditService);
        ReflectionTestUtils.setField(guard, "maxFailuresPerPrincipal", 3);
        ReflectionTestUtils.setField(guard, "maxFailuresPerIp", 100);
        ReflectionTestUtils.setField(guard, "windowMinutes", 15);
        ReflectionTestUtils.setField(guard, "lockMinutes", 15);
    }

    @Test
    @DisplayName("未达阈值：请求照常放行，且不产生审计事件")
    void failuresBelowThresholdDoNotLock() {
        guard.recordFailure(USERNAME, IP);
        guard.recordFailure(USERNAME, IP);

        StepVerifier.create(guard.checkNotLocked(USERNAME, IP)).verifyComplete();
        verify(auditService, never()).auditSecurityEvent(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("达到阈值：发一次 BRUTE_FORCE_ATTEMPT 审计事件，此后的请求返回 429")
    void thresholdCrossingEmitsAuditAndLocks() {
        guard.recordFailure(USERNAME, IP);
        guard.recordFailure(USERNAME, IP);
        guard.recordFailure(USERNAME, IP);

        final ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService, times(1)).auditSecurityEvent(
                typeCaptor.capture(), detailsCaptor.capture(), eq(USERNAME), eq(IP));

        assertEquals(AuditEventType.BRUTE_FORCE_ATTEMPT, AuditEventType.valueOf(typeCaptor.getValue()),
                "审计事件类型必须是枚举名（#301 起 parseEventType 对未知名字抛错）");
        assertTrue(detailsCaptor.getValue().contains("threshold=3"), detailsCaptor.getValue());

        StepVerifier.create(guard.checkNotLocked(USERNAME, IP))
                .expectErrorSatisfies(ex -> {
                    assertInstanceOf(ApiException.class, ex);
                    final ApiException apiEx = (ApiException) ex;
                    assertEquals("RATE_LIMITED_LOGIN", apiEx.getErrorCode());
                    assertEquals(HttpStatus.TOO_MANY_REQUESTS, apiEx.getStatus());
                })
                .verify();
    }

    @Test
    @DisplayName("锁定期内不重复发审计事件（防刷屏）")
    void lockWindowDoesNotReEmit() {
        guard.recordFailure(USERNAME, IP);
        guard.recordFailure(USERNAME, IP);
        guard.recordFailure(USERNAME, IP);
        guard.recordFailure(USERNAME, IP);
        guard.recordFailure(USERNAME, IP);

        verify(auditService, times(1)).auditSecurityEvent(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("登录成功清零该账号的失败计数")
    void successResetsFailureCounter() {
        guard.recordFailure(USERNAME, IP);
        guard.recordFailure(USERNAME, IP);
        guard.recordSuccess(USERNAME, IP);
        guard.recordFailure(USERNAME, IP);
        guard.recordFailure(USERNAME, IP);

        StepVerifier.create(guard.checkNotLocked(USERNAME, IP)).verifyComplete();
        verify(auditService, never()).auditSecurityEvent(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("计数按 IP + 用户名分组：换 IP 或换用户名不受同账号锁定影响")
    void countersAreScopedByIpAndUsername() {
        guard.recordFailure(USERNAME, IP);
        guard.recordFailure(USERNAME, IP);
        guard.recordFailure(USERNAME, IP);

        StepVerifier.create(guard.checkNotLocked(USERNAME, "198.51.100.9")).verifyComplete();
        StepVerifier.create(guard.checkNotLocked("bob", IP)).verifyComplete();
        StepVerifier.create(guard.checkNotLocked(USERNAME, IP)).expectError().verify();
    }

    @Test
    @DisplayName("换用户名撞库：同一 IP 的累计失败达到 IP 阈值同样锁定并审计")
    void perIpThresholdCatchesUsernameRotation() {
        ReflectionTestUtils.setField(guard, "maxFailuresPerPrincipal", 100);
        ReflectionTestUtils.setField(guard, "maxFailuresPerIp", 3);

        guard.recordFailure("alice", IP);
        guard.recordFailure("bob", IP);
        guard.recordFailure("carol", IP);

        final ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService, times(1)).auditSecurityEvent(anyString(), detailsCaptor.capture(), any(), eq(IP));
        assertTrue(detailsCaptor.getValue().contains("scope=ip"), detailsCaptor.getValue());

        StepVerifier.create(guard.checkNotLocked("dave", IP)).expectError().verify();
    }

    @Test
    @DisplayName("窗口过期即清零：window-minutes=0 时失败累计不到阈值")
    void expiredWindowResetsFailures() {
        ReflectionTestUtils.setField(guard, "windowMinutes", 0);
        for (int i = 0; i < 5; i++) {
            guard.recordFailure(USERNAME, IP);
        }

        StepVerifier.create(guard.checkNotLocked(USERNAME, IP)).verifyComplete();
        verify(auditService, never()).auditSecurityEvent(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("锁定到期自动解除：lock-minutes=0 时阈值后的下一次请求放行")
    void expiredLockReleasesImmediately() {
        ReflectionTestUtils.setField(guard, "lockMinutes", 0);
        guard.recordFailure(USERNAME, IP);
        guard.recordFailure(USERNAME, IP);
        guard.recordFailure(USERNAME, IP);

        StepVerifier.create(guard.checkNotLocked(USERNAME, IP)).verifyComplete();
    }
}
