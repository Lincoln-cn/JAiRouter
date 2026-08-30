package org.unreal.modelrouter.monitor.monitoring.collector;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.config.core.MonitoringProperties;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 追踪指标委托助手
 * 
 * 包含从 DefaultMetricsCollector 中提取的追踪相关指标方法和缓存指标方法。
 * 不是 Spring Bean，由 collector 直接实例化。
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
final class TracingMetricsDelegate {

    private static final Logger logger = LoggerFactory.getLogger(TracingMetricsDelegate.class);

    private final MeterRegistry meterRegistry;
    private final MonitoringProperties monitoringProperties;
    private final ConcurrentHashMap<String, Counter> counters;
    private final ConcurrentHashMap<String, Timer> timers;
    private final ConcurrentHashMap<String, DistributionSummary> summaries;
    private final ConcurrentHashMap<String, AtomicReference<Double>> usageGauges;

    TracingMetricsDelegate(
            final MeterRegistry meterRegistry,
            final MonitoringProperties monitoringProperties,
            final ConcurrentHashMap<String, Counter> counters,
            final ConcurrentHashMap<String, Timer> timers,
            final ConcurrentHashMap<String, DistributionSummary> summaries,
            final ConcurrentHashMap<String, AtomicReference<Double>> usageGauges) {
        this.meterRegistry = meterRegistry;
        this.monitoringProperties = monitoringProperties;
        this.counters = counters;
        this.timers = timers;
        this.summaries = summaries;
        this.usageGauges = usageGauges;
    }

    void recordTrace(final String traceId, final String spanId,
                     final String operationName, final long duration,
                     final boolean success) {
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

    void recordTraceExport(final String exporterType, final long duration,
                           final boolean success, final int batchSize) {
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

    void recordTraceSampling(final double samplingRate, final boolean sampled) {
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

    void recordTraceDataQuality(final String traceId, final int spanCount,
                                final int attributeCount, final int errorCount) {
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

            logger.debug("Recorded trace data quality metric: traceId={}, "
                    + "spanCount={}, attributeCount={}, errorCount={}",
                    traceId, spanCount, attributeCount, errorCount);
        } catch (Exception e) {
            logger.warn("Failed to record trace data quality metric: {}", e.getMessage());
        }
    }

    void recordTraceProcessing(final String processorName, final long duration, final boolean success) {
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

    void recordTraceAnalysis(final String analyzerName, final int spanCount,
                             final long duration, final boolean success) {
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

    // ==================== v2.9.0: KV 缓存指标 ====================

    void recordCacheTokenUsage(final String adapter, final String instance,
                               final long cacheHitTokens, final long cacheMissTokens) {
        try {
            String prefix = monitoringProperties.getPrefix();
            String metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";

            // 缓存命中 token 计数器
            if (cacheHitTokens > 0) {
                String counterKey = "cache.hit.tokens.total." + adapter + "." + instance;
                Counter counter = counters.computeIfAbsent(counterKey, key ->
                    Counter.builder(metricPrefix + "cache_hit_tokens_total")
                        .tag("adapter", adapter)
                        .tag("instance", instance)
                        .description("Total KV cache hit tokens")
                        .register(meterRegistry)
                );
                counter.increment(cacheHitTokens);
            }

            // 缓存未命中 token 计数器
            if (cacheMissTokens > 0) {
                String counterKey = "cache.miss.tokens.total." + adapter + "." + instance;
                Counter counter = counters.computeIfAbsent(counterKey, key ->
                    Counter.builder(metricPrefix + "cache_miss_tokens_total")
                        .tag("adapter", adapter)
                        .tag("instance", instance)
                        .description("Total KV cache miss tokens")
                        .register(meterRegistry)
                );
                counter.increment(cacheMissTokens);
            }

            // 缓存命中率 Gauge(按实例聚合，基于累积计数器)
            String hitCounterKey = "cache.hit.tokens.total." + adapter + "." + instance;
            String missCounterKey = "cache.miss.tokens.total." + adapter + "." + instance;
            Counter hitCounter = counters.get(hitCounterKey);
            Counter missCounter = counters.get(missCounterKey);
            double hitTotal = hitCounter != null ? hitCounter.count() : 0;
            double missTotal = missCounter != null ? missCounter.count() : 0;
            double cumulativeTotal = hitTotal + missTotal;
            if (cumulativeTotal > 0) {
                double hitRatio = hitTotal / cumulativeTotal;
                String gaugeKey = "cache.hit.ratio." + adapter + "." + instance;
                AtomicReference<Double> ratioRef = usageGauges.computeIfAbsent(gaugeKey, key -> {
                    AtomicReference<Double> ref = new AtomicReference<>(hitRatio);
                    Gauge.builder(metricPrefix + "cache_hit_ratio", ref, AtomicReference::get)
                        .tag("adapter", adapter)
                        .tag("instance", instance)
                        .description("KV cache hit ratio (0.0 ~ 1.0)")
                        .register(meterRegistry);
                    return ref;
                });
                ratioRef.set(hitRatio);
            }

            logger.debug("Recorded cache token usage: adapter={}, instance={}, hit={}, miss={}",
                    adapter, instance, cacheHitTokens, cacheMissTokens);
        } catch (Exception e) {
            logger.warn("Failed to record cache token usage: {}", e.getMessage());
        }
    }
}
