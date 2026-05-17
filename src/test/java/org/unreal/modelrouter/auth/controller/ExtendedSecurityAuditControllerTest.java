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
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.AuditEvent;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.dto.AuditEventQuery;
import org.unreal.modelrouter.common.dto.ExtendedAuditQueryResponse;
import org.unreal.modelrouter.common.dto.SecurityReport;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ExtendedSecurityAuditController 单元测试
 * 
 * <p>测试 JWT/API Key 审计功能</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("ExtendedSecurityAuditController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExtendedSecurityAuditControllerTest {

    @Mock
    private ExtendedSecurityAuditService extendedAuditService;

    @InjectMocks
    private ExtendedSecurityAuditController controller;

    // ==================== JWT Token 审计测试 ====================

    @Nested
    @DisplayName("GET /api/security/audit/extended/jwt-tokens - JWT令牌审计测试")
    class QueryJwtTokenAuditEventsTests {

        @Test
        @DisplayName("AUDIT-001: 成功查询JWT令牌审计事件")
        void testQueryJwtTokenAuditEvents_success() {
            // Given
            AuditEvent event = new AuditEvent();
            event.setType(AuditEventType.JWT_TOKEN_ISSUED);
            event.setUserId("user-1");
            
            when(extendedAuditService.countAuditEvents(any())).thenReturn(Mono.just(1L));
            when(extendedAuditService.findAuditEvents(any())).thenReturn(Flux.just(event));

            // When
            var result = controller.queryJwtTokenAuditEvents(null, null, null, null, null, null, 0, 20);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertNotNull(response.getData());
                        assertEquals(1, response.getData().getTotalElements());
                    })
                    .verifyComplete();
        }
    }

    // ==================== API Key 审计测试 ====================

    @Nested
    @DisplayName("GET /api/security/audit/extended/api-keys - API Key审计测试")
    class QueryApiKeyAuditEventsTests {

        @Test
        @DisplayName("AUDIT-002: 成功查询API Key审计事件")
        void testQueryApiKeyAuditEvents_success() {
            // Given
            AuditEvent event = new AuditEvent();
            event.setType(AuditEventType.API_KEY_USED);
            event.setUserId("user-1");
            
            when(extendedAuditService.countAuditEvents(any())).thenReturn(Mono.just(1L));
            when(extendedAuditService.findAuditEvents(any())).thenReturn(Flux.just(event));

            // When
            var result = controller.queryApiKeyAuditEvents(null, null, null, null, null, null, 0, 20);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertNotNull(response.getData());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 安全事件查询测试 ====================

    @Nested
    @DisplayName("GET /api/security/audit/extended/security-events - 安全事件查询测试")
    class QuerySecurityEventsTests {

        @Test
        @DisplayName("AUDIT-003: 成功查询安全事件")
        void testQuerySecurityEvents_success() {
            // Given
            AuditEvent event = new AuditEvent();
            event.setType(AuditEventType.SECURITY_ALERT);
            
            when(extendedAuditService.countAuditEvents(any())).thenReturn(Mono.just(2L));
            when(extendedAuditService.findAuditEvents(any())).thenReturn(Flux.just(event));

            // When
            var result = controller.querySecurityEvents(null, null, null, null, 0, 20);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(2, response.getData().getTotalElements());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 复杂查询测试 ====================

    @Nested
    @DisplayName("POST /api/security/audit/extended/query - 复杂查询测试")
    class QueryAuditEventsAdvancedTests {

        @Test
        @DisplayName("AUDIT-004: 成功执行复杂查询")
        void testQueryAuditEventsAdvanced_success() {
            // Given
            AuditEventQuery query = new AuditEventQuery();
            query.setPage(0);
            query.setSize(10);
            
            when(extendedAuditService.countAuditEvents(any())).thenReturn(Mono.just(0L));
            when(extendedAuditService.findAuditEvents(any())).thenReturn(Flux.empty());

            // When
            var result = controller.queryAuditEventsAdvanced(query);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 安全报告测试 ====================

    @Nested
    @DisplayName("GET /api/security/audit/extended/reports/security - 安全报告测试")
    class GenerateSecurityReportTests {

        @Test
        @DisplayName("AUDIT-005: 成功生成安全报告")
        void testGenerateSecurityReport_success() {
            // Given
            SecurityReport report = new SecurityReport();
            report.setReportPeriodStart(LocalDateTime.now().minusDays(1));
            report.setReportPeriodEnd(LocalDateTime.now());
            report.setTotalJwtOperations(10L);
            report.setTotalApiKeyOperations(5L);
            
            when(extendedAuditService.generateSecurityReport(any(), any())).thenReturn(Mono.just(report));

            // When
            var result = controller.generateSecurityReport(null, null);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertNotNull(response.getData());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 用户审计事件测试 ====================

    @Nested
    @DisplayName("GET /api/security/audit/extended/users/{userId}/events - 用户审计事件测试")
    class GetUserAuditEventsTests {

        @Test
        @DisplayName("AUDIT-006: 成功获取用户审计事件")
        void testGetUserAuditEvents_success() {
            // Given
            AuditEvent event = new AuditEvent();
            event.setUserId("user-123");
            
            when(extendedAuditService.getUserAuditEvents(anyString(), any(), any(), anyInt()))
                    .thenReturn(Flux.just(event));

            // When
            var result = controller.getUserAuditEvents("user-123", null, null, 50);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(1, response.getData().getEvents().size());
                    })
                    .verifyComplete();
        }
    }

    // ==================== IP审计事件测试 ====================

    @Nested
    @DisplayName("GET /api/security/audit/extended/ip-addresses/{ipAddress}/events - IP审计事件测试")
    class GetIpAuditEventsTests {

        @Test
        @DisplayName("AUDIT-007: 成功获取IP审计事件")
        void testGetIpAuditEvents_success() {
            // Given
            AuditEvent event = new AuditEvent();
            event.setIpAddress("192.168.1.100");
            
            when(extendedAuditService.getIpAuditEvents(anyString(), any(), any(), anyInt()))
                    .thenReturn(Flux.just(event));

            // When
            var result = controller.getIpAuditEvents("192.168.1.100", null, null, 50);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(1, response.getData().getEvents().size());
                    })
                    .verifyComplete();
        }
    }

    // ==================== 批量记录测试 ====================

    @Nested
    @DisplayName("POST /api/security/audit/extended/events/batch - 批量记录测试")
    class BatchRecordAuditEventsTests {

        @Test
        @DisplayName("AUDIT-008: 成功批量记录审计事件")
        void testBatchRecordAuditEvents_success() {
            // Given
            List<AuditEvent> events = List.of(new AuditEvent(), new AuditEvent());
            when(extendedAuditService.batchRecordAuditEvents(anyList())).thenReturn(Mono.empty());

            // When
            var result = controller.batchRecordAuditEvents(events);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(2, response.getData().get("recordedCount"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("AUDIT-009: 空事件列表返回错误")
        void testBatchRecordAuditEvents_empty() {
            // When
            var result = controller.batchRecordAuditEvents(List.of());

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertTrue(response.getMessage().contains("不能为空"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("AUDIT-010: 超过100条返回错误")
        void testBatchRecordAuditEvents_tooMany() {
            // Given
            List<AuditEvent> events = new java.util.ArrayList<>();
            for (int i = 0; i < 101; i++) {
                events.add(new AuditEvent());
            }

            // When
            var result = controller.batchRecordAuditEvents(events);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertTrue(response.getMessage().contains("不能超过100条"));
                    })
                    .verifyComplete();
        }
    }

    // ==================== 测试数据生成测试 ====================

    @Nested
    @DisplayName("POST /api/security/audit/extended/test-data/generate - 测试数据生成测试")
    class GenerateTestAuditDataTests {

        @Test
        @DisplayName("AUDIT-011: 成功生成测试审计数据")
        void testGenerateTestAuditData_success() {
            // Given
            when(extendedAuditService.auditTokenIssued(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());
            when(extendedAuditService.auditTokenValidated(anyString(), anyString(), anyBoolean(), anyString()))
                    .thenReturn(Mono.empty());
            when(extendedAuditService.auditTokenRefreshed(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());
            when(extendedAuditService.auditApiKeyCreated(anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());
            when(extendedAuditService.auditApiKeyUsed(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(Mono.empty());
            when(extendedAuditService.auditSecurityEvent(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());
            when(extendedAuditService.auditSuspiciousActivity(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());
            when(extendedAuditService.auditTokenRevoked(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());
            when(extendedAuditService.auditApiKeyRevoked(anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            // When
            var result = controller.generateTestAuditData();

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertTrue(response.isSuccess());
                        assertEquals(10, response.getData().get("eventsGenerated"));
                    })
                    .verifyComplete();
        }
    }
}
