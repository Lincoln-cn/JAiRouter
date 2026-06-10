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

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityAlert 测试类
 */
@DisplayName("SecurityAlert测试")
class SecurityAlertTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造函数")
        void testNoArgsConstructor() {
            SecurityAlert alert = new SecurityAlert();

            assertNull(alert.getId());
            assertNull(alert.getAlertType());
            assertNull(alert.getSeverity());
            assertNull(alert.getMessage());
            assertNull(alert.getUserId());
            assertNull(alert.getIpAddress());
            assertNull(alert.getTimestamp());
            assertFalse(alert.isResolved());
        }

        @Test
        @DisplayName("5参数构造函数")
        void testFiveArgConstructor() {
            LocalDateTime timestamp = LocalDateTime.of(2025, 1, 15, 10, 30);
            SecurityAlert alert = new SecurityAlert(
                    "AUTH_FAILED",
                    "Multiple authentication failures detected",
                    "user123",
                    "192.168.1.100",
                    timestamp
            );

            assertNull(alert.getId());
            assertEquals("AUTH_FAILED", alert.getAlertType());
            assertNull(alert.getSeverity());
            assertEquals("Multiple authentication failures detected", alert.getMessage());
            assertEquals("user123", alert.getUserId());
            assertEquals("192.168.1.100", alert.getIpAddress());
            assertEquals(timestamp, alert.getTimestamp());
            assertFalse(alert.isResolved());
        }

        @Test
        @DisplayName("8参数构造函数")
        void testEightArgConstructor() {
            LocalDateTime timestamp = LocalDateTime.of(2025, 2, 20, 14, 45);
            SecurityAlert alert = new SecurityAlert(
                    "alert-001",
                    "SUSPICIOUS_IP",
                    "HIGH",
                    "Suspicious IP address detected",
                    "user456",
                    "10.0.0.50",
                    timestamp,
                    true
            );

            assertEquals("alert-001", alert.getId());
            assertEquals("SUSPICIOUS_IP", alert.getAlertType());
            assertEquals("HIGH", alert.getSeverity());
            assertEquals("Suspicious IP address detected", alert.getMessage());
            assertEquals("user456", alert.getUserId());
            assertEquals("10.0.0.50", alert.getIpAddress());
            assertEquals(timestamp, alert.getTimestamp());
            assertTrue(alert.isResolved());
        }
    }

    @Nested
    @DisplayName("Setter测试")
    class SetterTests {

        @Test
        @DisplayName("设置所有属性")
        void testSetAllProperties() {
            SecurityAlert alert = new SecurityAlert();
            LocalDateTime timestamp = LocalDateTime.now();

            alert.setId("alert-002");
            alert.setAlertType("BRUTE_FORCE");
            alert.setSeverity("CRITICAL");
            alert.setMessage("Brute force attack detected");
            alert.setUserId("attacker");
            alert.setIpAddress("172.16.0.1");
            alert.setTimestamp(timestamp);
            alert.setResolved(false);

            assertEquals("alert-002", alert.getId());
            assertEquals("BRUTE_FORCE", alert.getAlertType());
            assertEquals("CRITICAL", alert.getSeverity());
            assertEquals("Brute force attack detected", alert.getMessage());
            assertEquals("attacker", alert.getUserId());
            assertEquals("172.16.0.1", alert.getIpAddress());
            assertEquals(timestamp, alert.getTimestamp());
            assertFalse(alert.isResolved());
        }

        @Test
        @DisplayName("标记告警为已解决")
        void testMarkAsResolved() {
            SecurityAlert alert = new SecurityAlert();
            assertFalse(alert.isResolved());

            alert.setResolved(true);
            assertTrue(alert.isResolved());
        }
    }

    @Nested
    @DisplayName("告警类型测试")
    class AlertTypeTests {

        @Test
        @DisplayName("认证失败告警")
        void testAuthenticationFailureAlert() {
            SecurityAlert alert = new SecurityAlert(
                    "AUTH_FAILED",
                    "User failed authentication 5 times",
                    "user789",
                    "192.168.100.50",
                    LocalDateTime.now()
            );

            assertEquals("AUTH_FAILED", alert.getAlertType());
            assertTrue(alert.getMessage().contains("authentication"));
        }

        @Test
        @DisplayName("可疑IP告警")
        void testSuspiciousIpAlert() {
            SecurityAlert alert = new SecurityAlert(
                    null,
                    "SUSPICIOUS_IP",
                    "MEDIUM",
                    "IP from blocked country",
                    null,
                    "203.0.113.1",
                    LocalDateTime.now(),
                    false
            );

            assertEquals("SUSPICIOUS_IP", alert.getAlertType());
            assertEquals("MEDIUM", alert.getSeverity());
        }

        @Test
        @DisplayName("令牌滥用告警")
        void testTokenAbuseAlert() {
            SecurityAlert alert = new SecurityAlert();
            alert.setAlertType("TOKEN_ABUSE");
            alert.setSeverity("HIGH");
            alert.setMessage("JWT token used from multiple locations");

            assertEquals("TOKEN_ABUSE", alert.getAlertType());
            assertEquals("HIGH", alert.getSeverity());
        }
    }

    @Nested
    @DisplayName("toString测试")
    class ToStringTests {

        @Test
        @DisplayName("toString包含所有字段")
        void testToString() {
            SecurityAlert alert = new SecurityAlert(
                    "alert-003",
                    "TEST_ALERT",
                    "LOW",
                    "Test message",
                    "testUser",
                    "127.0.0.1",
                    LocalDateTime.of(2025, 3, 1, 12, 0),
                    false
            );

            String str = alert.toString();

            assertTrue(str.contains("alert-003"));
            assertTrue(str.contains("TEST_ALERT"));
            assertTrue(str.contains("LOW"));
            assertTrue(str.contains("Test message"));
            assertTrue(str.contains("testUser"));
            assertTrue(str.contains("127.0.0.1"));
            assertTrue(str.contains("resolved=false"));
        }
    }
}
