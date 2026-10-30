package org.unreal.modelrouter.monitor.tracing.actuator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.unreal.modelrouter.monitor.tracing.async.AsyncTracingProcessor;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.memory.TracingMemoryManager;
import org.unreal.modelrouter.monitor.tracing.memory.model.MemoryPressureLevel;
import org.unreal.modelrouter.monitor.tracing.memory.model.MemoryStats;
import org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceMonitor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TracingHealthIndicator} 的健康判定守护。
 *
 * <p><b>为什么要有这个类（缺陷史）</b>：本指示器此前**零测试覆盖**。它把「span 丢弃率 &gt; 10%」
 * 直接判成 {@code DOWN}，而 dropRate 来自 {@link AsyncTracingProcessor.ProcessingStats} 的
 * {@code processedCount / droppedCount} —— 是**生命周期累计**值，越阈值后只能靠重启归零。
 * 于是实例一旦在启动/压测尖峰里丢够 span，聚合 {@code /actuator/health} 就**永久** 503；
 * 而三份 {@code docker-compose*.yml} 的容器健康检查都是 {@code curl -f /actuator/health}
 * ⇒ 容器被判 unhealthy（重启又不解决丢 span）。实测现场：
 * {@code tracing=DOWN, issue=数据丢弃率过高: 50.44%}，其余组件全 UP。</p>
 *
 * <p>本类把「哪些情况才配 DOWN」钉死：丢 span 属可观测性降级（WARNING、HTTP 仍 200），
 * 只有「异步处理器没在跑」「内存压力 CRITICAL」「性能监控自身 DOWN」才算追踪不可用。</p>
 */
@DisplayName("TracingHealthIndicator 健康判定守护")
class TracingHealthIndicatorTest {

    private TracingConfiguration tracingConfiguration;
    private TracingPerformanceMonitor performanceMonitor;
    private AsyncTracingProcessor asyncTracingProcessor;
    private TracingMemoryManager memoryManager;
    private TracingHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        tracingConfiguration = mock(TracingConfiguration.class);
        performanceMonitor = mock(TracingPerformanceMonitor.class);
        asyncTracingProcessor = mock(AsyncTracingProcessor.class);
        memoryManager = mock(TracingMemoryManager.class);

        when(tracingConfiguration.isEnabled()).thenReturn(true);
        when(tracingConfiguration.getServiceName()).thenReturn("jairouter");

        TracingConfiguration.OpenTelemetryConfig otel = mock(TracingConfiguration.OpenTelemetryConfig.class);
        TracingConfiguration.OpenTelemetryConfig.SdkConfig sdk =
                mock(TracingConfiguration.OpenTelemetryConfig.SdkConfig.class);
        when(otel.isEnabled()).thenReturn(true);
        when(otel.getSdk()).thenReturn(sdk);
        when(tracingConfiguration.getOpenTelemetry()).thenReturn(otel);

        TracingConfiguration.ExporterConfig exporter = mock(TracingConfiguration.ExporterConfig.class);
        TracingConfiguration.ExporterConfig.LoggingExporterConfig logging =
                mock(TracingConfiguration.ExporterConfig.LoggingExporterConfig.class);
        when(exporter.getType()).thenReturn("logging");
        when(exporter.getLogging()).thenReturn(logging);
        when(tracingConfiguration.getExporter()).thenReturn(exporter);

        when(performanceMonitor.health()).thenReturn(Health.up().withDetail("systemHealth", "HEALTHY").build());
        when(memoryManager.getMemoryStats()).thenReturn(memory(0L, 1000L, MemoryPressureLevel.LOW));
        when(asyncTracingProcessor.getProcessingStats()).thenReturn(stats(1000L, 0L, true));

