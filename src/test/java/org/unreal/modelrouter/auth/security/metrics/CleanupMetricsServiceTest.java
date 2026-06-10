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

package org.unreal.modelrouter.auth.security.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CleanupMetricsService 测试类
 */
@DisplayName("CleanupMetricsService测试")
class CleanupMetricsServiceTest {

    private CleanupMetricsService cleanupMetricsService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cleanupMetricsService = new CleanupMetricsService(meterRegistry);
    }

    @Nested
    @DisplayName("令牌清理测试")
    class TokenCleanupTests {

        @Test
        @DisplayName("成功完成令牌清理")
        void testFinishTokenCleanupSuccess() {
            Timer.Sample sample = cleanupMetricsService.startTokenCleanup();
            cleanupMetricsService.finishTokenCleanup(sample, 100, true);

            Map<String, Object> stats = cleanupMetricsService.getCleanupStats();
            assertEquals(100L, stats.get("lastTokenCleanupCount"));
            assertEquals(100L, stats.get("totalTokensRemoved"));
        }

        @Test
        @DisplayName("令牌清理失败")
        void testFinishTokenCleanupFailure() {
            Timer.Sample sample = cleanupMetricsService.startTokenCleanup();
            cleanupMetricsService.finishTokenCleanup(sample, 0, false);

            Map<String, Object> stats = cleanupMetricsService.getCleanupStats();
            assertEquals(0L, stats.get("lastTokenCleanupCount"));
        }

        @Test
        @DisplayName("多次令牌清理累计")
        void testMultipleTokenCleanups() {
            Timer.Sample sample1 = cleanupMetricsService.startTokenCleanup();
            cleanupMetricsService.finishTokenCleanup(sample1, 50, true);

            Timer.Sample sample2 = cleanupMetricsService.startTokenCleanup();
            cleanupMetricsService.finishTokenCleanup(sample2, 75, true);

            Map<String, Object> stats = cleanupMetricsService.getCleanupStats();
            assertEquals(75L, stats.get("lastTokenCleanupCount"));
            assertEquals(125L, stats.get("totalTokensRemoved"));
        }

        @Test
        @DisplayName("null sample处理")
        void testNullSample() {
            cleanupMetricsService.finishTokenCleanup(null, 100, true);

            Map<String, Object> stats = cleanupMetricsService.getCleanupStats();
            assertEquals(100L, stats.get("lastTokenCleanupCount"));
        }
    }

    @Nested
    @DisplayName("黑名单清理测试")
    class BlacklistCleanupTests {

        @Test
        @DisplayName("成功完成黑名单清理")
        void testFinishBlacklistCleanupSuccess() {
            Timer.Sample sample = cleanupMetricsService.startBlacklistCleanup();
            cleanupMetricsService.finishBlacklistCleanup(sample, 50, true);

            Map<String, Object> stats = cleanupMetricsService.getCleanupStats();
            assertEquals(50L, stats.get("lastBlacklistCleanupCount"));
            assertEquals(50L, stats.get("totalBlacklistItemsRemoved"));
        }

        @Test
        @DisplayName("黑名单清理失败")
        void testFinishBlacklistCleanupFailure() {
            Timer.Sample sample = cleanupMetricsService.startBlacklistCleanup();
            cleanupMetricsService.finishBlacklistCleanup(sample, 0, false);

            Map<String, Object> stats = cleanupMetricsService.getCleanupStats();
            assertEquals(0L, stats.get("lastBlacklistCleanupCount"));
        }

        @Test
        @DisplayName("多次黑名单清理累计")
        void testMultipleBlacklistCleanups() {
            Timer.Sample sample1 = cleanupMetricsService.startBlacklistCleanup();
            cleanupMetricsService.finishBlacklistCleanup(sample1, 30, true);

            Timer.Sample sample2 = cleanupMetricsService.startBlacklistCleanup();
            cleanupMetricsService.finishBlacklistCleanup(sample2, 45, true);

            Map<String, Object> stats = cleanupMetricsService.getCleanupStats();
            assertEquals(45L, stats.get("lastBlacklistCleanupCount"));
            assertEquals(75L, stats.get("totalBlacklistItemsRemoved"));
        }
    }

    @Nested
    @DisplayName("手动清理测试")
    class ManualCleanupTests {

        @Test
        @DisplayName("记录手动令牌清理")
        void testRecordManualTokenCleanup() {
            cleanupMetricsService.recordManualCleanup("token", 200, Duration.ofMillis(150));

            Map<String, Object> stats = cleanupMetricsService.getCleanupStats();
            assertNotNull(stats);
        }

        @Test
        @DisplayName("记录手动黑名单清理")
        void testRecordManualBlacklistCleanup() {
            cleanupMetricsService.recordManualCleanup("blacklist", 100, Duration.ofMillis(80));

            Map<String, Object> stats = cleanupMetricsService.getCleanupStats();
            assertNotNull(stats);
        }
    }

    @Nested
    @DisplayName("清理统计测试")
    class CleanupStatsTests {

        @Test
        @DisplayName("获取清理统计摘要")
        void testGetCleanupStats() {
            Timer.Sample sample1 = cleanupMetricsService.startTokenCleanup();
            cleanupMetricsService.finishTokenCleanup(sample1, 100, true);

            Timer.Sample sample2 = cleanupMetricsService.startBlacklistCleanup();
            cleanupMetricsService.finishBlacklistCleanup(sample2, 50, true);

            Map<String, Object> stats = cleanupMetricsService.getCleanupStats();

            assertNotNull(stats);
            assertEquals(100L, stats.get("lastTokenCleanupCount"));
            assertEquals(50L, stats.get("lastBlacklistCleanupCount"));
            assertEquals(100L, stats.get("totalTokensRemoved"));
            assertEquals(50L, stats.get("totalBlacklistItemsRemoved"));
            assertNotNull(stats.get("tokenCleanupExecutions"));
            assertNotNull(stats.get("blacklistCleanupExecutions"));
            assertNotNull(stats.get("cleanupFrequency"));
        }

        @Test
        @DisplayName("重置清理统计")
        void testResetCleanupStats() {
            Timer.Sample sample = cleanupMetricsService.startTokenCleanup();
            cleanupMetricsService.finishTokenCleanup(sample, 100, true);

            cleanupMetricsService.resetCleanupStats();

            Map<String, Object> stats = cleanupMetricsService.getCleanupStats();
            assertEquals(0L, stats.get("lastTokenCleanupCount"));
            assertEquals(0L, stats.get("totalTokensRemoved"));
        }
    }

    @Nested
    @DisplayName("健康状态测试")
    class HealthStatusTests {

        @Test
        @DisplayName("新服务健康状态检查")
        void testInitialHealthStatus() {
            // 新服务没有清理记录，超过24小时
            assertFalse(cleanupMetricsService.isCleanupHealthy());
        }

        @Test
        @DisplayName("执行清理后健康状态")
        void testHealthAfterCleanup() {
            Timer.Sample tokenSample = cleanupMetricsService.startTokenCleanup();
            cleanupMetricsService.finishTokenCleanup(tokenSample, 100, true);

            Timer.Sample blacklistSample = cleanupMetricsService.startBlacklistCleanup();
            cleanupMetricsService.finishBlacklistCleanup(blacklistSample, 50, true);

            assertTrue(cleanupMetricsService.isCleanupHealthy());
        }
    }

    @Nested
    @DisplayName("计时器测试")
    class TimerTests {

        @Test
        @DisplayName("启动令牌清理计时")
        void testStartTokenCleanupTimer() {
            Timer.Sample sample = cleanupMetricsService.startTokenCleanup();
            assertNotNull(sample);
        }

        @Test
        @DisplayName("启动黑名单清理计时")
        void testStartBlacklistCleanupTimer() {
            Timer.Sample sample = cleanupMetricsService.startBlacklistCleanup();
            assertNotNull(sample);
        }
    }
}
