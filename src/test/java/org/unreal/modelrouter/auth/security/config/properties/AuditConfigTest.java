/*
 * Copyright (c) 2025 JAiRouter Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.auth.security.config.properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuditConfig 测试类
 */
@DisplayName("AuditConfig测试")
class AuditConfigTest {

    private AuditConfig auditConfig;

    @BeforeEach
    void setUp() {
        auditConfig = new AuditConfig();
    }

    @Nested
    @DisplayName("默认值测试")
    class DefaultValueTests {

        @Test
        @DisplayName("默认启用审计日志")
        void testDefaultEnabled() {
            assertTrue(auditConfig.isEnabled());
        }

        @Test
        @DisplayName("默认存储类型为database")
        void testDefaultStorage() {
            assertEquals("database", auditConfig.getStorage());
        }

        @Test
        @DisplayName("默认保留天数为90天")
        void testDefaultRetentionDays() {
            assertEquals(90, auditConfig.getRetentionDays());
        }

        @Test
        @DisplayName("默认日志级别为INFO")
        void testDefaultLogLevel() {
            assertEquals("INFO", auditConfig.getLogLevel());
        }

        @Test
        @DisplayName("默认不包含请求体")
        void testDefaultIncludeRequestBody() {
            assertFalse(auditConfig.isIncludeRequestBody());
        }

        @Test
        @DisplayName("默认不包含响应体")
        void testDefaultIncludeResponseBody() {
            assertFalse(auditConfig.isIncludeResponseBody());
        }

        @Test
        @DisplayName("默认启用告警")
        void testDefaultAlertEnabled() {
            assertTrue(auditConfig.isAlertEnabled());
        }

        @Test
        @DisplayName("默认清理时间为凌晨2点")
        void testDefaultCleanupSchedule() {
            assertEquals("0 0 2 * * *", auditConfig.getCleanupSchedule());
        }

        @Test
        @DisplayName("默认每次清理10000条")
        void testDefaultCleanupBatchSize() {
            assertEquals(10000, auditConfig.getCleanupBatchSize());
        }

        @Test
        @DisplayName("默认不限制存储大小")
        void testDefaultMaxStorageSizeMb() {
            assertEquals(0L, auditConfig.getMaxStorageSizeMb());
        }

        @Test
        @DisplayName("默认启用压缩归档")
        void testDefaultEnableArchive() {
            assertTrue(auditConfig.isEnableArchive());
        }
    }

    @Nested
    @DisplayName("风险等级保留策略测试")
    class RetentionByRiskLevelTests {

        @Test
        @DisplayName("获取LOW风险等级保留天数")
        void testLowRiskRetention() {
            assertEquals(30, auditConfig.getRetentionDaysByRiskLevel("LOW"));
        }

        @Test
        @DisplayName("获取MEDIUM风险等级保留天数")
        void testMediumRiskRetention() {
            assertEquals(90, auditConfig.getRetentionDaysByRiskLevel("MEDIUM"));
        }

        @Test
        @DisplayName("获取HIGH风险等级保留天数")
        void testHighRiskRetention() {
            assertEquals(365, auditConfig.getRetentionDaysByRiskLevel("HIGH"));
        }

        @Test
        @DisplayName("获取CRITICAL风险等级保留天数")
        void testCriticalRiskRetention() {
            assertEquals(730, auditConfig.getRetentionDaysByRiskLevel("CRITICAL"));
        }

        @Test
        @DisplayName("未知风险等级使用默认值")
        void testUnknownRiskRetention() {
            assertEquals(90, auditConfig.getRetentionDaysByRiskLevel("UNKNOWN"));
        }
    }

    @Nested
    @DisplayName("事件类型保留策略测试")
    class RetentionByEventTypeTests {

        @Test
        @DisplayName("未配置事件类型时使用默认值")
        void testDefaultEventTypeRetention() {
            assertEquals(90, auditConfig.getRetentionDaysByEventType("JWT_TOKEN_ISSUED"));
        }

        @Test
        @DisplayName("配置特定事件类型保留天数")
        void testConfiguredEventTypeRetention() {
            auditConfig.getRetentionByEventType().put("JWT_TOKEN_ISSUED", 180);
            assertEquals(180, auditConfig.getRetentionDaysByEventType("JWT_TOKEN_ISSUED"));
        }
    }

