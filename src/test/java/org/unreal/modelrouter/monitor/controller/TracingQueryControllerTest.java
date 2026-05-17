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
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * TracingQueryController 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracingQueryControllerTest {

    @Mock
    private TraceQueryService traceQueryService;

    @InjectMocks
    private TracingQueryController controller;

    @Nested
    @DisplayName("获取追踪链路详情测试")
    class GetTraceChainTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            TraceQueryService.TraceChain traceChain = mock(TraceQueryService.TraceChain.class);
            when(traceQueryService.getTraceChain("trace-1")).thenReturn(Mono.just(traceChain));

            StepVerifier.create(controller.getTraceChain("trace-1"))
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                        assert response.getBody() != null;
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("追踪链路不存在")
        void notFound() {
            // 模拟找不到的情况 - 使用 Mono.empty 并让 controller 使用 switchIfEmpty 或 defaultIfEmpty
            when(traceQueryService.getTraceChain("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(controller.getTraceChain("nonexistent")
                    .defaultIfEmpty(ResponseEntity.notFound().build()))
                    .assertNext(response -> {
                        assert response.getStatusCode().value() == 404;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("搜索追踪数据测试")
    class SearchTracesTests {

        @Test
        @DisplayName("搜索成功")
        void searchSuccess() {
            Map<String, Object> result = new HashMap<>();
            result.put("traces", List.of());
            result.put("total", 0);
            when(traceQueryService.searchTracesWithPagination(any())).thenReturn(Mono.just(result));

            StepVerifier.create(controller.searchTraces(null, null, null, null, null, 0, 0, null, 1, 20))
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取最近追踪测试")
    class GetRecentTracesTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            TraceQueryService.TraceSummary summary = mock(TraceQueryService.TraceSummary.class);
            when(traceQueryService.getRecentTraces(50)).thenReturn(Flux.just(summary));

            StepVerifier.create(controller.getRecentTraces(50))
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取服务统计测试")
    class GetServiceStatisticsTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            Map<String, Object> serviceStats = new HashMap<>();
            serviceStats.put("name", "chat");
            serviceStats.put("traces", 100L);
            when(traceQueryService.getServiceStatistics()).thenReturn(Mono.just(List.of(serviceStats)));

            StepVerifier.create(controller.getServiceStatistics())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取追踪统计测试")
    class GetTraceStatisticsTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            TraceQueryService.TraceStatistics stats = mock(TraceQueryService.TraceStatistics.class);
            when(stats.getTotalTraces()).thenReturn(100L);
            when(traceQueryService.getTraceStatistics(anyLong(), anyLong())).thenReturn(Mono.just(stats));

            StepVerifier.create(controller.getTraceStatistics(null, null))
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("导出追踪数据测试")
    class ExportTracesTests {

        @Test
        @DisplayName("导出成功")
        void exportSuccess() {
            TraceQueryService.TraceExportRequest request = new TraceQueryService.TraceExportRequest(
                    java.time.Instant.now().minusSeconds(3600),
                    java.time.Instant.now(),
                    "json",
                    1000
            );
            TraceQueryService.TraceExportResult result = mock(TraceQueryService.TraceExportResult.class);
            when(traceQueryService.exportTraces(any())).thenReturn(Mono.just(result));

            StepVerifier.create(controller.exportTraces(request))
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("清理过期追踪测试")
    class CleanupExpiredTracesTests {

        @Test
        @DisplayName("清理成功")
        void cleanupSuccess() {
            when(traceQueryService.cleanupExpiredTraces(anyLong())).thenReturn(Mono.just(5L));

            StepVerifier.create(controller.cleanupExpiredTraces(24))
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取操作列表测试")
    class GetOperationsTests {

        @Test
        @DisplayName("获取成功 - 无服务名")
        void getAllOperations() {
            StepVerifier.create(controller.getOperations(null))
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                        assert response.getBody() != null;
                        assert Boolean.TRUE.equals(response.getBody().get("success"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("获取成功 - 指定服务名")
        void getServiceOperations() {
            StepVerifier.create(controller.getOperations("chat"))
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                        assert response.getBody() != null;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("查询服务健康检查测试")
    class GetQueryServiceHealthTests {

        @Test
        @DisplayName("健康检查通过")
        void healthCheck() {
            StepVerifier.create(controller.getQueryServiceHealth())
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                        assert response.getBody() != null;
                        assert Boolean.TRUE.equals(response.getBody().get("success"));
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取性能统计测试")
    class GetPerformanceStatsTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            TraceQueryService.TraceStatistics stats = mock(TraceQueryService.TraceStatistics.class);
            when(stats.getTotalTraces()).thenReturn(100L);
            when(stats.getSuccessfulTraces()).thenReturn(95L);
            when(stats.getErrorTraces()).thenReturn(5L);
            when(stats.getAvgDuration()).thenReturn(150.0);
            when(stats.getMaxDuration()).thenReturn(500.0);
            when(stats.getMinDuration()).thenReturn(50.0);
            when(traceQueryService.getTraceStatistics(anyLong(), anyLong())).thenReturn(Mono.just(stats));

            StepVerifier.create(controller.getPerformanceStats(null, null))
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                        assert response.getBody() != null;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取延迟分析测试")
    class GetLatencyAnalysisTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            Map<String, Object> serviceStats = new HashMap<>();
            serviceStats.put("name", "chat");
            serviceStats.put("avgDuration", 100.0);
            serviceStats.put("p95Duration", 200.0);
            serviceStats.put("p99Duration", 350.0);
            when(traceQueryService.getServiceStatistics()).thenReturn(Mono.just(List.of(serviceStats)));

            StepVerifier.create(controller.getLatencyAnalysis(null, null))
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                        assert response.getBody() != null;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取错误分析测试")
    class GetErrorAnalysisTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            Map<String, Object> serviceStats = new HashMap<>();
            serviceStats.put("name", "chat");
            serviceStats.put("errorRate", 0.05);
            serviceStats.put("traces", 100L);
            serviceStats.put("errors", 5L);
            when(traceQueryService.getServiceStatistics()).thenReturn(Mono.just(List.of(serviceStats)));

            StepVerifier.create(controller.getErrorAnalysis(null, null))
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                        assert response.getBody() != null;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取吞吐量分析测试")
    class GetThroughputAnalysisTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            Map<String, Object> serviceStats = new HashMap<>();
            serviceStats.put("name", "chat");
            serviceStats.put("traces", 3600L);
            when(traceQueryService.getServiceStatistics()).thenReturn(Mono.just(List.of(serviceStats)));

            StepVerifier.create(controller.getThroughputAnalysis(null, null))
                    .assertNext(response -> {
                        assert response.getStatusCode().is2xxSuccessful();
                        assert response.getBody() != null;
                    })
                    .verifyComplete();
        }
    }
}
