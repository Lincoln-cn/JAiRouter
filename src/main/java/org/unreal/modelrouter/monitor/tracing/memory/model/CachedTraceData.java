package org.unreal.modelrouter.monitor.tracing.memory.model;

import lombok.Data;

import java.time.Instant;

/**
 * 缓存的追踪数据
 */
@Data
public class CachedTraceData {
    private final String traceId;
    private final String spanId;
    private final Object data;
    private final long estimatedSize;
    private final Instant timestamp;

    public CachedTraceData(final String traceId, final String spanId, final Object data,
                           final long estimatedSize, final Instant timestamp) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.data = data;
        this.estimatedSize = estimatedSize;
        this.timestamp = timestamp;
    }
}
