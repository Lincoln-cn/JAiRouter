package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 追踪指标事件
 */
public class TraceMetricsEvent extends MetricsEvent {
    private final String traceId;
    private final String spanId;
    private final String operationName;
    private final long duration;
    private final boolean success;

    public TraceMetricsEvent(final String traceId, final String spanId, final String operationName, final long duration, final boolean success) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.operationName = operationName;
        this.duration = duration;
        this.success = success;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getOperationName() {
        return operationName;
    }

    public long getDuration() {
        return duration;
    }

    public boolean isSuccess() {
        return success;
    }
}
