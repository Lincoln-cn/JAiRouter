package org.unreal.modelrouter.monitor.monitoring.event;

/**
 * 负载均衡器指标事件
 */
public class LoadBalancerMetricsEvent extends MetricsEvent {
    private final String service;
    private final String strategy;
    private final String selectedInstance;

    public LoadBalancerMetricsEvent(final String service, final String strategy, final String selectedInstance) {
        this.service = service;
        this.strategy = strategy;
        this.selectedInstance = selectedInstance;
    }

    public String getService() {
        return service;
    }

    public String getStrategy() {
        return strategy;
    }

    public String getSelectedInstance() {
        return selectedInstance;
    }
}
