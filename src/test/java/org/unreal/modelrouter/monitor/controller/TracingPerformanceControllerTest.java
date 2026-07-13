package org.unreal.modelrouter.monitor.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.actuate.health.Health;
import org.unreal.modelrouter.monitor.tracing.TracingService;
import org.unreal.modelrouter.monitor.tracing.async.AsyncTracingProcessor;
import org.unreal.modelrouter.monitor.tracing.memory.TracingMemoryManager;
import org.unreal.modelrouter.monitor.tracing.memory.model.MemoryStats;
import org.unreal.modelrouter.monitor.tracing.memory.model.GCResult;
import org.unreal.modelrouter.monitor.tracing.memory.model.MemoryCheckResult;
import org.unreal.modelrouter.monitor.tracing.memory.model.MemoryPressureLevel;
import org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceMonitor;
import org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceModels;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * TracingPerformanceController 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracingPerformanceControllerTest {

    @Mock
    private TracingService tracingService;

    @Mock
    private TracingPerformanceMonitor performanceMonitor;

    @Mock
    private AsyncTracingProcessor asyncTracingProcessor;

    @Mock
    private TracingMemoryManager memoryManager;

    @InjectMocks
    private TracingPerformanceController controller;

    @Nested
    @DisplayName("获取性能统计信息测试")
    class GetPerformanceStatsTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalTraces", 100L);
            when(tracingService.getPerformanceStats()).thenReturn(Mono.just(stats));

            StepVerifier.create(controller.getPerformanceStats())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                        assert response.getBody() != null;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取异步处理统计测试")
    class GetProcessingStatsTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            AsyncTracingProcessor.ProcessingStats stats = mock(AsyncTracingProcessor.ProcessingStats.class);
            when(stats.getProcessedCount()).thenReturn(100L);
            when(asyncTracingProcessor.getProcessingStats()).thenReturn(stats);

            StepVerifier.create(controller.getProcessingStats())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取内存使用统计测试")
    class GetMemoryStatsTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            MemoryStats stats = mock(MemoryStats.class);
            when(stats.getUsedHeap()).thenReturn(1024L * 1024 * 100);
            when(stats.getMaxHeap()).thenReturn(1024L * 1024 * 512);
            when(stats.getHeapUsageRatio()).thenReturn(0.2);
            when(stats.getCacheSize()).thenReturn(50);
            when(stats.getHitRatio()).thenReturn(0.95);
            when(stats.getPressureLevel()).thenReturn(MemoryPressureLevel.LOW);
            when(memoryManager.getMemoryStats()).thenReturn(stats);

            StepVerifier.create(controller.getMemoryStats())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取追踪系统健康状态测试")
    class GetTracingHealthTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            Health health = Health.up().build();
            when(performanceMonitor.health()).thenReturn(health);

            StepVerifier.create(controller.getTracingHealth())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("检测性能瓶颈测试")
    class DetectBottlenecksTests {

        @Test
        @DisplayName("检测成功")
        void detectSuccess() {
            when(performanceMonitor.detectBottlenecks()).thenReturn(Mono.just(List.of()));

            StepVerifier.create(controller.detectBottlenecks())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取优化建议测试")
    class GetOptimizationSuggestionsTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            when(performanceMonitor.getOptimizationSuggestions()).thenReturn(Mono.just(List.of()));

            StepVerifier.create(controller.getOptimizationSuggestions())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("生成性能报告测试")
    class GeneratePerformanceReportTests {

        @Test
        @DisplayName("生成成功")
        void generateSuccess() {
            TracingPerformanceModels.PerformanceReport report =
                    mock(TracingPerformanceModels.PerformanceReport.class);
            when(performanceMonitor.generatePerformanceReport()).thenReturn(Mono.just(report));

            StepVerifier.create(controller.generatePerformanceReport())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("触发性能优化测试")
    class TriggerOptimizationTests {

        @Test
        @DisplayName("触发成功")
        void triggerSuccess() {
            when(tracingService.triggerPerformanceOptimization()).thenReturn(Mono.empty());

            StepVerifier.create(controller.triggerOptimization())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                        assert "success".equals(response.getBody().get("status"));
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("执行性能调优测试")
    class PerformTuningTests {

        @Test
        @DisplayName("执行成功")
        void performSuccess() {
            TracingPerformanceModels.TuningResult result =
                    mock(TracingPerformanceModels.TuningResult.class);
            when(performanceMonitor.performPerformanceTuning(any())).thenReturn(Mono.just(result));

            StepVerifier.create(controller.performTuning(List.of("optimize-memory")))
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("触发垃圾回收测试")
    class TriggerGarbageCollectionTests {

        @Test
        @DisplayName("触发成功")
        void triggerSuccess() {
            GCResult result = mock(GCResult.class);
            when(memoryManager.performGarbageCollection()).thenReturn(Mono.just(result));

            StepVerifier.create(controller.triggerGarbageCollection())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("执行内存检查测试")
    class PerformMemoryCheckTests {

        @Test
        @DisplayName("执行成功")
        void performSuccess() {
            MemoryCheckResult result =
                    mock(MemoryCheckResult.class);
            when(memoryManager.performMemoryCheck()).thenReturn(Mono.just(result));

            StepVerifier.create(controller.performMemoryCheck())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("刷新处理缓冲区测试")
    class FlushProcessingBufferTests {

        @Test
        @DisplayName("刷新成功")
        void flushSuccess() {
            when(asyncTracingProcessor.flush()).thenReturn(Mono.empty());

            StepVerifier.create(controller.flushProcessingBuffer())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                        assert "success".equals(response.getBody().get("status"));
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取监控仪表板数据测试")
    class GetDashboardMetricsTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            AsyncTracingProcessor.ProcessingStats processingStats =
                    mock(AsyncTracingProcessor.ProcessingStats.class);
            when(processingStats.getProcessedCount()).thenReturn(100L);
            when(processingStats.getDroppedCount()).thenReturn(5L);
            when(processingStats.getQueueSize()).thenReturn(10);
            when(processingStats.getSuccessRate()).thenReturn(0.95);
            when(processingStats.getDropRate()).thenReturn(0.05);
            when(processingStats.isRunning()).thenReturn(true);
            when(asyncTracingProcessor.getProcessingStats()).thenReturn(processingStats);

            MemoryStats memoryStats =
                    mock(MemoryStats.class);
            when(memoryStats.getUsedHeap()).thenReturn(1024L * 1024 * 100);
            when(memoryStats.getMaxHeap()).thenReturn(1024L * 1024 * 512);
            when(memoryStats.getHeapUsageRatio()).thenReturn(0.2);
            when(memoryStats.getCacheSize()).thenReturn(50);
            when(memoryStats.getHitRatio()).thenReturn(0.95);
            when(memoryStats.getPressureLevel()).thenReturn(MemoryPressureLevel.LOW);
            when(memoryManager.getMemoryStats()).thenReturn(memoryStats);

            Health health = Health.up().build();
            when(performanceMonitor.health()).thenReturn(health);

            StepVerifier.create(controller.getDashboardMetrics())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                        assert response.getBody().containsKey("processing");
                        assert response.getBody().containsKey("memory");
                        assert response.getBody().containsKey("health");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取活跃告警测试")
    class GetActiveAlertsTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            when(performanceMonitor.detectBottlenecks()).thenReturn(Mono.just(List.of()));

            StepVerifier.create(controller.getActiveAlerts())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }
}
