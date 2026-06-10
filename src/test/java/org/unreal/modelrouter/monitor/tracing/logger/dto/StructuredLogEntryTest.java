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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StructuredLogEntry测试
 */
@DisplayName("StructuredLogEntry测试")
class StructuredLogEntryTest {

    @Test
    @DisplayName("测试Builder构建")
    void testBuilder() {
        Instant now = Instant.now();
        SecurityEventFields fields = SecurityEventFields.builder()
                .event("authentication_success")
                .user("admin")
                .build();

        StructuredLogEntry entry = StructuredLogEntry.builder()
                .timestamp(now)
                .type(LogType.SECURITY)
                .serviceName("jairouter")
                .serviceVersion("2.16.4")
                .environment("production")
                .traceId("trace-123")
                .spanId("span-456")
                .fields(fields)
                .message("Authentication successful")
                .level("INFO")
                .build();

        assertEquals(now, entry.getTimestamp());
        assertEquals(LogType.SECURITY, entry.getType());
        assertEquals("jairouter", entry.getServiceName());
        assertEquals("2.16.4", entry.getServiceVersion());
        assertEquals("production", entry.getEnvironment());
        assertEquals("trace-123", entry.getTraceId());
        assertEquals("span-456", entry.getSpanId());
        assertEquals(fields, entry.getFields());
        assertEquals("Authentication successful", entry.getMessage());
        assertEquals("INFO", entry.getLevel());
    }

    @Test
    @DisplayName("测试无参构造函数")
    void testNoArgsConstructor() {
        StructuredLogEntry entry = new StructuredLogEntry();
        assertNull(entry.getTimestamp());
        assertNull(entry.getType());
        assertNull(entry.getServiceName());
        assertNull(entry.getTraceId());
        assertNull(entry.getFields());
    }

    @Test
    @DisplayName("测试全参构造函数")
    void testAllArgsConstructor() {
        Instant now = Instant.now();
        PerformanceFields fields = PerformanceFields.builder()
                .operation("test")
                .duration(100L)
                .build();

        StructuredLogEntry entry = new StructuredLogEntry(
                now, LogType.PERFORMANCE, "service", "1.0", "dev",
                "trace-1", "span-1", fields, "Test message", "DEBUG"
        );

        assertEquals(now, entry.getTimestamp());
        assertEquals(LogType.PERFORMANCE, entry.getType());
        assertEquals("service", entry.getServiceName());
        assertEquals("1.0", entry.getServiceVersion());
        assertEquals("dev", entry.getEnvironment());
    }

    @Test
    @DisplayName("测试getTypeValue方法")
    void testGetTypeValue() {
        StructuredLogEntry entry = StructuredLogEntry.builder()
                .type(LogType.REQUEST)
                .build();

        assertEquals("request", entry.getTypeValue());

        // 测试type为null的情况
        StructuredLogEntry nullTypeEntry = StructuredLogEntry.builder().build();
        assertNull(nullTypeEntry.getTypeValue());
    }

    @Test
    @DisplayName("测试setTypeFromValue方法")
    void testSetTypeFromValue() {
        StructuredLogEntry entry = new StructuredLogEntry();
        entry.setTypeFromValue("error");
        assertEquals(LogType.ERROR, entry.getType());

        // 测试无效值
        entry.setTypeFromValue("invalid");
        assertNull(entry.getType());

        // 测试null值
        entry.setTypeFromValue(null);
        assertNull(entry.getType());
    }

    @Test
    @DisplayName("测试Setter方法")
    void testSetters() {
        StructuredLogEntry entry = new StructuredLogEntry();
        Instant now = Instant.now();

        entry.setTimestamp(now);
        entry.setType(LogType.BUSINESS_EVENT);
        entry.setServiceName("test-service");
        entry.setServiceVersion("2.0");
        entry.setEnvironment("staging");
        entry.setTraceId("trace-789");
        entry.setSpanId("span-789");
        entry.setMessage("Test");
        entry.setLevel("WARN");

        assertEquals(now, entry.getTimestamp());
        assertEquals(LogType.BUSINESS_EVENT, entry.getType());
        assertEquals("test-service", entry.getServiceName());
        assertEquals("2.0", entry.getServiceVersion());
        assertEquals("staging", entry.getEnvironment());
        assertEquals("trace-789", entry.getTraceId());
        assertEquals("span-789", entry.getSpanId());
        assertEquals("Test", entry.getMessage());
        assertEquals("WARN", entry.getLevel());
    }

    @Test
    @DisplayName("测试不同类型的Fields")
    void testDifferentFieldTypes() {
        // SecurityEventFields
        SecurityEventFields securityFields = SecurityEventFields.builder()
                .event("test")
                .build();
        StructuredLogEntry securityEntry = StructuredLogEntry.builder()
                .type(LogType.SECURITY)
                .fields(securityFields)
                .build();
        assertEquals("security", securityEntry.getFields().getFieldType());

        // PerformanceFields
        PerformanceFields perfFields = PerformanceFields.builder()
                .operation("test")
                .build();
        StructuredLogEntry perfEntry = StructuredLogEntry.builder()
                .type(LogType.PERFORMANCE)
                .fields(perfFields)
                .build();
        assertEquals("performance", perfEntry.getFields().getFieldType());
    }
}
