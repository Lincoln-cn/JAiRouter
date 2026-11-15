package org.unreal.modelrouter.monitor.tracing.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService;
import org.unreal.modelrouter.monitor.tracing.helper.ServiceNameResolver;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把链路记录导出成 Micrometer 指标，让告警能基于追踪数据触发（#1156）。
 *
 * <p><b>为什么是「入口 span 到了才记」</b>：子 span 先到、入口 span 后到（由
 * {@code TracingService#finishHttpSpan} 在响应之后写入），只有入口 span 才带得到
 * 完整的耗时与最终状态码。用 traceId 去重，同一条链路只计一次。</p>
 *
 * <p><b>为什么直方图是必需的</b>：仪表盘原来的「最大延迟」是自进程启动以来的历史最大值，
 * 只增不减，不能当 p95。这里开 {@code publishPercentileHistogram}，
 * {@code jairouter_trace_duration_seconds_bucket} 才能让
 * {@code histogram_quantile(0.95, ...)} 算出真 p95。</p>
 *
 * <p>指标口径（全部落在 {@code /actuator/prometheus}）：</p>
 * <ul>
 *   <li>{@code jairouter_trace_requests_total{service,operation,status}} —— 链路数</li>
 *   <li>{@code jairouter_trace_duration_seconds{service,operation}} —— 直方图，含 {@code _bucket}</li>
 *   <li>{@code jairouter_trace_errors_total{service,operation,error_type}} —— 异常链路数</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceMetricsRecorder {

    /** 已计数的 traceId 上限。超了就整体清空——宁可多计一次，也不让集合无限膨胀。 */
    private static final int COUNTED_CAPACITY = 20_000;

    private static final String REQUESTS_TOTAL = "jairouter_trace_requests_total";
    private static final String DURATION = "jairouter_trace_duration_seconds";
    private static final String ERRORS_TOTAL = "jairouter_trace_errors_total";

    private final MeterRegistry meterRegistry;

    private final Set<String> countedTraceIds = ConcurrentHashMap.newKeySet();

    /**
     * 记录一条链路。入口 span 尚未到达时直接跳过，等它到达再来。
     *
     * @param trace 合并后的链路记录（可能还在增长）
     */
    public void record(final TraceQueryService.TraceRecord trace) {
        if (trace == null) {
            return;
        }
        final TraceQueryService.SpanRecord entry = entrySpan(trace);
        if (entry == null) {
            return;
        }
        if (!countedTraceIds.add(trace.getTraceId())) {
            return;
        }
        if (countedTraceIds.size() > COUNTED_CAPACITY) {
            countedTraceIds.clear();
            countedTraceIds.add(trace.getTraceId());
        }

        final String service = trace.getServiceName() != null && !trace.getServiceName().isEmpty()
            ? trace.getServiceName() : ServiceNameResolver.SERVICE_GATEWAY;
        final String operation = entry.getOperationName() != null ? entry.getOperationName() : "unknown";
        final String status = statusOf(entry);

        Counter.builder(REQUESTS_TOTAL)
            .description("链路总数（按服务 / 操作 / 状态）")
            .tags("service", service, "operation", operation, "status", status)
            .register(meterRegistry)
            .increment();

        final double durationMillis = trace.getDuration() > 0 ? trace.getDuration() : entry.getDuration();
        if (durationMillis > 0) {
            Timer.builder(DURATION)
                .description("链路耗时（直方图，用于 p95）")
                .tags("service", service, "operation", operation)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Duration.ofNanos((long) (durationMillis * 1_000_000L)));
        }

        if (hasError(trace)) {
            final String errorType = errorType(trace, entry);
            Counter.builder(ERRORS_TOTAL)
                .description("异常链路数（按服务 / 操作 / 错误类型）")
                .tags("service", service, "operation", operation, "error_type", errorType)
                .register(meterRegistry)
                .increment();
            // 结构化日志：日志系统可检索，sla 的 trace 子命令也能命中（#1156）
            log.warn("trace.error trace_id={} service={} operation={} duration_ms={} status={} error_type={}",
                trace.getTraceId(), service, operation, Math.round(durationMillis), status, errorType);
        }
    }

    /** 入口 span = 没有父 span 的那个。 */
    private static TraceQueryService.SpanRecord entrySpan(final TraceQueryService.TraceRecord trace) {
        for (TraceQueryService.SpanRecord span : trace.getSpans()) {
            final String parent = span.getParentSpanId();
            if (parent == null || parent.isEmpty()) {
                return span;
            }
        }
        return null;
    }

    private static String statusOf(final TraceQueryService.SpanRecord entry) {
        final String code = entry.getStatusCode();
        return code != null && !code.isEmpty() ? code : (entry.isError() ? "ERROR" : "OK");
    }

    private static boolean hasError(final TraceQueryService.TraceRecord trace) {
        for (TraceQueryService.SpanRecord span : trace.getSpans()) {
            if (span.isError()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 错误类型：HTTP 状态码归一成 {@code http_4xx}/{@code http_5xx}，
     * 其它按「哪个阶段失败」推断（上游调用失败记 {@code upstream}，其余 {@code internal}）。
     */
    private static String errorType(final TraceQueryService.TraceRecord trace, final TraceQueryService.SpanRecord entry) {
        final String code = entry.getStatusCode();
        if (code != null && code.length() >= 3) {
            if (code.startsWith("4")) {
                return "http_4xx";
            }
            if (code.startsWith("5")) {
                return "http_5xx";
            }
        }
        for (TraceQueryService.SpanRecord span : trace.getSpans()) {
            if (span.isError() && TraceQueryService.SpanRecord.KIND_CLIENT.equals(span.getSpanKind())) {
                return "upstream";
            }
        }
        return "internal";
    }
}
