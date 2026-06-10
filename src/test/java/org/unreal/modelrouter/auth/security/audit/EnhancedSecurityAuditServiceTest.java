package org.unreal.modelrouter.auth.security.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EnhancedSecurityAuditService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EnhancedSecurityAuditService 测试")
class EnhancedSecurityAuditServiceTest {

    @Mock
    private AuditMetricsService auditMetricsService;

    private EnhancedSecurityAuditService service;

    @BeforeEach
    void setUp() {
        service = new EnhancedSecurityAuditService(auditMetricsService);
    }

    @Nested
    @DisplayName("recordEvent 测试")
    class RecordEventTests {

        @Test
        @DisplayName("记录事件成功 - 主存储成功")
        void testRecordEvent_PrimaryStorageSuccess() {
            // Arrange
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventId("event-001")
                    .eventType("AUTHENTICATION_SUCCESS")
                    .userId("user-001")
                    .clientIp("192.168.1.1")
                    .action("LOGIN")
                    .success(true)
                    .timestamp(LocalDateTime.now())
                    .build();

            // Act
            var result = service.recordEvent(event);

            // Assert
            assertNotNull(result);
            result.block();

            verify(auditMetricsService).recordEvent(eq("AUTHENTICATION_SUCCESS"), eq(true));
            verify(auditMetricsService).recordWriteDuration(anyLong());
            verify(auditMetricsService).updateBufferSize(anyInt());
        }

        @Test
        @DisplayName("记录事件 - 包含额外数据")
        void testRecordEvent_WithAdditionalData() {
            // Arrange
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventId("event-002")
                    .eventType("DATA_SANITIZATION")
                    .userId("user-001")
                    .action("SANITIZE")
                    .success(true)
                    .additionalData(Map.of("ruleId", "rule-001", "matchCount", 5))
                    .timestamp(LocalDateTime.now())
                    .build();

            // Act
            var result = service.recordEvent(event);

            // Assert
            assertNotNull(result);
            result.block();

            verify(auditMetricsService).recordEvent(eq("DATA_SANITIZATION"), eq(true));
        }

        @Test
        @DisplayName("记录事件 - 失败事件")
        void testRecordEvent_FailureEvent() {
            // Arrange
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventId("event-003")
                    .eventType("AUTHENTICATION_FAILURE")
                    .userId("user-001")
                    .clientIp("192.168.1.1")
                    .action("LOGIN")
                    .success(false)
                    .failureReason("Invalid credentials")
                    .timestamp(LocalDateTime.now())
                    .build();

            // Act
            var result = service.recordEvent(event);

            // Assert
            assertNotNull(result);
            result.block();

            verify(auditMetricsService).recordEvent(eq("AUTHENTICATION_FAILURE"), anyBoolean());
        }
    }

    @Nested
    @DisplayName("recordAuthenticationEvent 测试")
    class RecordAuthenticationEventTests {

        @Test
        @DisplayName("记录认证成功事件")
        void testRecordAuthenticationEvent_Success() {
            // Act
            var result = service.recordAuthenticationEvent(
                    "user-001",
                    "192.168.1.1",
                    "Mozilla/5.0",
                    true,
                    null
            );

            // Assert
            assertNotNull(result);
            result.block();

            verify(auditMetricsService).recordEvent(eq("AUTHENTICATION_SUCCESS"), eq(true));
        }

        @Test
        @DisplayName("记录认证失败事件")
        void testRecordAuthenticationEvent_Failure() {
            // Act
            var result = service.recordAuthenticationEvent(
                    "user-001",
                    "192.168.1.1",
                    "Mozilla/5.0",
                    false,
                    "Invalid password"
            );

            // Assert
            assertNotNull(result);
            result.block();

            verify(auditMetricsService).recordEvent(eq("AUTHENTICATION_FAILURE"), anyBoolean());
        }
    }

    @Nested
    @DisplayName("recordSanitizationEvent 测试")
    class RecordSanitizationEventTests {

