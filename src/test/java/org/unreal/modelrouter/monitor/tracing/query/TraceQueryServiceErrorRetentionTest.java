package org.unreal.modelrouter.monitor.tracing.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitor.tracing.memory.TracingMemoryManager;
import org.unreal.modelrouter.monitor.tracing.exporter.TraceExporter;
import org.unreal.modelrouter.monitor.tracing.statistics.TraceStatisticsCalculator;
import reactor.core.publisher.Mono;

/**
 * 异常链路保留区的行为验证（#1155）：
 * 普通链路写满上限时按最旧淘汰，异常链路不参与淘汰，且淘汰后仍可查询。
 */
class TraceQueryServiceErrorRetentionTest {

    private TraceQueryService service;

    @BeforeEach
    void setUp() {
        final TracingMemoryManager memoryManager = mock(TracingMemoryManager.class);
        when(memoryManager.getCachedTraceData(anyString())).thenReturn(Mono.empty());
        final TraceStatisticsCalculator calculator = mock(TraceStatisticsCalculator.class);
        when(calculator.calculateChainStats(anyList()))
            .thenReturn(mock(TraceQueryService.TraceChainStats.class));
        service = new TraceQueryService(memoryManager, calculator, mock(TraceExporter.class), mock(TraceArchiveService.class));
    }

    @Test
    @DisplayName("异常链路不会被普通链路的淘汰挤出")
    void errorTraceSurvivesOrdinaryEviction() {
        final String errorTraceId = "trace-error-retained";
        service.recordTraceSync(errorTraceId, "gateway", new java.util.ArrayList<>(List.of(span(errorTraceId, "span-e", true, 0))), 10.0);

        // 写满并越过普通上限：异常链路是最早写入的，若参与淘汰会第一个被删
        for (int i = 0; i < 10001; i++) {
            final String id = "trace-ordinary-" + i;
            service.recordTraceSync(id, "gateway", new java.util.ArrayList<>(List.of(span(id, "span-o-" + i, false, 1 + i))), 5.0);
        }

        final TraceQueryService.TraceChain chain = service.getTraceChain(errorTraceId).block();
        assertThat(chain).isNotNull();
        assertThat(chain.getTraceId()).isEqualTo(errorTraceId);
    }

    @Test
    @DisplayName("普通链路超出上限后总量收敛到上限，最新写入仍可查")
    void ordinaryEvictionStillWorks() {
        for (int i = 0; i < 10001; i++) {
            final String id = "trace-ordinary-" + i;
            service.recordTraceSync(id, "gateway", new java.util.ArrayList<>(List.of(span(id, "span-o-" + i, false, 1 + i))), 5.0);
        }

        assertThat(service.getTraceChain("trace-ordinary-10000").block()).isNotNull();
        assertThat(service.getRecentTraces(20000).collectList().block()).hasSize(10000);
    }

    @Test
    @DisplayName("最近链路同时包含普通区与异常保留区")
    void recentTracesMergeBothStores() {
        service.recordTraceSync("trace-err", "gateway", new java.util.ArrayList<>(List.of(span("trace-err", "span-err", true, 0))), 10.0);
        service.recordTraceSync("trace-ok", "gateway", new java.util.ArrayList<>(List.of(span("trace-ok", "span-ok", false, 1))), 5.0);

        final List<TraceQueryService.TraceSummary> recent = service.getRecentTraces(10).collectList().block();
        assertThat(recent).isNotNull();
        assertThat(recent).extracting(TraceQueryService.TraceSummary::getTraceId)
            .contains("trace-err", "trace-ok");
    }

    private static TraceQueryService.SpanRecord span(final String traceId, final String spanId,
                                                     final boolean error, final long offsetMillis) {
        final Instant start = Instant.now().plusMillis(offsetMillis);
        return new TraceQueryService.SpanRecord(
            spanId, traceId, "op-" + spanId, start, start.plusMillis(3), 3.0, error,
            error ? "500" : "200", Map.of());
    }
}
