package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 请求指标事件
 */
public class RequestMetricsEvent extends MetricsEvent {
    private final String service;
    private final String method;
    private final long duration;
    private final String status;

    public RequestMetricsEvent(final String service, final String method, final long duration, final String status) {
        this.service = service;
        this.method = method;
        this.duration = duration;
        this.status = status;
    }

    public String getService() {
        return service;
    }

    public String getMethod() {
        return method;
    }

    public long getDuration() {
        return duration;
    }

    public String getStatus() {
        return status;
    }
}
