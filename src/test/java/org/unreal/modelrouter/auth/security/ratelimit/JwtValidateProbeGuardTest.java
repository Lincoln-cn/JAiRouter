package org.unreal.modelrouter.auth.security.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.dto.AuditEventType;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code POST /api/auth/jwt/validate} 的探测熔断守卫（issue #1248）。
 *
 * <p>行为约定：按来源 IP 计失败次数；越过阈值后在窗口期内直接拒绝；
 * 首次越阈值写一条 {@code BRUTE_FORCE_ATTEMPT} 审计（同一窗口只写一次，避免刷爆台账）；
 * 窗口过期后计数重置。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JWT 验证接口的探测熔断（issue #1248）")
class JwtValidateProbeGuardTest {

    private static final String IP = "203.0.113.7";

    @Mock
    private SecurityAuditService securityAuditService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private JwtValidateProbeGuard guard;

    @BeforeEach
    void setUp() throws Exception {
        guard = new JwtValidateProbeGuard();
        when(securityAuditService.recordEvent(any())).thenReturn(Mono.empty());
        when(meterRegistry.counter(anyString())).thenReturn(counter);
        setField(guard, "securityAuditService", securityAuditService);
        setField(guard, "meterRegistry", meterRegistry);
        setField(guard, "maxFailuresPerIp", 3);
        setField(guard, "windowMinutes", 5);
    }

    private static void setField(final Object target, final String name, final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("阈值以内不熔断，达到阈值才拒绝")
    void blocksOnlyAfterThreshold() {
        assertFalse(guard.isBlocked(IP), "还没失败过就不该拦");

        guard.recordFailure(IP, "invalid");
        guard.recordFailure(IP, "invalid");
        assertFalse(guard.isBlocked(IP), "低于阈值不该拦");

        guard.recordFailure(IP, "invalid");
        assertEquals(3, guard.failureCount(IP));
        assertTrue(guard.isBlocked(IP), "达到阈值必须拒绝");
    }

    @Test
    @DisplayName("来源 IP 之间互不影响")
    void countsArePerIp() {
        guard.recordFailure(IP, "invalid");
        guard.recordFailure(IP, "invalid");
        guard.recordFailure(IP, "invalid");
        assertTrue(guard.isBlocked(IP));
        assertFalse(guard.isBlocked("198.51.100.9"), "别的来源不该被连坐");
    }

    @Test
    @DisplayName("取不到 IP 时不设限（避免把缺 IP 变成拒绝服务）")
    void missingIpNeverBlocks() {
        assertFalse(guard.isBlocked(null));
        assertFalse(guard.isBlocked(""));
        guard.recordFailure(null, "invalid");
        guard.recordFailure("  ", "invalid");
        assertEquals(0, guard.failureCount(null));
    }

    @Test
    @DisplayName("首次越阈值写一次 BRUTE_FORCE_ATTEMPT 审计，同一窗口不重复写")
    void auditsOncePerWindow() {
        for (int i = 0; i < 3; i++) {
            guard.recordFailure(IP, "invalid");
        }
        final ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(securityAuditService, times(1)).recordEvent(captor.capture());
        final SecurityAuditEvent event = captor.getValue();
        assertEquals(AuditEventType.BRUTE_FORCE_ATTEMPT.name(), event.getEventType());
        assertEquals("anonymous", event.getUserId());
        assertEquals("jwt", event.getResource());
        assertEquals("VALIDATE", event.getAction());
        assertFalse(event.isSuccess());
        assertEquals(IP, event.getClientIp());
        final Map<?, ?> extra = (Map<?, ?>) event.getAdditionalData();
        assertEquals(IP, extra.get("ip"));
        assertEquals(3, extra.get("failures"));

        // 继续失败也不该再写第二条（否则攻击者可以刷爆审计表）
        guard.recordFailure(IP, "invalid");
        guard.recordFailure(IP, "invalid");
        verify(securityAuditService, times(1)).recordEvent(any());
        verify(counter, times(1)).increment();
    }

    @Test
    @DisplayName("窗口过期后计数重置，不再熔断")
    void windowExpiryResetsCounter() throws Exception {
        setField(guard, "windowMinutes", 0);
        guard.recordFailure(IP, "invalid");
        guard.recordFailure(IP, "invalid");
        guard.recordFailure(IP, "invalid");
        // window-minutes=0 ⇒ 立刻过期，每次 recordFailure 都会重置窗口
        assertFalse(guard.isBlocked(IP), "窗口已过期的计数不该继续拦人");
        assertEquals(1, guard.failureCount(IP), "过期后从 1 重新计数");
    }

    @Test
    @DisplayName("没有审计服务与指标注册表时不抛异常")
    void worksWithoutOptionalCollaborators() throws Exception {
        final JwtValidateProbeGuard bare = new JwtValidateProbeGuard();
        setField(bare, "maxFailuresPerIp", 1);
        setField(bare, "windowMinutes", 5);
        bare.recordFailure(IP, "invalid");
        assertTrue(bare.isBlocked(IP));
        verify(securityAuditService, never()).recordEvent(any());
    }

    @Test
    @DisplayName("reset 清空所有计数")
    void resetClearsBuckets() {
        guard.recordFailure(IP, "invalid");
        guard.reset();
        assertEquals(0, guard.failureCount(IP));
        assertFalse(guard.isBlocked(IP));
    }
}
