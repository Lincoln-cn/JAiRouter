package org.unreal.modelrouter.monitor.tracing.statistics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.SpanRecord;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.TraceChainStats;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.TraceRecord;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService.TraceStatistics;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 追踪统计计算器
 *
 * 提供追踪数据的统计计算功能，包括：
 * - 时间范围统计 (getTraceStatistics)
 * - 链路统计计算 (calculateChainStats)
 * - 最大深度计算 (calculateMaxDepth)
 * - 服务聚合统计 (getServiceStatistics)
 *
 * <h2>依赖关系</h2>
 * <ul>
 *   <li>TraceRecord/SpanRecord - 追踪数据模型（来自 TraceQueryService）</li>
 *   <li>TraceStatistics/TraceChainStats - 统计结果模型（来自 TraceQueryService）</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 2.10.0
 * @see TraceQueryService
 */
@Slf4j
@Service
public class TraceStatisticsCalculator {

    /** 采样比例来源；单测直接 new 时为空，按 0 处理。 */
    @Autowired(required = false)
    private TracingConfiguration tracingConfiguration;

    /**
     * 获取追踪统计信息
     *
     * @param traces   追踪记录集合
     * @param startTime 开始时间戳（毫秒）
     * @param endTime   结束时间戳（毫秒）
     * @return 统计信息
     */
    public TraceStatistics getTraceStatistics(final Map<String, TraceRecord> traces,
                                              final long startTime, final long endTime) {
        Instant start = Instant.ofEpochMilli(startTime);
        Instant end = Instant.ofEpochMilli(endTime);

        List<TraceRecord> filteredTraces = traces.values().stream()
            .filter(trace -> trace.getCreatedAt().isAfter(start) && trace.getCreatedAt().isBefore(end))
            .collect(Collectors.toList());

        if (filteredTraces.isEmpty()) {
            return new TraceStatistics(
                0, 0, 0, 0, 0.0, 0.0, 0.0,
                new HashMap<>(), new HashMap<>(), new HashMap<>(),
                start, end, samplingRate()
            );
        }

        // 计算统计信息
        long totalTraces = filteredTraces.size();
        long totalSpans = filteredTraces.stream().mapToLong(t -> t.getSpans().size()).sum();
        long successfulTraces = filteredTraces.stream()
            .mapToLong(t -> t.getSpans().stream().anyMatch(SpanRecord::isError) ? 0 : 1)
            .sum();
        long errorTraces = totalTraces - successfulTraces;

        double avgDuration = filteredTraces.stream()
            .mapToDouble(TraceRecord::getDuration)
            .average()
            .orElse(0.0);

        double maxDuration = filteredTraces.stream()
            .mapToDouble(TraceRecord::getDuration)
            .max()
            .orElse(0.0);

        double minDuration = filteredTraces.stream()
            .mapToDouble(TraceRecord::getDuration)
            .min()
            .orElse(0.0);

        // 按服务统计
        Map<String, Long> serviceStats = filteredTraces.stream()
            .collect(Collectors.groupingBy(
                TraceRecord::getServiceName,
                Collectors.counting()
            ));

        // 按操作统计
        Map<String, Long> operationStats = filteredTraces.stream()
            .flatMap(t -> t.getSpans().stream())
            .collect(Collectors.groupingBy(
                SpanRecord::getOperationName,
                Collectors.counting()
            ));

        // 按状态码统计
        Map<String, Long> statusStats = filteredTraces.stream()
            .flatMap(t -> t.getSpans().stream())
            .filter(s -> s.getStatusCode() != null)
            .collect(Collectors.groupingBy(
                SpanRecord::getStatusCode,
                Collectors.counting()
            ));

        return new TraceStatistics(
            totalTraces, totalSpans, successfulTraces, errorTraces,
            avgDuration, maxDuration, minDuration,
            serviceStats, operationStats, statusStats,
            start, end, samplingRate()
        );
    }

    /**
     * 计算链路统计信息
     *
     * @param spans Span记录列表
     * @return 链路统计信息
     */
    public TraceChainStats calculateChainStats(final List<SpanRecord> spans) {
        if (spans.isEmpty()) {
            return new TraceChainStats(0, 0.0, 0.0, 0.0, 0, 0);
        }

        int totalSpans = spans.size();
        double totalDuration = spans.stream().mapToDouble(SpanRecord::getDuration).sum();
        double avgDuration = totalDuration / totalSpans;
        double maxDuration = spans.stream().mapToDouble(SpanRecord::getDuration).max().orElse(0.0);
        long errorCount = spans.stream().mapToLong(s -> s.isError() ? 1 : 0).sum();
        int depth = calculateMaxDepth(spans);

        return new TraceChainStats(totalSpans, totalDuration, avgDuration, maxDuration, errorCount, depth);
    }

