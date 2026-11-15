package org.unreal.modelrouter.monitor.tracing.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.monitor.tracing.exporter.TraceExporter;
import org.unreal.modelrouter.monitor.tracing.helper.ServiceNameResolver;
import org.unreal.modelrouter.monitor.tracing.memory.TracingMemoryManager;
import org.unreal.modelrouter.monitor.tracing.memory.model.CachedTraceData;
import org.unreal.modelrouter.monitor.tracing.statistics.TraceStatisticsCalculator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceQueryService {

    private final TracingMemoryManager memoryManager;
    private final TraceStatisticsCalculator statisticsCalculator;
    private final TraceExporter traceExporter;
    private final TraceArchiveService traceArchiveService;

    private final Map<String, TraceRecord> traceStore = new ConcurrentHashMap<>();
    private final Queue<TraceRecord> recentTraces = new ConcurrentLinkedQueue<>();
    /** 异常链路单独保留区：LRU，与 traceStore 的淘汰互不影响（#1155）。 */
    private final Map<String, TraceRecord> errorTraceStore = Collections.synchronizedMap(
        new LinkedHashMap<String, TraceRecord>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, TraceRecord> eldest) {
                return size() > MAX_ERROR_TRACES;
            }
        });
    private final AtomicLong traceCounter = new AtomicLong(0);

    private static final int MAX_STORED_TRACES = 10000;
    private static final int MAX_RECENT_TRACES = 1000;
    /** 异常链路的单独保留上限：与普通链路分开计数，避免被控制台的高频请求挤出（#1155）。 */
    private static final int MAX_ERROR_TRACES = 1000;

    public Mono<TraceChain> getTraceChain(final String traceId) {
        return Mono.fromCallable(() -> {
            // 异常链路优先从保留区取：它在 traceStore 里可能已被淘汰（#1155）
            TraceRecord trace = errorTraceStore.get(traceId);
            if (trace == null) {
                trace = traceStore.get(traceId);
            }
            if (trace == null) {
                CachedTraceData cachedData =
                    memoryManager.getCachedTraceData(traceId).block(org.unreal.modelrouter.common.util.ReactorTimeouts.BLOCK);
                if (cachedData != null) {
                    trace = convertFromCachedData(cachedData);
                }
            }
            if (trace == null) {
                // 内存里都没有时再查归档：归档只存异常链路，普通链路不会走到这里（#1155）
                final TraceChain archived = traceArchiveService.loadArchivedChain(traceId).orElse(null);
                if (archived != null) {
                    return archived;
                }
            }
            if (trace == null) {
                return null;
            }
            List<SpanRecord> spans = trace.getSpans();
            spans.sort(Comparator.comparing(SpanRecord::getStartTime));
            TraceChainStats stats = statisticsCalculator.calculateChainStats(spans);
            return new TraceChain(traceId, trace.getServiceName(), spans, stats, trace.getCreatedAt());
        }).doOnNext(chain -> {
            if (chain != null) {
                log.debug("查询到追踪链路: traceId={}, spans={}", traceId, chain.getSpans().size());
            } else {
                log.debug("未找到追踪链路: traceId={}", traceId);
            }
        });
    }

    public Flux<TraceSummary> searchTraces(final TraceSearchCriteria criteria) {
        return Flux.fromIterable(allTraces())
            .filter(trace -> matchesCriteria(trace, criteria))
            .map(this::createTraceSummary)
            .sort((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
            .take(criteria.getLimit() > 0 ? criteria.getLimit() : 100);
    }

    public Mono<Map<String, Object>> searchTracesWithPagination(final TraceSearchCriteria criteria) {
        return Mono.fromCallable(() -> {
            List<TraceRecord> allTraces = allTraces().stream()
                .filter(trace -> matchesCriteria(trace, criteria))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
            int total = allTraces.size();
            int page = Math.max(1, criteria.getPage());
            int size = Math.max(1, Math.min(100, criteria.getSize()));
            int offset = (page - 1) * size;
            List<TraceSummary> pageTraces = allTraces.stream()
                .skip(offset).limit(size).map(this::createTraceSummary).collect(Collectors.toList());
            Map<String, Object> result = new HashMap<>();
            result.put("traces", pageTraces);
            result.put("total", total);
            result.put("page", page);
            result.put("size", size);
            result.put("totalPages", (int) Math.ceil((double) total / size));
            return result;
        });
    }

    /** 普通区 + 异常保留区的全量视图（去重，异常区优先）（#1155）。 */
    private List<TraceRecord> allTraces() {
        final Map<String, TraceRecord> merged = new LinkedHashMap<>();
        for (TraceRecord trace : errorTraceStore.values()) {
            merged.put(trace.getTraceId(), trace);
        }
        for (TraceRecord trace : traceStore.values()) {
            merged.putIfAbsent(trace.getTraceId(), trace);
        }
        return new ArrayList<>(merged.values());
    }

    public Mono<TraceStatistics> getTraceStatistics(final long startTime, final long endTime) {
        return Mono.fromCallable(() -> statisticsCalculator.getTraceStatistics(traceStore, startTime, endTime));
    }

    public Flux<TraceSummary> getRecentTraces(final int limit) {
        return Flux.fromIterable(recentTraceSnapshot(limit))
            .map(this::createTraceSummary)
            .sort((a, b) -> b.getStartTime().compareTo(a.getStartTime()));
    }

    /**
     * 追踪查询接口自己打出来的链路。
     *
     * <p>控制台打开「链路搜索」页就会连发好几个查询请求，如果不排除，
     * 「最近链路」里永远是观测者自己——真正的业务链路和异常链路反而看不见。</p>
     */
    private static final String SELF_QUERY_PREFIX = "/api/tracing/";

    private static boolean isSelfObservation(final TraceRecord trace) {
        for (SpanRecord span : trace.getSpans()) {
            final String operation = span.getOperationName();
            if (operation != null && operation.contains(SELF_QUERY_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 最近链路快照：普通区与异常保留区合并去重，按发生时间倒序（#1155）。
     *
     * <p>顺带剔除追踪查询接口自身产生的链路，避免控制台一刷新就把列表刷成自己。</p>
     */
    private List<TraceRecord> recentTraceSnapshot(final int limit) {
        return allTraces().stream()
            .filter(trace -> !isSelfObservation(trace))
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(Math.max(1, limit))
            .collect(Collectors.toList());
    }

    public Mono<TraceExportResult> exportTraces(final TraceExportRequest request) {
        return Mono.fromCallable(() -> {
            List<TraceRecord> traces = new ArrayList<>(traceStore.values());
            return traceExporter.exportTraces(traces, request);
        });
    }

    public Mono<Void> recordTrace(final String traceId, final String serviceName,
                                 final List<SpanRecord> spans, final double duration) {
        return Mono.fromRunnable(() -> recordTraceSync(traceId, serviceName, spans, duration));
    }

    public void recordTraceSync(final String traceId, final String serviceName,
                                 final List<SpanRecord> spans, final double duration) {
        TraceRecord existingTrace = traceStore.get(traceId);
        TraceRecord trace;
        if (existingTrace != null) {
            List<SpanRecord> mergedSpans = new ArrayList<>(existingTrace.getSpans());
            Set<String> existingSpanIds = mergedSpans.stream().map(SpanRecord::getSpanId).collect(Collectors.toSet());
            for (SpanRecord newSpan : spans) {
                if (!existingSpanIds.contains(newSpan.getSpanId())) {
                    mergedSpans.add(newSpan);
                }
            }
            double totalDuration = Math.max(existingTrace.getDuration(), duration);
            // 服务名以入口 span 写入的为准：子 span 后到，不能覆盖入口的服务名，
            // 否则同一条链路会被拆成两个服务（#1153）。
            trace = new TraceRecord(traceId, existingTrace.getServiceName(), mergedSpans, totalDuration,
                existingTrace.getCreatedAt());
        } else {
            // 子 span 可能先于入口 span 到达（理论上不会），此时服务名兜底为 gateway（#1153）
            trace = new TraceRecord(traceId,
                serviceName != null ? serviceName : ServiceNameResolver.SERVICE_GATEWAY,
                spans, duration, Instant.now());
        }
        traceStore.put(traceId, trace);
        // 异常链路额外放进保留区：它在主存储里被淘汰后仍可查（#1155）
        if (hasErrorSpan(trace)) {
            errorTraceStore.put(traceId, trace);
            traceArchiveService.archiveAsync(trace);
        }
        if (existingTrace == null) {
            recentTraces.offer(trace);
            traceCounter.incrementAndGet();
        } else {
            recentTraces.remove(existingTrace);
            recentTraces.offer(trace);
        }
        if (traceStore.size() > MAX_STORED_TRACES) {
            // 淘汰最旧的普通链路；异常链路已进保留区，不参与本次淘汰（#1155）
            String oldestTraceId = traceStore.values().stream()
                .filter(candidate -> !errorTraceStore.containsKey(candidate.getTraceId()))
                .min(Comparator.comparing(TraceRecord::getCreatedAt))
                .map(TraceRecord::getTraceId).orElse(null);
            if (oldestTraceId != null) {
                traceStore.remove(oldestTraceId);
            }
        }
        if (recentTraces.size() > MAX_RECENT_TRACES) {
            recentTraces.poll();
        }
    }

    /** 链路上任一 span 失败即视为异常链路（#1155）。 */
    private static boolean hasErrorSpan(final TraceRecord trace) {
        return trace.getSpans().stream().anyMatch(SpanRecord::isError);
    }

    public Mono<List<Map<String, Object>>> getServiceStatistics() {
        return Mono.fromCallable(() -> statisticsCalculator.getServiceStatistics(traceStore));
    }

    /** 延迟趋势（真实聚合，数据源为本副本内存里的 traceStore）。 */
    public List<Map<String, Object>> latencyTrend(final long startTime, final long endTime, final long bucketMillis) {
        return statisticsCalculator.latencyTrend(traceStore, startTime, endTime, bucketMillis);
    }

    /** 错误趋势（真实聚合）。 */
    public List<Map<String, Object>> errorTrend(final long startTime, final long endTime, final long bucketMillis) {
        return statisticsCalculator.errorTrend(traceStore, startTime, endTime, bucketMillis);
    }

    /** 吞吐趋势（真实聚合）。 */
    public List<Map<String, Object>> throughputTrend(final long startTime, final long endTime, final long bucketMillis) {
        return statisticsCalculator.throughputTrend(traceStore, startTime, endTime, bucketMillis);
    }

    public Mono<Long> cleanupExpiredTraces(final long maxAgeMillis) {
        return Mono.fromCallable(() -> {
            Instant cutoff = Instant.now().minusMillis(maxAgeMillis);
            long removedCount = traceStore.entrySet().stream()
                .filter(entry -> entry.getValue().getCreatedAt().isBefore(cutoff)).count();
            traceStore.entrySet().removeIf(entry -> entry.getValue().getCreatedAt().isBefore(cutoff));
            recentTraces.removeIf(trace -> trace.getCreatedAt().isBefore(cutoff));
            log.info("清理过期追踪数据: 移除 {} 条记录", removedCount);
            return removedCount;
        });
    }

    private boolean matchesCriteria(final TraceRecord trace, final TraceSearchCriteria criteria) {
        if (criteria.getStartTime() != null && trace.getCreatedAt().isBefore(criteria.getStartTime())) return false;
        if (criteria.getEndTime() != null && trace.getCreatedAt().isAfter(criteria.getEndTime())) return false;
        if (criteria.getServiceName() != null
                && !criteria.getServiceName().equals(trace.getServiceName())) return false;
        if (criteria.getTraceId() != null
                && !trace.getTraceId().contains(criteria.getTraceId())) return false;
        if (criteria.getOperationName() != null) {
            boolean hasOperation = trace.getSpans().stream()
                    .anyMatch(span -> criteria.getOperationName().equals(span.getOperationName()));
            if (!hasOperation) return false;
        }
        if (criteria.getMinDuration() > 0 && trace.getDuration() < criteria.getMinDuration()) return false;
        if (criteria.getMaxDuration() > 0 && trace.getDuration() > criteria.getMaxDuration()) return false;
        if (criteria.getHasError() != null) {
            boolean hasError = trace.getSpans().stream().anyMatch(SpanRecord::isError);
            if (!criteria.getHasError().equals(hasError)) return false;
        }
        return true;
    }

    private TraceSummary createTraceSummary(final TraceRecord trace) {
        SpanRecord rootSpan = trace.getSpans().stream()
                .min(Comparator.comparing(SpanRecord::getStartTime)).orElse(null);
        boolean hasError = trace.getSpans().stream().anyMatch(SpanRecord::isError);
        return new TraceSummary(trace.getTraceId(), trace.getServiceName(),
            rootSpan != null ? rootSpan.getOperationName() : "unknown",
            trace.getDuration(), trace.getSpans().size(), hasError, trace.getCreatedAt());
    }

    private TraceRecord convertFromCachedData(final CachedTraceData cachedData) {
        SpanRecord span = new SpanRecord(cachedData.getSpanId(), cachedData.getTraceId(),
            "cached-operation", cachedData.getTimestamp(),
            cachedData.getTimestamp().plusMillis(100),
            100.0, false, "200", new HashMap<>());
        return new TraceRecord(cachedData.getTraceId(), "cached-service",
                List.of(span), 100.0, cachedData.getTimestamp());
    }

    public static class TraceRecord {
        private final String traceId;
        private final String serviceName;
        private final List<SpanRecord> spans;
        private final double duration;
        private final Instant createdAt;
        public TraceRecord(String traceId, String serviceName,
                           List<SpanRecord> spans, double duration, Instant createdAt) {
            this.traceId = traceId;
            this.serviceName = serviceName;
            this.spans = spans;
            this.duration = duration;
            this.createdAt = createdAt;
        }
        public String getTraceId() { return traceId; }
        public String getServiceName() { return serviceName; }
        public List<SpanRecord> getSpans() { return spans; }
        public double getDuration() { return duration; }
        public Instant getCreatedAt() { return createdAt; }
    }

    public static class SpanRecord {
        private final String spanId; private final String traceId; private final String operationName;
        private final Instant startTime; private final Instant endTime; private final double duration;
        private final boolean error; private final String statusCode; private final Map<String, Object> attributes;

        /** 父 span 标识：入口 span 为 null，子 span 指向发起它的 span（见 #1151）。 */
        private final String parentSpanId;

        /** span 类型：SERVER（入口）/ INTERNAL（网关内部）/ CLIENT（上游调用），见 #1153。 */
        public static final String KIND_SERVER = "SERVER";
        public static final String KIND_INTERNAL = "INTERNAL";
        public static final String KIND_CLIENT = "CLIENT";

        private final String spanKind;

        public SpanRecord(String spanId, String traceId, String operationName, Instant startTime, Instant endTime,
                         double duration, boolean error, String statusCode, Map<String, Object> attributes,
                         String parentSpanId, String spanKind) {
            this.spanId = spanId; this.traceId = traceId; this.operationName = operationName;
            this.startTime = startTime; this.endTime = endTime; this.duration = duration;
            this.error = error; this.statusCode = statusCode; this.attributes = attributes;
            this.parentSpanId = parentSpanId;
            this.spanKind = spanKind != null ? spanKind : KIND_INTERNAL;
        }

        /** 兼容构造：有父 span 但未指定类型时按网关内部处理。 */
        public SpanRecord(String spanId, String traceId, String operationName, Instant startTime, Instant endTime,
                         double duration, boolean error, String statusCode, Map<String, Object> attributes,
                         String parentSpanId) {
            this(spanId, traceId, operationName, startTime, endTime, duration, error, statusCode, attributes,
                parentSpanId, KIND_INTERNAL);
        }

        /** 简化构造：没有父 span 的记录按入口 span 处理。 */
        public SpanRecord(String spanId, String traceId, String operationName, Instant startTime, Instant endTime,
                         double duration, boolean error, String statusCode, Map<String, Object> attributes) {
            this(spanId, traceId, operationName, startTime, endTime, duration, error, statusCode, attributes,
                null, KIND_SERVER);
        }
        public String getSpanId() { return spanId; }
        public String getTraceId() { return traceId; }
        public String getOperationName() { return operationName; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public double getDuration() { return duration; }
        public boolean isError() { return error; }
        public String getStatusCode() { return statusCode; }
        public Map<String, Object> getAttributes() { return attributes; }
        public String getParentSpanId() { return parentSpanId; }
        public String getSpanKind() { return spanKind; }
    }

    public static class TraceChain {
        private final String traceId;
        private final String serviceName;
        private final List<SpanRecord> spans;
        private final TraceChainStats stats;
        private final Instant startTime;
        public TraceChain(String traceId, String serviceName,
                          List<SpanRecord> spans, TraceChainStats stats, Instant startTime) {
            this.traceId = traceId;
            this.serviceName = serviceName;
            this.spans = spans;
            this.stats = stats;
            this.startTime = startTime;
        }
        public String getTraceId() { return traceId; }
        public String getServiceName() { return serviceName; }
        public List<SpanRecord> getSpans() { return spans; }
        public TraceChainStats getStats() { return stats; }
        public Instant getStartTime() { return startTime; }
    }

    public static class TraceChainStats {
        private final int totalSpans;
        private final double totalDuration;
        private final double avgDuration;
        private final double maxDuration;
        private final long errorCount;
        private final int depth;
        public TraceChainStats(int totalSpans, double totalDuration,
                              double avgDuration, double maxDuration,
                              long errorCount, int depth) {
            this.totalSpans = totalSpans;
            this.totalDuration = totalDuration;
            this.avgDuration = avgDuration;
            this.maxDuration = maxDuration;
            this.errorCount = errorCount;
            this.depth = depth;
        }
        public int getTotalSpans() { return totalSpans; }
        public double getTotalDuration() { return totalDuration; }
        public double getAvgDuration() { return avgDuration; }
        public double getMaxDuration() { return maxDuration; }
        public long getErrorCount() { return errorCount; }
        public int getDepth() { return depth; }
    }

    public static class TraceSummary {
        private final String traceId;
        private final String serviceName;
        private final String operationName;
        private final double duration;
        private final int spanCount;
        private final boolean hasError;
        private final Instant startTime;
        public TraceSummary(String traceId, String serviceName, String operationName,
                           double duration, int spanCount, boolean hasError, Instant startTime) {
            this.traceId = traceId;
            this.serviceName = serviceName;
            this.operationName = operationName;
            this.duration = duration;
            this.spanCount = spanCount;
            this.hasError = hasError;
            this.startTime = startTime;
        }
        public String getTraceId() { return traceId; }
        public String getServiceName() { return serviceName; }
        public String getOperationName() { return operationName; }
        public double getDuration() { return duration; }
        public int getSpanCount() { return spanCount; }
        public boolean isHasError() { return hasError; }
        public Instant getStartTime() { return startTime; }
    }

    public static class TraceSearchCriteria {
        private Instant startTime; private Instant endTime; private String serviceName; private String operationName;
        private String traceId; private double minDuration; private double maxDuration; private Boolean hasError;
        private int limit = 100; private int page = 1; private int size = 20;
        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        public Instant getEndTime() { return endTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public String getOperationName() { return operationName; }
        public void setOperationName(String operationName) { this.operationName = operationName; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public double getMinDuration() { return minDuration; }
        public void setMinDuration(double minDuration) { this.minDuration = minDuration; }
        public double getMaxDuration() { return maxDuration; }
        public void setMaxDuration(double maxDuration) { this.maxDuration = maxDuration; }
        public Boolean getHasError() { return hasError; }
        public void setHasError(Boolean hasError) { this.hasError = hasError; }
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
    }

    public static class TraceStatistics {
        private final long totalTraces; private final long totalSpans; private final long successfulTraces;
        private final long errorTraces; private final double avgDuration; private final double maxDuration;
        private final double minDuration; private final Map<String, Long> serviceStats;
        private final Map<String, Long> operationStats; private final Map<String, Long> statusStats;
        private final Instant startTime; private final Instant endTime;

        /** 当前全局采样比例（0.0–1.0），供仪表盘展示；来自 jairouter.tracing.sampling.ratio。 */
        private final double samplingRate;

        public TraceStatistics(long totalTraces, long totalSpans, long successfulTraces, long errorTraces,
                              double avgDuration, double maxDuration, double minDuration,
                              Map<String, Long> serviceStats, Map<String, Long> operationStats,
                              Map<String, Long> statusStats, Instant startTime, Instant endTime,
                              double samplingRate) {
            this.totalTraces = totalTraces; this.totalSpans = totalSpans; this.successfulTraces = successfulTraces;
            this.errorTraces = errorTraces; this.avgDuration = avgDuration; this.maxDuration = maxDuration;
            this.minDuration = minDuration; this.serviceStats = serviceStats; this.operationStats = operationStats;
            this.statusStats = statusStats; this.startTime = startTime; this.endTime = endTime;
            this.samplingRate = samplingRate;
        }

        /** 兼容旧调用：采样率未知时记 0，由调用方在展示层说明。 */
        public TraceStatistics(long totalTraces, long totalSpans, long successfulTraces, long errorTraces,
                              double avgDuration, double maxDuration, double minDuration,
                              Map<String, Long> serviceStats, Map<String, Long> operationStats,
                              Map<String, Long> statusStats, Instant startTime, Instant endTime) {
            this(totalTraces, totalSpans, successfulTraces, errorTraces, avgDuration, maxDuration, minDuration,
                 serviceStats, operationStats, statusStats, startTime, endTime, 0.0);
        }
        public long getTotalTraces() { return totalTraces; }
        public long getTotalSpans() { return totalSpans; }
        public long getSuccessfulTraces() { return successfulTraces; }
        public long getErrorTraces() { return errorTraces; }
        public double getAvgDuration() { return avgDuration; }
        public double getMaxDuration() { return maxDuration; }
        public double getMinDuration() { return minDuration; }
        public Map<String, Long> getServiceStats() { return serviceStats; }
        public Map<String, Long> getOperationStats() { return operationStats; }
        public Map<String, Long> getStatusStats() { return statusStats; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public double getSamplingRate() { return samplingRate; }
    }

    public static class TraceExportRequest {
        private final Instant startTime;
        private final Instant endTime;
        private final String format;
        private final int maxRecords;
        public TraceExportRequest(Instant startTime, Instant endTime,
                                 String format, int maxRecords) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.format = format;
            this.maxRecords = maxRecords;
        }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public String getFormat() { return format; }
        public int getMaxRecords() { return maxRecords; }
    }

    public static class TraceExportResult {
        private final int recordCount;
        private final String format;
        private final String data;
        private final Instant startTime;
        private final Instant endTime;
        private final Instant exportedAt;
        public TraceExportResult(int recordCount, String format, String data,
                                Instant startTime, Instant endTime, Instant exportedAt) {
            this.recordCount = recordCount;
            this.format = format;
            this.data = data;
            this.startTime = startTime;
            this.endTime = endTime;
            this.exportedAt = exportedAt;
        }
        public int getRecordCount() { return recordCount; }
        public String getFormat() { return format; }
        public String getData() { return data; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public Instant getExportedAt() { return exportedAt; }
    }
}
