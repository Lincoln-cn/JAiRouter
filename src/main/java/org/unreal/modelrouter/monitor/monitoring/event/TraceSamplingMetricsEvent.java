package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 追踪采样指标事件
 */
public class TraceSamplingMetricsEvent extends MetricsEvent {
    private final double samplingRate;
    private final boolean sampled;

    public TraceSamplingMetricsEvent(final double samplingRate, final boolean sampled) {
        this.samplingRate = samplingRate;
        this.sampled = sampled;
    }

    public double getSamplingRate() {
        return samplingRate;
    }

    public boolean isSampled() {
        return sampled;
    }
}
