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

package org.unreal.modelrouter.monitor.tracing.logger.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityEventFields测试
 */
@DisplayName("SecurityEventFields测试")
class SecurityEventFieldsTest {

    @Test
    @DisplayName("测试Builder构建")
    void testBuilder() {
        SecurityEventFields fields = SecurityEventFields.builder()
                .event("authentication_success")
                .user("admin")
                .ip("192.168.1.100")
                .authMethod("JWT")
                .success(true)
                .build();

        assertEquals("authentication_success", fields.getEvent());
        assertEquals("admin", fields.getUser());
        assertEquals("192.168.1.100", fields.getIp());
        assertEquals("JWT", fields.getAuthMethod());
        assertTrue(fields.getSuccess());
    }

    @Test
    @DisplayName("测试无参构造函数")
    void testNoArgsConstructor() {
        SecurityEventFields fields = new SecurityEventFields();
        assertNull(fields.getEvent());
        assertNull(fields.getUser());
        assertNull(fields.getIp());
    }

    @Test
    @DisplayName("测试全参构造函数")
    void testAllArgsConstructor() {
        Map<String, Object> details = Map.of("key", "value");
        SecurityEventFields fields = new SecurityEventFields(
                "event", "user", "ip", "method", true,
                "field", "action", "ruleId", "configType", details
        );

        assertEquals("event", fields.getEvent());
        assertEquals("user", fields.getUser());
        assertEquals("ip", fields.getIp());
        assertEquals("method", fields.getAuthMethod());
        assertTrue(fields.getSuccess());
        assertEquals("field", fields.getField());
        assertEquals("action", fields.getAction());
        assertEquals("ruleId", fields.getRuleId());
        assertEquals("configType", fields.getConfigType());
        assertEquals(details, fields.getDetails());
    }

    @Test
    @DisplayName("测试getFieldType方法")
    void testGetFieldType() {
        SecurityEventFields fields = new SecurityEventFields();
        assertEquals("security", fields.getFieldType());
    }

    @Nested
    @DisplayName("forAuthentication静态工厂方法测试")
    class ForAuthenticationTests {

        @Test
        @DisplayName("成功认证事件")
        void testSuccessAuthentication() {
            SecurityEventFields fields = SecurityEventFields.forAuthentication(
                    true, "JWT", "admin", "192.168.1.100"
            );

            assertEquals("authentication_success", fields.getEvent());
            assertEquals("admin", fields.getUser());
            assertEquals("192.168.1.100", fields.getIp());
            assertEquals("JWT", fields.getAuthMethod());
            assertTrue(fields.getSuccess());
        }

        @Test
        @DisplayName("失败认证事件")
        void testFailureAuthentication() {
            SecurityEventFields fields = SecurityEventFields.forAuthentication(
                    false, "API_KEY", "user1", "10.0.0.1"
            );

            assertEquals("authentication_failure", fields.getEvent());
            assertEquals("user1", fields.getUser());
            assertEquals("10.0.0.1", fields.getIp());
            assertEquals("API_KEY", fields.getAuthMethod());
            assertFalse(fields.getSuccess());
        }
    }

    @Nested
    @DisplayName("forSanitization静态工厂方法测试")
    class ForSanitizationTests {

        @Test
        @DisplayName("数据脱敏事件")
        void testSanitizationEvent() {
            SecurityEventFields fields = SecurityEventFields.forSanitization(
                    "password", "mask", "rule-001"
            );

            assertEquals("data_sanitization", fields.getEvent());
            assertEquals("password", fields.getField());
            assertEquals("mask", fields.getAction());
            assertEquals("rule-001", fields.getRuleId());
        }
    }

    @Nested
    @DisplayName("forConfigurationChange静态工厂方法测试")
    class ForConfigurationChangeTests {

        @Test
        @DisplayName("配置变更事件")
        void testConfigurationChangeEvent() {
            Map<String, Object> details = Map.of(
                    "oldValue", "config1",
                    "newValue", "config2"
            );

            SecurityEventFields fields = SecurityEventFields.forConfigurationChange(
                    "security", "update", details
            );

            assertEquals("configuration_change", fields.getEvent());
            assertEquals("security", fields.getConfigType());
            assertEquals("update", fields.getAction());
            assertEquals(details, fields.getDetails());
        }
    }

    @Test
    @DisplayName("测试Setter方法")
    void testSetters() {
        SecurityEventFields fields = new SecurityEventFields();
        Map<String, Object> details = Map.of("test", "data");

        fields.setEvent("test_event");
        fields.setUser("testuser");
        fields.setIp("127.0.0.1");
        fields.setAuthMethod("OAUTH");
        fields.setSuccess(false);
        fields.setField("email");
        fields.setAction("encrypt");
        fields.setRuleId("rule-002");
        fields.setConfigType("database");
        fields.setDetails(details);

        assertEquals("test_event", fields.getEvent());
        assertEquals("testuser", fields.getUser());
        assertEquals("127.0.0.1", fields.getIp());
        assertEquals("OAUTH", fields.getAuthMethod());
        assertFalse(fields.getSuccess());
        assertEquals("email", fields.getField());
        assertEquals("encrypt", fields.getAction());
        assertEquals("rule-002", fields.getRuleId());
        assertEquals("database", fields.getConfigType());
        assertEquals(details, fields.getDetails());
    }
}
