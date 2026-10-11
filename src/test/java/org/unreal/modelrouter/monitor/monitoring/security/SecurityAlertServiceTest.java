package org.unreal.modelrouter.monitor.monitoring.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.common.dto.AuditEventType;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #283 / #285：告警判据必须落在正确的枚举类型上，且在存储侧计数（不受取回条数限制）。
 * #292：出口 = 指标 + 日志（不再发布无人订阅的仓内事件），因此断言落在指标上。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityAlertService：告警判据取数与出口")
class SecurityAlertServiceTest {

    private static final String CLIENT_IP = "10.0.0.9";

    @Mock
    private SecurityAuditService auditService;

    @Mock
    private SecurityMetrics securityMetrics;

    private SecurityAlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new SecurityAlertService(auditService, securityMetrics);
    }

    @Test
    @DisplayName("认证失败告警按 AUTHENTICATION_FAILED 取数（不是枚举里不存在的 AUTHENTICATION_FAILURE）")
    void authenticationFailureAlert_countsFailedAuthenticationEvents() {
        when(auditService.shouldTriggerAlert(AuditEventType.AUTHENTICATION_FAILED.name(), 5, 5))
                .thenReturn(Mono.just(true));

        alertService.checkAuthenticationFailureAlert(CLIENT_IP, "bad credentials").block();

        verify(auditService).shouldTriggerAlert("AUTHENTICATION_FAILED", 5, 5);
        verify(securityMetrics).recordSecurityViolation("AUTH_FAILURE_SPIKE", "HIGH");
    }

    @Test
    @DisplayName("可疑 IP 告警把 IP 条件交给存储侧计数")
    void suspiciousIpAlert_countsByIpInStorage() {
        when(auditService.countEvents(any(LocalDateTime.class), any(LocalDateTime.class),
                anyList(), eq(CLIENT_IP), any())).thenReturn(Mono.just(10L));

        alertService.checkSuspiciousIpAlert(CLIENT_IP).block();

        assertEquals(List.of(AuditEventType.AUTHENTICATION_FAILED), capturedTypes());
        verify(securityMetrics).recordSecurityViolation("SUSPICIOUS_IP_ACTIVITY", "HIGH");
    }

    @Test
    @DisplayName("可疑 IP 低于阈值时不告警")
    void suspiciousIpAlert_belowThreshold_doesNotAlert() {
        when(auditService.countEvents(any(LocalDateTime.class), any(LocalDateTime.class),
                anyList(), eq(CLIENT_IP), any())).thenReturn(Mono.just(9L));

        alertService.checkSuspiciousIpAlert(CLIENT_IP).block();

        verify(securityMetrics, never()).recordSecurityViolation(anyString(), anyString());
    }

    @Test
    @DisplayName("脱敏异常按 DATA_SANITIZATION 取数（该类型此前在枚举里不存在，判据恒为 0）")
    void sanitizationAnomalyAlert_countsSanitizationEvents() {
        when(auditService.countEvents(any(LocalDateTime.class), any(LocalDateTime.class),
                anyList(), any(), any())).thenReturn(Mono.just(100L));

        alertService.checkSanitizationAnomalyAlert().block();

        assertEquals(List.of(AuditEventType.DATA_SANITIZATION), capturedTypes());
        verify(securityMetrics).recordSecurityViolation("SANITIZATION_ANOMALY", "MEDIUM");
    }

    @Test
    @DisplayName("JWT 异常按 JWT 类型 + 失败条件取数（不再取回 100 条任意事件后在内存里筛）")
    void jwtAnomalyAlert_countsJwtFailures() {
        when(auditService.countEvents(any(LocalDateTime.class), any(LocalDateTime.class),
                anyList(), any(), any())).thenReturn(Mono.just(20L));

        alertService.checkJwtAnomalyAlert().block();

        final List<AuditEventType> types = capturedTypes();
        assertTrue(types.contains(AuditEventType.JWT_TOKEN_VALIDATED), "应包含 JWT 类型");
        assertTrue(types.contains(AuditEventType.JWT_TOKEN_EXPIRED), "应包含 JWT 类型");
        assertFalse(types.contains(AuditEventType.SYSTEM_MAINTENANCE), "不得落在回退类型上");
        verify(securityMetrics).recordSecurityViolation("JWT_ANOMALY", "MEDIUM");
    }

    @Test
    @DisplayName("冷却期内的重复触发被抑制（同类型 15 分钟内只记一次）")
    void repeatedAlert_withinCooldown_isSuppressed() {
        when(auditService.countEvents(any(LocalDateTime.class), any(LocalDateTime.class),
                anyList(), any(), any())).thenReturn(Mono.just(100L));

        alertService.checkSanitizationAnomalyAlert().block();
        alertService.checkSanitizationAnomalyAlert().block();

        verify(securityMetrics, times(1)).recordSecurityViolation(anyString(), anyString());
    }

    @SuppressWarnings("unchecked")
    private List<AuditEventType> capturedTypes() {
        final ArgumentCaptor<List<AuditEventType>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditService).countEvents(any(LocalDateTime.class), any(LocalDateTime.class),
                captor.capture(), any(), any());
        return captor.getValue();
    }
}