        indicator = new TracingHealthIndicator(tracingConfiguration, performanceMonitor,
                asyncTracingProcessor, memoryManager);
    }

    private MemoryStats memory(final long used, final long max, final MemoryPressureLevel level) {
        return new MemoryStats(used, max, used, 0, 0, 0L, 0L, 0L, 0L, level);
    }

    private AsyncTracingProcessor.ProcessingStats stats(final long processed, final long dropped,
                                                        final boolean running) {
        return new AsyncTracingProcessor.ProcessingStats(processed, dropped, 0L, 0L, 0, running);
    }

    @Nested
    @DisplayName("丢弃率：降级而非不可用")
    class DropRateTests {

        @Test
        @DisplayName("DROP-001: 丢弃率超阈值**不得** DOWN（否则 /actuator/health 永久 503）")
        void highDropRateIsNotDown() {
            // 现场复现值：丢 4222 / 处理 4149 ≈ 50.4%
            when(asyncTracingProcessor.getProcessingStats()).thenReturn(stats(4149L, 4222L, true));

            Health health = indicator.health();

            assertNotEquals(Status.DOWN, health.getStatus(),
                    "丢 span 是可观测性降级，不是服务不可用；判 DOWN 会让容器永久 unhealthy");
            assertNotEquals("DOWN", health.getStatus().getCode());
        }

        @Test
        @DisplayName("DROP-002: 丢弃率超阈值时数值仍完整可见（供告警规则使用）")
        void highDropRateKeepsNumbersVisible() {
            when(asyncTracingProcessor.getProcessingStats()).thenReturn(stats(4149L, 4222L, true));

            Health health = indicator.health();

            @SuppressWarnings("unchecked")
            Map<String, Object> processor = (Map<String, Object>) health.getDetails().get("asyncProcessor");
            assertNotNull(processor, "details 必须保留异步处理器统计");
            assertEquals(4222L, processor.get("droppedCount"));
            assertEquals(4149L, processor.get("processedCount"));

            Object warnings = health.getDetails().get("warnings");
            assertTrue(warnings instanceof List<?> && !((List<?>) warnings).isEmpty(),
                    "应把「丢弃率过高」记为 warning，而不是沉默丢掉：" + warnings);
        }

        @Test
        @DisplayName("DROP-003: 丢弃率正常时为 UP")
        void lowDropRateIsUp() {
            when(asyncTracingProcessor.getProcessingStats()).thenReturn(stats(1000L, 1L, true));

            assertEquals(Status.UP, indicator.health().getStatus());
        }
    }

    @Nested
    @DisplayName("真正不可用的情形仍须 DOWN")
    class DownTests {

        @Test
        @DisplayName("DOWN-001: 异步处理器未运行时 DOWN")
        void processorNotRunningIsDown() {
            when(asyncTracingProcessor.getProcessingStats()).thenReturn(stats(10L, 0L, false));

            Health health = indicator.health();

            assertEquals(Status.DOWN, health.getStatus());
            assertEquals("异步处理器未运行", health.getDetails().get("issue"));
        }

        @Test
        @DisplayName("DOWN-002: 内存压力 CRITICAL 时 DOWN")
        void criticalMemoryIsDown() {
            when(memoryManager.getMemoryStats()).thenReturn(memory(999L, 1000L, MemoryPressureLevel.CRITICAL));

            assertEquals(Status.DOWN, indicator.health().getStatus());
        }

        @Test
        @DisplayName("DOWN-003: 性能监控自身 DOWN 时 DOWN")
        void performanceMonitorDownIsDown() {
            when(performanceMonitor.health()).thenReturn(Health.down().withDetail("x", "y").build());

            assertEquals(Status.DOWN, indicator.health().getStatus());
        }

        @Test
        @DisplayName("DOWN-004: 多条 DOWN 级问题**同时**呈现，不得被后一条覆盖丢失")
        void multipleDownIssuesAreNotLost() {
            // 原实现是 `healthBuilder = Health.down()...` 逐条覆盖：
            // 「处理器未运行」会被随后「内存压力严重」改写掉，issue 只剩后一条。
            when(asyncTracingProcessor.getProcessingStats()).thenReturn(stats(10L, 0L, false));
            when(memoryManager.getMemoryStats()).thenReturn(memory(999L, 1000L, MemoryPressureLevel.CRITICAL));
            when(performanceMonitor.health()).thenReturn(Health.down().build());

            Health health = indicator.health();

            assertEquals(Status.DOWN, health.getStatus());
            @SuppressWarnings("unchecked")
            List<String> issues = (List<String>) health.getDetails().get("downIssues");
            assertNotNull(issues);
            assertEquals(3, issues.size(), "三条 DOWN 级问题都应保留，实际=" + issues);
            assertTrue(issues.contains("异步处理器未运行"), "不得丢失「异步处理器未运行」");
        }
    }

    @Nested
    @DisplayName("追踪被禁用")
    class DisabledTests {

        @Test
        @DisplayName("OFF-001: 追踪被显式禁用时 UP（#849：关闭追踪是合法运行态，不得拉垮存活探针）")
        void tracingDisabledIsUp() {
            when(tracingConfiguration.isEnabled()).thenReturn(false);

            Health health = indicator.health();

            // 旧断言是 DOWN。实测证据（#849）：Dockerfile / Dockerfile.china 的 HEALTHCHECK
            // 打的是聚合 /actuator/health，DOWN ⇒ 容器被判 unhealthy；docker-compose 默认
            // restart 策略下会反复重启，而重启并不解决「追踪被关掉」。追踪是可选组件，
            // 关掉它只应体现在 details 里，不应影响存活判定。
            assertEquals(Status.UP, health.getStatus());
            assertEquals("追踪系统已禁用（可选组件，不影响请求处理）", health.getDetails().get("reason"));
        }
    }
}
