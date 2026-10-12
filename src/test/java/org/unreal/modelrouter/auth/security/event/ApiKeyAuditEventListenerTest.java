package org.unreal.modelrouter.auth.security.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.monitor.monitoring.security.SecurityAlertService;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ApiKeyAuditEventListener 的安全告警接线测试（issue #276）.
 *
 * <p>背景：{@link SecurityAlertService} 的检查实现完整却<b>没有任何调用方</b>，导致四类安全告警
 * 实际都不会产生。本测试锁定新接线：认证失败/安全事件必须驱动「失败次数陡增」与「可疑 IP」两项检查。</p>
 *
 * <p>用例直接调用监听方法（绕过 {@code @Async} 代理），验证的是接线本身。</p>
 *
 * <p>另含 issue #301 的动态事件类型降级用例：本监听器从消息前缀里抠类型名，是唯一一处
 * 「任意字符串 → 审计事件类型」的入口。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ApiKeyAuditEventListener 安全告警接线（issue #276）")
class ApiKeyAuditEventListenerTest {

    @Mock
    private SecurityAlertService securityAlertService;

    @Mock
    private ExtendedSecurityAuditService extendedAuditService;

    private ApiKeyAuditEventListener listener;

    @BeforeEach
    void setUp() throws Exception {
        listener = new ApiKeyAuditEventListener();
        setField(listener, "securityAlertService", securityAlertService);
        setField(listener, "extendedAuditService", extendedAuditService);
        when(securityAlertService.checkAuthenticationFailureAlert(anyString(), anyString()))
                .thenReturn(Mono.empty());
        when(securityAlertService.checkSuspiciousIpAlert(anyString()))
                .thenReturn(Mono.empty());
        when(extendedAuditService.auditSecurityEvent(anyString(), any(), any(), any()))
                .thenReturn(Mono.empty());
        // 装配了审计服务后，「使用」路径也会真的写审计，需一并桩掉
        when(extendedAuditService.auditApiKeyUsed(any(), any(), any(), anyBoolean()))
                .thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("安全事件（无效密钥）驱动认证失败与可疑 IP 两项告警检查")
    void securityEventTriggersAlerts() {
        listener.onSecurityEvent(ApiKeyAuditEvent.securityEvent(
                "API_KEY_INVALID", "无效的API Key", null, "10.0.0.1"));

        verify(securityAlertService).checkAuthenticationFailureAlert(
                eq("10.0.0.1"), eq("[API_KEY_INVALID] 无效的API Key"));
        verify(securityAlertService).checkSuspiciousIpAlert(eq("10.0.0.1"));
    }

    @Test
    @DisplayName("使用失败事件同样驱动两项告警检查")
    void failedUsageTriggersAlerts() {
        listener.onApiKeyUsed(ApiKeyAuditEvent.used(
                "key-1", "/v1/chat/completions", "10.0.0.2", false));

        verify(securityAlertService).checkAuthenticationFailureAlert(eq("10.0.0.2"), anyString());
        verify(securityAlertService).checkSuspiciousIpAlert(eq("10.0.0.2"));
    }

    @Test
    @DisplayName("使用成功事件不触发告警检查")
    void successfulUsageDoesNotTriggerAlerts() {
        listener.onApiKeyUsed(ApiKeyAuditEvent.used(
                "key-1", "/v1/chat/completions", "10.0.0.3", true));

        verify(securityAlertService, never()).checkAuthenticationFailureAlert(anyString(), anyString());
        verify(securityAlertService, never()).checkSuspiciousIpAlert(anyString());
    }

    @Test
    @DisplayName("IP 为空时跳过告警检查且不抛异常")
    void nullIpSkipsAlerts() {
        listener.onSecurityEvent(ApiKeyAuditEvent.securityEvent(
                "API_KEY_MISSING", "请求缺少认证信息", null, null));

        verify(securityAlertService, never()).checkAuthenticationFailureAlert(anyString(), anyString());
        verify(securityAlertService, never()).checkSuspiciousIpAlert(anyString());
    }

    @Test
    @DisplayName("告警服务未装配时事件处理不受影响（不抛异常）")
    void missingAlertServiceIsTolerated() throws Exception {
        setField(listener, "securityAlertService", null);

        listener.onSecurityEvent(ApiKeyAuditEvent.securityEvent(
                "API_KEY_INVALID", "无效的API Key", null, "10.0.0.4"));

        verify(securityAlertService, never()).checkAuthenticationFailureAlert(anyString(), anyString());
    }

    @Test
    @DisplayName("告警检查抛错时不影响事件处理")
    void alertFailureIsSwallowed() {
        when(securityAlertService.checkAuthenticationFailureAlert(anyString(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("alert backend down")));

        listener.onSecurityEvent(ApiKeyAuditEvent.securityEvent(
                "API_KEY_INVALID", "无效的API Key", null, "10.0.0.5"));

        verify(securityAlertService).checkSuspiciousIpAlert(eq("10.0.0.5"));
    }

    @Test
    @DisplayName("issue #301：已知的动态类型名原样落库，且仍是可解析的枚举名")
    void knownDynamicEventTypeIsPreserved() {
        listener.onSecurityEvent(ApiKeyAuditEvent.securityEvent(
                AuditEventType.API_KEY_INVALID.name(), "无效的API Key", null, "10.0.0.6"));

        assertEquals(AuditEventType.API_KEY_INVALID, AuditEventType.valueOf(captureAuditEventType()));
    }

    @Test
    @DisplayName("issue #301：未知前缀降级为 SUSPICIOUS_ACTIVITY，既不抛错也不丢事件")
    void unknownPrefixDegradesToSuspiciousActivity() {
        listener.onSecurityEvent(ApiKeyAuditEvent.securityEvent(
                "SOMETHING_WEIRD", "自定义安全事件", null, "10.0.0.7"));

        assertEquals(AuditEventType.SUSPICIOUS_ACTIVITY.name(), captureAuditEventType());
    }

    @Test
    @DisplayName("issue #301：消息无 [#] 前缀时同样降级而非抛错")
    void messageWithoutPrefixDegrades() {
        listener.onSecurityEvent(new ApiKeyAuditEvent(
                ApiKeyAuditEvent.EventType.SECURITY_EVENT, "key-1", "裸消息无前缀",
                null, "10.0.0.8", null, false, Instant.now()));

        assertEquals(AuditEventType.SUSPICIOUS_ACTIVITY.name(), captureAuditEventType());
    }

    private String captureAuditEventType() {
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(extendedAuditService).auditSecurityEvent(typeCaptor.capture(), any(), any(), any());
        return typeCaptor.getValue();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