    /**
     * 计算最大调用深度
     *
     * @param spans Span记录列表
     * @return 最大调用深度
     */
    public int calculateMaxDepth(final List<SpanRecord> spans) {
        if (spans.isEmpty()) {
            return 0;
        }
        Map<String, SpanRecord> byId = new HashMap<>();
        for (SpanRecord span : spans) {
            byId.put(span.getSpanId(), span);
        }
        Map<String, Integer> cache = new HashMap<>();
        int max = 0;
        for (SpanRecord span : spans) {
            max = Math.max(max, depthOf(span, byId, cache, 0));
        }
        return max;
    }

    /**
     * 递归计算单个 span 的层级：入口 span 为 1，父 span 不在本次链路内时也按 1 处理。
     */
    private int depthOf(final SpanRecord span, final Map<String, SpanRecord> byId,
                        final Map<String, Integer> cache, final int guard) {
        if (span == null || guard > 32) {
            return 0;
        }
        Integer cached = cache.get(span.getSpanId());
        if (cached != null) {
            return cached;
        }
        String parentId = span.getParentSpanId();
        SpanRecord parent = parentId == null ? null : byId.get(parentId);
        int depth = parent == null ? 1 : 1 + depthOf(parent, byId, cache, guard + 1);
        cache.put(span.getSpanId(), depth);
        return depth;
    }

    /** 当前采样比例，用于仪表盘展示（0.0–1.0）。 */
    private double samplingRate() {
        if (tracingConfiguration == null || tracingConfiguration.getSampling() == null) {
            return 0.0;
        }
        return tracingConfiguration.getSampling().getRatio();
    }

    /**
     * 获取服务统计信息
     *
     * @param traces 追踪记录集合
     * @return 服务统计列表
     */
    public List<Map<String, Object>> getServiceStatistics(final Map<String, TraceRecord> traces) {
        // 按服务聚合统计信息
        Map<String, ServiceStatistics> serviceStatsMap = new HashMap<>();

        for (TraceRecord trace : traces.values()) {
            String serviceName = trace.getServiceName();
            ServiceStatistics stats = serviceStatsMap.computeIfAbsent(serviceName,
                ServiceStatistics::new);

            stats.addTrace(trace);
        }

        // 转换为前端期望的格式
        return serviceStatsMap.values().stream()
            .map(stats -> {
                Map<String, Object> serviceData = new HashMap<>();
                serviceData.put("name", stats.getServiceName());
                serviceData.put("traces", stats.getTraceCount());
                serviceData.put("requestCount", stats.getRequestCount());
                serviceData.put("errors", stats.getErrorCount());
                serviceData.put("avgDuration", Math.round(stats.getAvgDuration()));
                serviceData.put("p95Duration", Math.round(stats.getP95Duration()));
                serviceData.put("p99Duration", Math.round(stats.getP99Duration()));
                serviceData.put("errorRate", Math.round(stats.getErrorRate() * 100.0 * 100.0) / 100.0);
                return serviceData;
            })
            .collect(Collectors.toList());
    }

    /**
     * 服务统计辅助类
     */
    private static class ServiceStatistics {
        private final String serviceName;
        private final List<Double> durations = new ArrayList<>();
        private int traceCount = 0;
        private int errorCount = 0;
        private int requestCount = 0;

        ServiceStatistics(final String serviceName) {
            this.serviceName = serviceName;
        }

        public void addTrace(final TraceRecord trace) {
            traceCount++;
            requestCount++;
            durations.add(trace.getDuration());

            boolean hasError = trace.getSpans().stream().anyMatch(SpanRecord::isError);
            if (hasError) {
                errorCount++;
            }
        }

        public String getServiceName() {
            return serviceName;
        }

        public int getTraceCount() {
            return traceCount;
        }

        public int getRequestCount() {
            return requestCount;
        }

        public int getErrorCount() {
            return errorCount;
        }

        public double getErrorRate() {
            return traceCount > 0 ? (double) errorCount / traceCount : 0.0;
        }

        public double getAvgDuration() {
            return durations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        public double getP95Duration() {
            if (durations.isEmpty()) {
                return 0.0;
            }
            List<Double> sorted = new ArrayList<>(durations);
            sorted.sort(Double::compareTo);
            int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
            return sorted.get(Math.max(0, index));
        }

        public double getP99Duration() {
            if (durations.isEmpty()) {
                return 0.0;
            }
            List<Double> sorted = new ArrayList<>(durations);
            sorted.sort(Double::compareTo);
            int index = (int) Math.ceil(0.99 * sorted.size()) - 1;
            return sorted.get(Math.max(0, index));
        }
    }

    // ========================================
    // 趋势聚合（真实数据，供仪表盘图表使用，见 #1150）
    // ========================================

