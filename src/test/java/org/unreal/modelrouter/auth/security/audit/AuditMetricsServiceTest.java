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
    }

    @Nested
    @DisplayName("存储失败记录测试")
    class StorageFailureTests {

        @Test
        @DisplayName("记录主存储失败")
        void testRecordPrimaryStorageFailure() {
            metricsService.recordPrimaryStorageFailure();
            metricsService.recordPrimaryStorageFailure();

            Map<String, Object> summary = metricsService.getMetricsSummary();
            assertEquals(2L, summary.get("primaryStorageFailures"));
        }

        @Test
        @DisplayName("记录备用存储成功")
        void testRecordFallbackStorageSuccess() {
            metricsService.recordFallbackStorageSuccess();
            metricsService.recordFallbackStorageSuccess();
            metricsService.recordFallbackStorageSuccess();

            Map<String, Object> summary = metricsService.getMetricsSummary();
            assertEquals(3L, summary.get("fallbackStorageSuccesses"));
        }
    }

    @Nested
    @DisplayName("缓冲区测试")
    class BufferTests {

        @Test
        @DisplayName("更新缓冲区大小")
        void testUpdateBufferSize() {
            metricsService.updateBufferSize(50);

            Map<String, Object> summary = metricsService.getMetricsSummary();
            assertEquals(50L, summary.get("bufferSize"));
        }

        @Test
        @DisplayName("多次更新缓冲区大小")
        void testMultipleBufferUpdates() {
            metricsService.updateBufferSize(10);
            metricsService.updateBufferSize(20);
            metricsService.updateBufferSize(30);

            Map<String, Object> summary = metricsService.getMetricsSummary();
            assertEquals(30L, summary.get("bufferSize"));
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

            // 验证计时器已注册
            assertNotNull(meterRegistry.find("jairouter_audit_event_write_duration_seconds").timer());
        }
    }

    @Nested
    @DisplayName("健康分数测试")
    class HealthScoreTests {

        @Test
        @DisplayName("初始健康分数为100")
        void testInitialHealthScore() {
            Map<String, Object> summary = metricsService.getMetricsSummary();
            assertEquals(100L, summary.get("healthScore"));
        }

        @Test
        @DisplayName("主存储失败降低健康分数")
        void testHealthScoreDecrease() {
            // 记录一些事件
            metricsService.recordEvent("JWT_TOKEN_ISSUED", true);
            metricsService.recordEvent("JWT_TOKEN_ISSUED", true);
            metricsService.recordEvent("JWT_TOKEN_ISSUED", true);
            metricsService.recordEvent("JWT_TOKEN_ISSUED", true);

            // 记录一次主存储失败
            metricsService.recordPrimaryStorageFailure();

            Map<String, Object> summary = metricsService.getMetricsSummary();
            // 健康分数应该降低 (4个事件，1个失败 = 75%)
            assertEquals(75L, summary.get("healthScore"));
        }

        @Test
        @DisplayName("无事件时健康分数保持100")
        void testHealthScoreNoEvents() {
            // 只记录失败，没有事件
            metricsService.recordPrimaryStorageFailure();

            Map<String, Object> summary = metricsService.getMetricsSummary();
            // 无事件时健康分数为100
            assertEquals(100L, summary.get("healthScore"));
        }
    }

    @Nested
    @DisplayName("重置指标测试")
    class ResetMetricsTests {

        @Test
        @DisplayName("重置所有指标")
        void testResetMetrics() {
            // 记录一些数据
            metricsService.recordEvent("JWT_TOKEN_ISSUED", true);
            metricsService.recordEvent("API_KEY_USED", false);
            metricsService.recordPrimaryStorageFailure();
            metricsService.recordFallbackStorageSuccess();
            metricsService.updateBufferSize(50);

            // 重置
            metricsService.resetMetrics();

            Map<String, Object> summary = metricsService.getMetricsSummary();
            assertEquals(0L, summary.get("totalEvents"));
            assertEquals(0L, summary.get("primaryStorageFailures"));
            assertEquals(0L, summary.get("fallbackStorageSuccesses"));
            assertEquals(0L, summary.get("bufferSize"));
            assertEquals(100L, summary.get("healthScore"));
        }
    }

    @Nested
    @DisplayName("getMetricsSummary测试")
    class GetMetricsSummaryTests {

        @Test
        @DisplayName("获取完整指标摘要")
        void testGetMetricsSummary() {
            metricsService.recordEvent("JWT_TOKEN_ISSUED", true);
            metricsService.recordEvent("API_KEY_USED", true);
            metricsService.recordPrimaryStorageFailure();
            metricsService.updateBufferSize(25);

            Map<String, Object> summary = metricsService.getMetricsSummary();

            assertNotNull(summary);
            assertEquals(2L, summary.get("totalEvents"));
            assertEquals(1L, summary.get("primaryStorageFailures"));
            assertEquals(0L, summary.get("fallbackStorageSuccesses"));
            assertEquals(25L, summary.get("bufferSize"));
            assertTrue(summary.containsKey("healthScore"));
            assertTrue(summary.containsKey("eventTypeStatistics"));
        }
    }
}
