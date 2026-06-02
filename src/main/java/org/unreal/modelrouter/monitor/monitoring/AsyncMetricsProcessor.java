package org.unreal.modelrouter.monitor.monitoring;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.core.MonitoringProperties;
import org.unreal.modelrouter.monitor.monitoring.circuitbreaker.MetricsCircuitBreaker;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitor.monitoring.config.MonitoringEnabledCondition;
import org.unreal.modelrouter.monitor.monitoring.event.BackendCallMetricsEvent;
import org.unreal.modelrouter.monitor.monitoring.event.CircuitBreakerMetricsEvent;
import org.unreal.modelrouter.monitor.monitoring.event.HealthCheckMetricsEvent;
import org.unreal.modelrouter.monitor.monitoring.event.LoadBalancerMetricsEvent;
import org.unreal.modelrouter.monitor.monitoring.event.MetricsEvent;
import org.unreal.modelrouter.monitor.monitoring.event.MetricsType;
import org.unreal.modelrouter.monitor.monitoring.event.ProcessingStats;
import org.unreal.modelrouter.monitor.monitoring.event.RateLimitMetricsEvent;
import org.unreal.modelrouter.monitor.monitoring.event.RequestMetricsEvent;
import org.unreal.modelrouter.monitor.monitoring.event.RequestSizeMetricsEvent;
import org.unreal.modelrouter.monitor.monitoring.event.TraceAnalysisMetricsEvent;
import org.unreal.modelrouter.monitor.monitoring.event.TraceDataQualityMetricsEvent;
import org.unreal.modelrouter.monitor.monitoring.event.TraceExportMetricsEvent;
import org.unreal.modelrouter.monitor.monitoring.event.TraceMetricsEvent;
import org.unreal.modelrouter.monitor.monitoring.event.TraceProcessingMetricsEvent;
import org.unreal.modelrouter.monitor.monitoring.event.TraceSamplingMetricsEvent;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步指标处理器
 * 提供非阻塞的指标收集处理，避免影响主业务流程
 */
