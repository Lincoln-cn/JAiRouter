package org.unreal.modelrouter.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SecurityAuditController RESTful 接口测试
 * 
 * 测试范围：
 * - GET /api/security/audit/logs - 查询安全审计日志
 * - POST /api/security/audit/logs/query - 复杂条件查询
 * - GET /api/security/audit/statistics - 获取安全统计信息
 * - DELETE /api/security/audit/logs/cleanup - 清理过期日志
 * - GET /api/security/audit/alerts/check - 检查告警状态
 * - GET /api/security/audit/alerts/statistics - 获取告警统计
 * - POST /api/security/audit/alerts/reset - 重置告警统计
 * 
 * v2.7.6: 使用 Mockito 单元测试方式
 */
@DisplayName("SecurityAuditController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
class SecurityAuditControllerIntegrationTest {

    @Mock
    private SecurityAuditService auditService;

    @Mock
    private SecurityAlertService alertService;

    @InjectMocks
    private SecurityAuditController controller;

    private static final String BASE_URL = "/api/security/audit";

    private SecurityAuditEvent testEvent;

    @BeforeEach
    void setUp() {
        // 创建测试用的 SecurityAuditEvent
        testEvent = SecurityAuditEvent.builder()
                .eventId("event-001")
                .eventType("LOGIN")
                .userId("user-001")
                .clientIp("192.168.1.1")
                .action("login")
                .resource("/api/auth/login")
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ==================== 查询审计日志测试 ====================

    @Nested
    @DisplayName("GET /api/security/audit/logs - 查询审计日志测试")
    class QueryAuditLogsTests {

        @Test
        @DisplayName("AUDIT-001: 查询审计日志成功")
        void testQueryAuditLogs_success() {
            // Given
            when(auditService.queryEvents(any(), any(), anyString(), anyString(), anyInt()))
                    .thenReturn(Flux.just(testEvent));

            // When
            Mono<ResponseEntity<SecurityAuditQueryResponse>> result = 
                    controller.queryAuditLogs(null, null, "LOGIN", "user-001", null, null, 0, 20);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.getStatusCode().is2xxSuccessful());
                        assertNotNull(response.getBody());
                        assertEquals(0, response.getBody().getPage());
                        assertEquals(20, response.getBody().getSize());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("AUDIT-002: 空审计日志结果")
        void testQueryAuditLogs_empty() {
            // Given
            when(auditService.queryEvents(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Flux.empty());

            // When
            Mono<ResponseEntity<SecurityAuditQueryResponse>> result = 
                    controller.queryAuditLogs(null, null, null, null, null, null, 0, 20);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.getStatusCode().is2xxSuccessful());
                        assertTrue(response.getBody().getEvents().isEmpty());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("AUDIT-003: 按客户端IP过滤")
        void testQueryAuditLogs_filterByClientIp() {
            // Given
            when(auditService.queryEvents(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Flux.just(testEvent));

            // When - 匹配的 IP
            Mono<ResponseEntity<SecurityAuditQueryResponse>> resultMatch = 
                    controller.queryAuditLogs(null, null, null, null, "192.168.1.1", null, 0, 20);

            // Then
            StepVerifier.create(resultMatch)
                    .assertNext(response -> {
                        assertEquals(1, response.getBody().getEvents().size());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 复杂条件查询测试 ====================

    @Nested
    @DisplayName("POST /api/security/audit/logs/query - 复杂条件查询测试")
    class QueryAuditLogsAdvancedTests {

        @Test
        @DisplayName("AUDIT-004: 复杂查询成功")
        void testQueryAuditLogsAdvanced_success() {
            // Given
            SecurityAuditQueryRequest request = new SecurityAuditQueryRequest();
            request.setStartTime(LocalDateTime.now().minusDays(1));
            request.setEndTime(LocalDateTime.now());
            request.setEventType("LOGIN");
            request.setPage(0);
            request.setSize(20);

            when(auditService.queryEvents(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Flux.just(testEvent));

            // When
            Mono<ResponseEntity<SecurityAuditQueryResponse>> result = 
                    controller.queryAuditLogsAdvanced(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.getStatusCode().is2xxSuccessful());
                        assertNotNull(response.getBody());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("AUDIT-005: 复杂查询参数验证")
        void testQueryAuditLogsAdvanced_parameterValidation() {
            // Given - 设置无效的分页参数
            SecurityAuditQueryRequest request = new SecurityAuditQueryRequest();
            request.setPage(-1);  // 无效页码
            request.setSize(200); // 超出限制
            request.setStartTime(null);
            request.setEndTime(null);

            when(auditService.queryEvents(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Flux.empty());

            // When
            Mono<ResponseEntity<SecurityAuditQueryResponse>> result = 
                    controller.queryAuditLogsAdvanced(request);

            // Then - 应该自动修正参数
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.getStatusCode().is2xxSuccessful());
                        assertEquals(0, response.getBody().getPage());
                        assertEquals(20, response.getBody().getSize());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 安全统计测试 ====================

    @Nested
    @DisplayName("GET /api/security/audit/statistics - 安全统计测试")
    class GetSecurityStatisticsTests {

        @Test
        @DisplayName("AUDIT-006: 获取安全统计信息成功")
        void testGetSecurityStatistics_success() {
            // Given
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalEvents", 100L);
            stats.put("successRate", 95.0);

            when(auditService.getSecurityStatistics(any(), any()))
                    .thenReturn(Mono.just(stats));
            when(alertService.getAlertStatistics())
                    .thenReturn(Collections.emptyMap());

            // When
            Mono<ResponseEntity<SecurityStatisticsResponse>> result = 
                    controller.getSecurityStatistics(null, null);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.getStatusCode().is2xxSuccessful());
                        assertNotNull(response.getBody());
                        assertNotNull(response.getBody().getAuditStatistics());
                        assertNotNull(response.getBody().getAlertStatistics());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 清理过期日志测试 ====================

    @Nested
    @DisplayName("DELETE /api/security/audit/logs/cleanup - 清理过期日志测试")
    class CleanupExpiredLogsTests {

        @Test
        @DisplayName("AUDIT-007: 清理过期日志成功")
        void testCleanupExpiredLogs_success() {
            // Given
            when(auditService.cleanupExpiredLogs(anyInt()))
                    .thenReturn(Mono.just(50L));

            // When
            Mono<ResponseEntity<Map<String, Object>>> result = 
                    controller.cleanupExpiredLogs(90);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.getStatusCode().is2xxSuccessful());
                        assertEquals(50L, response.getBody().get("deletedCount"));
                        assertEquals(90, response.getBody().get("retentionDays"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("AUDIT-008: 清理日志参数边界验证")
        void testCleanupExpiredLogs_boundaryValidation() {
            // Given
            when(auditService.cleanupExpiredLogs(anyInt()))
                    .thenReturn(Mono.just(0L));

            // When - 测试边界值 (超过365天会被限制为365天)
            Mono<ResponseEntity<Map<String, Object>>> result = 
                    controller.cleanupExpiredLogs(400);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.getStatusCode().is2xxSuccessful());
                        // 验证 retentionDays 被限制在范围内
                        int retentionDays = (int) response.getBody().get("retentionDays");
                        assertTrue(retentionDays >= 1 && retentionDays <= 365);
                    })
                    .verifyComplete();
        }
    }

    // ==================== 告警检查测试 ====================

    @Nested
    @DisplayName("GET /api/security/audit/alerts/* - 告警相关测试")
    class AlertTests {

        @Test
        @DisplayName("AUDIT-009: 检查告警状态 - 应触发")
        void testCheckAlertStatus_shouldTrigger() {
            // Given
            when(auditService.shouldTriggerAlert(anyString(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(true));

            // When
            Mono<ResponseEntity<Map<String, Object>>> result = 
                    controller.checkAlertStatus("LOGIN_FAILED", 5, 10);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.getStatusCode().is2xxSuccessful());
                        assertEquals(true, response.getBody().get("shouldTriggerAlert"));
                        assertEquals("LOGIN_FAILED", response.getBody().get("eventType"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("AUDIT-010: 检查告警状态 - 不应触发")
        void testCheckAlertStatus_shouldNotTrigger() {
            // Given
            when(auditService.shouldTriggerAlert(anyString(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(false));

            // When
            Mono<ResponseEntity<Map<String, Object>>> result = 
                    controller.checkAlertStatus("LOGIN", 5, 100);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.getStatusCode().is2xxSuccessful());
                        assertEquals(false, response.getBody().get("shouldTriggerAlert"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("AUDIT-011: 获取告警统计信息")
        void testGetAlertStatistics() {
            // Given
            Map<String, Object> alertStats = new HashMap<>();
            alertStats.put("totalAlerts", 10);
            alertStats.put("activeAlerts", 2);

            when(alertService.getAlertStatistics())
                    .thenReturn(alertStats);

            // When
            Mono<ResponseEntity<Map<String, Object>>> result = 
                    controller.getAlertStatistics();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.getStatusCode().is2xxSuccessful());
                        assertEquals(10, response.getBody().get("totalAlerts"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("AUDIT-012: 重置告警统计")
        void testResetAlertStatistics() {
            // Given
            doNothing().when(alertService).resetAlertStatistics();

            // When
            Mono<ResponseEntity<Map<String, Object>>> result = 
                    controller.resetAlertStatistics();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.getStatusCode().is2xxSuccessful());
                        assertEquals("告警统计已重置", response.getBody().get("message"));
                    })
                    .verifyComplete();

            verify(alertService).resetAlertStatistics();
        }
    }
}
