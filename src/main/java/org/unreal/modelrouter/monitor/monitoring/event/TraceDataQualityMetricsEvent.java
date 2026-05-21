package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 追踪数据质量指标事件
 */
public class TraceDataQualityMetricsEvent extends MetricsEvent {
    private final String traceId;
    private final int spanCount;
    private final int attributeCount;
    private final int errorCount;

    public TraceDataQualityMetricsEvent(final String traceId, final int spanCount, final int attributeCount, final int errorCount) {
        this.traceId = traceId;
        this.spanCount = spanCount;
        this.attributeCount = attributeCount;
        this.errorCount = errorCount;
    }

    public String getTraceId() {
        return traceId;
    }

    public int getSpanCount() {
        return spanCount;
    }

    public int getAttributeCount() {
        return attributeCount;
    }

    public int getErrorCount() {
        return errorCount;
    }
}