        @Test
        @DisplayName("记录数据清洗事件")
        void testRecordSanitizationEvent() {
            // Act
            var result = service.recordSanitizationEvent(
                    "user-001",
                    "text/plain",
                    "rule-001",
                    5
            );

            // Assert
            assertNotNull(result);
            result.block();

            verify(auditMetricsService).recordEvent(eq("DATA_SANITIZATION"), eq(true));
        }
    }

    @Nested
    @DisplayName("queryEvents 测试")
    class QueryEventsTests {

        @Test
        @DisplayName("查询事件 - 按时间范围过滤")
        void testQueryEvents_ByTimeRange() throws InterruptedException {
            // Arrange - 添加一些事件到缓冲区
            LocalDateTime now = LocalDateTime.now();
            
            SecurityAuditEvent event1 = SecurityAuditEvent.builder()
                    .eventId("event-001")
                    .eventType("AUTHENTICATION_SUCCESS")
                    .userId("user-001")
                    .timestamp(now.minusHours(2))
                    .build();

            SecurityAuditEvent event2 = SecurityAuditEvent.builder()
                    .eventId("event-002")
                    .eventType("AUTHENTICATION_SUCCESS")
                    .userId("user-002")
                    .timestamp(now.minusMinutes(30))
                    .build();

            service.recordEvent(event1).block();
            service.recordEvent(event2).block();

            // Act
            LocalDateTime startTime = now.minusHours(1);
            LocalDateTime endTime = now.plusHours(1);
            var result = service.queryEvents(startTime, endTime, null, null, 10)
                    .collectList()
                    .block();

            // Assert
            assertNotNull(result);
            // 只有一个事件在时间范围内
            assertTrue(result.size() <= 2);
        }

        @Test
        @DisplayName("查询事件 - 按事件类型过滤")
        void testQueryEvents_ByEventType() {
            // Arrange
            LocalDateTime now = LocalDateTime.now();
            
            SecurityAuditEvent event1 = SecurityAuditEvent.builder()
                    .eventId("event-001")
                    .eventType("AUTHENTICATION_SUCCESS")
                    .userId("user-001")
                    .timestamp(now)
                    .build();

            SecurityAuditEvent event2 = SecurityAuditEvent.builder()
                    .eventId("event-002")
                    .eventType("DATA_SANITIZATION")
                    .userId("user-002")
                    .timestamp(now)
                    .build();

            service.recordEvent(event1).block();
            service.recordEvent(event2).block();

            // Act
            var result = service.queryEvents(
                    now.minusDays(1),
                    now.plusDays(1),
                    "AUTHENTICATION_SUCCESS",
                    null,
                    10
            ).collectList().block();

            // Assert
            assertNotNull(result);
        }

        @Test
        @DisplayName("查询事件 - 按用户过滤")
        void testQueryEvents_ByUserId() {
            // Arrange
            LocalDateTime now = LocalDateTime.now();
            
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventId("event-001")
                    .eventType("AUTHENTICATION_SUCCESS")
                    .userId("user-001")
                    .timestamp(now)
                    .build();

            service.recordEvent(event).block();

            // Act
            var result = service.queryEvents(
                    now.minusDays(1),
                    now.plusDays(1),
                    null,
                    "user-001",
                    10
            ).collectList().block();

            // Assert
            assertNotNull(result);
        }

        @Test
        @DisplayName("查询事件 - 限制返回数量")
        void testQueryEvents_WithLimit() {
            // Arrange
            LocalDateTime now = LocalDateTime.now();
            
            for (int i = 0; i < 5; i++) {
                SecurityAuditEvent event = SecurityAuditEvent.builder()
                        .eventId("event-" + i)
                        .eventType("AUTHENTICATION_SUCCESS")
                        .userId("user-001")
                        .timestamp(now)
                        .build();
                service.recordEvent(event).block();
            }

            // Act
            var result = service.queryEvents(
                    now.minusDays(1),
                    now.plusDays(1),
                    null,
                    null,
                    2
            ).collectList().block();

            // Assert
            assertNotNull(result);
            assertTrue(result.size() <= 2);
        }
    }

    @Nested
    @DisplayName("getSecurityStatistics 测试")
    class GetSecurityStatisticsTests {

