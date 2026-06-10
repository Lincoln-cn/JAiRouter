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

package org.unreal.modelrouter.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityReport 测试类
 */
@DisplayName("SecurityReport测试")
class SecurityReportTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造函数")
        void testNoArgsConstructor() {
            SecurityReport report = new SecurityReport();

            assertNull(report.getReportPeriodStart());
            assertNull(report.getReportPeriodEnd());
            assertEquals(0L, report.getTotalJwtOperations());
        }

        @Test
        @DisplayName("全参构造函数")
        void testAllArgsConstructor() {
            LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 1, 31, 23, 59);
            Map<String, Long> byType = Map.of("JWT_TOKEN_ISSUED", 100L);
            Map<String, Long> byUser = Map.of("user1", 50L);
            List<String> topIps = List.of("192.168.1.1");
            List<SecurityAlert> alerts = List.of();

            SecurityReport report = new SecurityReport(
                    start, end, 100L, 200L, 10L, 5L,
                    byType, byUser, topIps, alerts
            );

            assertEquals(start, report.getReportPeriodStart());
            assertEquals(end, report.getReportPeriodEnd());
            assertEquals(100L, report.getTotalJwtOperations());
            assertEquals(200L, report.getTotalApiKeyOperations());
            assertEquals(10L, report.getFailedAuthentications());
            assertEquals(5L, report.getSuspiciousActivities());
            assertEquals(byType, report.getOperationsByType());
            assertEquals(byUser, report.getOperationsByUser());
            assertEquals(topIps, report.getTopIpAddresses());
            assertEquals(alerts, report.getAlerts());
        }
    }

    @Nested
    @DisplayName("Setter测试")
    class SetterTests {

        @Test
        @DisplayName("设置所有属性")
        void testSetAllProperties() {
            SecurityReport report = new SecurityReport();

            LocalDateTime start = LocalDateTime.now().minusDays(7);
            LocalDateTime end = LocalDateTime.now();
            Map<String, Long> byType = Map.of("API_KEY_USED", 500L);
            Map<String, Long> byUser = Map.of("admin", 300L);
            List<String> topIps = List.of("10.0.0.1", "10.0.0.2");
            List<SecurityAlert> alerts = List.of(
                    new SecurityAlert("AUTH_FAILED", "Multiple failures", "user1", "10.0.0.1", LocalDateTime.now())
            );

            report.setReportPeriodStart(start);
            report.setReportPeriodEnd(end);
            report.setTotalJwtOperations(150L);
            report.setTotalApiKeyOperations(300L);
            report.setFailedAuthentications(20L);
            report.setSuspiciousActivities(3L);
            report.setOperationsByType(byType);
            report.setOperationsByUser(byUser);
            report.setTopIpAddresses(topIps);
            report.setAlerts(alerts);

            assertEquals(start, report.getReportPeriodStart());
            assertEquals(end, report.getReportPeriodEnd());
            assertEquals(150L, report.getTotalJwtOperations());
            assertEquals(300L, report.getTotalApiKeyOperations());
            assertEquals(20L, report.getFailedAuthentications());
            assertEquals(3L, report.getSuspiciousActivities());
            assertEquals(byType, report.getOperationsByType());
            assertEquals(byUser, report.getOperationsByUser());
            assertEquals(topIps, report.getTopIpAddresses());
            assertEquals(alerts, report.getAlerts());
        }
    }

    @Nested
    @DisplayName("toString测试")
    class ToStringTests {

        @Test
        @DisplayName("toString包含所有字段")
        void testToString() {
            SecurityReport report = new SecurityReport();
            report.setTotalJwtOperations(100L);
            report.setFailedAuthentications(5L);

            String str = report.toString();

            assertTrue(str.contains("totalJwtOperations=100"));
            assertTrue(str.contains("failedAuthentications=5"));
        }
    }
}
