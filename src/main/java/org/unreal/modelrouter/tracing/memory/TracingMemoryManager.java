package org.unreal.modelrouter.tracing.memory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 追踪内存管理器
 * 
 * 负责管理追踪数据的内存使用，包括：
 * - 追踪数据的LRU缓存策略
 * - 内存压力检测和自动清理
 * - 内存使用监控和告警
 * - 缓存优化和性能调优
 * 
 * @author JAiRouter Team
 * @since 1.0.0
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

    public TracingMemoryManager(TracingConfiguration tracingConfiguration) {
        this.tracingConfiguration = tracingConfiguration;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.memoryScheduler = Schedulers.newBoundedElastic(2, 100, "tracing-memory");
        
        TracingConfiguration.PerformanceConfig.MemoryConfig memoryConfig = 
                tracingConfiguration.getPerformance().getMemory();
        
        // 初始化LRU缓存
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
    public Mono<Boolean> cacheTraceData(String traceId, String spanId, Object data, long estimatedSize) {
        return Mono.fromCallable(() -> {
            if (!isRunning.get()) {
                return false;
            }

            // 检查内存压力
            if (pressureLevel.get() == MemoryPressureLevel.CRITICAL) {
                log.warn("内存压力严重，拒绝缓存数据: traceId={}", traceId);
                return false;
            }

            CachedTraceData cachedData = new CachedTraceData(traceId, spanId, data, estimatedSize, Instant.now());
            
            // 检查是否需要预先清理
            if (shouldTriggerCleanup()) {
                performEmergencyCleanup();
            }

            traceCache.put(traceId, cachedData);
            totalMemoryUsed.addAndGet(estimatedSize);
            
            log.debug("缓存追踪数据: traceId={}, spanId={}, size={}B", traceId, spanId, estimatedSize);
            return true;
            
        }).subscribeOn(memoryScheduler)
          .onErrorReturn(false);
    }

    /**
     * 获取缓存的追踪数据
     */
    public Mono<CachedTraceData> getCachedTraceData(String traceId) {
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
    public Mono<SpanCache> createSpanCache(String traceId, int initialCapacity) {
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
    public Mono<Void> removeSpanCache(String traceId) {
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

            // 更新内存压力级别
            MemoryPressureLevel newLevel = determineMemoryPressureLevel(usageRatio);
            MemoryPressureLevel oldLevel = pressureLevel.getAndSet(newLevel);

            // 记录压力级别变化
            if (newLevel != oldLevel) {
                log.info("内存压力级别变化: {} -> {}, 堆使用率: {:.2f}%", 
                        oldLevel, newLevel, usageRatio * 100);
            }

            // 触发告警
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
            
            // 建议JVM执行GC
            System.gc();
            
            // 等待一小段时间让GC完成
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
     * 启动内存监控
     */
    private void startMemoryMonitoring() {
        Duration checkInterval = tracingConfiguration.getPerformance().getMemory().getGcInterval();
        
        Flux.interval(checkInterval, memoryScheduler)
                .flatMap(tick -> performMemoryCheck())
                .subscribe(
                        result -> handleMemoryCheckResult(result),
                        error -> log.error("内存监控错误", error)
                );
    }

    /**
     * 启动定期清理
     */
    private void startPeriodicCleanup() {
        Duration cleanupInterval = Duration.ofMinutes(5); // 每5分钟清理一次
        
        Flux.interval(cleanupInterval, memoryScheduler)
                .subscribe(tick -> performPeriodicCleanup());
    }

    /**
     * 处理内存检查结果
     */
    private void handleMemoryCheckResult(MemoryCheckResult result) {
        if (result.getPressureLevel() == MemoryPressureLevel.HIGH) {
            performOptimization();
        } else if (result.getPressureLevel() == MemoryPressureLevel.CRITICAL) {
            performEmergencyCleanup();
        }
    }

    /**
     * 执行优化
     */
    private void performOptimization() {
        log.info("执行内存优化");
        
        // 1. 清理过期缓存
        cleanupExpiredCache();
        
        // 2. 压缩缓存
        compressCache();
        
        // 3. 调整缓存大小
        adjustCacheSize();
    }

    /**
     * 执行紧急清理
     */
    private void performEmergencyCleanup() {
        log.warn("执行紧急内存清理");
        
        // 1. 强制清理50%的缓存
        int targetSize = traceCache.size() / 2;
        while (traceCache.size() > targetSize && !traceCache.isEmpty()) {
            traceCache.removeEldest();
            evictionCount.incrementAndGet();
        }
        
        // 2. 清理所有过期的Span缓存
        spanCaches.entrySet().removeIf(entry -> {
            SpanCache cache = entry.getValue();
            if (cache.isExpired()) {
                totalMemoryUsed.addAndGet(-cache.getEstimatedSize());
                return true;
            }
            return false;
        });
        
        // 3. 强制垃圾回收
        performGarbageCollection().subscribe();
    }

    /**
     * 定期清理
     */
    private void performPeriodicCleanup() {
        cleanupExpiredCache();
        
        // 清理统计信息
        if (gcCount.get() > 1000) {
            gcCount.set(0);
            evictionCount.set(0);
        }
    }

    /**
     * 清理过期缓存
     */
    private void cleanupExpiredCache() {
        Duration maxAge = Duration.ofHours(1); // 缓存最大保存1小时
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

    /**
     * 压缩缓存
     */
    private void compressCache() {
        // 实现缓存压缩逻辑
        log.debug("执行缓存压缩");
    }

    /**
     * 调整缓存大小
     */
    private void adjustCacheSize() {
        MemoryPressureLevel currentLevel = pressureLevel.get();
        if (currentLevel == MemoryPressureLevel.HIGH) {
            // 减小缓存容量
            int newCapacity = Math.max(traceCache.getCapacity() * 80 / 100, 100);
            traceCache.setCapacity(newCapacity);
            log.info("调整缓存容量: {}", newCapacity);
        }
    }

    /**
     * 判断是否需要触发清理
     */
    private boolean shouldTriggerCleanup() {
        TracingConfiguration.PerformanceConfig.MemoryConfig memoryConfig = 
                tracingConfiguration.getPerformance().getMemory();
        
        return totalMemoryUsed.get() > memoryConfig.getMemoryLimitMb() * 1024 * 1024 * 0.8;
    }

    /**
     * 确定内存压力级别
     */
    private MemoryPressureLevel determineMemoryPressureLevel(double usageRatio) {
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

    /**
     * 发出内存告警
     */
    private void issueMemoryWarning(MemoryPressureLevel level, double usageRatio) {
        if (memoryWarningIssued.compareAndSet(false, true)) {
            log.warn("内存使用告警: 级别={}, 使用率={:.2f}%, 缓存大小={}, Span缓存数={}", 
                    level, usageRatio * 100, traceCache.size(), spanCaches.size());
        }
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

    // 内部类和枚举定义
    
    public enum MemoryPressureLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    @Data
    public static class CachedTraceData {
        private final String traceId;
        private final String spanId;
        private final Object data;
        private final long estimatedSize;
        private final Instant timestamp;
    }

    @Data
    public static class SpanCache {
        private final String traceId;
        private final Map<String, Object> spans = new ConcurrentHashMap<>();
        private final Instant createdAt = Instant.now();
        private volatile Instant lastAccess = Instant.now();
        private final AtomicLong estimatedSize = new AtomicLong(0);

        public SpanCache(String traceId, int initialCapacity) {
            this.traceId = traceId;
        }

        public void put(String spanId, Object spanData, long size) {
            spans.put(spanId, spanData);
            estimatedSize.addAndGet(size);
            lastAccess = Instant.now();
        }

        public Object get(String spanId) {
            lastAccess = Instant.now();
            return spans.get(spanId);
        }

        public boolean isExpired() {
            return lastAccess.isBefore(Instant.now().minus(Duration.ofMinutes(30)));
        }

        public long getEstimatedSize() {
            return estimatedSize.get();
        }
    }

    @Data
    public static class MemoryCheckResult {
        private final long usedHeap;
        private final long maxHeap;
        private final double usageRatio;
        private final MemoryPressureLevel pressureLevel;
        private final int cacheSize;
        private final int spanCacheCount;
        private final long totalMemoryUsed;
    }

    @Data
    public static class GCResult {
        private final long beforeUsed;
        private final long afterUsed;
        private final long freedMemory;
        private final long gcTime;
    }

    @Data
    public static class MemoryStats {
        private final long usedHeap;
        private final long maxHeap;
        private final long totalMemoryUsed;
        private final int cacheSize;
        private final int spanCacheCount;
        private final long cacheHits;
        private final long cacheMisses;
        private final long evictionCount;
        private final long gcCount;
        private final MemoryPressureLevel pressureLevel;

        public double getHitRatio() {
            long total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total : 0.0;
        }

        public double getHeapUsageRatio() {
            return maxHeap > 0 ? (double) usedHeap / maxHeap : 0.0;
        }
    }

    /**
     * LRU缓存实现
     */
    private static class LRUCache<K, V> {
        private final Map<K, Node<K, V>> map = new ConcurrentHashMap<>();
        private volatile int capacity;
        private final Node<K, V> head = new Node<>(null, null);
        private final Node<K, V> tail = new Node<>(null, null);

        public LRUCache(int capacity) {
            this.capacity = capacity;
            head.next = tail;
            tail.prev = head;
        }

        public synchronized V get(K key) {
            Node<K, V> node = map.get(key);
            if (node != null) {
                moveToHead(node);
                return node.value;
            }
            return null;
        }

        public synchronized void put(K key, V value) {
            Node<K, V> existing = map.get(key);
            if (existing != null) {
                existing.value = value;
                moveToHead(existing);
            } else {
                Node<K, V> newNode = new Node<>(key, value);
                map.put(key, newNode);
                addToHead(newNode);

                if (map.size() > capacity) {
                    Node<K, V> tail = removeTail();
                    map.remove(tail.key);
                }
            }
        }

        public synchronized void removeEldest() {
            if (!map.isEmpty()) {
                Node<K, V> tail = removeTail();
                map.remove(tail.key);
            }
        }

        public synchronized void removeIf(java.util.function.Predicate<Map.Entry<K, V>> predicate) {
            map.entrySet().removeIf(entry -> {
                // 创建一个包含实际键值对的Entry用于测试
                Map.Entry<K, V> valueEntry = new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().value);
                if (predicate.test(valueEntry)) {
                    removeNode(entry.getValue());
                    return true;
                }
                return false;
            });
        }

        public int size() {
            return map.size();
        }

        public boolean isEmpty() {
            return map.isEmpty();
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
            while (map.size() > capacity) {
                removeEldest();
            }
        }

        private void addToHead(Node<K, V> node) {
            node.prev = head;
            node.next = head.next;
            head.next.prev = node;
            head.next = node;
        }

        private void removeNode(Node<K, V> node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
        }

        private void moveToHead(Node<K, V> node) {
            removeNode(node);
            addToHead(node);
        }

        private Node<K, V> removeTail() {
            Node<K, V> lastNode = tail.prev;
            removeNode(lastNode);
            return lastNode;
        }

        private static class Node<K, V> {
            K key;
            V value;
            Node<K, V> prev;
            Node<K, V> next;

            Node(K key, V value) {
                this.key = key;
                this.value = value;
            }
        }
    }
}