package org.unreal.modelrouter.monitor.tracing.statistics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.SpanRecord;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.TraceChainStats;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.TraceRecord;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.TraceStatistics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TraceStatisticsCalculator 单元测试
 *
 * 测试覆盖：
 * - getTraceStatistics: 时间范围过滤、空数据、正常统计
 * - calculateChainStats: 空列表、单span、多span统计
 * - calculateMaxDepth: 空列表、单span、多span深度
 * - getServiceStatistics: 空数据、单服务、多服务统计
 */
@DisplayName("TraceStatisticsCalculator 单元测试")
class TraceStatisticsCalculatorTest {

    private TraceStatisticsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TraceStatisticsCalculator();
    }

    private SpanRecord createSpan(String spanId, String traceId, String operation,
                                  double duration, boolean error, String statusCode) {
        Instant now = Instant.now();
        return new SpanRecord(
            spanId, traceId, operation,
            now.minusMillis((long) duration), now,
            duration, error, statusCode, new HashMap<>()
        );
    }

    private TraceRecord createTrace(String traceId, String serviceName,
                                    List<SpanRecord> spans, double duration) {
        return new TraceRecord(traceId, serviceName, spans, duration, Instant.now());
    }

    private TraceRecord createTraceWithTime(String traceId, String serviceName,
                                            List<SpanRecord> spans, double duration, Instant createdAt) {
        return new TraceRecord(traceId, serviceName, spans, duration, createdAt);
    }

    @Nested
    @DisplayName("getTraceStatistics 测试")
    class GetTraceStatisticsTest {

        @Test
        @DisplayName("空追踪数据返回空统计")
        void testEmptyTraces() {
            Map<String, TraceRecord> traces = new HashMap<>();
            long startTime = System.currentTimeMillis() - 3600000;
            long endTime = System.currentTimeMillis();

            TraceStatistics stats = calculator.getTraceStatistics(traces, startTime, endTime);

            assertEquals(0, stats.getTotalTraces());
            assertEquals(0, stats.getTotalSpans());
            assertEquals(0, stats.getSuccessfulTraces());
            assertEquals(0, stats.getErrorTraces());
            assertEquals(0.0, stats.getAvgDuration());
            assertEquals(0.0, stats.getMaxDuration());
            assertEquals(0.0, stats.getMinDuration());
        }

        @Test
        @DisplayName("单条追踪记录统计")
        void testSingleTrace() {
            Map<String, TraceRecord> traces = new HashMap<>();
            Instant now = Instant.now();

            SpanRecord span = createSpan("span-1", "trace-1", "test-operation", 100.0, false, "200");
            TraceRecord trace = createTrace("trace-1", "test-service", List.of(span), 100.0);

            traces.put("trace-1", trace);

            long startTime = now.toEpochMilli() - 1000;
            long endTime = now.toEpochMilli() + 1000;

            TraceStatistics stats = calculator.getTraceStatistics(traces, startTime, endTime);

            assertEquals(1, stats.getTotalTraces());
            assertEquals(1, stats.getTotalSpans());
            assertEquals(1, stats.getSuccessfulTraces());
            assertEquals(0, stats.getErrorTraces());
            assertEquals(100.0, stats.getAvgDuration());
            assertTrue(stats.getServiceStats().containsKey("test-service"));
        }

        @Test
        @DisplayName("多条追踪记录统计含错误")
        void testMultipleTracesWithErrors() {
            Map<String, TraceRecord> traces = new HashMap<>();
            Instant now = Instant.now();

            SpanRecord successSpan = createSpan("span-1", "trace-1", "operation-1", 50.0, false, "200");
            TraceRecord successTrace = createTrace("trace-1", "service-a", List.of(successSpan), 50.0);

            SpanRecord errorSpan = createSpan("span-2", "trace-2", "operation-2", 200.0, true, "500");
            TraceRecord errorTrace = createTrace("trace-2", "service-b", List.of(errorSpan), 200.0);

            traces.put("trace-1", successTrace);
            traces.put("trace-2", errorTrace);

            long startTime = now.toEpochMilli() - 1000;
            long endTime = now.toEpochMilli() + 1000;

            TraceStatistics stats = calculator.getTraceStatistics(traces, startTime, endTime);

            assertEquals(2, stats.getTotalTraces());
            assertEquals(2, stats.getTotalSpans());
            assertEquals(1, stats.getSuccessfulTraces());
            assertEquals(1, stats.getErrorTraces());
            assertEquals(125.0, stats.getAvgDuration());
            assertEquals(2, stats.getServiceStats().size());
        }

        @Test
        @DisplayName("时间范围过滤测试")
        void testTimeRangeFilter() {
            Map<String, TraceRecord> traces = new HashMap<>();
            Instant now = Instant.now();
            Instant oldTime = now.minusSeconds(7200); // 2小时前

            // 创建旧追踪（2小时前）
            TraceRecord oldTrace = createTraceWithTime("old-trace", "service-old", new ArrayList<>(), 100.0, oldTime);
            // 创建最近追踪（当前时间）
            TraceRecord recentTrace = createTraceWithTime("recent-trace", "service-recent", new ArrayList<>(), 100.0, now);

            traces.put("old-trace", oldTrace);
            traces.put("recent-trace", recentTrace);

            // 查询最近1小时的范围
            long startTime = now.minusSeconds(3600).toEpochMilli();
            long endTime = now.plusSeconds(1000).toEpochMilli();

            TraceStatistics stats = calculator.getTraceStatistics(traces, startTime, endTime);

            // 只有recent-trace应该被包含（old-trace在2小时前，超出范围）
            assertEquals(1, stats.getTotalTraces());
        }
    }

    @Nested
    @DisplayName("calculateChainStats 测试")
    class CalculateChainStatsTest {

        @Test
        @DisplayName("空Span列表返回零统计")
        void testEmptySpans() {
            List<SpanRecord> spans = new ArrayList<>();
            TraceChainStats stats = calculator.calculateChainStats(spans);

            assertEquals(0, stats.getTotalSpans());
            assertEquals(0.0, stats.getTotalDuration());
            assertEquals(0.0, stats.getAvgDuration());
            assertEquals(0.0, stats.getMaxDuration());
            assertEquals(0, stats.getErrorCount());
            assertEquals(0, stats.getDepth());
        }

        @Test
        @DisplayName("单个Span统计")
        void testSingleSpan() {
            SpanRecord span = createSpan("span-1", "trace-1", "op", 100.0, false, "200");
            List<SpanRecord> spans = List.of(span);

            TraceChainStats stats = calculator.calculateChainStats(spans);

            assertEquals(1, stats.getTotalSpans());
            assertEquals(100.0, stats.getTotalDuration());
            assertEquals(100.0, stats.getAvgDuration());
            assertEquals(100.0, stats.getMaxDuration());
            assertEquals(0, stats.getErrorCount());
            assertEquals(1, stats.getDepth());
        }

        @Test
        @DisplayName("多个Span统计含错误")
        void testMultipleSpansWithErrors() {
            SpanRecord span1 = createSpan("span-1", "trace-1", "op1", 50.0, false, "200");
            SpanRecord span2 = createSpan("span-2", "trace-1", "op2", 150.0, true, "500");
            SpanRecord span3 = createSpan("span-3", "trace-1", "op3", 100.0, false, "200");

            List<SpanRecord> spans = List.of(span1, span2, span3);

            TraceChainStats stats = calculator.calculateChainStats(spans);

            assertEquals(3, stats.getTotalSpans());
            assertEquals(300.0, stats.getTotalDuration());
            assertEquals(100.0, stats.getAvgDuration());
            assertEquals(150.0, stats.getMaxDuration());
            assertEquals(1, stats.getErrorCount());
            assertEquals(1, stats.getDepth());
        }
    }

    @Nested
    @DisplayName("calculateMaxDepth 测试")
    class CalculateMaxDepthTest {

        @Test
        @DisplayName("空列表深度为0")
        void testEmptyList() {
            List<SpanRecord> spans = new ArrayList<>();
            int depth = calculator.calculateMaxDepth(spans);
            assertEquals(0, depth);
        }

        @Test
        @DisplayName("非空列表深度为1")
        void testNonEmptyList() {
            SpanRecord span = createSpan("span-1", "trace-1", "op", 100.0, false, "200");
            List<SpanRecord> spans = List.of(span);
            int depth = calculator.calculateMaxDepth(spans);
            assertEquals(1, depth);
        }
    }

    @Nested
    @DisplayName("getServiceStatistics 测试")
    class GetServiceStatisticsTest {

        @Test
        @DisplayName("空追踪数据返回空列表")
        void testEmptyTraces() {
            Map<String, TraceRecord> traces = new HashMap<>();
            List<Map<String, Object>> stats = calculator.getServiceStatistics(traces);
            assertTrue(stats.isEmpty());
        }

        @Test
        @DisplayName("单个服务统计")
        void testSingleService() {
            Map<String, TraceRecord> traces = new HashMap<>();

            SpanRecord span = createSpan("span-1", "trace-1", "op", 100.0, false, "200");
            TraceRecord trace = createTrace("trace-1", "service-a", List.of(span), 100.0);

            traces.put("trace-1", trace);

            List<Map<String, Object>> stats = calculator.getServiceStatistics(traces);

            assertEquals(1, stats.size());
            Map<String, Object> serviceStats = stats.get(0);
            assertEquals("service-a", serviceStats.get("name"));
            assertEquals(1, serviceStats.get("traces"));
            assertEquals(1, serviceStats.get("requestCount"));
            assertEquals(0, serviceStats.get("errors"));
        }

        @Test
        @DisplayName("多服务聚合统计")
        void testMultipleServices() {
            Map<String, TraceRecord> traces = new HashMap<>();

            SpanRecord span1 = createSpan("span-a-1", "trace-a-1", "op", 110.0, false, "200");
            TraceRecord trace1 = createTrace("trace-a-1", "service-a", List.of(span1), 110.0);

            SpanRecord span2 = createSpan("span-a-2", "trace-a-2", "op", 120.0, false, "200");
            TraceRecord trace2 = createTrace("trace-a-2", "service-a", List.of(span2), 120.0);

            SpanRecord errorSpan = createSpan("span-b-1", "trace-b-1", "op", 200.0, true, "500");
            TraceRecord errorTrace = createTrace("trace-b-1", "service-b", List.of(errorSpan), 200.0);

            traces.put("trace-a-1", trace1);
            traces.put("trace-a-2", trace2);
            traces.put("trace-b-1", errorTrace);

            List<Map<String, Object>> stats = calculator.getServiceStatistics(traces);

            assertEquals(2, stats.size());

            for (Map<String, Object> serviceStats : stats) {
                String name = (String) serviceStats.get("name");
                if ("service-a".equals(name)) {
                    assertEquals(2, serviceStats.get("traces"));
                    assertEquals(0, serviceStats.get("errors"));
                } else if ("service-b".equals(name)) {
                    assertEquals(1, serviceStats.get("traces"));
                    assertEquals(1, serviceStats.get("errors"));
                }
            }
        }

        @Test
        @DisplayName("错误率计算验证")
        void testErrorRateCalculation() {
            Map<String, TraceRecord> traces = new HashMap<>();

            SpanRecord span1 = createSpan("span-1", "trace-1", "op", 100.0, true, "500");
            SpanRecord span2 = createSpan("span-2", "trace-2", "op", 100.0, true, "500");
            SpanRecord span3 = createSpan("span-3", "trace-3", "op", 100.0, false, "200");

            TraceRecord trace1 = createTrace("trace-1", "service-test", List.of(span1), 100.0);
            TraceRecord trace2 = createTrace("trace-2", "service-test", List.of(span2), 100.0);
            TraceRecord trace3 = createTrace("trace-3", "service-test", List.of(span3), 100.0);

            traces.put("trace-1", trace1);
            traces.put("trace-2", trace2);
            traces.put("trace-3", trace3);

            List<Map<String, Object>> stats = calculator.getServiceStatistics(traces);

            assertEquals(1, stats.size());
            Map<String, Object> serviceStats = stats.get(0);
            assertEquals(3, serviceStats.get("traces"));
            assertEquals(2, serviceStats.get("errors"));

            double errorRate = (Double) serviceStats.get("errorRate");
            assertTrue(errorRate >= 66.0 && errorRate <= 67.0);
        }
    }
}