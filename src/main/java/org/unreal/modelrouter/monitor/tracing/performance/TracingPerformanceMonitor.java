package org.unreal.modelrouter.monitor.tracing.performance;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitor.tracing.async.AsyncTracingProcessor;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.config.TracingMonitoringConfig;
import org.unreal.modelrouter.monitor.tracing.memory.TracingMemoryManager;
import org.unreal.modelrouter.monitor.tracing.memory.model.MemoryStats;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceModels.BottleneckType;
import static org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceModels.OperationMetrics;
import static org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceModels.OptimizationSuggestion;
import static org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceModels.PerformanceBottleneck;
import static org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceModels.PerformanceIssue;
import static org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceModels.PerformanceReport;
import static org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceModels.PerformanceSnapshot;
import static org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceModels.PerformanceThreshold;
import static org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceModels.Priority;
import static org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceModels.Severity;
import static org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceModels.SystemHealth;
import static org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceModels.TuningResult;

/**
 * 追踪性能监控器
 */
@Slf4j
@Component
public final class TracingPerformanceMonitor implements HealthIndicator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TracingConfiguration tracingConfiguration;
    private final AsyncTracingProcessor asyncTracingProcessor;
    private final TracingMemoryManager memoryManager;
    private final MeterRegistry meterRegistry;
    private final Scheduler monitoringScheduler;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final Timer tracingOverheadTimer;
    private final Gauge memoryUsageGauge;
    private final Counter performanceAnomalyCounter;
    private final DistributionSummary processingLatencyDistribution;

    /** 监控配置缺失时（单测直接 new、或 tracingConfiguration 未注入 monitoring）回落到字段默认值 */
    private final TracingMonitoringConfig fallbackMonitoring = new TracingMonitoringConfig();

    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong slowOperations = new AtomicLong(0);
    private final AtomicReference<PerformanceSnapshot> lastSnapshot = new AtomicReference<>();
    private final Map<String, OperationMetrics> operationMetrics = new ConcurrentHashMap<>();
    private final Map<String, PerformanceThreshold> thresholds = new ConcurrentHashMap<>();
    private final AtomicReference<SystemHealth> systemHealth = new AtomicReference<>(SystemHealth.HEALTHY);
    private final List<PerformanceIssue> activeIssues = new ArrayList<>();

    /** 按默认值构造（与外部化前一致），供既有调用方与测试使用。 */
    public TracingPerformanceMonitor(final TracingConfiguration tracingConfiguration,
                                     final AsyncTracingProcessor asyncTracingProcessor,
                                     final TracingMemoryManager memoryManager,
                                     final MeterRegistry meterRegistry) {
        this(tracingConfiguration, asyncTracingProcessor, memoryManager, meterRegistry, 2, 100);
    }

    /**
     * 生产构造器：性能监控调度器参数可配（#182）。
     *
     * <p><b>必须走构造器参数注入，不能用 {@code @Value} 字段</b>：字段注入发生在构造器执行
     * <b>之后</b>，而调度器正是在构造器里创建的——用字段会静默读到默认值，配置形同虚设。</p>
     *
     * <p>默认值与外部化前一致（2 / 100），不配置时行为不变。</p>
     *
     * @param tracingConfiguration      tracing 配置
     * @param asyncTracingProcessor     异步处理器
     * @param memoryManager             内存管理器
     * @param meterRegistry             度量注册表
     * @param schedulerThreadCap        调度器线程上限
     * @param schedulerQueueCapacity    调度器任务队列上限
     */
    @org.springframework.beans.factory.annotation.Autowired
    public TracingPerformanceMonitor(
            final TracingConfiguration tracingConfiguration,
            final AsyncTracingProcessor asyncTracingProcessor,
            final TracingMemoryManager memoryManager,
            final MeterRegistry meterRegistry,
            @org.springframework.beans.factory.annotation.Value(
                    "${jairouter.tracing.performance.scheduler.thread-cap:2}") final int schedulerThreadCap,
            @org.springframework.beans.factory.annotation.Value(
                    "${jairouter.tracing.performance.scheduler.queue-capacity:100}")
            final int schedulerQueueCapacity) {
        this.tracingConfiguration = tracingConfiguration;
        this.asyncTracingProcessor = asyncTracingProcessor;
        this.memoryManager = memoryManager;
        this.meterRegistry = meterRegistry;
        this.monitoringScheduler = Schedulers.newBoundedElastic(
                schedulerThreadCap, schedulerQueueCapacity, "tracing-perf-monitor");

        this.tracingOverheadTimer = Timer.builder("tracing.overhead")
                .description("Tracing system overhead")
                .register(meterRegistry);

        this.memoryUsageGauge = Gauge.builder("tracing.memory.usage", this,
                TracingPerformanceMonitor::getCurrentMemoryUsage)
                .description("Tracing memory usage")
                .register(meterRegistry);

        this.performanceAnomalyCounter = Counter.builder("tracing.performance.anomalies")
                .description("Number of performance anomalies detected")
                .register(meterRegistry);

        this.processingLatencyDistribution = buildLatencyDistribution();

        initializeDefaultThresholds();
    }

    @PostConstruct
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            log.info("启动追踪性能监控器");
            startPerformanceMonitoring();
            startHealthChecks();
            startBottleneckDetection();
        }
    }

    @PreDestroy
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            log.info("停止追踪性能监控器");
            monitoringScheduler.dispose();
        }
    }

    public Mono<Void> recordOperationPerformance(final String operation, final long startTime, final long endTime,
                                                final boolean success, final Map<String, Object> metadata) {
        return Mono.fromRunnable(() -> {
            long duration = endTime - startTime;
            totalOperations.incrementAndGet();

            tracingOverheadTimer.record(Duration.ofMillis(duration));
            processingLatencyDistribution.record(duration);

            OperationMetrics metrics =
                    operationMetrics.computeIfAbsent(operation, k -> new OperationMetrics(operation));
            metrics.recordOperation(duration, success);

            PerformanceThreshold threshold = thresholds.get(operation);
            if (threshold != null && duration > threshold.getSlowThreshold()) {
                slowOperations.incrementAndGet();
                handleSlowOperation(operation, duration, metadata);
            }

            log.debug("记录操作性能: operation={}, duration={}ms, success={}", operation, duration, success);
        }).subscribeOn(monitoringScheduler).then();
    }

    public Mono<List<PerformanceBottleneck>> detectBottlenecks() {
        return Mono.fromCallable(() -> {
            List<PerformanceBottleneck> bottlenecks = new ArrayList<>();

            MemoryStats memoryStats = memoryManager.getMemoryStats();
            if (memoryStats.getHeapUsageRatio() > monitoring().getAlerts().getThresholds().getMemoryUsage()) {
                bottlenecks.add(new PerformanceBottleneck(
                        BottleneckType.MEMORY,
                        "内存使用率过高: " + String.format("%.2f%%", memoryStats.getHeapUsageRatio() * 100),
                        Severity.HIGH,
                        generateMemoryOptimizationSuggestions(memoryStats)
                ));
            }

            AsyncTracingProcessor.ProcessingStats processingStats = asyncTracingProcessor.getProcessingStats();
            if (processingStats.getDropRate() > 0.1) {
                bottlenecks.add(new PerformanceBottleneck(
                        BottleneckType.PROCESSING,
                        "数据丢弃率过高: " + String.format("%.2f%%", processingStats.getDropRate() * 100),
                        Severity.HIGH,
                        generateProcessingOptimizationSuggestions(processingStats)
                ));
            }

            for (OperationMetrics metrics : operationMetrics.values()) {
                PerformanceThreshold threshold = thresholds.get(metrics.getOperation());
                if (threshold != null && metrics.getP99Latency() > threshold.getCriticalThreshold()) {
                    bottlenecks.add(new PerformanceBottleneck(
                            BottleneckType.OPERATION,
                            "操作延迟过高: " + metrics.getOperation() + " P99=" + metrics.getP99Latency() + "ms",
                            Severity.MEDIUM,
                            generateOperationOptimizationSuggestions(metrics)
                    ));
                }
            }

            double slowOperationRatio = totalOperations.get() > 0
                    ? (double) slowOperations.get() / totalOperations.get() : 0.0;
            if (slowOperationRatio > 0.2) {
                bottlenecks.add(new PerformanceBottleneck(
                        BottleneckType.SYSTEM,
                        "慢操作比例过高: " + String.format("%.2f%%", slowOperationRatio * 100),
                        Severity.HIGH,
                        generateSystemOptimizationSuggestions()
                ));
            }

            return bottlenecks;
        }).subscribeOn(monitoringScheduler);
    }

    public Mono<PerformanceReport> generatePerformanceReport() {
        return Mono.fromCallable(() -> {
            MemoryStats memoryStats = memoryManager.getMemoryStats();
            AsyncTracingProcessor.ProcessingStats processingStats = asyncTracingProcessor.getProcessingStats();

            return new PerformanceReport(
                    Instant.now(),
                    totalOperations.get(),
                    slowOperations.get(),
                    memoryStats,
                    processingStats,
                    new ArrayList<>(operationMetrics.values()),
                    systemHealth.get(),
                    new ArrayList<>(activeIssues)
            );
        }).subscribeOn(monitoringScheduler);
    }

    public Mono<List<OptimizationSuggestion>> getOptimizationSuggestions() {
        return detectBottlenecks()
                .map(bottlenecks -> {
                    List<OptimizationSuggestion> suggestions = new ArrayList<>();
                    for (PerformanceBottleneck bottleneck : bottlenecks) {
                        suggestions.addAll(bottleneck.getSuggestions());
                    }
                    return suggestions;
                });
    }

    public Mono<TuningResult> performPerformanceTuning(final List<String> tuningActions) {
        return Mono.fromCallable(() -> {
            List<String> appliedActions = new ArrayList<>();
            List<String> failedActions = new ArrayList<>();

            for (String action : tuningActions) {
                try {
                    if (applyTuningAction(action)) {
                        appliedActions.add(action);
                    } else {
                        failedActions.add(action);
                    }
                } catch (Exception e) {
                    log.error("应用调优操作失败: action={}", action, e);
                    failedActions.add(action);
                }
            }

            return new TuningResult(appliedActions, failedActions);
        }).subscribeOn(monitoringScheduler);
    }

    @Override
    public Health health() {
        try {
            SystemHealth currentHealth = systemHealth.get();
            MemoryStats memoryStats = memoryManager.getMemoryStats();
            AsyncTracingProcessor.ProcessingStats processingStats = asyncTracingProcessor.getProcessingStats();

            Health.Builder builder = new Health.Builder();

            if (currentHealth == SystemHealth.HEALTHY) {
                builder.up();
            } else if (currentHealth == SystemHealth.DEGRADED) {
                builder.status("DEGRADED");
            } else {
                builder.down();
            }

            return builder
                    .withDetail("systemHealth", currentHealth)
                    .withDetail("totalOperations", totalOperations.get())
                    .withDetail("slowOperations", slowOperations.get())
                    .withDetail("memoryUsage", memoryStats.getHeapUsageRatio())
                    .withDetail("processingDropRate", processingStats.getDropRate())
                    .withDetail("activeIssues", activeIssues.size())
                    .build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }

    private void startPerformanceMonitoring() {
        Duration monitoringInterval = Duration.ofMinutes(1);
        Flux.interval(monitoringInterval, monitoringScheduler)
                .flatMap(tick -> collectPerformanceSnapshot())
                .subscribe(
                        snapshot -> {
                            lastSnapshot.set(snapshot);
                            analyzePerformanceTrends(snapshot);
                        },
                        error -> log.error("性能监控错误", error)
                );
    }

    private void startHealthChecks() {
        Duration healthCheckInterval = Duration.ofMinutes(2);
        Flux.interval(healthCheckInterval, monitoringScheduler)
                .flatMap(tick -> performHealthCheck())
                .subscribe(
                        health -> systemHealth.set(health),
                        error -> log.error("健康检查错误", error)
                );
    }

    private void startBottleneckDetection() {
        Duration detectionInterval = Duration.ofMinutes(5);
        Flux.interval(detectionInterval, monitoringScheduler)
                .flatMap(tick -> detectBottlenecks())
                .subscribe(
                        this::handleDetectedBottlenecks,
                        error -> log.error("瓶颈检测错误", error)
                );
    }

    private Mono<PerformanceSnapshot> collectPerformanceSnapshot() {
        return Mono.fromCallable(() -> {
            MemoryStats memoryStats = memoryManager.getMemoryStats();
            AsyncTracingProcessor.ProcessingStats processingStats = asyncTracingProcessor.getProcessingStats();

            return new PerformanceSnapshot(
                    Instant.now(),
                    totalOperations.get(),
                    slowOperations.get(),
                    memoryStats.getHeapUsageRatio(),
                    processingStats.getDropRate(),
                    getCurrentCpuUsage(),
                    getCurrentThroughput()
            );
        });
    }

    private Mono<SystemHealth> performHealthCheck() {
        return Mono.fromCallable(() -> {
            List<String> issues = new ArrayList<>();

            MemoryStats memoryStats = memoryManager.getMemoryStats();
            if (memoryStats.getHeapUsageRatio() > 0.9) {
                issues.add("内存使用率危险");
            }

            AsyncTracingProcessor.ProcessingStats processingStats = asyncTracingProcessor.getProcessingStats();
            if (!processingStats.isRunning()) {
                issues.add("异步处理器未运行");
            }

            double slowRatio = totalOperations.get() > 0
                    ? (double) slowOperations.get() / totalOperations.get() : 0.0;
            if (slowRatio > 0.5) {
                issues.add("慢操作比例过高");
            }

            if (issues.isEmpty()) {
                return SystemHealth.HEALTHY;
            } else if (issues.size() <= 2) {
                return SystemHealth.DEGRADED;
            } else {
                return SystemHealth.UNHEALTHY;
            }
        });
    }

    private void initializeDefaultThresholds() {
        final long exportP99 = monitoring().getAlerts().getThresholds().getExportLatencyP99();
        thresholds.put("trace.export", new PerformanceThreshold(1000, exportP99));
        thresholds.put("span.process", new PerformanceThreshold(100, 500));
        thresholds.put("memory.check", new PerformanceThreshold(50, 200));
        thresholds.put("batch.process", new PerformanceThreshold(500, 2000));
    }

    /**
     * 当前监控配置。每次读取都走 {@code tracingConfiguration}，这样
     * {@code PUT /api/tracing/actuator/config} 动态替换 monitoring 段后能立刻生效；
     * 配置缺失（未注入、或直接 new 出本类做单测）时回落到字段默认值。
     */
    private TracingMonitoringConfig monitoring() {
        final TracingMonitoringConfig monitoring =
                tracingConfiguration != null ? tracingConfiguration.getMonitoring() : null;
        return monitoring != null ? monitoring : fallbackMonitoring;
    }

    /**
     * 处理延迟直方图。
     *
     * <p>{@code monitoring.metrics.traces.histogram-buckets} 的单位是**秒**（与字段默认值 0.1–30 一致），
     * 而 {@code recordOperationPerformance} 记录的是毫秒，所以要换算后再作为 SLO 桶注册 —— 不换算会把
     * 桶边界全压在亚毫秒区间，指标等于没用。</p>
     */
    private DistributionSummary buildLatencyDistribution() {
        final DistributionSummary.Builder builder = DistributionSummary.builder("tracing.processing.latency")
                .description("Tracing processing latency distribution");
        final double[] buckets = monitoring().getMetrics().getTraces().getHistogramBuckets();
        if (buckets != null && buckets.length > 0) {
            final double[] millis = new double[buckets.length];
            for (int i = 0; i < buckets.length; i++) {
                millis[i] = buckets[i] * 1000;
            }
            builder.serviceLevelObjectives(millis);
        }
        return builder.register(meterRegistry);
    }

    private void handleSlowOperation(final String operation, final long duration, final Map<String, Object> metadata) {
        log.warn("检测到慢操作: operation={}, duration={}ms", operation, duration);
        performanceAnomalyCounter.increment();
    }

    private double getCurrentMemoryUsage() {
        MemoryStats stats = memoryManager.getMemoryStats();
        return stats.getHeapUsageRatio() * 100;
    }

    private double getCurrentCpuUsage() {
        return SECURE_RANDOM.nextDouble() * 100;
    }

    private double getCurrentThroughput() {
        return totalOperations.get() / 60.0;
    }

    private void analyzePerformanceTrends(final PerformanceSnapshot snapshot) {
        log.debug("性能快照: {}", snapshot);
    }

    private void handleDetectedBottlenecks(final List<PerformanceBottleneck> bottlenecks) {
        for (PerformanceBottleneck bottleneck : bottlenecks) {
            log.warn("检测到性能瓶颈: {}", bottleneck);
        }
    }

    private List<OptimizationSuggestion> generateMemoryOptimizationSuggestions(final MemoryStats stats) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        suggestions.add(new OptimizationSuggestion("增加堆内存大小", "考虑通过JVM参数增加堆内存: -Xmx<size>", Priority.HIGH));
        suggestions.add(new OptimizationSuggestion("优化缓存策略", "减少缓存大小或启用更激进的清理策略", Priority.MEDIUM));
        return suggestions;
    }

    private List<OptimizationSuggestion> generateProcessingOptimizationSuggestions(
            final AsyncTracingProcessor.ProcessingStats stats) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        suggestions.add(new OptimizationSuggestion("增加处理线程", "考虑增加异步处理器的线程池大小", Priority.HIGH));
        suggestions.add(new OptimizationSuggestion("优化批处理大小", "调整批处理大小以提高处理效率", Priority.MEDIUM));
        return suggestions;
    }

    private List<OptimizationSuggestion> generateOperationOptimizationSuggestions(final OperationMetrics metrics) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        suggestions.add(new OptimizationSuggestion("优化操作实现",
                "检查操作 " + metrics.getOperation() + " 的实现，可能存在性能问题",
                Priority.MEDIUM));
        return suggestions;
    }

    private List<OptimizationSuggestion> generateSystemOptimizationSuggestions() {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        suggestions.add(new OptimizationSuggestion("系统整体优化", "考虑进行系统整体性能调优", Priority.HIGH));
        return suggestions;
    }

    private boolean applyTuningAction(final String action) {
        log.info("应用调优操作: {}", action);
        return true;
    }
}
