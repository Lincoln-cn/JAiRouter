package org.unreal.modelrouter.auth.security.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.auth.security.config.properties.AuditConfig;
import org.unreal.modelrouter.common.dto.SecurityAlert;
import org.unreal.modelrouter.common.dto.SecurityReport;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuditLogCleanupTask 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogCleanupTask 测试")
class AuditLogCleanupTaskTest {

    @Mock
    private ExtendedSecurityAuditService auditService;

    private AuditConfig auditConfig;
    private AuditLogCleanupTask cleanupTask;

    @BeforeEach
    void setUp() {
        auditConfig = new AuditConfig();
        auditConfig.setEnabled(true);
        auditConfig.setRetentionDays(90);
        auditConfig.setMaxStorageSizeMb(1000);
        
        cleanupTask = new AuditLogCleanupTask(auditService, auditConfig);
    }

    @Nested
    @DisplayName("cleanupExpiredAuditLogs 测试")
    class CleanupExpiredAuditLogsTests {

        @Test
        @DisplayName("清理过期审计日志 - 成功")
        void testCleanupExpiredAuditLogs_Success() {
            // Arrange
            when(auditService.cleanupExpiredLogs(anyInt())).thenReturn(Mono.just(100L));

            // Act
            cleanupTask.cleanupExpiredAuditLogs();

            // Assert - 验证各风险等级的清理被调用
            verify(auditService, atLeastOnce()).cleanupExpiredLogs(anyInt());
        }

        @Test
        @DisplayName("清理过期审计日志 - LOW 风险等级")
        void testCleanupExpiredAuditLogs_LowRisk() {
            // Arrange
            when(auditService.cleanupExpiredLogs(anyInt())).thenReturn(Mono.just(50L));

            // Act
            cleanupTask.cleanupExpiredAuditLogs();

            // Assert
            verify(auditService).cleanupExpiredLogs(30); // LOW 默认30天
        }

        @Test
        @DisplayName("清理过期审计日志 - MEDIUM 风险等级")
        void testCleanupExpiredAuditLogs_MediumRisk() {
            // Arrange
            when(auditService.cleanupExpiredLogs(anyInt())).thenReturn(Mono.just(30L));

            // Act
            cleanupTask.cleanupExpiredAuditLogs();

            // Assert
            verify(auditService).cleanupExpiredLogs(90); // MEDIUM 默认90天
        }

        @Test
        @DisplayName("清理过期审计日志 - HIGH 风险等级")
        void testCleanupExpiredAuditLogs_HighRisk() {
            // Arrange
            when(auditService.cleanupExpiredLogs(anyInt())).thenReturn(Mono.just(10L));

            // Act
            cleanupTask.cleanupExpiredAuditLogs();

            // Assert
            verify(auditService).cleanupExpiredLogs(365); // HIGH 默认365天
        }

        @Test
        @DisplayName("清理过期审计日志 - CRITICAL 风险等级")
        void testCleanupExpiredAuditLogs_CriticalRisk() {
            // Arrange
            when(auditService.cleanupExpiredLogs(anyInt())).thenReturn(Mono.just(5L));

            // Act
            cleanupTask.cleanupExpiredAuditLogs();

            // Assert
            verify(auditService).cleanupExpiredLogs(730); // CRITICAL 默认730天
        }

        @Test
        @DisplayName("清理过期审计日志 - 异常处理")
        void testCleanupExpiredAuditLogs_Exception() {
            // Arrange
            when(auditService.cleanupExpiredLogs(anyInt()))
                    .thenReturn(Mono.error(new RuntimeException("Database error")));

            // Act & Assert - 不应抛出异常
            assertDoesNotThrow(() -> cleanupTask.cleanupExpiredAuditLogs());
        }
    }

    @Nested
    @DisplayName("checkStorageSize 测试")
    class CheckStorageSizeTests {

        @Test
        @DisplayName("检查存储空间 - 未超限")
        void testCheckStorageSize_UnderLimit() {
            // Arrange
            auditConfig.setMaxStorageSizeMb(1000); // 1000MB 限制

            // Act
            cleanupTask.checkStorageSize();

            // Assert - 不应触发紧急清理
            verify(auditService, never()).cleanupExpiredLogs(anyInt());
        }

        @Test
        @DisplayName("检查存储空间 - 未配置限制")
        void testCheckStorageSize_NoLimit() {
            // Arrange
            auditConfig.setMaxStorageSizeMb(0); // 不限制

            // Act
            cleanupTask.checkStorageSize();

            // Assert - 不应调用任何清理
            verify(auditService, never()).cleanupExpiredLogs(anyInt());
        }
    }

    @Nested
    @DisplayName("generateDailyReport 测试")
    class GenerateDailyReportTests {

