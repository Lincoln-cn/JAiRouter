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

package org.unreal.modelrouter.auth.security.audit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuditMetricsService 测试类
 *
 * <p>issue #396：只对已删除的「内存缓冲 / 回退通道」有意义的指标（`primary_storage_failures`、
 * `fallback_events`、`buffer_size`、`health_score`）连同其用例一并删除；本类改为断言**保留**的指标
 * （总数/成功/失败/写入耗时/总事件 Gauge）确实被写入。</p>
 */
@DisplayName("AuditMetricsService测试")
class AuditMetricsServiceTest {

    private AuditMetricsService metricsService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new AuditMetricsService(meterRegistry);
        metricsService.initMetrics();
    }

    @Nested
    @DisplayName("recordEvent测试")
    class RecordEventTests {

        @Test
        @DisplayName("记录成功事件")
        void testRecordSuccessEvent() {
            metricsService.recordEvent("JWT_TOKEN_ISSUED", true);

            Map<String, Object> summary = metricsService.getMetricsSummary();
            assertEquals(1L, summary.get("totalEvents"));
        }

        @Test
        @DisplayName("记录失败事件")
        void testRecordFailureEvent() {
            metricsService.recordEvent("AUTHENTICATION_FAILED", false);

            Map<String, Object> summary = metricsService.getMetricsSummary();
            assertEquals(1L, summary.get("totalEvents"));
        }

        @Test
        @DisplayName("多次记录事件")
        void testMultipleEvents() {
            metricsService.recordEvent("JWT_TOKEN_ISSUED", true);
            metricsService.recordEvent("JWT_TOKEN_REFRESHED", true);
            metricsService.recordEvent("AUTHENTICATION_FAILED", false);
            metricsService.recordEvent("API_KEY_USED", true);

            Map<String, Object> summary = metricsService.getMetricsSummary();
            assertEquals(4L, summary.get("totalEvents"));
        }

        @Test
        @DisplayName("按事件类型统计")
        void testEventTypeStatistics() {
            metricsService.recordEvent("JWT_TOKEN_ISSUED", true);
            metricsService.recordEvent("JWT_TOKEN_ISSUED", true);
            metricsService.recordEvent("API_KEY_USED", true);

            Map<String, Object> summary = metricsService.getMetricsSummary();
            @SuppressWarnings("unchecked")
            Map<String, Long> eventTypeStats = (Map<String, Long>) summary.get("eventTypeStatistics");

            assertNotNull(eventTypeStats);
            assertEquals(2L, eventTypeStats.get("JWT_TOKEN_ISSUED"));
            assertEquals(1L, eventTypeStats.get("API_KEY_USED"));
        }

        @Test
        @DisplayName("成功/失败分流到各自的 Micrometer 计数器（issue #396 保留的指标面）")
        void testCountersSplitByOutcome() {
            metricsService.recordEvent("JWT_TOKEN_ISSUED", true);
            metricsService.recordEvent("API_KEY_USED", true);
            metricsService.recordEvent("AUTHENTICATION_FAILED", false);

            assertEquals(3.0d, meterRegistry.get("jairouter_audit_events_total").counter().count(), 0.0001d);
            assertEquals(2.0d, meterRegistry.get("jairouter_audit_events_success_total").counter().count(), 0.0001d);
            assertEquals(1.0d, meterRegistry.get("jairouter_audit_events_failure_total").counter().count(), 0.0001d);
        }

        @Test
        @DisplayName("总事件 Gauge 反映累计事件数")
        void testTotalEventsGauge() {
            metricsService.recordEvent("JWT_TOKEN_ISSUED", true);
            metricsService.recordEvent("API_KEY_USED", true);

            assertEquals(2.0d, meterRegistry.get("jairouter_audit_total_events").gauge().value(), 0.0001d);
        }
    }

    @Nested
    @DisplayName("写入耗时测试")
    class WriteDurationTests {

        @Test
        @DisplayName("记录写入耗时")
        void testRecordWriteDuration() {
            metricsService.recordWriteDuration(100);
            metricsService.recordWriteDuration(200);
            metricsService.recordWriteDuration(150);

            assertNotNull(meterRegistry.find("jairouter_audit_event_write_duration_seconds").timer());
            assertEquals(3L, meterRegistry.find("jairouter_audit_event_write_duration_seconds").timer().count());
        }
    }

    @Nested
    @DisplayName("重置指标测试")
    class ResetMetricsTests {

        @Test
        @DisplayName("重置内存计数与按类型统计（Micrometer 计数器本身不可重置）")
        void testResetMetrics() {
            metricsService.recordEvent("JWT_TOKEN_ISSUED", true);
            metricsService.recordEvent("API_KEY_USED", false);

            metricsService.resetMetrics();

            Map<String, Object> summary = metricsService.getMetricsSummary();
            assertEquals(0L, summary.get("totalEvents"));
            @SuppressWarnings("unchecked")
            Map<String, Long> eventTypeStats = (Map<String, Long>) summary.get("eventTypeStatistics");
            assertTrue(eventTypeStats.isEmpty(), "重置后按类型统计应为空");
        }
    }

    @Nested
    @DisplayName("getMetricsSummary测试")
    class GetMetricsSummaryTests {

        @Test
        @DisplayName("摘要只含保留的键（totalEvents + eventTypeStatistics）")
        void testGetMetricsSummary() {
            metricsService.recordEvent("JWT_TOKEN_ISSUED", true);
            metricsService.recordEvent("API_KEY_USED", true);

            Map<String, Object> summary = metricsService.getMetricsSummary();

            assertNotNull(summary);
            assertEquals(2L, summary.get("totalEvents"));
            assertTrue(summary.containsKey("eventTypeStatistics"));
            // 已删除的键不得再出现（issue #396）
            assertFalse(summary.containsKey("primaryStorageFailures"));
            assertFalse(summary.containsKey("fallbackStorageSuccesses"));
            assertFalse(summary.containsKey("bufferSize"));
            assertFalse(summary.containsKey("healthScore"));
        }
    }
}
