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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.dto.SecurityAuditQueryRequest;
import org.unreal.modelrouter.common.dto.SecurityAuditQueryResponse;
import org.unreal.modelrouter.common.dto.SecurityStatisticsResponse;
import org.unreal.modelrouter.monitor.monitoring.security.SecurityAlertService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SecurityAuditController 单元测试
 *
 * <p>使用 StepVerifier 测试响应式 API</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("SecurityAuditController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityAuditControllerTest {

    @Mock
    private SecurityAuditService auditService;

    @Mock
    private SecurityAlertService alertService;

    @InjectMocks
    private SecurityAuditController controller;

    private SecurityAuditEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = SecurityAuditEvent.builder()
                .eventId("test-id")
                .eventType("LOGIN")
                .userId("testuser")
                .clientIp("192.168.1.1")
                .timestamp(LocalDateTime.now())
                .success(true)
                .build();
    }

    // ==================== 查询审计日志测试 ====================

    @Nested
    @DisplayName("GET /api/security/audit/logs - 查询审计日志测试")
    class QueryAuditLogsTests {

        @Test
        @DisplayName("AUDIT-001: 成功查询审计日志")
        void testQueryAuditLogs_success() {
            // Given
            when(auditService.queryEvents(any(), any(), anyString(), anyString(), anyInt()))
                    .thenReturn(Flux.just(testEvent));

            // When
            Mono<ResponseEntity<SecurityAuditQueryResponse>> result = controller.queryAuditLogs(
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now(),
                    "LOGIN",
                    "testuser",
                    null,
                    null,
                    0,
                    20);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.getStatusCode() == HttpStatus.OK
                            && response.getBody().getEvents().size() == 1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("AUDIT-002: 使用默认参数查询")
        void testQueryAuditLogs_defaultParams() {
            // Given
            when(auditService.queryEvents(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Flux.just(testEvent));

            // When
            Mono<ResponseEntity<SecurityAuditQueryResponse>> result = controller.queryAuditLogs(
                    null, null, null, null, null, null, 0, 20);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.getStatusCode() == HttpStatus.OK)
                    .verifyComplete();
        }
    }

    // ==================== 高级查询测试 ====================

    @Nested
    @DisplayName("POST /api/security/audit/logs/query - 高级查询测试")
    class QueryAuditLogsAdvancedTests {

        @Test
        @DisplayName("AUDIT-003: 成功执行高级查询")
        void testQueryAuditLogsAdvanced_success() {
            // Given
            SecurityAuditQueryRequest request = new SecurityAuditQueryRequest();
            request.setStartTime(LocalDateTime.now().minusDays(1));
            request.setEndTime(LocalDateTime.now());
            request.setPage(0);
            request.setSize(20);

            when(auditService.queryEvents(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Flux.just(testEvent));

            // When
            Mono<ResponseEntity<SecurityAuditQueryResponse>> result = controller.queryAuditLogsAdvanced(request);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.getStatusCode() == HttpStatus.OK)
                    .verifyComplete();
        }
    }

    // ==================== 安全统计测试 ====================

    @Nested
    @DisplayName("GET /api/security/audit/statistics - 安全统计测试")
    class GetSecurityStatisticsTests {

        @Test
        @DisplayName("AUDIT-004: 成功获取安全统计")
        void testGetSecurityStatistics_success() {
            // Given
            when(auditService.getSecurityStatistics(any(), any()))
                    .thenReturn(Mono.just(Map.of("totalEvents", 100L)));
            when(alertService.getAlertStatistics())
                    .thenReturn(Map.of("alertCount", 5L));

            // When
            Mono<ResponseEntity<SecurityStatisticsResponse>> result = controller.getSecurityStatistics(
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now());

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.getStatusCode() == HttpStatus.OK)
                    .verifyComplete();
        }
    }

    // ==================== 清理过期日志测试 ====================

    @Nested
    @DisplayName("DELETE /api/security/audit/logs/cleanup - 清理过期日志测试")
    class CleanupExpiredLogsTests {

        @Test
        @DisplayName("AUDIT-005: 成功清理过期日志")
        void testCleanupExpiredLogs_success() {
            // Given
            when(auditService.cleanupExpiredLogs(anyInt()))
                    .thenReturn(Mono.just(10L));

            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.cleanupExpiredLogs(30);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.getStatusCode() == HttpStatus.OK
                            && response.getBody().containsKey("deletedCount"))
                    .verifyComplete();
        }
    }

    // ==================== 告警检查测试 ====================

    @Nested
    @DisplayName("GET /api/security/audit/alerts/check - 告警检查测试")
    class CheckAlertStatusTests {

        @Test
        @DisplayName("AUDIT-006: 成功检查告警状态")
        void testCheckAlertStatus_success() {
            // Given
            when(auditService.shouldTriggerAlert(anyString(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(true));

            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.checkAlertStatus("LOGIN", 5, 10);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.getStatusCode() == HttpStatus.OK
                            && response.getBody().containsKey("shouldTriggerAlert"))
                    .verifyComplete();
        }
    }

    // ==================== 告警统计测试 ====================

    @Nested
    @DisplayName("GET /api/security/audit/alerts/statistics - 告警统计测试")
    class GetAlertStatisticsTests {

        @Test
        @DisplayName("AUDIT-007: 成功获取告警统计")
        void testGetAlertStatistics_success() {
            // Given
            when(alertService.getAlertStatistics())
                    .thenReturn(new java.util.HashMap<>(Map.of("totalAlerts", 10L)));

            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.getAlertStatistics();

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.getStatusCode() == HttpStatus.OK)
                    .verifyComplete();
        }
    }

    // ==================== 重置告警统计测试 ====================

    @Nested
    @DisplayName("POST /api/security/audit/alerts/reset - 重置告警统计测试")
    class ResetAlertStatisticsTests {

        @Test
        @DisplayName("AUDIT-008: 成功重置告警统计")
        void testResetAlertStatistics_success() {
            // Given
            doNothing().when(alertService).resetAlertStatistics();

            // When
            Mono<ResponseEntity<Map<String, Object>>> result = controller.resetAlertStatistics();

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> response.getStatusCode() == HttpStatus.OK
                            && response.getBody().containsKey("message"))
                    .verifyComplete();
        }
    }
}
