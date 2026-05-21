package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 请求大小指标事件
 */
public class RequestSizeMetricsEvent extends MetricsEvent {
    private final String service;
    private final long requestSize;
    private final long responseSize;

    public RequestSizeMetricsEvent(final String service, final long requestSize, final long responseSize) {
        this.service = service;
        this.requestSize = requestSize;
        this.responseSize = responseSize;
    }

    public String getService() {
        return service;
    }

    public long getRequestSize() {
        return requestSize;
    }

    public long getResponseSize() {
        return responseSize;
    }
}