@Component
@Conditional(MonitoringEnabledCondition.class)
public class AsyncMetricsProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AsyncMetricsProcessor.class);

    private final MonitoringProperties monitoringProperties;
    private final MetricsCollector metricsCollector;
    private final MetricsCircuitBreaker circuitBreaker;

    // 异步处理相关
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;
    private final BlockingQueue<MetricsEvent> metricsQueue;
    private final AtomicBoolean running = new AtomicBoolean(true);

    // 统计信息
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong droppedCount = new AtomicLong(0);
    private final AtomicLong queueSize = new AtomicLong(0);

    // 安全随机数生成器
    private final SecureRandom secureRandom = new SecureRandom();

    public AsyncMetricsProcessor(final MonitoringProperties monitoringProperties,
                               @Lazy final MetricsCollector metricsCollector,
                               final MetricsCircuitBreaker circuitBreaker) {
        this.monitoringProperties = monitoringProperties;
        this.metricsCollector = metricsCollector;
        this.circuitBreaker = circuitBreaker;

        int bufferSize = monitoringProperties.getPerformance().getBufferSize();
        this.metricsQueue = new ArrayBlockingQueue<>(bufferSize);

        // 创建线程池
        this.executorService = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "metrics-processor");
            t.setDaemon(true);
            return t;
        });

        this.scheduledExecutorService = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "metrics-scheduler");
            t.setDaemon(true);
            return t;
        });

        // 启动处理器
        startProcessing();

        logger.info("AsyncMetricsProcessor initialized with buffer size: {}", bufferSize);
    }

    /**
     * 异步记录请求指标
     */
    public void recordRequestAsync(final String service, final String method, final long duration, final String status) {
        if (!shouldSample(MetricsType.REQUEST)) {
            return;
        }
        submitEvent(new RequestMetricsEvent(service, method, duration, status));
    }

    /**
     * 异步记录后端调用指标
     */
    public void recordBackendCallAsync(final String adapter, final String instance, final long duration, final boolean success) {
        if (!shouldSample(MetricsType.BACKEND)) {
            return;
        }
        submitEvent(new BackendCallMetricsEvent(adapter, instance, duration, success));
    }

    /**
     * 异步记录限流器指标
     */
    public void recordRateLimitAsync(final String service, final String algorithm, final boolean allowed) {
        if (!shouldSample(MetricsType.INFRASTRUCTURE)) {
            return;
        }
        submitEvent(new RateLimitMetricsEvent(service, algorithm, allowed));
    }

    /**
     * 异步记录熔断器指标
     */
    public void recordCircuitBreakerAsync(final String service, final String state, final String event) {
        if (!shouldSample(MetricsType.INFRASTRUCTURE)) {
            return;
        }
        submitEvent(new CircuitBreakerMetricsEvent(service, state, event));
    }

    /**
     * 异步记录负载均衡器指标
     */
    public void recordLoadBalancerAsync(final String service, final String strategy, final String selectedInstance) {
        if (!shouldSample(MetricsType.INFRASTRUCTURE)) {
            return;
        }
        submitEvent(new LoadBalancerMetricsEvent(service, strategy, selectedInstance));
    }

    /**
     * 异步记录健康检查指标
     */
    public void recordHealthCheckAsync(final String adapter, final String instance, final boolean healthy, final long responseTime) {
        if (!shouldSample(MetricsType.INFRASTRUCTURE)) {
            return;
        }
        submitEvent(new HealthCheckMetricsEvent(adapter, instance, healthy, responseTime));
    }

    /**
     * 异步记录请求大小指标
     */
    public void recordRequestSizeAsync(final String service, final long requestSize, final long responseSize) {
        if (!shouldSample(MetricsType.REQUEST)) {
            return;
        }
        submitEvent(new RequestSizeMetricsEvent(service, requestSize, responseSize));
    }

    /**
     * 异步记录追踪指标
     */
    public void recordTraceAsync(final String traceId, final String spanId, final String operationName, final long duration, final boolean success) {
        if (!shouldSample(MetricsType.TRACE)) {
            return;
        }
        submitEvent(new TraceMetricsEvent(traceId, spanId, operationName, duration, success));
    }

    /**
     * 异步记录追踪处理指标
     */
    public void recordTraceProcessingAsync(final String processorName, final long duration, final boolean success) {
        if (!shouldSample(MetricsType.TRACE_PROCESSING)) {
            return;
        }
        submitEvent(new TraceProcessingMetricsEvent(processorName, duration, success));
    }

    /**
     * 异步记录追踪分析指标
     */
    public void recordTraceAnalysisAsync(final String analyzerName, final int spanCount, final long duration, final boolean success) {
        if (!shouldSample(MetricsType.TRACE_ANALYSIS)) {
            return;
        }
        submitEvent(new TraceAnalysisMetricsEvent(analyzerName, spanCount, duration, success));
    }

    /**
     * 异步记录追踪导出指标
     */
    public void recordTraceExportAsync(final String exporterType, final long duration, final boolean success, final int batchSize) {
        if (!shouldSample(MetricsType.TRACE)) {
            return;
        }
        submitEvent(new TraceExportMetricsEvent(exporterType, duration, success, batchSize));
    }

    /**
     * 异步记录追踪采样指标
     */
    public void recordTraceSamplingAsync(final double samplingRate, final boolean sampled) {
        submitEvent(new TraceSamplingMetricsEvent(samplingRate, sampled));
    }

    /**
     * 异步记录追踪数据质量指标
     */
    public void recordTraceDataQualityAsync(final String traceId, final int spanCount, final int attributeCount, final int errorCount) {
        if (!shouldSample(MetricsType.TRACE)) {
            return;
        }
        submitEvent(new TraceDataQualityMetricsEvent(traceId, spanCount, attributeCount, errorCount));
    }

    /**
     * 提交指标事件到队列
     */
    private void submitEvent(final MetricsEvent event) {
        if (!circuitBreaker.allowRequest()) {
            droppedCount.incrementAndGet();
            logger.debug("Metrics event dropped due to circuit breaker: {}", event.getClass().getSimpleName());
            return;
        }

        boolean offered = metricsQueue.offer(event);
        if (offered) {
            queueSize.incrementAndGet();
        } else {
            droppedCount.incrementAndGet();
            logger.warn("Metrics queue is full, dropping event: {}", event.getClass().getSimpleName());
        }
    }

    /**
     * 检查是否应该采样
     */
    private boolean shouldSample(final MetricsType type) {
        double samplingRate = getSamplingRate(type);
        return secureRandom.nextDouble() < samplingRate;
    }

    /**
     * 获取采样率
     */
    private double getSamplingRate(final MetricsType type) {
        MonitoringProperties.Sampling sampling = monitoringProperties.getSampling();
        switch (type) {
            case REQUEST:
                return sampling.getRequestMetrics();
            case BACKEND:
                return sampling.getBackendMetrics();
            case INFRASTRUCTURE:
                return sampling.getInfrastructureMetrics();
            case TRACE:
                return sampling.getTraceMetrics();
            case TRACE_PROCESSING:
                return sampling.getTraceProcessingMetrics();
            case TRACE_ANALYSIS:
                return sampling.getTraceAnalysisMetrics();
            default:
                return 1.0;
        }
    }

    /**
     * 启动异步处理
     */
    private void startProcessing() {
        executorService.submit(this::processBatch);
        scheduledExecutorService.scheduleAtFixedRate(this::logStatistics, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * 批量处理指标事件
     */
    private void processBatch() {
        List<MetricsEvent> batch = new ArrayList<>();
        int batchSize = monitoringProperties.getPerformance().getBatchSize();

        while (running.get()) {
            try {
                MetricsEvent event = metricsQueue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    batch.add(event);
                    queueSize.decrementAndGet();

                    while (batch.size() < batchSize) {
                        MetricsEvent nextEvent = metricsQueue.poll();
                        if (nextEvent == null) {
                            break;
                        }
                        batch.add(nextEvent);
                        queueSize.decrementAndGet();
                    }

                    processBatchEvents(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Metrics processor interrupted");
                break;
            } catch (Exception e) {
                logger.error("Error processing metrics batch", e);
                circuitBreaker.recordFailure();
            }
        }
    }

    /**
     * 处理批量指标事件
     */
    private void processBatchEvents(final List<MetricsEvent> events) {
        try {
            for (MetricsEvent event : events) {
                processEvent(event);
                processedCount.incrementAndGet();
            }
            circuitBreaker.recordSuccess();
        } catch (Exception e) {
            logger.error("Error processing metrics events", e);
            circuitBreaker.recordFailure();
            throw e;
        }
    }

    /**
     * 处理单个指标事件
     */
    private void processEvent(final MetricsEvent event) {
        try {
            if (event instanceof RequestMetricsEvent) {
                RequestMetricsEvent req = (RequestMetricsEvent) event;
                metricsCollector.recordRequest(req.getService(), req.getMethod(), req.getDuration(), req.getStatus());
            } else if (event instanceof BackendCallMetricsEvent) {
                BackendCallMetricsEvent backend = (BackendCallMetricsEvent) event;
                metricsCollector.recordBackendCall(backend.getAdapter(), backend.getInstance(), backend.getDuration(), backend.isSuccess());
            } else if (event instanceof RateLimitMetricsEvent) {
                RateLimitMetricsEvent rateLimit = (RateLimitMetricsEvent) event;
                metricsCollector.recordRateLimit(rateLimit.getService(), rateLimit.getAlgorithm(), rateLimit.isAllowed());
            } else if (event instanceof CircuitBreakerMetricsEvent) {
                CircuitBreakerMetricsEvent cb = (CircuitBreakerMetricsEvent) event;
                metricsCollector.recordCircuitBreaker(cb.getService(), cb.getState(), cb.getEvent());
            } else if (event instanceof LoadBalancerMetricsEvent) {
                LoadBalancerMetricsEvent lb = (LoadBalancerMetricsEvent) event;
                metricsCollector.recordLoadBalancer(lb.getService(), lb.getStrategy(), lb.getSelectedInstance());
            } else if (event instanceof HealthCheckMetricsEvent) {
                HealthCheckMetricsEvent health = (HealthCheckMetricsEvent) event;
                metricsCollector.recordHealthCheck(health.getAdapter(), health.getInstance(), health.isHealthy(), health.getResponseTime());
            } else if (event instanceof RequestSizeMetricsEvent) {
                RequestSizeMetricsEvent size = (RequestSizeMetricsEvent) event;
                metricsCollector.recordRequestSize(size.getService(), size.getRequestSize(), size.getResponseSize());
            } else if (event instanceof TraceMetricsEvent) {
                TraceMetricsEvent trace = (TraceMetricsEvent) event;
                metricsCollector.recordTrace(trace.getTraceId(), trace.getSpanId(), trace.getOperationName(), trace.getDuration(), trace.isSuccess());
            } else if (event instanceof TraceExportMetricsEvent) {
                TraceExportMetricsEvent traceExport = (TraceExportMetricsEvent) event;
                metricsCollector.recordTraceExport(traceExport.getExporterType(), traceExport.getDuration(), traceExport.isSuccess(), traceExport.getBatchSize());
            } else if (event instanceof TraceSamplingMetricsEvent) {
                TraceSamplingMetricsEvent traceSampling = (TraceSamplingMetricsEvent) event;
                metricsCollector.recordTraceSampling(traceSampling.getSamplingRate(), traceSampling.isSampled());
            } else if (event instanceof TraceDataQualityMetricsEvent) {
                TraceDataQualityMetricsEvent traceDataQuality = (TraceDataQualityMetricsEvent) event;
                metricsCollector.recordTraceDataQuality(traceDataQuality.getTraceId(), traceDataQuality.getSpanCount(), traceDataQuality.getAttributeCount(), traceDataQuality.getErrorCount());
            } else if (event instanceof TraceProcessingMetricsEvent) {
                TraceProcessingMetricsEvent traceProcessing = (TraceProcessingMetricsEvent) event;
                metricsCollector.recordTraceProcessing(traceProcessing.getProcessorName(), traceProcessing.getDuration(), traceProcessing.isSuccess());
            } else if (event instanceof TraceAnalysisMetricsEvent) {
                TraceAnalysisMetricsEvent traceAnalysis = (TraceAnalysisMetricsEvent) event;
                metricsCollector.recordTraceAnalysis(traceAnalysis.getAnalyzerName(), traceAnalysis.getSpanCount(), traceAnalysis.getDuration(), traceAnalysis.isSuccess());
            }
        } catch (Exception e) {
            logger.warn("Failed to process metrics event: {}", e.getMessage());
        }
    }

    /**
     * 记录统计信息
     */
    private void logStatistics() {
        logger.info("Metrics processor statistics - Processed: {}, Dropped: {}, Queue size: {}, Circuit breaker state: {}",
                   processedCount.get(), droppedCount.get(), queueSize.get(), circuitBreaker.getState());
    }

    /**
     * 获取处理统计信息
     */
    public ProcessingStats getStats() {
        return new ProcessingStats(
            processedCount.get(),
            droppedCount.get(),
            queueSize.get(),
            circuitBreaker.getState()
        );
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down AsyncMetricsProcessor");
        running.set(false);

        executorService.shutdown();
        scheduledExecutorService.shutdown();

        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            scheduledExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("AsyncMetricsProcessor shutdown completed");
    }
}
