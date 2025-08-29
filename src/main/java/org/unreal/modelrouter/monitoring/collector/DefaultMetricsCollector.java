package org.unreal.modelrouter.monitoring.collector;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Conditional;
import org.unreal.modelrouter.config.MonitoringProperties;
import org.unreal.modelrouter.monitoring.config.MonitoringEnabledCondition;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Override
    public void recordTrace(String traceId, String spanId, String operationName, long duration, boolean success) {
        try {
            String prefix = monitoringProperties.getPrefix();
            String metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";
            String status = success ? "success" : "failure";

            // 记录追踪操作总数
            String counterKey = "tracing.operations.total." + operationName + "." + status;
            Counter counter = counters.computeIfAbsent(counterKey, key ->
                Counter.builder(metricPrefix + "tracing_operations_total")
                    .tag("operation", operationName)
                    .tag("status", status)
                    .description("Total number of tracing operations")
                    .register(meterRegistry)
            );
            counter.increment();

            // 记录追踪操作耗时
            String timerKey = "tracing.operation.duration." + operationName;
            Timer timer = timers.computeIfAbsent(timerKey, key ->
                Timer.builder(metricPrefix + "tracing_operation_duration_seconds")
                    .tag("operation", operationName)
                    .description("Tracing operation duration in seconds")
                    .register(meterRegistry)
            );
            timer.record(Duration.ofMillis(duration));

            logger.debug("Recorded trace metric: operation={}, duration={}ms, success={}",
                    operationName, duration, success);
        } catch (Exception e) {
            logger.warn("Failed to record trace metric: {}", e.getMessage());
        }
    }

    @Override
    public void recordTraceExport(String exporterType, long duration, boolean success, int batchSize) {
        try {
            String prefix = monitoringProperties.getPrefix();
            String metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";
            String status = success ? "success" : "failure";

            // 记录追踪导出事件总数
            String counterKey = "tracing.export.total." + exporterType + "." + status;
            Counter counter = counters.computeIfAbsent(counterKey, key ->
                Counter.builder(metricPrefix + "tracing_export_total")
                    .tag("exporter", exporterType)
                    .tag("status", status)
                    .description("Total number of tracing export events")
                    .register(meterRegistry)
            );
            counter.increment();

            // 记录追踪导出耗时
            String timerKey = "tracing.export.duration." + exporterType;
            Timer timer = timers.computeIfAbsent(timerKey, key ->
                Timer.builder(metricPrefix + "tracing_export_duration_seconds")
                    .tag("exporter", exporterType)
                    .description("Tracing export duration in seconds")
                    .register(meterRegistry)
            );
            timer.record(Duration.ofMillis(duration));

            // 记录批次大小
            String summaryKey = "tracing.export.batch.size." + exporterType;
            DistributionSummary summary = summaries.computeIfAbsent(summaryKey, key ->
                DistributionSummary.builder(metricPrefix + "tracing_export_batch_size")
                    .tag("exporter", exporterType)
                    .description("Tracing export batch size")
                    .register(meterRegistry)
            );
            summary.record(batchSize);

            logger.debug("Recorded trace export metric: exporter={}, duration={}ms, success={}, batchSize={}",
                    exporterType, duration, success, batchSize);
        } catch (Exception e) {
            logger.warn("Failed to record trace export metric: {}", e.getMessage());
        }
    }

    @Override
    public void recordTraceSampling(double samplingRate, boolean sampled) {
        try {
            String prefix = monitoringProperties.getPrefix();
            String metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";
            String result = sampled ? "sampled" : "dropped";

            // 记录采样决策
            String counterKey = "tracing.sampling.decisions." + result;
            Counter counter = counters.computeIfAbsent(counterKey, key ->
                Counter.builder(metricPrefix + "tracing_sampling_decisions_total")
                    .tag("decision", result)
                    .tag("sampling_rate", String.valueOf(samplingRate))
                    .description("Total number of tracing sampling decisions")
                    .register(meterRegistry)
            );
            counter.increment();

            logger.debug("Recorded trace sampling metric: samplingRate={}, sampled={}",
                    samplingRate, sampled);
        } catch (Exception e) {
            logger.warn("Failed to record trace sampling metric: {}", e.getMessage());
        }
    }

    @Override
    public void recordTraceDataQuality(String traceId, int spanCount, int attributeCount, int errorCount) {
        try {
            String prefix = monitoringProperties.getPrefix();
            String metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";

            // 记录Span数量
            String spanSummaryKey = "tracing.data.quality.span.count";
            DistributionSummary spanSummary = summaries.computeIfAbsent(spanSummaryKey, key ->
                DistributionSummary.builder(metricPrefix + "tracing_data_quality_span_count")
                    .description("Number of spans per trace")
                    .register(meterRegistry)
            );
            spanSummary.record(spanCount);

            // 记录属性数量
            String attributeSummaryKey = "tracing.data.quality.attribute.count";
            DistributionSummary attributeSummary = summaries.computeIfAbsent(attributeSummaryKey, key ->
                DistributionSummary.builder(metricPrefix + "tracing_data_quality_attribute_count")
                    .description("Number of attributes per trace")
                    .register(meterRegistry)
            );
            attributeSummary.record(attributeCount);

            // 记录错误数量
            String errorSummaryKey = "tracing.data.quality.error.count";
            DistributionSummary errorSummary = summaries.computeIfAbsent(errorSummaryKey, key ->
                DistributionSummary.builder(metricPrefix + "tracing_data_quality_error_count")
                    .description("Number of errors per trace")
                    .register(meterRegistry)
            );
            errorSummary.record(errorCount);

            logger.debug("Recorded trace data quality metric: traceId={}, spanCount={}, attributeCount={}, errorCount={}",
                    traceId, spanCount, attributeCount, errorCount);
        } catch (Exception e) {
            logger.warn("Failed to record trace data quality metric: {}", e.getMessage());
        }
    }

    @Override
    public void recordTraceProcessing(String processorName, long duration, boolean success) {
        try {
            String prefix = monitoringProperties.getPrefix();
            String metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";
            String status = success ? "success" : "failure";

            // 记录追踪处理事件总数
            String counterKey = "tracing.processing.total." + processorName + "." + status;
            Counter counter = counters.computeIfAbsent(counterKey, key ->
                Counter.builder(metricPrefix + "tracing_processing_total")
                    .tag("processor", processorName)
                    .tag("status", status)
                    .description("Total number of tracing processing events")
                    .register(meterRegistry)
            );
            counter.increment();

            // 记录追踪处理耗时
            String timerKey = "tracing.processing.duration." + processorName;
            Timer timer = timers.computeIfAbsent(timerKey, key ->
                Timer.builder(metricPrefix + "tracing_processing_duration_seconds")
                    .tag("processor", processorName)
                    .description("Tracing processing duration in seconds")
                    .register(meterRegistry)
            );
            timer.record(Duration.ofMillis(duration));

            logger.debug("Recorded trace processing metric: processor={}, duration={}ms, success={}",
                    processorName, duration, success);
        } catch (Exception e) {
            logger.warn("Failed to record trace processing metric: {}", e.getMessage());
        }
    }

    @Override
    public void recordTraceAnalysis(String analyzerName, int spanCount, long duration, boolean success) {
        try {
            String prefix = monitoringProperties.getPrefix();
            String metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";
            String status = success ? "success" : "failure";

            // 记录追踪分析事件总数
            String counterKey = "tracing.analysis.total." + analyzerName + "." + status;
            Counter counter = counters.computeIfAbsent(counterKey, key ->
                Counter.builder(metricPrefix + "tracing_analysis_total")
                    .tag("analyzer", analyzerName)
                    .tag("status", status)
                    .description("Total number of tracing analysis events")
                    .register(meterRegistry)
            );
            counter.increment();

            // 记录追踪分析耗时
            String timerKey = "tracing.analysis.duration." + analyzerName;
            Timer timer = timers.computeIfAbsent(timerKey, key ->
                Timer.builder(metricPrefix + "tracing_analysis_duration_seconds")
                    .tag("analyzer", analyzerName)
                    .description("Tracing analysis duration in seconds")
                    .register(meterRegistry)
            );
            timer.record(Duration.ofMillis(duration));

            // 记录Span数量
            String summaryKey = "tracing.analysis.span.count." + analyzerName;
            DistributionSummary summary = summaries.computeIfAbsent(summaryKey, key ->
                DistributionSummary.builder(metricPrefix + "tracing_analysis_span_count")
                    .tag("analyzer", analyzerName)
                    .description("Number of spans analyzed")
                    .register(meterRegistry)
            );
            summary.record(spanCount);

            logger.debug("Recorded trace analysis metric: analyzer={}, spanCount={}, duration={}ms, success={}",
                    analyzerName, spanCount, duration, success);
        } catch (Exception e) {
            logger.warn("Failed to record trace analysis metric: {}", e.getMessage());
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