package org.unreal.modelrouter.monitor.tracing.query;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.monitor.tracing.config.TracingArchiveProperties;
import org.unreal.modelrouter.persistence.jpa.entity.TraceArchiveEntity;
import org.unreal.modelrouter.persistence.jpa.repository.TraceArchiveRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 异常链路归档服务（#1155）。
 *
 * <p>写入异步执行，避免把落库耗时带进请求主链路；清理按小时执行；读取时把
 * {@code spans_json} 还原成 {@link TraceQueryService.SpanRecord}，让归档链路在
 * 「链路搜索 → 查看链路」里与内存链路表现一致。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceArchiveService {

    private final TraceArchiveRepository repository;
    private final TracingArchiveProperties properties;
    private final ObjectMapper objectMapper;

    /** 异步归档一条异常链路；任何失败只记日志，不影响请求。 */
    @Async
    public void archiveAsync(final TraceQueryService.TraceRecord trace) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            final List<TraceQueryService.SpanRecord> spans = trace.getSpans();
            final long errorCount = spans.stream().filter(TraceQueryService.SpanRecord::isError).count();
            final TraceQueryService.SpanRecord entry = spans.stream()
                .filter(span -> span.getParentSpanId() == null || span.getParentSpanId().isEmpty())
                .findFirst()
                .orElse(spans.get(0));
            final TraceArchiveEntity entity = TraceArchiveEntity.builder()
                .traceId(trace.getTraceId())
                .createdAt(trace.getCreatedAt())
                .service(trace.getServiceName())
                .operation(entry.getOperationName())
                .durationMs(trace.getDuration())
                .statusCode(entry.getStatusCode())
                .errorCount((int) errorCount)
                .spansJson(objectMapper.writeValueAsString(spans))
                .build();
            repository.save(entity);
        } catch (RuntimeException ex) {
            log.warn("异常链路归档失败 traceId={}: {}", trace.getTraceId(), ex.getMessage());
        }
    }

    /** 每小时清理一次过期归档。 */
    @Scheduled(fixedDelayString = "${jairouter.tracing.archive.cleanup-interval-ms:3600000}",
        initialDelayString = "${jairouter.tracing.archive.cleanup-initial-delay-ms:600000}")
    @Transactional
    public void cleanup() {
        if (!properties.isEnabled() || properties.getRetentionDays() <= 0) {
            return;
        }
        final Instant cutoff = Instant.now().minus(Duration.ofDays(properties.getRetentionDays()));
        final int removed = repository.deleteOlderThan(cutoff);
        if (removed > 0) {
            log.info("清理过期链路归档 {} 条（保留 {} 天）", removed, properties.getRetentionDays());
        }
    }

    /** 从归档里还原链路；内存里没有时才调用。 */
    public Optional<TraceQueryService.TraceChain> loadArchivedChain(final String traceId) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        return repository.findById(traceId).map(entity -> {
            final List<TraceQueryService.SpanRecord> spans = readSpans(entity.getSpansJson());
            spans.sort(java.util.Comparator.comparing(TraceQueryService.SpanRecord::getStartTime));
            final double totalDuration = spans.stream()
                .mapToDouble(TraceQueryService.SpanRecord::getDuration).sum();
            final double maxDuration = spans.stream()
                .mapToDouble(TraceQueryService.SpanRecord::getDuration).max().orElse(0.0);
            final double avgDuration = spans.isEmpty() ? 0.0 : totalDuration / spans.size();
            final long errorCount = spans.stream()
                .filter(TraceQueryService.SpanRecord::isError).count();
            final TraceQueryService.TraceChainStats stats = new TraceQueryService.TraceChainStats(
                spans.size(), totalDuration, avgDuration, maxDuration, errorCount, maxDepth(spans));
            return new TraceQueryService.TraceChain(
                entity.getTraceId(), entity.getService(), spans, stats, entity.getCreatedAt());
        });
    }

    /** 归档列表（按时间倒序）。 */
    public List<TraceArchiveEntity> recent(final int limit) {
        final Instant to = Instant.now().plusSeconds(1);
        final Instant from = to.minus(Duration.ofDays(Math.max(1, properties.getRetentionDays())));
        return repository.findByCreatedAtBetweenOrderByCreatedAtDesc(
            from, to, org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit)));
    }

    @SuppressWarnings("unchecked")
    private List<TraceQueryService.SpanRecord> readSpans(final String json) {
        final List<TraceQueryService.SpanRecord> spans = new ArrayList<>();
        try {
            final JsonNode root = objectMapper.readTree(json);
            for (JsonNode node : root) {
                spans.add(new TraceQueryService.SpanRecord(
                    text(node, "spanId"),
                    text(node, "traceId"),
                    text(node, "operationName"),
                    Instant.parse(text(node, "startTime")),
                    Instant.parse(text(node, "endTime")),
                    node.path("duration").asDouble(),
                    node.path("error").asBoolean(),
                    text(node, "statusCode"),
                    Map.of(),
                    text(node, "parentSpanId"),
                    text(node, "spanKind")));
            }
        } catch (RuntimeException ex) {
            log.warn("解析归档链路失败: {}", ex.getMessage());
        }
        return spans;
    }

    private static String text(final JsonNode node, final String field) {
        final JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static int maxDepth(final List<TraceQueryService.SpanRecord> spans) {
        if (spans.isEmpty()) {
            return 0;
        }
        final Map<String, String> parents = new java.util.HashMap<>();
        for (TraceQueryService.SpanRecord span : spans) {
            parents.put(span.getSpanId(), span.getParentSpanId());
        }
        int max = 1;
        for (TraceQueryService.SpanRecord span : spans) {
            int depth = 1;
            String current = span.getParentSpanId();
            int guard = 0;
            while (current != null && parents.containsKey(current) && guard++ < 32) {
                depth++;
                current = parents.get(current);
            }
            max = Math.max(max, depth);
        }
        return max;
    }
}
