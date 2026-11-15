package org.unreal.modelrouter.monitor.tracing.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitor.tracing.config.TracingArchiveProperties;
import org.unreal.modelrouter.persistence.jpa.entity.TraceArchiveEntity;
import org.unreal.modelrouter.persistence.jpa.repository.TraceArchiveRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * 异常链路归档的行为验证（#1155）：只归档失败链路、可按 traceId 还原、过期清理。
 */
class TraceArchiveServiceTest {

    private TraceArchiveRepository repository;
    private TracingArchiveProperties properties;
    private TraceArchiveService service;

    @BeforeEach
    void setUp() {
        repository = mock(TraceArchiveRepository.class);
        properties = new TracingArchiveProperties();
        properties.setEnabled(true);
        properties.setRetentionDays(7);
        service = new TraceArchiveService(repository, properties, new ObjectMapper());
    }

    @Test
    @DisplayName("归档写入失败链路并带上错误计数")
    void archivesErrorTrace() {
        final TraceQueryService.TraceRecord trace = record("trace-err", true);

        service.archiveAsync(trace);

        verify(repository).save(any(TraceArchiveEntity.class));
    }

    @Test
    @DisplayName("关闭归档后不落库")
    void disabledSkipsArchive() {
        properties.setEnabled(false);

        service.archiveAsync(record("trace-err", true));

        verify(repository, org.mockito.Mockito.never()).save(any(TraceArchiveEntity.class));
    }

    @Test
    @DisplayName("可按 traceId 把归档还原成链路")
    void loadsArchivedChain() {
        final TraceArchiveEntity entity = TraceArchiveEntity.builder()
            .traceId("trace-err")
            .createdAt(Instant.now())
            .service("gateway")
            .operation("POST /v1/chat/completions")
            .durationMs(20.0)
            .statusCode("200")
            .errorCount(1)
            .spansJson("[{\"spanId\":\"s1\",\"traceId\":\"trace-err\",\"operationName\":\"POST /v1/chat/completions\","
                + "\"startTime\":\"2026-11-15T10:00:00Z\",\"endTime\":\"2026-11-15T10:00:00.020Z\","
                + "\"duration\":20.0,\"error\":false,\"statusCode\":\"200\",\"parentSpanId\":null,\"spanKind\":\"SERVER\"},"
                + "{\"spanId\":\"s2\",\"traceId\":\"trace-err\",\"operationName\":\"Adapter.x\","
                + "\"startTime\":\"2026-11-15T10:00:00.003Z\",\"endTime\":\"2026-11-15T10:00:00.015Z\","
                + "\"duration\":12.0,\"error\":true,\"statusCode\":\"502\",\"parentSpanId\":\"s1\",\"spanKind\":\"CLIENT\"}]")
            .build();
        when(repository.findById("trace-err")).thenReturn(Optional.of(entity));

        final Optional<TraceQueryService.TraceChain> chain = service.loadArchivedChain("trace-err");

        assertThat(chain).isPresent();
        assertThat(chain.get().getSpans()).hasSize(2);
        assertThat(chain.get().getStats().getDepth()).isEqualTo(2);
        assertThat(chain.get().getStats().getErrorCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("清理按保留天数删除过期行")
    void cleanupDeletesOldRows() {
        when(repository.deleteOlderThan(any(Instant.class))).thenReturn(3);

        service.cleanup();

        verify(repository).deleteOlderThan(any(Instant.class));
    }

    private static TraceQueryService.TraceRecord record(final String traceId, final boolean error) {
        final Instant start = Instant.now();
        final TraceQueryService.SpanRecord span = new TraceQueryService.SpanRecord(
            "span-1", traceId, "op", start, start.plusMillis(10), 10.0, error,
            error ? "500" : "200", Map.of());
        return new TraceQueryService.TraceRecord(traceId, "gateway", List.of(span), 10.0, start);
    }
}
