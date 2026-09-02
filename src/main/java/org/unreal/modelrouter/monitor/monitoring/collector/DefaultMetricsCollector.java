package org.unreal.modelrouter.monitor.monitoring.collector;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.core.MonitoringProperties;
import org.unreal.modelrouter.monitor.monitoring.config.MonitoringEnabledCondition;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    private final ConcurrentHashMap<String, AtomicReference<Double>> usageGauges = new ConcurrentHashMap<>();

    // 委托助手，包含追踪指标和缓存指标方法
    private final TracingMetricsDelegate tracingDelegate;

    public DefaultMetricsCollector(final MeterRegistry meterRegistry, final MonitoringProperties monitoringProperties) {
        this.meterRegistry = meterRegistry;
        this.monitoringProperties = monitoringProperties;
        this.tracingDelegate = new TracingMetricsDelegate(
                meterRegistry, monitoringProperties, counters, timers, summaries, usageGauges);
        logger.info("DefaultMetricsCollector initialized with prefix: {}", monitoringProperties.getPrefix());
    }

    @Override
    public void recordRequest(final String service, final String method, final long duration, final String status) {
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
    public void recordBackendCall(final String adapter, final String instance,
                                   final long duration, final boolean success) {
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
    public void recordRateLimit(final String service, final String algorithm, final boolean allowed) {
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
    public void recordCircuitBreaker(final String service, final String state, final String event) {
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
    public void recordLoadBalancer(final String service, final String strategy, final String selectedInstance) {
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
    public void recordHealthCheck(final String adapter, final String instance,
                                   final boolean healthy, final long responseTime) {
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
    public void recordRequestSize(final String service, final long requestSize, final long responseSize) {
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

    @Override
    public void recordTrace(final String traceId, final String spanId,
                             final String operationName, final long duration,
                             final boolean success) {
        tracingDelegate.recordTrace(traceId, spanId, operationName, duration, success);
    }

    @Override
    public void recordTraceExport(final String exporterType, final long duration,
                                   final boolean success, final int batchSize) {
        tracingDelegate.recordTraceExport(exporterType, duration, success, batchSize);
    }

    @Override
    public void recordTraceSampling(final double samplingRate, final boolean sampled) {
        tracingDelegate.recordTraceSampling(samplingRate, sampled);
    }

    @Override
    public void recordTraceDataQuality(final String traceId, final int spanCount,
                                        final int attributeCount, final int errorCount) {
        tracingDelegate.recordTraceDataQuality(traceId, spanCount, attributeCount, errorCount);
    }

    @Override
    public void recordTraceProcessing(final String processorName, final long duration, final boolean success) {
        tracingDelegate.recordTraceProcessing(processorName, duration, success);
    }

    @Override
    public void recordTraceAnalysis(final String analyzerName, final int spanCount,
                                     final long duration, final boolean success) {
        tracingDelegate.recordTraceAnalysis(analyzerName, spanCount, duration, success);
    }

    @Override
    public void recordRateLimitStatus(final String service, final String scope, final String algorithm,
                                       final long remainingCapacity, final double usageRatio) {
        try {
            String prefix = monitoringProperties.getPrefix();
            String metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";

            // 记录剩余容量 Gauge
            String remainingKey = "rate.limit.remaining." + service + "." + scope;
            AtomicLong remainingValue = gaugeValues.computeIfAbsent(remainingKey, key -> {
                AtomicLong value = new AtomicLong(remainingCapacity);
                Gauge.builder(metricPrefix + "rate_limit_remaining", value, AtomicLong::doubleValue)
                    .tag("service", service)
                    .tag("scope", scope)
                    .tag("algorithm", algorithm)
                    .description("Remaining capacity of rate limiter")
                    .register(meterRegistry);
                return value;
            });
            remainingValue.set(remainingCapacity);

            // 记录使用率 Gauge (使用 double 存储)
            String usageKey = "rate.limit.usage.ratio." + service + "." + scope;
            AtomicReference<Double> usageRef = usageGauges.computeIfAbsent(usageKey, key -> {
                AtomicReference<Double> ref = new AtomicReference<>(usageRatio);
                Gauge.builder(metricPrefix + "rate_limit_usage_ratio", ref, AtomicReference::get)
                    .tag("service", service)
                    .tag("scope", scope)
                    .tag("algorithm", algorithm)
                    .description("Rate limiter usage ratio (0.0 ~ 1.0)")
                    .register(meterRegistry);
                return ref;
            });
            usageRef.set(usageRatio);

            logger.debug("Recorded rate limit status: service={}, scope={}, remaining={}, usage={}",
                    service, scope, remainingCapacity, usageRatio);
        } catch (Exception e) {
            logger.warn("Failed to record rate limit status: {}", e.getMessage());
        }
    }

    @Override
    public void recordCacheTokenUsage(final String adapter, final String instance,
                                       final long cacheHitTokens, final long cacheMissTokens) {
        tracingDelegate.recordCacheTokenUsage(adapter, instance, cacheHitTokens, cacheMissTokens);
    }

    @Override
    public void recordResponseCacheHit(final String service, final String model) {
        tracingDelegate.recordResponseCacheHit(service, model);
    }

    @Override
    public void recordResponseCacheMiss(final String service, final String model) {
        tracingDelegate.recordResponseCacheMiss(service, model);
    }

    /**
     * 将熔断器状态转换为数值
     */
    private long getStateValue(final String state) {
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
