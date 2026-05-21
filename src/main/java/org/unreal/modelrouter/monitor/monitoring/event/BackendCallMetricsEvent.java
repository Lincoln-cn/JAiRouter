package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 后端调用指标事件
 */
public class BackendCallMetricsEvent extends MetricsEvent {
    private final String adapter;
    private final String instance;
    private final long duration;
    private final boolean success;

    public BackendCallMetricsEvent(final String adapter, final String instance, final long duration, final boolean success) {
        this.adapter = adapter;
        this.instance = instance;
        this.duration = duration;
        this.success = success;
    }

    public String getAdapter() {
        return adapter;
    }

    public String getInstance() {
        return instance;
    }

    public long getDuration() {
        return duration;
    }

    public boolean isSuccess() {
        return success;
    }
}