    /**
     * 延迟趋势：按时间桶聚合 trace 耗时。
     *
     * @param traces      追踪记录（数据源，不做随机、不做外推）
     * @param startTime   窗口开始（毫秒）
     * @param endTime     窗口结束（毫秒）
     * @param bucketMillis 每个桶的宽度（毫秒）
     * @return 每个桶一条记录，含 timestamp/hour/requestCount/avgDuration/maxDuration/p95Duration
     */
    public List<Map<String, Object>> latencyTrend(final Map<String, TraceRecord> traces,
                                                  final long startTime, final long endTime,
                                                  final long bucketMillis) {
        List<List<TraceRecord>> buckets = bucketize(traces, startTime, endTime, bucketMillis);
        if (buckets.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> series = new ArrayList<>();
        long width = Math.max(1L, bucketMillis);
        for (int i = 0; i < buckets.size(); i++) {
            List<TraceRecord> bucket = buckets.get(i);
            List<Double> durations = bucket.stream().map(TraceRecord::getDuration)
                .sorted().collect(Collectors.toList());
            Map<String, Object> point = point(startTime, width, i);
            point.put("requestCount", durations.size());
            point.put("avgDuration", round(durations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0)));
            point.put("maxDuration", round(durations.isEmpty() ? 0.0 : durations.get(durations.size() - 1)));
            point.put("p95Duration", round(percentile(durations, 0.95)));
            series.add(point);
        }
        return series;
    }

    /**
     * 错误趋势：按时间桶聚合错误 trace 数量与错误率。
     */
    public List<Map<String, Object>> errorTrend(final Map<String, TraceRecord> traces,
                                                final long startTime, final long endTime,
                                                final long bucketMillis) {
        List<List<TraceRecord>> buckets = bucketize(traces, startTime, endTime, bucketMillis);
        if (buckets.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> series = new ArrayList<>();
        long width = Math.max(1L, bucketMillis);
        for (int i = 0; i < buckets.size(); i++) {
            List<TraceRecord> bucket = buckets.get(i);
            long errors = bucket.stream().filter(t -> t.getSpans().stream().anyMatch(SpanRecord::isError)).count();
            Map<String, Object> point = point(startTime, width, i);
            point.put("requestCount", bucket.size());
            point.put("errorCount", errors);
            point.put("errorRate", bucket.isEmpty() ? 0.0 : round(errors * 100.0 / bucket.size()));
            series.add(point);
        }
        return series;
    }

    /**
     * 吞吐趋势：按时间桶聚合请求数与 QPS。
     */
    public List<Map<String, Object>> throughputTrend(final Map<String, TraceRecord> traces,
                                                     final long startTime, final long endTime,
                                                     final long bucketMillis) {
        List<List<TraceRecord>> buckets = bucketize(traces, startTime, endTime, bucketMillis);
        if (buckets.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> series = new ArrayList<>();
        long width = Math.max(1L, bucketMillis);
        double seconds = width / 1000.0;
        for (int i = 0; i < buckets.size(); i++) {
            int count = buckets.get(i).size();
            Map<String, Object> point = point(startTime, width, i);
            point.put("requestCount", count);
            point.put("qps", round(seconds > 0 ? count / seconds : 0.0));
            series.add(point);
        }
        return series;
    }

    private Map<String, Object> point(final long startTime, final long width, final int index) {
        long timestamp = startTime + width * index;
        Map<String, Object> point = new HashMap<>();
        point.put("timestamp", timestamp);
        point.put("hour", java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        return point;
    }

    private List<List<TraceRecord>> bucketize(final Map<String, TraceRecord> traces,
                                              final long startTime, final long endTime,
                                              final long bucketMillis) {
        long width = Math.max(1L, bucketMillis);
        int count = (int) Math.max(1L, Math.min(720L, (endTime - startTime) / width));
        List<List<TraceRecord>> buckets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            buckets.add(new ArrayList<>());
        }
        if (traces == null) {
        for (List<TraceRecord> bucket : buckets) {
            if (!bucket.isEmpty()) {
                return buckets;
            }
        }
        // 整个窗口没有任何 trace：返回空列表，前端据此显示「暂无数据」而不是一条 0 值直线。
        return new ArrayList<>();
        }
        for (TraceRecord trace : traces.values()) {
            long ts = trace.getCreatedAt().toEpochMilli();
            if (ts < startTime || ts >= endTime) {
                continue;
            }
            int index = (int) ((ts - startTime) / width);
            if (index >= 0 && index < count) {
                buckets.get(index).add(trace);
            }
        }
        for (List<TraceRecord> bucket : buckets) {
            if (!bucket.isEmpty()) {
                return buckets;
            }
        }
        // 整个窗口没有任何 trace：返回空列表，前端据此显示「暂无数据」而不是一条 0 值直线。
        return new ArrayList<>();
    }

    private double percentile(final List<Double> sorted, final double p) {
        if (sorted.isEmpty()) {
            return 0.0;
        }
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private double round(final double value) {
        return Math.round(value * 100.0) / 100.0;
    }

}
