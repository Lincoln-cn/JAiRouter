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
 * PerformanceFields测试
 */
@DisplayName("PerformanceFields测试")
class PerformanceFieldsTest {

    @Test
    @DisplayName("测试Builder构建")
    void testBuilder() {
        Map<String, Object> metrics = Map.of("count", 10, "avgTime", 50.5);
        PerformanceFields fields = PerformanceFields.builder()
                .operation("database_query")
                .duration(150L)
                .durationMs(150L)
                .metrics(metrics)
                .threshold(100L)
                .slowQueryDetected(true)
                .exceededBy(50L)
                .build();

        assertEquals("database_query", fields.getOperation());
        assertEquals(150L, fields.getDuration());
        assertEquals(150L, fields.getDurationMs());
        assertEquals(metrics, fields.getMetrics());
        assertEquals(100L, fields.getThreshold());
        assertTrue(fields.getSlowQueryDetected());
        assertEquals(50L, fields.getExceededBy());
    }

    @Test
    @DisplayName("测试无参构造函数")
    void testNoArgsConstructor() {
        PerformanceFields fields = new PerformanceFields();
        assertNull(fields.getOperation());
        assertNull(fields.getDuration());
        assertNull(fields.getDurationMs());
        assertNull(fields.getMetrics());
    }

    @Test
    @DisplayName("测试全参构造函数")
    void testAllArgsConstructor() {
        Map<String, Object> metrics = Map.of("key", "value");
        PerformanceFields fields = new PerformanceFields(
                "test_op", 200L, 200L, metrics, 100L, false, 0L
        );

        assertEquals("test_op", fields.getOperation());
        assertEquals(200L, fields.getDuration());
        assertEquals(200L, fields.getDurationMs());
        assertEquals(metrics, fields.getMetrics());
        assertEquals(100L, fields.getThreshold());
        assertFalse(fields.getSlowQueryDetected());
        assertEquals(0L, fields.getExceededBy());
    }

    @Test
    @DisplayName("测试getFieldType方法")
    void testGetFieldType() {
        PerformanceFields fields = new PerformanceFields();
        assertEquals("performance", fields.getFieldType());
    }

    @Nested
    @DisplayName("forPerformance静态工厂方法测试")
    class ForPerformanceTests {

        @Test
        @DisplayName("带指标的性能日志")
        void testWithMetrics() {
            Map<String, Object> metrics = Map.of(
                    "cpuUsage", 75.5,
                    "memoryUsed", 1024L
            );

            PerformanceFields fields = PerformanceFields.forPerformance(
                    "api_call", 250, metrics
            );

            assertEquals("api_call", fields.getOperation());
            assertEquals(250L, fields.getDuration());
            assertEquals(250L, fields.getDurationMs());
            assertEquals(metrics, fields.getMetrics());
            assertNull(fields.getThreshold());
            assertNull(fields.getSlowQueryDetected());
        }

        @Test
        @DisplayName("无指标的性能日志")
        void testWithoutMetrics() {
            PerformanceFields fields = PerformanceFields.forPerformance(
                    "cache_lookup", 10, null
            );

            assertEquals("cache_lookup", fields.getOperation());
            assertEquals(10L, fields.getDuration());
            assertNull(fields.getMetrics());
        }
    }

    @Nested
    @DisplayName("forSlowQuery静态工厂方法测试")
    class ForSlowQueryTests {

        @Test
        @DisplayName("慢查询日志")
        void testSlowQuery() {
            PerformanceFields fields = PerformanceFields.forSlowQuery(
                    "select_users", 500, 200
            );

            assertEquals("select_users", fields.getOperation());
            assertEquals(500L, fields.getDuration());
            assertEquals(500L, fields.getDurationMs());
            assertEquals(200L, fields.getThreshold());
            assertTrue(fields.getSlowQueryDetected());
            assertEquals(300L, fields.getExceededBy());
        }

        @Test
        @DisplayName("刚好等于阈值的查询")
        void testQueryAtThreshold() {
            PerformanceFields fields = PerformanceFields.forSlowQuery(
                    "exact_threshold", 100, 100
            );

            assertEquals(100L, fields.getDuration());
            assertEquals(100L, fields.getThreshold());
            assertTrue(fields.getSlowQueryDetected());
            assertEquals(0L, fields.getExceededBy());
        }
    }

    @Nested
    @DisplayName("isSlowQuery方法测试")
    class IsSlowQueryTests {

        @Test
        @DisplayName("超过阈值 - 是慢查询")
        void testIsSlowQueryTrue() {
            PerformanceFields fields = PerformanceFields.builder()
                    .duration(150L)
                    .threshold(100L)
                    .build();

            assertTrue(fields.isSlowQuery());
        }

        @Test
        @DisplayName("等于阈值 - 不是慢查询")
        void testIsSlowQueryFalse_EqualThreshold() {
            PerformanceFields fields = PerformanceFields.builder()
                    .duration(100L)
                    .threshold(100L)
                    .build();

            assertFalse(fields.isSlowQuery());
        }

        @Test
        @DisplayName("低于阈值 - 不是慢查询")
        void testIsSlowQueryFalse_BelowThreshold() {
            PerformanceFields fields = PerformanceFields.builder()
                    .duration(50L)
                    .threshold(100L)
                    .build();

            assertFalse(fields.isSlowQuery());
        }

        @Test
        @DisplayName("无阈值 - 不是慢查询")
        void testIsSlowQueryFalse_NoThreshold() {
            PerformanceFields fields = PerformanceFields.builder()
                    .duration(150L)
                    .build();

            assertFalse(fields.isSlowQuery());
        }

        @Test
        @DisplayName("无持续时间 - 不是慢查询")
        void testIsSlowQueryFalse_NoDuration() {
            PerformanceFields fields = PerformanceFields.builder()
                    .threshold(100L)
                    .build();

            assertFalse(fields.isSlowQuery());
        }
    }

    @Test
    @DisplayName("测试Setter方法")
    void testSetters() {
        PerformanceFields fields = new PerformanceFields();
        Map<String, Object> metrics = Map.of("requests", 100);

        fields.setOperation("new_operation");
        fields.setDuration(300L);
        fields.setDurationMs(300L);
        fields.setMetrics(metrics);
        fields.setThreshold(250L);
        fields.setSlowQueryDetected(true);
        fields.setExceededBy(50L);

        assertEquals("new_operation", fields.getOperation());
        assertEquals(300L, fields.getDuration());
        assertEquals(300L, fields.getDurationMs());
        assertEquals(metrics, fields.getMetrics());
        assertEquals(250L, fields.getThreshold());
        assertTrue(fields.getSlowQueryDetected());
        assertEquals(50L, fields.getExceededBy());
    }
}
