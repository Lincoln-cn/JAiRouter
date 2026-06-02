package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 追踪处理指标事件
 */
public class TraceProcessingMetricsEvent extends MetricsEvent {
    private final String processorName;
    private final long duration;
    private final boolean success;

    public TraceProcessingMetricsEvent(final String processorName, final long duration, final boolean success) {
        this.processorName = processorName;
        this.duration = duration;
        this.success = success;
    }

    public String getProcessorName() {
        return processorName;
    }

    public long getDuration() {
        return duration;
    }

    public boolean isSuccess() {
        return success;
    }
}
