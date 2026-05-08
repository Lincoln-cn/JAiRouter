package org.unreal.modelrouter.monitor.tracing.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.monitor.tracing.exporter.TraceExporter;
import org.unreal.modelrouter.monitor.tracing.memory.TracingMemoryManager;
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

    private final Map<String, TraceRecord> traceStore = new ConcurrentHashMap<>();
    private final Queue<TraceRecord> recentTraces = new ConcurrentLinkedQueue<>();
    private final AtomicLong traceCounter = new AtomicLong(0);

    private static final int MAX_STORED_TRACES = 10000;
    private static final int MAX_RECENT_TRACES = 1000;

    public Mono<TraceChain> getTraceChain(final String traceId) {
        return Mono.fromCallable(() -> {
            TraceRecord trace = traceStore.get(traceId);
            if (trace == null) {
                TracingMemoryManager.CachedTraceData cachedData =
                    memoryManager.getCachedTraceData(traceId).block();
                if (cachedData != null) {
                    trace = convertFromCachedData(cachedData);
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
        return Flux.fromIterable(traceStore.values())
            .filter(trace -> matchesCriteria(trace, criteria))
            .map(this::createTraceSummary)
            .sort((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
            .take(criteria.getLimit() > 0 ? criteria.getLimit() : 100);
    }

    public Mono<Map<String, Object>> searchTracesWithPagination(final TraceSearchCriteria criteria) {
        return Mono.fromCallable(() -> {
            List<TraceRecord> allTraces = traceStore.values().stream()
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

    public Mono<TraceStatistics> getTraceStatistics(final long startTime, final long endTime) {
        return Mono.fromCallable(() -> statisticsCalculator.getTraceStatistics(traceStore, startTime, endTime));
    }

    public Flux<TraceSummary> getRecentTraces(final int limit) {
        return Flux.fromIterable(recentTraces)
            .takeLast(Math.min(limit, recentTraces.size()))
            .map(this::createTraceSummary)
            .sort((a, b) -> b.getStartTime().compareTo(a.getStartTime()));
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
            trace = new TraceRecord(traceId, serviceName, mergedSpans, totalDuration, existingTrace.getCreatedAt());
        } else {
            trace = new TraceRecord(traceId, serviceName, spans, duration, Instant.now());
        }
        traceStore.put(traceId, trace);
        if (existingTrace == null) {
            recentTraces.offer(trace);
            traceCounter.incrementAndGet();
        } else {
            recentTraces.remove(existingTrace);
            recentTraces.offer(trace);
        }
        if (traceStore.size() > MAX_STORED_TRACES) {
            String oldestTraceId = traceStore.values().stream()
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

    public Mono<List<Map<String, Object>>> getServiceStatistics() {
        return Mono.fromCallable(() -> statisticsCalculator.getServiceStatistics(traceStore));
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
        if (criteria.getServiceName() != null && !criteria.getServiceName().equals(trace.getServiceName())) return false;
        if (criteria.getTraceId() != null && !trace.getTraceId().contains(criteria.getTraceId())) return false;
        if (criteria.getOperationName() != null) {
            boolean hasOperation = trace.getSpans().stream().anyMatch(span -> criteria.getOperationName().equals(span.getOperationName()));
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
        SpanRecord rootSpan = trace.getSpans().stream().min(Comparator.comparing(SpanRecord::getStartTime)).orElse(null);
        boolean hasError = trace.getSpans().stream().anyMatch(SpanRecord::isError);
        return new TraceSummary(trace.getTraceId(), trace.getServiceName(),
            rootSpan != null ? rootSpan.getOperationName() : "unknown",
            trace.getDuration(), trace.getSpans().size(), hasError, trace.getCreatedAt());
    }

    private TraceRecord convertFromCachedData(final TracingMemoryManager.CachedTraceData cachedData) {
        SpanRecord span = new SpanRecord(cachedData.getSpanId(), cachedData.getTraceId(),
            "cached-operation", cachedData.getTimestamp(), cachedData.getTimestamp().plusMillis(100),
            100.0, false, "200", new HashMap<>());
        return new TraceRecord(cachedData.getTraceId(), "cached-service", List.of(span), 100.0, cachedData.getTimestamp());
    }

    public static class TraceRecord {
        private final String traceId; private final String serviceName; private final List<SpanRecord> spans;
        private final double duration; private final Instant createdAt;
        public TraceRecord(String traceId, String serviceName, List<SpanRecord> spans, double duration, Instant createdAt) {
            this.traceId = traceId; this.serviceName = serviceName; this.spans = spans; this.duration = duration; this.createdAt = createdAt;
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
        public SpanRecord(String spanId, String traceId, String operationName, Instant startTime, Instant endTime,
                         double duration, boolean error, String statusCode, Map<String, Object> attributes) {
            this.spanId = spanId; this.traceId = traceId; this.operationName = operationName;
            this.startTime = startTime; this.endTime = endTime; this.duration = duration;
            this.error = error; this.statusCode = statusCode; this.attributes = attributes;
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
    }

    public static class TraceChain {
        private final String traceId; private final String serviceName; private final List<SpanRecord> spans;
        private final TraceChainStats stats; private final Instant startTime;
        public TraceChain(String traceId, String serviceName, List<SpanRecord> spans, TraceChainStats stats, Instant startTime) {
            this.traceId = traceId; this.serviceName = serviceName; this.spans = spans; this.stats = stats; this.startTime = startTime;
        }
        public String getTraceId() { return traceId; }
        public String getServiceName() { return serviceName; }
        public List<SpanRecord> getSpans() { return spans; }
        public TraceChainStats getStats() { return stats; }
        public Instant getStartTime() { return startTime; }
    }

    public static class TraceChainStats {
        private final int totalSpans; private final double totalDuration; private final double avgDuration;
        private final double maxDuration; private final long errorCount; private final int depth;
        public TraceChainStats(int totalSpans, double totalDuration, double avgDuration, double maxDuration, long errorCount, int depth) {
            this.totalSpans = totalSpans; this.totalDuration = totalDuration; this.avgDuration = avgDuration;
            this.maxDuration = maxDuration; this.errorCount = errorCount; this.depth = depth;
        }
        public int getTotalSpans() { return totalSpans; }
        public double getTotalDuration() { return totalDuration; }
        public double getAvgDuration() { return avgDuration; }
        public double getMaxDuration() { return maxDuration; }
        public long getErrorCount() { return errorCount; }
        public int getDepth() { return depth; }
    }

    public static class TraceSummary {
        private final String traceId; private final String serviceName; private final String operationName;
        private final double duration; private final int spanCount; private final boolean hasError; private final Instant startTime;
        public TraceSummary(String traceId, String serviceName, String operationName, double duration, int spanCount, boolean hasError, Instant startTime) {
            this.traceId = traceId; this.serviceName = serviceName; this.operationName = operationName;
            this.duration = duration; this.spanCount = spanCount; this.hasError = hasError; this.startTime = startTime;
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
        public TraceStatistics(long totalTraces, long totalSpans, long successfulTraces, long errorTraces,
                              double avgDuration, double maxDuration, double minDuration,
                              Map<String, Long> serviceStats, Map<String, Long> operationStats,
                              Map<String, Long> statusStats, Instant startTime, Instant endTime) {
            this.totalTraces = totalTraces; this.totalSpans = totalSpans; this.successfulTraces = successfulTraces;
            this.errorTraces = errorTraces; this.avgDuration = avgDuration; this.maxDuration = maxDuration;
            this.minDuration = minDuration; this.serviceStats = serviceStats; this.operationStats = operationStats;
            this.statusStats = statusStats; this.startTime = startTime; this.endTime = endTime;
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
    }

    public static class TraceExportRequest {
        private final Instant startTime; private final Instant endTime; private final String format; private final int maxRecords;
        public TraceExportRequest(Instant startTime, Instant endTime, String format, int maxRecords) {
            this.startTime = startTime; this.endTime = endTime; this.format = format; this.maxRecords = maxRecords;
        }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public String getFormat() { return format; }
        public int getMaxRecords() { return maxRecords; }
    }

    public static class TraceExportResult {
        private final int recordCount; private final String format; private final String data;
        private final Instant startTime; private final Instant endTime; private final Instant exportedAt;
        public TraceExportResult(int recordCount, String format, String data, Instant startTime, Instant endTime, Instant exportedAt) {
            this.recordCount = recordCount; this.format = format; this.data = data;
            this.startTime = startTime; this.endTime = endTime; this.exportedAt = exportedAt;
        }
        public int getRecordCount() { return recordCount; }
        public String getFormat() { return format; }
        public String getData() { return data; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public Instant getExportedAt() { return exportedAt; }
    }
}