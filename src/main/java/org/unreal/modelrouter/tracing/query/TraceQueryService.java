package org.unreal.modelrouter.tracing.query;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.tracing.memory.TracingMemoryManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 追踪数据查询服务
 * 
 * 提供追踪数据的查询、搜索和聚合功能，包括：
 * - 基于traceId的完整链路查询
 * - 基于时间范围和条件的追踪搜索
 * - 追踪统计和聚合查询
 * - 追踪数据导出功能
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceQueryService {

    private final TracingMemoryManager memoryManager;
    
    // 模拟追踪数据存储（实际项目中应该集成真实的追踪存储）
    private final Map<String, TraceRecord> traceStore = new ConcurrentHashMap<>();
    private final Queue<TraceRecord> recentTraces = new ConcurrentLinkedQueue<>();
    private final AtomicLong traceCounter = new AtomicLong(0);
    
    // 最大存储数量限制
    private static final int MAX_STORED_TRACES = 10000;
    private static final int MAX_RECENT_TRACES = 1000;

    /**
     * 根据traceId查询完整的追踪链路
     */
    public Mono<TraceChain> getTraceChain(String traceId) {
        return Mono.fromCallable(() -> {
            TraceRecord trace = traceStore.get(traceId);
            if (trace == null) {
                // 尝试从内存管理器中查找
                TracingMemoryManager.CachedTraceData cachedData = 
                    memoryManager.getCachedTraceData(traceId).block();
                if (cachedData != null) {
                    trace = convertFromCachedData(cachedData);
                }
            }
            
            if (trace == null) {
                return null;
            }
            
            // 构建完整的追踪链路
            List<SpanRecord> spans = trace.getSpans();
            
            // 按时间排序
            spans.sort(Comparator.comparing(SpanRecord::getStartTime));
            
            // 计算链路统计信息
            TraceChainStats stats = calculateChainStats(spans);
            
            return new TraceChain(
                traceId,
                trace.getServiceName(),
                spans,
                stats,
                trace.getCreatedAt()
            );
        })
        .doOnNext(chain -> {
            if (chain != null) {
                log.debug("查询到追踪链路: traceId={}, spans={}", traceId, chain.getSpans().size());
            } else {
                log.debug("未找到追踪链路: traceId={}", traceId);
            }
        });
    }

    /**
     * 基于条件搜索追踪数据
     */
    public Flux<TraceSummary> searchTraces(TraceSearchCriteria criteria) {
        return Flux.fromIterable(traceStore.values())
            .filter(trace -> matchesCriteria(trace, criteria))
            .map(this::createTraceSummary)
            .sort((a, b) -> b.getStartTime().compareTo(a.getStartTime())) // 按时间倒序
            .take(criteria.getLimit() > 0 ? criteria.getLimit() : 100); // 默认限制100条
    }

    /**
     * 获取追踪统计信息
     */
    public Mono<TraceStatistics> getTraceStatistics(long startTime, long endTime) {
        return Mono.fromCallable(() -> {
            Instant start = Instant.ofEpochMilli(startTime);
            Instant end = Instant.ofEpochMilli(endTime);
            
            List<TraceRecord> traces = traceStore.values().stream()
                .filter(trace -> trace.getCreatedAt().isAfter(start) && trace.getCreatedAt().isBefore(end))
                .collect(Collectors.toList());
            
            if (traces.isEmpty()) {
                return new TraceStatistics(
                    0, 0, 0, 0, 0.0, 0.0, 0.0,
                    new HashMap<>(), new HashMap<>(), new HashMap<>(),
                    start, end
                );
            }
            
            // 计算统计信息
            long totalTraces = traces.size();
            long totalSpans = traces.stream().mapToLong(t -> t.getSpans().size()).sum();
            long successfulTraces = traces.stream()
                .mapToLong(t -> t.getSpans().stream().anyMatch(s -> s.isError()) ? 0 : 1)
                .sum();
            long errorTraces = totalTraces - successfulTraces;
            
            double avgDuration = traces.stream()
                .mapToDouble(TraceRecord::getDuration)
                .average()
                .orElse(0.0);
            
            double maxDuration = traces.stream()
                .mapToDouble(TraceRecord::getDuration)
                .max()
                .orElse(0.0);
            
            double minDuration = traces.stream()
                .mapToDouble(TraceRecord::getDuration)
                .min()
                .orElse(0.0);
            
            // 按服务统计
            Map<String, Long> serviceStats = traces.stream()
                .collect(Collectors.groupingBy(
                    TraceRecord::getServiceName,
                    Collectors.counting()
                ));
            
            // 按操作统计
            Map<String, Long> operationStats = traces.stream()
                .flatMap(t -> t.getSpans().stream())
                .collect(Collectors.groupingBy(
                    SpanRecord::getOperationName,
                    Collectors.counting()
                ));
            
            // 按状态码统计
            Map<String, Long> statusStats = traces.stream()
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
        });
    }

    /**
     * 获取最近的追踪记录
     */
    public Flux<TraceSummary> getRecentTraces(int limit) {
        return Flux.fromIterable(recentTraces)
            .takeLast(Math.min(limit, recentTraces.size()))
            .map(this::createTraceSummary)
            .sort((a, b) -> b.getStartTime().compareTo(a.getStartTime()));
    }

    /**
     * 导出追踪数据
     */
    public Mono<TraceExportResult> exportTraces(TraceExportRequest request) {
        return Mono.fromCallable(() -> {
            List<TraceRecord> traces = traceStore.values().stream()
                .filter(trace -> {
                    Instant traceTime = trace.getCreatedAt();
                    return traceTime.isAfter(request.getStartTime()) && 
                           traceTime.isBefore(request.getEndTime());
                })
                .limit(request.getMaxRecords())
                .collect(Collectors.toList());
            
            // 根据格式导出数据
            String exportData;
            switch (request.getFormat().toLowerCase()) {
                case "json":
                    exportData = exportAsJson(traces);
                    break;
                case "csv":
                    exportData = exportAsCsv(traces);
                    break;
                default:
                    exportData = exportAsJson(traces);
            }
            
            return new TraceExportResult(
                traces.size(),
                request.getFormat(),
                exportData,
                request.getStartTime(),
                request.getEndTime(),
                Instant.now()
            );
        });
    }

    /**
     * 记录追踪数据（模拟存储）
     */
    public Mono<Void> recordTrace(String traceId, String serviceName, 
                                 List<SpanRecord> spans, double duration) {
        return Mono.fromRunnable(() -> {
            TraceRecord trace = new TraceRecord(
                traceId, serviceName, spans, duration, Instant.now()
            );
            
            // 存储到内存
            traceStore.put(traceId, trace);
            recentTraces.offer(trace);
            
            // 限制存储数量
            if (traceStore.size() > MAX_STORED_TRACES) {
                // 移除最旧的记录
                String oldestTraceId = traceStore.values().stream()
                    .min(Comparator.comparing(TraceRecord::getCreatedAt))
                    .map(TraceRecord::getTraceId)
                    .orElse(null);
                if (oldestTraceId != null) {
                    traceStore.remove(oldestTraceId);
                }
            }
            
            if (recentTraces.size() > MAX_RECENT_TRACES) {
                recentTraces.poll();
            }
            
            traceCounter.incrementAndGet();
            log.debug("记录追踪数据: traceId={}, spans={}", traceId, spans.size());
        });
    }

    /**
     * 清理过期的追踪数据
     */
    public Mono<Long> cleanupExpiredTraces(long maxAgeMillis) {
        return Mono.fromCallable(() -> {
            Instant cutoff = Instant.now().minusMillis(maxAgeMillis);
            
            // 计算删除数量
            long removedCount = traceStore.entrySet().stream()
                .filter(entry -> entry.getValue().getCreatedAt().isBefore(cutoff))
                .count();
            
            // 实际删除
            traceStore.entrySet().removeIf(entry -> 
                entry.getValue().getCreatedAt().isBefore(cutoff));
            
            recentTraces.removeIf(trace -> trace.getCreatedAt().isBefore(cutoff));
            
            log.info("清理过期追踪数据: 移除 {} 条记录", removedCount);
            return removedCount;
        });
    }

    // 私有辅助方法

    private boolean matchesCriteria(TraceRecord trace, TraceSearchCriteria criteria) {
        // 时间范围过滤
        if (criteria.getStartTime() != null && 
            trace.getCreatedAt().isBefore(criteria.getStartTime())) {
            return false;
        }
        if (criteria.getEndTime() != null && 
            trace.getCreatedAt().isAfter(criteria.getEndTime())) {
            return false;
        }
        
        // 服务名过滤
        if (criteria.getServiceName() != null && 
            !criteria.getServiceName().equals(trace.getServiceName())) {
            return false;
        }
        
        // 操作名过滤
        if (criteria.getOperationName() != null) {
            boolean hasOperation = trace.getSpans().stream()
                .anyMatch(span -> criteria.getOperationName().equals(span.getOperationName()));
            if (!hasOperation) {
                return false;
            }
        }
        
        // 持续时间过滤
        if (criteria.getMinDuration() > 0 && trace.getDuration() < criteria.getMinDuration()) {
            return false;
        }
        if (criteria.getMaxDuration() > 0 && trace.getDuration() > criteria.getMaxDuration()) {
            return false;
        }
        
        // 错误过滤
        if (criteria.getHasError() != null) {
            boolean hasError = trace.getSpans().stream().anyMatch(SpanRecord::isError);
            if (!criteria.getHasError().equals(hasError)) {
                return false;
            }
        }
        
        return true;
    }

    private TraceSummary createTraceSummary(TraceRecord trace) {
        SpanRecord rootSpan = trace.getSpans().stream()
            .min(Comparator.comparing(SpanRecord::getStartTime))
            .orElse(null);
        
        boolean hasError = trace.getSpans().stream().anyMatch(SpanRecord::isError);
        
        return new TraceSummary(
            trace.getTraceId(),
            trace.getServiceName(),
            rootSpan != null ? rootSpan.getOperationName() : "unknown",
            trace.getDuration(),
            trace.getSpans().size(),
            hasError,
            trace.getCreatedAt()
        );
    }

    private TraceChainStats calculateChainStats(List<SpanRecord> spans) {
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

    private int calculateMaxDepth(List<SpanRecord> spans) {
        // 简化实现，实际应该根据父子关系计算
        return spans.size() > 0 ? 1 : 0;
    }

    private TraceRecord convertFromCachedData(TracingMemoryManager.CachedTraceData cachedData) {
        // 从缓存数据转换为TraceRecord
        SpanRecord span = new SpanRecord(
            cachedData.getSpanId(),
            cachedData.getTraceId(),
            "cached-operation",
            cachedData.getTimestamp(),
            cachedData.getTimestamp().plusMillis(100), // 假设持续100ms
            100.0,
            false,
            "200",
            new HashMap<>()
        );
        
        return new TraceRecord(
            cachedData.getTraceId(),
            "cached-service",
            List.of(span),
            100.0,
            cachedData.getTimestamp()
        );
    }

    private String exportAsJson(List<TraceRecord> traces) {
        // 简化的JSON导出实现
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

    private String exportAsCsv(List<TraceRecord> traces) {
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

    // 数据类定义

    @Data
    public static class TraceRecord {
        private final String traceId;
        private final String serviceName;
        private final List<SpanRecord> spans;
        private final double duration;
        private final Instant createdAt;
    }

    @Data
    public static class SpanRecord {
        private final String spanId;
        private final String traceId;
        private final String operationName;
        private final Instant startTime;
        private final Instant endTime;
        private final double duration;
        private final boolean error;
        private final String statusCode;
        private final Map<String, Object> attributes;
    }

    @Data
    public static class TraceChain {
        private final String traceId;
        private final String serviceName;
        private final List<SpanRecord> spans;
        private final TraceChainStats stats;
        private final Instant startTime;
    }

    @Data
    public static class TraceChainStats {
        private final int totalSpans;
        private final double totalDuration;
        private final double avgDuration;
        private final double maxDuration;
        private final long errorCount;
        private final int depth;
    }

    @Data
    public static class TraceSummary {
        private final String traceId;
        private final String serviceName;
        private final String operationName;
        private final double duration;
        private final int spanCount;
        private final boolean hasError;
        private final Instant startTime;
    }

    @Data
    public static class TraceSearchCriteria {
        private Instant startTime;
        private Instant endTime;
        private String serviceName;
        private String operationName;
        private double minDuration;
        private double maxDuration;
        private Boolean hasError;
        private int limit = 100;
    }

    @Data
    public static class TraceStatistics {
        private final long totalTraces;
        private final long totalSpans;
        private final long successfulTraces;
        private final long errorTraces;
        private final double avgDuration;
        private final double maxDuration;
        private final double minDuration;
        private final Map<String, Long> serviceStats;
        private final Map<String, Long> operationStats;
        private final Map<String, Long> statusStats;
        private final Instant startTime;
        private final Instant endTime;
    }

    @Data
    public static class TraceExportRequest {
        private final Instant startTime;
        private final Instant endTime;
        private final String format; // json, csv
        private final int maxRecords;
    }

    @Data
    public static class TraceExportResult {
        private final int recordCount;
        private final String format;
        private final String data;
        private final Instant startTime;
        private final Instant endTime;
        private final Instant exportedAt;
    }
}