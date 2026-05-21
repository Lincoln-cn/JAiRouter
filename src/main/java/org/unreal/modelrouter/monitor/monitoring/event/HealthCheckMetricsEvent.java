package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 健康检查指标事件
 */
public class HealthCheckMetricsEvent extends MetricsEvent {
    private final String adapter;
    private final String instance;
    private final boolean healthy;
    private final long responseTime;

    public HealthCheckMetricsEvent(final String adapter, final String instance, final boolean healthy, final long responseTime) {
        this.adapter = adapter;
        this.instance = instance;
        this.healthy = healthy;
        this.responseTime = responseTime;
    }

    public String getAdapter() {
        return adapter;
    }

    public String getInstance() {
        return instance;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public long getResponseTime() {
        return responseTime;
    }
}
