package org.unreal.modelrouter.monitor.tracing.statistics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
                start, end
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
            start, end
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
        // 简化实现，实际应该根据父子关系计算
        return spans.size() > 0 ? 1 : 0;
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
}