        @Test
        @DisplayName("生成每日报告 - 成功")
        void testGenerateDailyReport_Success() {
            // Arrange
            SecurityReport report = new SecurityReport();
            report.setTotalJwtOperations(100L);
            report.setTotalApiKeyOperations(50L);
            report.setFailedAuthentications(5L);
            report.setSuspiciousActivities(2L);
            report.setAlerts(new ArrayList<>());

            when(auditService.generateSecurityReport(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(Mono.just(report));

            // Act
            cleanupTask.generateDailyReport();

            // Assert
            verify(auditService).generateSecurityReport(any(LocalDateTime.class), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("生成每日报告 - 包含告警")
        void testGenerateDailyReport_WithAlerts() {
            // Arrange
            SecurityReport report = new SecurityReport();
            report.setTotalJwtOperations(100L);
            report.setTotalApiKeyOperations(50L);
            report.setFailedAuthentications(5L);
            report.setSuspiciousActivities(2L);
            SecurityAlert alert1 = new SecurityAlert("AUTH_FAILURE", "Multiple auth failures", "user-001", "192.168.1.1", LocalDateTime.now());
            SecurityAlert alert2 = new SecurityAlert("SUSPICIOUS", "Suspicious activity detected", "user-002", "192.168.1.2", LocalDateTime.now());
            report.setAlerts(List.of(alert1, alert2));

            when(auditService.generateSecurityReport(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(Mono.just(report));

            // Act
            cleanupTask.generateDailyReport();

            // Assert
            verify(auditService).generateSecurityReport(any(LocalDateTime.class), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("生成每日报告 - 异常处理")
        void testGenerateDailyReport_Exception() {
            // Arrange
            when(auditService.generateSecurityReport(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(Mono.error(new RuntimeException("Report generation failed")));

            // Act & Assert - 不应抛出异常
            assertDoesNotThrow(() -> cleanupTask.generateDailyReport());
        }
    }

    @Nested
    @DisplayName("checkSecurityAlerts 测试")
    class CheckSecurityAlertsTests {

        @Test
        @DisplayName("检查安全告警 - 告警启用")
        void testCheckSecurityAlerts_AlertEnabled() {
            // Arrange
            auditConfig.getAlert().setEnabled(true);
            when(auditService.shouldTriggerAlert(anyString(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(false));

            // Act
            cleanupTask.checkSecurityAlerts();

            // Assert
            verify(auditService, atLeastOnce()).shouldTriggerAlert(anyString(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("检查安全告警 - 告警禁用")
        void testCheckSecurityAlerts_AlertDisabled() {
            // Arrange
            auditConfig.getAlert().setEnabled(false);

            // Act
            cleanupTask.checkSecurityAlerts();

            // Assert
            verify(auditService, never()).shouldTriggerAlert(anyString(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("检查安全告警 - 认证失败告警")
        void testCheckSecurityAlerts_AuthFailureAlert() {
            // Arrange
            auditConfig.getAlert().setEnabled(true);
            auditConfig.getAlert().setAuthFailureThreshold(5);
            auditConfig.getAlert().setAuthFailureWindowMinutes(5);
            
            when(auditService.shouldTriggerAlert(anyString(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(true));

            // Act
            cleanupTask.checkSecurityAlerts();

            // Assert
            verify(auditService).shouldTriggerAlert(eq("AUTHENTICATION_FAILED"), anyInt(), anyInt());
        }

        @Test
        @DisplayName("检查安全告警 - 可疑活动告警")
        void testCheckSecurityAlerts_SuspiciousActivityAlert() {
            // Arrange
            auditConfig.getAlert().setEnabled(true);
            when(auditService.shouldTriggerAlert(anyString(), anyInt(), anyInt()))
                    .thenReturn(Mono.just(true));

            // Act
            cleanupTask.checkSecurityAlerts();

            // Assert
            verify(auditService).shouldTriggerAlert(eq("SUSPICIOUS_ACTIVITY"), eq(10), eq(3));
        }
    }

    @Nested
    @DisplayName("配置测试")
    class ConfigurationTests {

        @Test
        @DisplayName("验证默认保留策略")
        void testDefaultRetentionPolicies() {
            // Assert
            assertEquals(30, auditConfig.getRetentionDaysByRiskLevel("LOW"));
            assertEquals(90, auditConfig.getRetentionDaysByRiskLevel("MEDIUM"));
            assertEquals(365, auditConfig.getRetentionDaysByRiskLevel("HIGH"));
            assertEquals(730, auditConfig.getRetentionDaysByRiskLevel("CRITICAL"));
        }

        @Test
        @DisplayName("验证未知风险等级使用默认保留天数")
        void testUnknownRiskLevelUsesDefault() {
            // Assert
            assertEquals(90, auditConfig.getRetentionDaysByRiskLevel("UNKNOWN"));
        }

        @Test
        @DisplayName("验证告警默认配置")
        void testDefaultAlertConfig() {
            // Assert
            assertTrue(auditConfig.getAlert().isEnabled());
            assertEquals(5, auditConfig.getAlert().getAuthFailureThreshold());
            assertEquals(5, auditConfig.getAlert().getAuthFailureWindowMinutes());
        }
    }
}
