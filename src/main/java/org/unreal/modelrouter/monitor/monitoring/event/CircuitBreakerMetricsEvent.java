package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 熔断器指标事件
 */
public class CircuitBreakerMetricsEvent extends MetricsEvent {
    private final String service;
    private final String state;
    private final String event;

    public CircuitBreakerMetricsEvent(final String service, final String state, final String event) {
        this.service = service;
        this.state = state;
        this.event = event;
    }

    public String getService() {
        return service;
    }

    public String getState() {
        return state;
    }

    public String getEvent() {
        return event;
    }
}
