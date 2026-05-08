package org.unreal.modelrouter.monitor.tracing.exporter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.SpanRecord;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.TraceExportRequest;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.TraceExportResult;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.TraceRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TraceExporter 单元测试
 *
 * 测试覆盖：
 * - exportTraces: 时间过滤、格式选择、数量限制
 * - exportAsJson: 空列表、单条、多条记录
 * - exportAsCsv: 空列表、单条、多条记录
 * - exportAsDetailedJson: Span信息包含
 */
@DisplayName("TraceExporter 单元测试")
class TraceExporterTest {

    private TraceExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new TraceExporter();
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

    private TraceRecord createTraceWithTime(String traceId, String serviceName,
                                            List<SpanRecord> spans, double duration, Instant createdAt) {
        return new TraceRecord(traceId, serviceName, spans, duration, createdAt);
    }

    @Nested
    @DisplayName("exportTraces 测试")
    class ExportTracesTest {

        @Test
        @DisplayName("空列表导出")
        void testEmptyList() {
            List<TraceRecord> traces = new ArrayList<>();
            TraceExportRequest request = new TraceExportRequest(
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(1000),
                "json",
                100
            );

            TraceExportResult result = exporter.exportTraces(traces, request);

            assertEquals(0, result.getRecordCount());
            assertEquals("json", result.getFormat());
            assertNotNull(result.getData());
        }

        @Test
        @DisplayName("单条记录JSON导出")
        void testSingleTraceJson() {
            Instant now = Instant.now();
            SpanRecord span = createSpan("span-1", "trace-1", "test-op", 100.0, false, "200");
            TraceRecord trace = createTraceWithTime("trace-1", "test-service", List.of(span), 100.0, now);

            List<TraceRecord> traces = List.of(trace);

            TraceExportRequest request = new TraceExportRequest(
                now.minusSeconds(1000),
                now.plusSeconds(1000),
                "json",
                100
            );

            TraceExportResult result = exporter.exportTraces(traces, request);

            assertEquals(1, result.getRecordCount());
            assertEquals("json", result.getFormat());
            assertTrue(result.getData().contains("\"traceId\": \"trace-1\""));
        }

        @Test
        @DisplayName("多条记录CSV导出")
        void testMultipleTracesCsv() {
            Instant now = Instant.now();

            SpanRecord span1 = createSpan("span-1", "trace-1", "op1", 50.0, false, "200");
            TraceRecord trace1 = createTraceWithTime("trace-1", "service-a", List.of(span1), 50.0, now);

            SpanRecord span2 = createSpan("span-2", "trace-2", "op2", 150.0, true, "500");
            TraceRecord trace2 = createTraceWithTime("trace-2", "service-b", List.of(span2), 150.0, now);

            List<TraceRecord> traces = List.of(trace1, trace2);

            TraceExportRequest request = new TraceExportRequest(
                now.minusSeconds(1000),
                now.plusSeconds(1000),
                "csv",
                100
            );

            TraceExportResult result = exporter.exportTraces(traces, request);

            assertEquals(2, result.getRecordCount());
            assertEquals("csv", result.getFormat());
            assertTrue(result.getData().contains("traceId,serviceName,duration,spanCount,createdAt"));
            assertTrue(result.getData().contains("trace-1"));
            assertTrue(result.getData().contains("trace-2"));
        }

        @Test
        @DisplayName("时间范围过滤")
        void testTimeRangeFilter() {
            Instant now = Instant.now();
            Instant oldTime = now.minusSeconds(7200); // 2小时前

            TraceRecord oldTrace = createTraceWithTime("old-trace", "service-old", new ArrayList<>(), 100.0, oldTime);
            TraceRecord recentTrace = createTraceWithTime("recent-trace", "service-recent", new ArrayList<>(), 100.0, now);

            List<TraceRecord> traces = List.of(oldTrace, recentTrace);

            // 查询最近1小时
            TraceExportRequest request = new TraceExportRequest(
                now.minusSeconds(3600),
                now.plusSeconds(1000),
                "json",
                100
            );

            TraceExportResult result = exporter.exportTraces(traces, request);

            assertEquals(1, result.getRecordCount());
            assertTrue(result.getData().contains("recent-trace"));
            assertFalse(result.getData().contains("old-trace"));
        }

        @Test
        @DisplayName("数量限制测试")
        void testMaxRecordsLimit() {
            Instant now = Instant.now();

            List<TraceRecord> traces = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                TraceRecord trace = createTraceWithTime("trace-" + i, "service-" + i, new ArrayList<>(), 100.0, now);
                traces.add(trace);
            }

            // 限制导出3条
            TraceExportRequest request = new TraceExportRequest(
                now.minusSeconds(1000),
                now.plusSeconds(1000),
                "json",
                3
            );

            TraceExportResult result = exporter.exportTraces(traces, request);

            assertEquals(3, result.getRecordCount());
        }

