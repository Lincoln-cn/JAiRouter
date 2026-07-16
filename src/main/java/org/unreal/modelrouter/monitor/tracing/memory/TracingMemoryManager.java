package org.unreal.modelrouter.monitor.tracing.memory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.memory.LRUCache;
import org.unreal.modelrouter.monitor.tracing.memory.model.CachedTraceData;
import org.unreal.modelrouter.monitor.tracing.memory.model.GCResult;
import org.unreal.modelrouter.monitor.tracing.memory.model.MemoryCheckResult;
import org.unreal.modelrouter.monitor.tracing.memory.model.MemoryPressureLevel;
import org.unreal.modelrouter.monitor.tracing.memory.model.MemoryStats;
import org.unreal.modelrouter.monitor.tracing.memory.model.SpanCache;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 追踪内存管理器
 * 负责管理追踪数据的内存使用，包括LRU缓存策略、内存压力检测和自动清理
 */
@Slf4j
@Component
public class TracingMemoryManager {

    private final TracingConfiguration tracingConfiguration;
    private final MemoryMXBean memoryMXBean;
    private final Scheduler memoryScheduler;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // LRU缓存实现
    private final LRUCache<String, CachedTraceData> traceCache;
    private final Map<String, SpanCache> spanCaches = new ConcurrentHashMap<>();