    @Nested
    @DisplayName("告警配置测试")
    class AlertConfigTests {

        @Test
        @DisplayName("告警配置默认值")
        void testAlertConfigDefaults() {
            AuditConfig.AlertConfig alertConfig = auditConfig.getAlert();

            assertTrue(alertConfig.isEnabled());
            assertEquals(5, alertConfig.getAuthFailureThreshold());
            assertEquals(5, alertConfig.getAuthFailureWindowMinutes());
            assertEquals(10, alertConfig.getSuspiciousIpThreshold());
            assertEquals(10, alertConfig.getSuspiciousIpWindowMinutes());
            assertEquals(15, alertConfig.getAlertCooldownMinutes());
            assertFalse(alertConfig.isEnableNotification());
            assertEquals("", alertConfig.getNotificationChannel());
        }

        @Test
        @DisplayName("设置告警配置")
        void testSetAlertConfig() {
            AuditConfig.AlertConfig alertConfig = new AuditConfig.AlertConfig();
            alertConfig.setEnabled(false);
            alertConfig.setAuthFailureThreshold(10);
            alertConfig.setAuthFailureWindowMinutes(10);

            auditConfig.setAlert(alertConfig);

            assertFalse(auditConfig.getAlert().isEnabled());
            assertEquals(10, auditConfig.getAlert().getAuthFailureThreshold());
            assertEquals(10, auditConfig.getAlert().getAuthFailureWindowMinutes());
        }
    }

    @Nested
    @DisplayName("导出配置测试")
    class ExportConfigTests {

        @Test
        @DisplayName("导出配置默认值")
        void testExportConfigDefaults() {
            AuditConfig.ExportConfig exportConfig = auditConfig.getExport();

            assertEquals(100000, exportConfig.getMaxExportRecords());
            assertEquals("CSV", exportConfig.getDefaultFormat());
            assertEquals("logs/audit/export", exportConfig.getTempPath());
            assertEquals(24, exportConfig.getFileRetentionHours());
        }
    }

    @Nested
    @DisplayName("告警阈值配置测试")
    class AlertThresholdsTests {

        @Test
        @DisplayName("告警阈值默认值")
        void testAlertThresholdsDefaults() {
            AuditConfig.AlertThresholds thresholds = auditConfig.getAlertThresholds();

            assertEquals(10, thresholds.getAuthFailuresPerMinute());
            assertEquals(1000, thresholds.getSanitizationOperationsPerMinute());
            assertEquals(5, thresholds.getSuspiciousActivitiesPerMinute());
        }
    }

    @Nested
    @DisplayName("Setter测试")
    class SetterTests {

        @Test
        @DisplayName("设置所有属性")
        void testSetAllProperties() {
            auditConfig.setEnabled(false);
            auditConfig.setStorage("memory");
            auditConfig.setRetentionDays(60);
            auditConfig.setLogLevel("DEBUG");
            auditConfig.setIncludeRequestBody(true);
            auditConfig.setIncludeResponseBody(true);
            auditConfig.setAlertEnabled(false);
            auditConfig.setCleanupSchedule("0 0 3 * * *");
            auditConfig.setCleanupBatchSize(5000);
            auditConfig.setMaxStorageSizeMb(1000L);
            auditConfig.setEnableArchive(false);
            auditConfig.setArchivePath("/var/log/audit");

            assertFalse(auditConfig.isEnabled());
            assertEquals("memory", auditConfig.getStorage());
            assertEquals(60, auditConfig.getRetentionDays());
            assertEquals("DEBUG", auditConfig.getLogLevel());
            assertTrue(auditConfig.isIncludeRequestBody());
            assertTrue(auditConfig.isIncludeResponseBody());
            assertFalse(auditConfig.isAlertEnabled());
            assertEquals("0 0 3 * * *", auditConfig.getCleanupSchedule());
            assertEquals(5000, auditConfig.getCleanupBatchSize());
            assertEquals(1000L, auditConfig.getMaxStorageSizeMb());
            assertFalse(auditConfig.isEnableArchive());
            assertEquals("/var/log/audit", auditConfig.getArchivePath());
        }
    }
}
