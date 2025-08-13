package org.unreal.modelrouter.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Conditional;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 默认指标收集器实现
 * 使用Micrometer收集各种业务指标
 */
@Component
@Conditional(MonitoringEnabledCondition.class)
public class DefaultMetricsCollector implements MetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(DefaultMetricsCollector.class);

    private final MeterRegistry meterRegistry;
    private final MonitoringProperties monitoringProperties;

    // 缓存各种指标实例
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> summaries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> gaugeValues = new ConcurrentHashMap<>();

    public DefaultMetricsCollector(MeterRegistry meterRegistry, MonitoringProperties monitoringProperties) {
        this.meterRegistry = meterRegistry;
        this.monitoringProperties = monitoringProperties;
        logger.info("DefaultMetricsCollector initialized with prefix: {}", monitoringProperties.getPrefix());
    }

    @Override
    public void recordRequest(String service, String method, long duration, String status) {
        try {
            String prefix = monitoringProperties.getPrefix();
            String metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";

            // 记录请求总数
            String counterKey = "requests.total." + service + "." + method + "." + status;
            Counter counter = counters.computeIfAbsent(counterKey, key -> 
                Counter.builder(metricPrefix + "requests_total")
                    .tag("service", service)
                    .tag("method", method)
                    .tag("status", status)
                    .description("Total number of requests")
                    .register(meterRegistry)
            );
            counter.increment();

            // 记录响应时间
            String timerKey = "request.duration." + service + "." + method;
            Timer timer = timers.computeIfAbsent(timerKey, key ->
                Timer.builder(metricPrefix + "request_duration_seconds")
                    .tag("service", service)
                    .tag("method", method)
                    .description("Request duration in seconds")
                    .register(meterRegistry)
            );
            timer.record(Duration.ofMillis(duration));

            logger.debug("Recorded request metric: service={}, method={}, duration={}ms, status={}", 
                        service, method, duration, status);
        } catch (Exception e) {
            logger.warn("Failed to record request metric: {}", e.getMessage());
        }
    }

    @Override
    public void recordBackendCall(String adapter, String instance, long duration, boolean success) {
        try {
            String prefix = monitoringProperties.getPrefix();
            String metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";
            String status = success ? "success" : "failure";
            
            // 记录后端调用总数
            String counterKey = "backend.calls.total." + adapter + "." + instance + "." + status;
            Counter counter = counters.computeIfAbsent(counterKey, key ->
                Counter.builder(metricPrefix + "backend_calls_total")
                    .tag("adapter", adapter)
                    .tag("instance", instance)
                    .tag("status", status)
                    .description("Total number of backend calls")
                    .register(meterRegistry)
            );
            counter.increment();

            // 记录后端调用时间
            String timerKey = "backend.call.duration." + adapter + "." + instance;
            Timer timer = timers.computeIfAbsent(timerKey, key ->
                Timer.builder(metricPrefix + "backend_call_duration_seconds")
                    .tag("adapter", adapter)
                    .tag("instance", instance)
                    .description("Backend call duration in seconds")
                    .register(meterRegistry)
            );
            timer.record(Duration.ofMillis(duration));

            logger.debug("Recorded backend call metric: adapter={}, instance={}, duration={}ms, success={}", 
                        adapter, instance, duration, success);
        } catch (Exception e) {
            logger.warn("Failed to record backend call metric: {}", e.getMessage());
        }
    }

    @Override
    public void recordRateLimit(String service, String algorithm, boolean allowed) {
        try {
            String prefix = monitoringProperties.getPrefix();
            String metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";
            String result = allowed ? "allowed" : "rejected";
            
            String counterKey = "rate.limit.events.total." + service + "." + algorithm + "." + result;
            Counter counter = counters.computeIfAbsent(counterKey, key ->
                Counter.builder(metricPrefix + "rate_limit_events_total")
                    .tag("service", service)
                    .tag("algorithm", algorithm)
                    .tag("result", result)
                    .description("Total number of rate limit events")
                    .register(meterRegistry)
            );
            counter.increment();

            logger.debug("Recorded rate limit metric: service={}, algorithm={}, allowed={}", 
                        service, algorithm, allowed);
        } catch (Exception e) {
            logger.warn("Failed to record rate limit metric: {}", e.getMessage());
        }
    }

    @Override
    public void recordCircuitBreaker(String service, String state, String event) {
        try {
            String prefix = monitoringProperties.getPrefix();
            String metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";
            
            // 记录熔断器事件
            String counterKey = "circuit.breaker.events.total." + service + "." + event;
            Counter counter = counters.computeIfAbsent(counterKey, key ->
                Counter.builder(metricPrefix + "circuit_breaker_events_total")
                    .tag("service", service)
                    .tag("event", event)
                    .description("Total number of circuit breaker events")
                    .register(meterRegistry)
            );
            counter.increment();

            // 记录熔断器状态 (使用Gauge)
            String gaugeKey = "circuit.breaker.state." + service;
            AtomicLong stateValue = gaugeValues.computeIfAbsent(gaugeKey, key -> {
                AtomicLong value = new AtomicLong(getStateValue(state));
                Gauge.builder(metricPrefix + "circuit_breaker_state", value, AtomicLong::doubleValue)
                    .tag("service", service)
                    .description("Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
                    .register(meterRegistry);
                return value;
            });
            stateValue.set(getStateValue(state));

            logger.debug("Recorded circuit breaker metric: service={}, state={}, event={}", 
                        service, state, event);
        } catch (Exception e) {
            logger.warn("Failed to record circuit breaker metric: {}", e.getMessage());
        }
    }

    @Override
    public void recordLoadBalancer(String service, String strategy, String selectedInstance) {
        try {
            String prefix = monitoringProperties.getPrefix();
            String metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";
            
            String counterKey = "loadbalancer.selections.total." + service + "." + strategy;
            Counter counter = counters.computeIfAbsent(counterKey, key ->
                Counter.builder(metricPrefix + "loadbalancer_selections_total")
                    .tag("service", service)
                    .tag("strategy", strategy)
                    .tag("instance", selectedInstance)
                    .description("Total number of load balancer selections")
                    .register(meterRegistry)
            );
            counter.increment();

            logger.debug("Recorded load balancer metric: service={}, strategy={}, instance={}", 
                        service, strategy, selectedInstance);
        } catch (Exception e) {
            logger.warn("Failed to record load balancer metric: {}", e.getMessage());
        }
    }

    @Override
    public void recordHealthCheck(String adapter, String instance, boolean healthy, long responseTime) {
        try {
            String prefix = monitoringProperties.getPrefix();
            String metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";
            
            // 记录健康状态
            String gaugeKey = "backend.health." + adapter + "." + instance;
            AtomicLong healthValue = gaugeValues.computeIfAbsent(gaugeKey, key -> {
                AtomicLong value = new AtomicLong(healthy ? 1 : 0);
                Gauge.builder(metricPrefix + "backend_health", value, AtomicLong::doubleValue)
                    .tag("adapter", adapter)
                    .tag("instance", instance)
                    .description("Backend health status (1=healthy, 0=unhealthy)")
                    .register(meterRegistry);
                return value;
            });
            healthValue.set(healthy ? 1 : 0);

            // 记录健康检查响应时间
            String timerKey = "health.check.duration." + adapter + "." + instance;
            Timer timer = timers.computeIfAbsent(timerKey, key ->
                Timer.builder(metricPrefix + "health_check_duration_seconds")
                    .tag("adapter", adapter)
                    .tag("instance", instance)
                    .description("Health check duration in seconds")
                    .register(meterRegistry)
            );
            timer.record(Duration.ofMillis(responseTime));

            logger.debug("Recorded health check metric: adapter={}, instance={}, healthy={}, responseTime={}ms", 
                        adapter, instance, healthy, responseTime);
        } catch (Exception e) {
            logger.warn("Failed to record health check metric: {}", e.getMessage());
        }
    }

    @Override
    public void recordRequestSize(String service, long requestSize, long responseSize) {
        try {
            String prefix = monitoringProperties.getPrefix();
            String metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";
            
            // 记录请求大小
            String requestSummaryKey = "request.size." + service;
            DistributionSummary requestSummary = summaries.computeIfAbsent(requestSummaryKey, key ->
                DistributionSummary.builder(metricPrefix + "request_size_bytes")
                    .tag("service", service)
                    .description("Request size in bytes")
                    .register(meterRegistry)
            );
            requestSummary.record(requestSize);

            // 记录响应大小
            String responseSummaryKey = "response.size." + service;
            DistributionSummary responseSummary = summaries.computeIfAbsent(responseSummaryKey, key ->
                DistributionSummary.builder(metricPrefix + "response_size_bytes")
                    .tag("service", service)
                    .description("Response size in bytes")
                    .register(meterRegistry)
            );
            responseSummary.record(responseSize);

            logger.debug("Recorded request size metric: service={}, requestSize={}, responseSize={}", 
                        service, requestSize, responseSize);
        } catch (Exception e) {
            logger.warn("Failed to record request size metric: {}", e.getMessage());
        }
    }

    /**
     * 将熔断器状态转换为数值
     */
    private long getStateValue(String state) {
        switch (state.toUpperCase()) {
            case "CLOSED":
                return 0;
            case "OPEN":
                return 1;
            case "HALF_OPEN":
                return 2;
            default:
                return -1;
        }
    }
}