    // 内存监控
    private final AtomicLong totalMemoryUsed = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);
    private final AtomicLong gcCount = new AtomicLong(0);

    // 内存压力状态
    private final AtomicReference<MemoryPressureLevel> pressureLevel =
            new AtomicReference<>(MemoryPressureLevel.LOW);
    private final AtomicBoolean memoryWarningIssued = new AtomicBoolean(false);

    public TracingMemoryManager(final TracingConfiguration tracingConfiguration) {
        this.tracingConfiguration = tracingConfiguration;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.memoryScheduler = Schedulers.newBoundedElastic(2, 100, "tracing-memory");

        TracingConfiguration.PerformanceConfig.MemoryConfig memoryConfig =
                tracingConfiguration.getPerformance().getMemory();

        this.traceCache = new LRUCache<>(memoryConfig.getMaxSpansInMemory());
    }

    @PostConstruct
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            log.info("启动追踪内存管理器");
            startMemoryMonitoring();
            startPeriodicCleanup();
        }
    }

    @PreDestroy
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            log.info("停止追踪内存管理器");
            memoryScheduler.dispose();
        }
    }

    /**
     * 缓存追踪数据
     */
    public Mono<Boolean> cacheTraceData(final String traceId, final String spanId,
                                         final Object data, final long estimatedSize) {
        return Mono.fromCallable(() -> {
            if (!isRunning.get()) {
                return false;
            }

            if (pressureLevel.get() == MemoryPressureLevel.CRITICAL) {
                log.warn("内存压力严重，拒绝缓存数据: traceId={}", traceId);
                return false;
            }

            CachedTraceData cachedData = new CachedTraceData(traceId, spanId, data, estimatedSize, Instant.now());

            if (shouldTriggerCleanup()) {
                performEmergencyCleanup();
            }

            traceCache.put(traceId, cachedData);
            totalMemoryUsed.addAndGet(estimatedSize);

            log.debug("缓存追踪数据: traceId={}, spanId={}, size={}B", traceId, spanId, estimatedSize);
            return true;

        }).subscribeOn(memoryScheduler).onErrorReturn(false);
    }

    /**
     * 获取缓存的追踪数据
     */
    public Mono<CachedTraceData> getCachedTraceData(final String traceId) {
        return Mono.fromCallable(() -> {
            CachedTraceData data = traceCache.get(traceId);
            if (data != null) {
                cacheHits.incrementAndGet();
                log.debug("缓存命中: traceId={}", traceId);
                return data;
            } else {
                cacheMisses.incrementAndGet();
                log.debug("缓存未命中: traceId={}", traceId);
                return null;
            }
        }).subscribeOn(memoryScheduler);
    }

    /**
     * 创建Span缓存
     */
    public Mono<SpanCache> createSpanCache(final String traceId, final int initialCapacity) {
        return Mono.fromCallable(() -> {
            SpanCache spanCache = new SpanCache(traceId, initialCapacity);
            spanCaches.put(traceId, spanCache);
            log.debug("创建Span缓存: traceId={}, capacity={}", traceId, initialCapacity);
            return spanCache;
        }).subscribeOn(memoryScheduler);
    }

    /**
     * 移除Span缓存
     */
    public Mono<Void> removeSpanCache(final String traceId) {
        return Mono.fromRunnable(() -> {
            SpanCache removed = spanCaches.remove(traceId);
            if (removed != null) {
                totalMemoryUsed.addAndGet(-removed.getEstimatedSize());
                log.debug("移除Span缓存: traceId={}", traceId);
            }
        }).subscribeOn(memoryScheduler).then();
    }

    /**
     * 执行内存检查
     */
    public Mono<MemoryCheckResult> performMemoryCheck() {
        return Mono.fromCallable(() -> {
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            long usedHeap = heapUsage.getUsed();
            long maxHeap = heapUsage.getMax();
            double usageRatio = (double) usedHeap / maxHeap;

            MemoryPressureLevel newLevel = determineMemoryPressureLevel(usageRatio);
            MemoryPressureLevel oldLevel = pressureLevel.getAndSet(newLevel);

            if (newLevel != oldLevel) {
                log.info("内存压力级别变化: {} -> {}, 堆使用率: {:.2f}%",
                        oldLevel, newLevel, usageRatio * 100);
            }

            if (newLevel == MemoryPressureLevel.HIGH || newLevel == MemoryPressureLevel.CRITICAL) {
                issueMemoryWarning(newLevel, usageRatio);
            } else {
                memoryWarningIssued.set(false);
            }

            return new MemoryCheckResult(
                    usedHeap, maxHeap, usageRatio, newLevel,
                    traceCache.size(), spanCaches.size(), totalMemoryUsed.get()
            );

        }).subscribeOn(memoryScheduler);
    }

    /**
     * 执行垃圾回收
     */
    public Mono<GCResult> performGarbageCollection() {
        return Mono.fromCallable(() -> {
            long beforeUsed = memoryMXBean.getHeapMemoryUsage().getUsed();
            long startTime = System.currentTimeMillis();

            System.gc();

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            long afterUsed = memoryMXBean.getHeapMemoryUsage().getUsed();
            long gcTime = System.currentTimeMillis() - startTime;
            long freedMemory = beforeUsed - afterUsed;

            gcCount.incrementAndGet();

            log.info("执行垃圾回收: 释放内存={}MB, 耗时={}ms",
                    freedMemory / (1024 * 1024), gcTime);

            return new GCResult(beforeUsed, afterUsed, freedMemory, gcTime);

        }).subscribeOn(memoryScheduler);
    }

    /**
     * 获取内存统计信息
     */
    public MemoryStats getMemoryStats() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();

        return new MemoryStats(
                heapUsage.getUsed(),
                heapUsage.getMax(),
                totalMemoryUsed.get(),
                traceCache.size(),
                spanCaches.size(),
                cacheHits.get(),
                cacheMisses.get(),
                evictionCount.get(),
                gcCount.get(),
                pressureLevel.get()
        );
    }

    // 私有方法

    private void startMemoryMonitoring() {
        Duration checkInterval = tracingConfiguration.getPerformance().getMemory().getGcInterval();

        Flux.interval(checkInterval, memoryScheduler)
                .flatMap(tick -> performMemoryCheck())
                .subscribe(
                        result -> handleMemoryCheckResult(result),
                        error -> log.error("内存监控错误", error)
                );
    }

    private void startPeriodicCleanup() {
        Duration cleanupInterval = Duration.ofMinutes(5);

        Flux.interval(cleanupInterval, memoryScheduler)
                .subscribe(tick -> performPeriodicCleanup());
    }

    private void handleMemoryCheckResult(final MemoryCheckResult result) {
        if (result.getPressureLevel() == MemoryPressureLevel.HIGH) {
            performOptimization();
        } else if (result.getPressureLevel() == MemoryPressureLevel.CRITICAL) {
            performEmergencyCleanup();
        }
    }

    private void performOptimization() {
        log.info("执行内存优化");
        cleanupExpiredCache();
        compressCache();
        adjustCacheSize();
    }

    private void performEmergencyCleanup() {
        log.warn("执行紧急内存清理");

        int targetSize = traceCache.size() / 2;
        while (traceCache.size() > targetSize && !traceCache.isEmpty()) {
            traceCache.removeEldest();
            evictionCount.incrementAndGet();
        }

        spanCaches.entrySet().removeIf(entry -> {
            SpanCache cache = entry.getValue();
            if (cache.isExpired()) {
                totalMemoryUsed.addAndGet(-cache.getEstimatedSize());
                return true;
            }
            return false;
        });

        performGarbageCollection().subscribe();
    }

    private void performPeriodicCleanup() {
        cleanupExpiredCache();

        if (gcCount.get() > 1000) {
            gcCount.set(0);
            evictionCount.set(0);
        }
    }

    private void cleanupExpiredCache() {
        Duration maxAge = Duration.ofHours(1);
        Instant cutoff = Instant.now().minus(maxAge);

        traceCache.removeIf(entry -> entry.getValue().getTimestamp().isBefore(cutoff));

        spanCaches.entrySet().removeIf(entry -> {
            SpanCache cache = entry.getValue();
            if (cache.getLastAccess().isBefore(cutoff)) {
                totalMemoryUsed.addAndGet(-cache.getEstimatedSize());
                return true;
            }
            return false;
        });
    }

    private void compressCache() {
        log.debug("执行缓存压缩");
    }

    private void adjustCacheSize() {
        MemoryPressureLevel currentLevel = pressureLevel.get();
        if (currentLevel == MemoryPressureLevel.HIGH) {
            int newCapacity = Math.max(traceCache.getCapacity() * 80 / 100, 100);
            traceCache.setCapacity(newCapacity);
            log.info("调整缓存容量: {}", newCapacity);
        }
    }

    private boolean shouldTriggerCleanup() {
        TracingConfiguration.PerformanceConfig.MemoryConfig memoryConfig =
                tracingConfiguration.getPerformance().getMemory();

        return totalMemoryUsed.get() > memoryConfig.getMemoryLimitMb() * 1024 * 1024 * 0.8;
    }

    private MemoryPressureLevel determineMemoryPressureLevel(final double usageRatio) {
        if (usageRatio > 0.9) {
            return MemoryPressureLevel.CRITICAL;
        } else if (usageRatio > 0.8) {
            return MemoryPressureLevel.HIGH;
        } else if (usageRatio > 0.6) {
            return MemoryPressureLevel.MEDIUM;
        } else {
            return MemoryPressureLevel.LOW;
        }
    }

    private void issueMemoryWarning(final MemoryPressureLevel level, final double usageRatio) {
        if (memoryWarningIssued.compareAndSet(false, true)) {
            log.warn("内存使用告警: 级别={}, 使用率={:.2f}%, 缓存大小={}, Span缓存数={}",
                    level, usageRatio * 100, traceCache.size(), spanCaches.size());
        }
    }
}
