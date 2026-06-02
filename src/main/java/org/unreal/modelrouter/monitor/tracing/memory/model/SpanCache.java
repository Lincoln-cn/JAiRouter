package org.unreal.modelrouter.monitor.tracing.memory.model;

import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Span缓存
 */
@Data
public class SpanCache {
    private final String traceId;
    private final Map<String, Object> spans = new ConcurrentHashMap<>();
    private final Instant createdAt = Instant.now();
    private volatile Instant lastAccess = Instant.now();
    private final AtomicLong estimatedSize = new AtomicLong(0);

    public SpanCache(final String traceId, final int initialCapacity) {
        this.traceId = traceId;
    }

    public void put(final String spanId, final Object spanData, final long size) {
        spans.put(spanId, spanData);
        estimatedSize.addAndGet(size);
        lastAccess = Instant.now();
    }

    public Object get(final String spanId) {
        lastAccess = Instant.now();
        return spans.get(spanId);
    }

    public boolean isExpired() {
        return lastAccess.isBefore(Instant.now().minus(Duration.ofMinutes(30)));
    }

    public long getEstimatedSize() {
        return estimatedSize.get();
    }
}
