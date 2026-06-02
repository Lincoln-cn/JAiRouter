package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 追踪分析指标事件
 */
public class TraceAnalysisMetricsEvent extends MetricsEvent {
    private final String analyzerName;
    private final int spanCount;
    private final long duration;
    private final boolean success;

    public TraceAnalysisMetricsEvent(final String analyzerName, final int spanCount, final long duration, final boolean success) {
        this.analyzerName = analyzerName;
        this.spanCount = spanCount;
        this.duration = duration;
        this.success = success;
    }

    public String getAnalyzerName() {
        return analyzerName;
    }

    public int getSpanCount() {
        return spanCount;
    }

    public long getDuration() {
        return duration;
    }

    public boolean isSuccess() {
        return success;
    }
}
