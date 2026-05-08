package org.unreal.modelrouter.monitor.tracing.exporter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.SpanRecord;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.TraceExportRequest;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.TraceExportResult;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.TraceRecord;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 追踪数据导出器
 *
 * 提供追踪数据的导出功能，支持多种格式：
 * - JSON格式导出 (exportAsJson)
 * - CSV格式导出 (exportAsCsv)
 * - 通用导出接口 (exportTraces)
 *
 * <h2>依赖关系</h2>
 * <ul>
 *   <li>TraceRecord/SpanRecord - 追踪数据模型（来自 TraceQueryService）</li>
 *   <li>TraceExportRequest/TraceExportResult - 导出请求/响应模型（来自 TraceQueryService）</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 2.10.0
 * @see TraceQueryService
 */
@Slf4j
@Service
public class TraceExporter {

    /**
     * 导出追踪数据
     *
     * @param traces  追踪记录列表
     * @param request 导出请求
     * @return 导出结果
     */
    public TraceExportResult exportTraces(final List<TraceRecord> traces, final TraceExportRequest request) {
        List<TraceRecord> filteredTraces = traces.stream()
            .filter(trace -> {
                Instant traceTime = trace.getCreatedAt();
                return traceTime.isAfter(request.getStartTime())
                       && traceTime.isBefore(request.getEndTime());
            })
            .limit(request.getMaxRecords())
            .collect(Collectors.toList());

        // 根据格式导出数据
        String exportData;
        switch (request.getFormat().toLowerCase()) {
            case "json":
                exportData = exportAsJson(filteredTraces);
                break;
            case "csv":
                exportData = exportAsCsv(filteredTraces);
                break;
            default:
                exportData = exportAsJson(filteredTraces);
        }

        return new TraceExportResult(
            filteredTraces.size(),
            request.getFormat(),
            exportData,
            request.getStartTime(),
            request.getEndTime(),
            Instant.now()
        );
    }

    /**
     * 导出追踪数据为JSON格式
     *
     * @param traces 追踪记录列表
     * @return JSON格式字符串
     */
    public String exportAsJson(final List<TraceRecord> traces) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"traces\": [\n");

        for (int i = 0; i < traces.size(); i++) {
            TraceRecord trace = traces.get(i);
            json.append("    {\n");
            json.append("      \"traceId\": \"").append(trace.getTraceId()).append("\",\n");
            json.append("      \"serviceName\": \"").append(trace.getServiceName()).append("\",\n");
            json.append("      \"duration\": ").append(trace.getDuration()).append(",\n");
            json.append("      \"spanCount\": ").append(trace.getSpans().size()).append(",\n");
            json.append("      \"createdAt\": \"").append(trace.getCreatedAt()).append("\"\n");
            json.append("    }");
            if (i < traces.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  ]\n}");
        return json.toString();
    }

    /**
     * 导出追踪数据为CSV格式
     *
     * @param traces 追踪记录列表
     * @return CSV格式字符串
     */
    public String exportAsCsv(final List<TraceRecord> traces) {
        StringBuilder csv = new StringBuilder();
        csv.append("traceId,serviceName,duration,spanCount,createdAt\n");

        for (TraceRecord trace : traces) {
            csv.append(trace.getTraceId()).append(",");
            csv.append(trace.getServiceName()).append(",");
            csv.append(trace.getDuration()).append(",");
            csv.append(trace.getSpans().size()).append(",");
            csv.append(trace.getCreatedAt()).append("\n");
        }

        return csv.toString();
    }

    /**
     * 导出追踪数据为详细JSON格式（包含Span信息）
     *
     * @param traces 追踪记录列表
     * @return 详细JSON格式字符串
     */
    public String exportAsDetailedJson(final List<TraceRecord> traces) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"traces\": [\n");

        for (int i = 0; i < traces.size(); i++) {
            TraceRecord trace = traces.get(i);
            json.append("    {\n");
            json.append("      \"traceId\": \"").append(trace.getTraceId()).append("\",\n");
            json.append("      \"serviceName\": \"").append(trace.getServiceName()).append("\",\n");
            json.append("      \"duration\": ").append(trace.getDuration()).append(",\n");
            json.append("      \"createdAt\": \"").append(trace.getCreatedAt()).append("\",\n");
            json.append("      \"spans\": [\n");

            List<SpanRecord> spans = trace.getSpans();
            for (int j = 0; j < spans.size(); j++) {
                SpanRecord span = spans.get(j);
                json.append("        {\n");
                json.append("          \"spanId\": \"").append(span.getSpanId()).append("\",\n");
                json.append("          \"operationName\": \"").append(span.getOperationName()).append("\",\n");
                json.append("          \"duration\": ").append(span.getDuration()).append(",\n");
                json.append("          \"error\": ").append(span.isError()).append(",\n");
                json.append("          \"statusCode\": \"").append(span.getStatusCode()).append("\"\n");
                json.append("        }");
                if (j < spans.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }

            json.append("      ]\n");
            json.append("    }");
            if (i < traces.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  ]\n}");
        return json.toString();
    }
}