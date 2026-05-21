package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 限流指标事件
 */
public class RateLimitMetricsEvent extends MetricsEvent {
    private final String service;
    private final String algorithm;
    private final boolean allowed;

    public RateLimitMetricsEvent(final String service, final String algorithm, final boolean allowed) {
        this.service = service;
        this.algorithm = algorithm;
        this.allowed = allowed;
    }

    public String getService() {
        return service;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public boolean isAllowed() {
        return allowed;
    }
}
