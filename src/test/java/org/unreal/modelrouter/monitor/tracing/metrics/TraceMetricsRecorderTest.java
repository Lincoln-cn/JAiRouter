package org.unreal.modelrouter.monitor.tracing.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 指标出口的行为约束（#1156）。
 *
 * <p>这些用例守的是「口径」而不是「有没有调用」：入口 span 未到不记、同一链路只记一次、
 * 异常链路要带上错误类型——任何一条错了，外部 Alertmanager 收到的就是错的信号。</p>
 */
class TraceMetricsRecorderTest {

    private MeterRegistry registry;
    private TraceMetricsRecorder recorder;

    @BeforeEach
    void setUp() {
        // 用 Prometheus registry 而不是 SimpleMeterRegistry：只有它能验证直方图桶是否真的生成
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        recorder = new TraceMetricsRecorder(registry);
    }

    @Test
    void entrySpanCountsRequestAndDuration() {
        recorder.record(trace("t1", "gateway", List.of(span("s1", null, "POST /v1/chat/completions", "200", false)), 71));

        assertEquals(1.0, counter("jairouter_trace_requests_total",
            Map.of("service", "gateway", "operation", "POST /v1/chat/completions", "status", "200")));
        assertEquals(1L, timer("jairouter_trace_duration_seconds",
            Map.of("service", "gateway", "operation", "POST /v1/chat/completions")));
        // 直方图必须带 _bucket，否则 p95 算不出来
        assertTrue(((PrometheusMeterRegistry) registry).scrape()
            .contains("jairouter_trace_duration_seconds_bucket"), "直方图桶未生成");
    }

    @Test
    void sameTraceIsCountedOnce() {
        TraceQueryService.TraceRecord trace = trace("t1", "gateway",
            List.of(span("s1", null, "POST /v1/chat/completions", "200", false)), 71);

        recorder.record(trace);
        recorder.record(trace);

        assertEquals(1.0, counter("jairouter_trace_requests_total",
            Map.of("service", "gateway", "operation", "POST /v1/chat/completions", "status", "200")));
    }

    @Test
    void noEntrySpanMeansNoCountingYet() {
        // 子 span 先到、入口还没到 —— 此时耗时与状态码都不可信
        recorder.record(trace("t1", "gateway",
            List.of(span("s2", "s0", "InstanceSelection", "OK", false)), 2));

        assertTrue(registry.find("jairouter_trace_requests_total").counters().isEmpty());
    }

    @Test
    void errorTraceCarriesErrorType() {
        recorder.record(trace("t1", "cert", List.of(span("s1", null, "PUT /api/config/ratelimit", "400", true)), 24));

        assertEquals(1.0, counter("jairouter_trace_errors_total",
            Map.of("service", "cert", "operation", "PUT /api/config/ratelimit", "error_type", "http_4xx")));
    }

    @Test
    void upstreamFailureIsTaggedAsUpstream() {
        List<TraceQueryService.SpanRecord> spans = new ArrayList<>();
        spans.add(span("s1", null, "POST /v1/chat/completions", "200", false));
        spans.add(new TraceQueryService.SpanRecord("s2", "t1", "Adapter.gpustack.chat",
            Instant.now(), Instant.now(), 29, true, "503", Map.of(), "s1", "CLIENT"));

        recorder.record(trace("t1", "gateway", spans, 71));

        assertEquals(1.0, counter("jairouter_trace_errors_total",
            Map.of("service", "gateway", "operation", "POST /v1/chat/completions", "error_type", "upstream")));
    }

    private static TraceQueryService.TraceRecord trace(String traceId, String service,
                                                       List<TraceQueryService.SpanRecord> spans, double duration) {
        return new TraceQueryService.TraceRecord(traceId, service, new ArrayList<>(spans), duration, Instant.now());
    }

    private static TraceQueryService.SpanRecord span(String spanId, String parentId, String operation,
                                                     String status, boolean error) {
        return new TraceQueryService.SpanRecord(spanId, "t1", operation,
            Instant.now(), Instant.now(), 10, error, status, Map.of(), parentId,
            parentId == null ? "SERVER" : "INTERNAL");
    }

    private double counter(String name, Map<String, String> tags) {
        var counter = search(name, tags).counter();
        return counter == null ? -1 : counter.count();
    }

    private long timer(String name, Map<String, String> tags) {
        var timer = search(name, tags).timer();
        return timer == null ? -1 : timer.count();
    }

    /** {@code Search.tags(Map)} 不存在，只能逐个 tag 叠加。 */
    private io.micrometer.core.instrument.search.Search search(String name, Map<String, String> tags) {
        var search = registry.find(name);
        for (Map.Entry<String, String> e : tags.entrySet()) {
            search = search.tag(e.getKey(), e.getValue());
        }
        return search;
    }
}