        @Test
        @DisplayName("默认格式JSON")
        void testDefaultFormat() {
            Instant now = Instant.now();
            TraceRecord trace = createTraceWithTime("trace-1", "service", new ArrayList<>(), 100.0, now);

            List<TraceRecord> traces = List.of(trace);

            TraceExportRequest request = new TraceExportRequest(
                now.minusSeconds(1000),
                now.plusSeconds(1000),
                "unknown", // 未知格式
                100
            );

            TraceExportResult result = exporter.exportTraces(traces, request);

            assertEquals("unknown", result.getFormat());
            assertTrue(result.getData().contains("\"traceId\":"));
        }
    }

    @Nested
    @DisplayName("exportAsJson 测试")
    class ExportAsJsonTest {

        @Test
        @DisplayName("空列表导出")
        void testEmptyList() {
            List<TraceRecord> traces = new ArrayList<>();
            String json = exporter.exportAsJson(traces);

            assertTrue(json.contains("\"traces\":"));
            assertTrue(json.contains("["));
            assertTrue(json.contains("]"));
        }

        @Test
        @DisplayName("单条记录导出")
        void testSingleTrace() {
            Instant now = Instant.now();
            SpanRecord span = createSpan("span-1", "trace-1", "op", 100.0, false, "200");
            TraceRecord trace = createTraceWithTime("trace-1", "service", List.of(span), 100.0, now);

            String json = exporter.exportAsJson(List.of(trace));

            assertTrue(json.contains("\"traceId\": \"trace-1\""));
            assertTrue(json.contains("\"serviceName\": \"service\""));
            assertTrue(json.contains("\"duration\": 100.0"));
            assertTrue(json.contains("\"spanCount\": 1"));
        }

        @Test
        @DisplayName("多条记录导出")
        void testMultipleTraces() {
            Instant now = Instant.now();

            TraceRecord trace1 = createTraceWithTime("trace-1", "service-a", new ArrayList<>(), 50.0, now);
            TraceRecord trace2 = createTraceWithTime("trace-2", "service-b", new ArrayList<>(), 150.0, now);

            String json = exporter.exportAsJson(List.of(trace1, trace2));

            assertTrue(json.contains("\"traceId\": \"trace-1\""));
            assertTrue(json.contains("\"traceId\": \"trace-2\""));
            assertTrue(json.contains(","));
        }
    }

    @Nested
    @DisplayName("exportAsCsv 测试")
    class ExportAsCsvTest {

        @Test
        @DisplayName("空列表导出")
        void testEmptyList() {
            List<TraceRecord> traces = new ArrayList<>();
            String csv = exporter.exportAsCsv(traces);

            assertEquals("traceId,serviceName,duration,spanCount,createdAt\n", csv);
        }

        @Test
        @DisplayName("单条记录导出")
        void testSingleTrace() {
            Instant now = Instant.now();
            TraceRecord trace = createTraceWithTime("trace-1", "service", new ArrayList<>(), 100.0, now);

            String csv = exporter.exportAsCsv(List.of(trace));

            assertTrue(csv.startsWith("traceId,serviceName,duration,spanCount,createdAt\n"));
            assertTrue(csv.contains("trace-1"));
            assertTrue(csv.contains("service"));
            assertTrue(csv.contains("100.0"));
        }

        @Test
        @DisplayName("多条记录导出")
        void testMultipleTraces() {
            Instant now = Instant.now();

            TraceRecord trace1 = createTraceWithTime("trace-1", "service-a", new ArrayList<>(), 50.0, now);
            TraceRecord trace2 = createTraceWithTime("trace-2", "service-b", new ArrayList<>(), 150.0, now);

            String csv = exporter.exportAsCsv(List.of(trace1, trace2));

            assertTrue(csv.contains("trace-1"));
            assertTrue(csv.contains("trace-2"));
        }
    }

    @Nested
    @DisplayName("exportAsDetailedJson 测试")
    class ExportAsDetailedJsonTest {

        @Test
        @DisplayName("包含Span信息")
        void testContainsSpanInfo() {
            Instant now = Instant.now();

            SpanRecord span1 = createSpan("span-1", "trace-1", "operation-1", 50.0, false, "200");
            SpanRecord span2 = createSpan("span-2", "trace-1", "operation-2", 100.0, true, "500");

            TraceRecord trace = createTraceWithTime("trace-1", "service", List.of(span1, span2), 150.0, now);

            String json = exporter.exportAsDetailedJson(List.of(trace));

            assertTrue(json.contains("\"spans\":"));
            assertTrue(json.contains("\"spanId\": \"span-1\""));
            assertTrue(json.contains("\"spanId\": \"span-2\""));
            assertTrue(json.contains("\"operationName\": \"operation-1\""));
            assertTrue(json.contains("\"error\": false"));
            assertTrue(json.contains("\"error\": true"));
        }

        @Test
        @DisplayName("空Span列表")
        void testEmptySpans() {
            Instant now = Instant.now();
            TraceRecord trace = createTraceWithTime("trace-1", "service", new ArrayList<>(), 100.0, now);

            String json = exporter.exportAsDetailedJson(List.of(trace));

            assertTrue(json.contains("\"spans\": ["));
            assertTrue(json.contains("]"));
        }
    }
}