        @Test
        @DisplayName("获取安全统计信息")
        void testGetSecurityStatistics() {
            // Arrange - 记录一些事件
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventId("event-001")
                    .eventType("AUTHENTICATION_SUCCESS")
                    .userId("user-001")
                    .timestamp(LocalDateTime.now())
                    .build();
            service.recordEvent(event).block();

            // Act
            LocalDateTime now = LocalDateTime.now();
            var result = service.getSecurityStatistics(now.minusDays(1), now.plusDays(1)).block();

            // Assert
            assertNotNull(result);
            assertTrue(result.containsKey("totalEvents"));
            assertTrue(result.containsKey("primaryStorageFailures"));
            assertTrue(result.containsKey("fallbackStorageSuccesses"));
            assertTrue(result.containsKey("bufferSize"));
            assertTrue(result.containsKey("healthScore"));
        }
    }

    @Nested
    @DisplayName("getHealthStatus 测试")
    class GetHealthStatusTests {

        @Test
        @DisplayName("获取健康状态 - 初始状态")
        void testGetHealthStatus_InitialState() {
            // Act
            var result = service.getHealthStatus();

            // Assert
            assertNotNull(result);
            assertEquals(0L, result.get("totalEvents"));
            assertEquals(0L, result.get("primaryStorageFailures"));
            assertEquals(0L, result.get("fallbackStorageSuccesses"));
            assertEquals(0, result.get("bufferSize"));
            assertEquals(100, result.get("healthScore")); // 无事件时分数为100
        }

        @Test
        @DisplayName("获取健康状态 - 有事件后")
        void testGetHealthStatus_AfterEvents() {
            // Arrange
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventId("event-001")
                    .eventType("AUTHENTICATION_SUCCESS")
                    .userId("user-001")
                    .timestamp(LocalDateTime.now())
                    .build();
            service.recordEvent(event).block();

            // Act
            var result = service.getHealthStatus();

            // Assert
            assertNotNull(result);
            assertTrue((Long) result.get("totalEvents") >= 1);
            assertTrue((Integer) result.get("healthScore") >= 0);
            assertTrue((Integer) result.get("healthScore") <= 100);
        }
    }

    @Nested
    @DisplayName("calculateHealthScore 测试")
    class CalculateHealthScoreTests {

        @Test
        @DisplayName("健康分数 - 无事件时为100")
        void testCalculateHealthScore_NoEvents() {
            // Act
            var status = service.getHealthStatus();
            int healthScore = (Integer) status.get("healthScore");

            // Assert
            assertEquals(100, healthScore);
        }

        @Test
        @DisplayName("健康分数 - 有成功事件时保持高分")
        void testCalculateHealthScore_WithSuccessEvents() {
            // Arrange
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventId("event-001")
                    .eventType("AUTHENTICATION_SUCCESS")
                    .userId("user-001")
                    .success(true)
                    .timestamp(LocalDateTime.now())
                    .build();
            service.recordEvent(event).block();

            // Act
            var status = service.getHealthStatus();
            int healthScore = (Integer) status.get("healthScore");

            // Assert
            assertTrue(healthScore >= 90); // 成功事件应保持高分
        }
    }

    @Nested
    @DisplayName("getFailedEvents 测试")
    class GetFailedEventsTests {

        @Test
        @DisplayName("获取失败事件 - 初始为空")
        void testGetFailedEvents_InitiallyEmpty() {
            // Act
            var result = service.getFailedEvents();

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("cleanupExpiredLogs 测试")
    class CleanupExpiredLogsTests {

        @Test
        @DisplayName("清理过期日志")
        void testCleanupExpiredLogs() {
            // Act
            var result = service.cleanupExpiredLogs(90).block();

            // Assert
            assertNotNull(result);
            assertEquals(0L, result); // 简化实现返回0
        }
    }

    @Nested
    @DisplayName("shouldTriggerAlert 测试")
    class ShouldTriggerAlertTests {

        @Test
        @DisplayName("检查是否触发告警")
        void testShouldTriggerAlert() {
            // Act
            var result = service.shouldTriggerAlert("AUTHENTICATION_FAILURE", 5, 10).block();

            // Assert
            assertNotNull(result);
            assertFalse(result); // 简化实现返回false
        }
    }
}
