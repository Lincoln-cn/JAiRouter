package org.unreal.modelrouter.monitor.tracing.performance;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitor.tracing.async.AsyncTracingProcessor;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.memory.TracingMemoryManager;
import org.unreal.modelrouter.monitor.tracing.memory.model.MemoryStats;
import org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceModels.BottleneckType;
import org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceModels.PerformanceBottleneck;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 监控配置的接线守护（issue #224）。
 *
 * <p>这三个键在 #224 复核后从「绑定得上但没人读」改成真正接线，本测试钉住「改了配置，判定就变」——
 * 否则接线又会悄悄退化成骗人配置。</p>
 */
@DisplayName("监控配置接线")
class TracingMonitoringWiringTest {

    /** usedHeap / maxHeap = 1 / 2 = 0.5 */
    private static final MemoryStats HALF_HEAP =
            new MemoryStats(1L, 2L, 0L, 0, 0, 0L, 0L, 0L, 0L, null);

    private static TracingPerformanceMonitor monitor(final TracingConfiguration configuration,
                                                     final MeterRegistry registry) {
        final AsyncTracingProcessor asyncProcessor = mock(AsyncTracingProcessor.class);
        when(asyncProcessor.getProcessingStats())
                .thenReturn(new AsyncTracingProcessor.ProcessingStats(100L, 0L, 0L, 0L, 0, true));

        final TracingMemoryManager memoryManager = mock(TracingMemoryManager.class);
        when(memoryManager.getMemoryStats()).thenReturn(HALF_HEAP);

        return new TracingPerformanceMonitor(configuration, asyncProcessor, memoryManager, registry);
    }

    private static TracingPerformanceMonitor monitor(final TracingConfiguration configuration) {
        return monitor(configuration, new SimpleMeterRegistry());
    }

    private static List<PerformanceBottleneck> bottlenecks(final TracingPerformanceMonitor monitor) {
        return monitor.detectBottlenecks().block();
    }

    @Test
    @DisplayName("alerts.thresholds.memory-usage 决定是否判内存瓶颈")
    void memoryUsageThresholdDrivesMemoryBottleneck() {
        final TracingConfiguration configuration = new TracingConfiguration();

        configuration.getMonitoring().getAlerts().getThresholds().setMemoryUsage(0.4);
        assertTrue(hasMemoryBottleneck(bottlenecks(monitor(configuration))),
                "堆使用率 0.5 > 阈值 0.4，应判为内存瓶颈");

        configuration.getMonitoring().getAlerts().getThresholds().setMemoryUsage(0.6);
        assertFalse(hasMemoryBottleneck(bottlenecks(monitor(configuration))),
                "堆使用率 0.5 < 阈值 0.6，不应判为内存瓶颈");
    }

    @Test
    @DisplayName("alerts.thresholds.export-latency-p99 决定 trace.export 是否判操作瓶颈")
    void exportLatencyThresholdDrivesOperationBottleneck() {
        final TracingConfiguration configuration = new TracingConfiguration();

        // OperationMetrics.getP99Latency() = 平均延迟 × 1.5，记录 200ms ⇒ P99 = 300ms
        configuration.getMonitoring().getAlerts().getThresholds().setExportLatencyP99(100L);
        final TracingPerformanceMonitor tight = monitor(configuration);
        tight.recordOperationPerformance("trace.export", 0L, 200L, true, Map.of()).block();
        assertTrue(hasOperationBottleneck(bottlenecks(tight), "trace.export"),
                "P99 300ms > 阈值 100ms，应判 trace.export 操作瓶颈");

        configuration.getMonitoring().getAlerts().getThresholds().setExportLatencyP99(5000L);
        final TracingPerformanceMonitor loose = monitor(configuration);
        loose.recordOperationPerformance("trace.export", 0L, 200L, true, Map.of()).block();
        assertFalse(hasOperationBottleneck(bottlenecks(loose), "trace.export"),
                "P99 300ms < 阈值 5000ms，不应判 trace.export 操作瓶颈");
    }

    @Test
    @DisplayName("metrics.traces.histogram-buckets 决定 processing.latency 的 SLO 桶，且秒→毫秒换算")
    void histogramBucketsDriveLatencyDistributionSlos() {
        final TracingConfiguration configuration = new TracingConfiguration();
        configuration.getMonitoring().getMetrics().getTraces().setHistogramBuckets(new double[]{1.0, 2.0});

        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final TracingPerformanceMonitor monitor = monitor(configuration, registry);
        monitor.recordOperationPerformance("span.process", 0L, 1500L, true, Map.of()).block();

        final CountAtBucket[] counts = registry.get("tracing.processing.latency").summary()
                .takeSnapshot().histogramCounts();
        final List<Double> boundaries = Arrays.stream(counts).map(CountAtBucket::bucket).toList();

        assertEquals(List.of(1000.0, 2000.0), boundaries,
                "桶边界应等于配置的秒值 ×1000（指标本身以毫秒记录）");
    }

    private static boolean hasMemoryBottleneck(final List<PerformanceBottleneck> bottlenecks) {
        return bottlenecks.stream().anyMatch(b -> b.getType() == BottleneckType.MEMORY);
    }

    private static boolean hasOperationBottleneck(final List<PerformanceBottleneck> bottlenecks,
                                                 final String operation) {
        return bottlenecks.stream().anyMatch(b -> b.getType() == BottleneckType.OPERATION
                && b.getDescription() != null && b.getDescription().contains(operation));
    }
}
