package org.unreal.modelrouter.monitoring.circuitbreaker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 指标数据本地缓存和重试机制
 * 提供指标数据的本地缓存、批量处理和失败重试功能
 */
@Component
public class MetricsCacheAndRetry {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsCacheAndRetry.class);
    
    private final MeterRegistry meterRegistry;
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter retryAttempts;
    private final Counter retrySuccesses;
    private final Counter retryFailures;
    private final Timer cacheOperationTimer;
    
    // 缓存配置
    private static final int MAX_CACHE_SIZE = 10000;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(1);
    private static final double RETRY_BACKOFF_MULTIPLIER = 2.0;
    
    // 缓存存储
    private final ConcurrentHashMap<String, CachedMetric> metricsCache = new ConcurrentHashMap<>();
    private final BlockingQueue<RetryableMetric> retryQueue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(2);
    
    // 统计信息
    private final AtomicLong cacheSize = new AtomicLong(0);
    private final AtomicInteger activeRetries = new AtomicInteger(0);
    
    public MetricsCacheAndRetry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.cacheHits = Counter.builder("jairouter.metrics.cache.hits")
            .description("缓存命中次数")
            .register(meterRegistry);
        this.cacheMisses = Counter.builder("jairouter.metrics.cache.misses")
            .description("缓存未命中次数")
            .register(meterRegistry);
        this.retryAttempts = Counter.builder("jairouter.metrics.retry.attempts")
            .description("重试尝试次数")
            .register(meterRegistry);
        this.retrySuccesses = Counter.builder("jairouter.metrics.retry.successes")
            .description("重试成功次数")
            .register(meterRegistry);
        this.retryFailures = Counter.builder("jairouter.metrics.retry.failures")
            .description("重试失败次数")
            .register(meterRegistry);
        this.cacheOperationTimer = Timer.builder("jairouter.metrics.cache.operation_time")
            .description("缓存操作耗时")
            .register(meterRegistry);
        
        // 注册缓存大小指标
        Gauge.builder("jairouter.metrics.cache.size", cacheSize, AtomicLong::doubleValue)
            .description("缓存大小")
            .register(meterRegistry);
        
        Gauge.builder("jairouter.metrics.retry.active", activeRetries, AtomicInteger::doubleValue)
            .description("活跃重试任务数")
            .register(meterRegistry);
    }
    
    /**
     * 缓存指标数据
     */
    public void cacheMetric(String key, Object value, Duration ttl) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // 检查缓存大小限制
            if (metricsCache.size() >= MAX_CACHE_SIZE) {
                evictOldestEntries();
            }
            
            Instant expiryTime = Instant.now().plus(ttl != null ? ttl : CACHE_TTL);
            CachedMetric cachedMetric = new CachedMetric(value, expiryTime);
            
            metricsCache.put(key, cachedMetric);
            cacheSize.set(metricsCache.size());
            
            logger.debug("缓存指标数据: key={}, ttl={}ms", key, ttl != null ? ttl.toMillis() : CACHE_TTL.toMillis());
        } finally {
            sample.stop(cacheOperationTimer);
        }
    }
    
    /**
     * 从缓存获取指标数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getCachedMetric(String key, Class<T> type) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            CachedMetric cached = metricsCache.get(key);
            
            if (cached == null) {
                cacheMisses.increment();
                return null;
            }
            
            if (cached.isExpired()) {
                metricsCache.remove(key);
                cacheSize.set(metricsCache.size());
                cacheMisses.increment();
                return null;
            }
            
            cacheHits.increment();
            return type.cast(cached.getValue());
        } finally {
            sample.stop(cacheOperationTimer);
        }
    }
    
    /**
     * 带缓存的指标获取
     */
    public <T> T getOrCompute(String key, Class<T> type, Supplier<T> supplier, Duration ttl) {
        T cached = getCachedMetric(key, type);
        if (cached != null) {
            return cached;
        }
        
        try {
            T computed = supplier.get();
            if (computed != null) {
                cacheMetric(key, computed, ttl);
            }
            return computed;
        } catch (Exception e) {
            logger.warn("计算指标数据失败: key={}, error={}", key, e.getMessage());
            return null;
        }
    }
    
    /**
     * 异步执行指标操作，失败时加入重试队列
     */
    public void executeWithRetry(String operationId, Runnable operation) {
        executeWithRetry(operationId, () -> {
            operation.run();
            return null;
        });
    }
    
    /**
     * 异步执行指标操作，失败时加入重试队列（带返回值）
     */
    public <T> CompletableFuture<T> executeWithRetry(String operationId, Supplier<T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.get();
            } catch (Exception e) {
                logger.debug("指标操作失败，加入重试队列: operationId={}, error={}", operationId, e.getMessage());
                scheduleRetry(new RetryableMetric(operationId, operation, 0, e));
                throw new RuntimeException("操作失败，已加入重试队列", e);
            }
        });
    }
    
    /**
     * 调度重试任务
     */
    private void scheduleRetry(RetryableMetric retryableMetric) {
        if (retryableMetric.getAttempts() >= MAX_RETRY_ATTEMPTS) {
            retryFailures.increment();
            logger.warn("指标操作重试次数超限: operationId={}, attempts={}", 
                       retryableMetric.getOperationId(), retryableMetric.getAttempts());
            return;
        }
        
        long delayMs = (long) (INITIAL_RETRY_DELAY.toMillis() * 
                              Math.pow(RETRY_BACKOFF_MULTIPLIER, retryableMetric.getAttempts()));
        
        retryExecutor.schedule(() -> {
            executeRetry(retryableMetric);
        }, delayMs, TimeUnit.MILLISECONDS);
        
        activeRetries.incrementAndGet();
    }
    
    /**
     * 执行重试
     */
    private void executeRetry(RetryableMetric retryableMetric) {
        try {
            retryAttempts.increment();
            
            retryableMetric.getOperation().get();
            
            retrySuccesses.increment();
            logger.debug("指标操作重试成功: operationId={}, attempts={}", 
                        retryableMetric.getOperationId(), retryableMetric.getAttempts() + 1);
            
        } catch (Exception e) {
            logger.debug("指标操作重试失败: operationId={}, attempts={}, error={}", 
                        retryableMetric.getOperationId(), retryableMetric.getAttempts() + 1, e.getMessage());
            
            RetryableMetric nextRetry = new RetryableMetric(
                retryableMetric.getOperationId(),
                retryableMetric.getOperation(),
                retryableMetric.getAttempts() + 1,
                e
            );
            
            scheduleRetry(nextRetry);
        } finally {
            activeRetries.decrementAndGet();
        }
    }
    
    /**
     * 清理过期缓存条目
     */
    @Scheduled(fixedRate = 60000) // 每分钟执行一次
    public void cleanupExpiredCache() {
        int removedCount = 0;
        Instant now = Instant.now();
        
        for (var entry : metricsCache.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                metricsCache.remove(entry.getKey());
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            cacheSize.set(metricsCache.size());
            logger.debug("清理过期缓存条目: count={}, remaining={}", removedCount, metricsCache.size());
        }
    }
    
    /**
     * 驱逐最旧的缓存条目
     */
    private void evictOldestEntries() {
        int evictCount = MAX_CACHE_SIZE / 10; // 驱逐10%的条目
        
        metricsCache.entrySet().stream()
            .sorted((e1, e2) -> e1.getValue().getCreatedAt().compareTo(e2.getValue().getCreatedAt()))
            .limit(evictCount)
            .forEach(entry -> metricsCache.remove(entry.getKey()));
        
        cacheSize.set(metricsCache.size());
        logger.debug("驱逐最旧缓存条目: count={}, remaining={}", evictCount, metricsCache.size());
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStats getCacheStats() {
        return new CacheStats(
            metricsCache.size(),
            MAX_CACHE_SIZE,
            activeRetries.get(),
            retryQueue.size()
        );
    }
    
    /**
     * 清空缓存
     */
    public void clearCache() {
        metricsCache.clear();
        cacheSize.set(0);
        logger.info("清空指标缓存");
    }
    
    /**
     * 关闭重试执行器
     */
    public void shutdown() {
        retryExecutor.shutdown();
        try {
            if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                retryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            retryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    

    
    /**
     * 缓存的指标数据
     */
    private static class CachedMetric {
        private final Object value;
        private final Instant expiryTime;
        private final Instant createdAt;
        
        public CachedMetric(Object value, Instant expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
            this.createdAt = Instant.now();
        }
        
        public Object getValue() { return value; }
        public Instant getExpiryTime() { return expiryTime; }
        public Instant getCreatedAt() { return createdAt; }
        
        public boolean isExpired() {
            return isExpired(Instant.now());
        }
        
        public boolean isExpired(Instant now) {
            return now.isAfter(expiryTime);
        }
    }
    
    /**
     * 可重试的指标操作
     */
    private static class RetryableMetric {
        private final String operationId;
        private final Supplier<?> operation;
        private final int attempts;
        private final Exception lastError;
        
        public RetryableMetric(String operationId, Supplier<?> operation, int attempts, Exception lastError) {
            this.operationId = operationId;
            this.operation = operation;
            this.attempts = attempts;
            this.lastError = lastError;
        }
        
        public String getOperationId() { return operationId; }
        public Supplier<?> getOperation() { return operation; }
        public int getAttempts() { return attempts; }
        public Exception getLastError() { return lastError; }
    }
    
    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final int currentSize;
        private final int maxSize;
        private final int activeRetries;
        private final int queuedRetries;
        
        public CacheStats(int currentSize, int maxSize, int activeRetries, int queuedRetries) {
            this.currentSize = currentSize;
            this.maxSize = maxSize;
            this.activeRetries = activeRetries;
            this.queuedRetries = queuedRetries;
        }
        
        public int getCurrentSize() { return currentSize; }
        public int getMaxSize() { return maxSize; }
        public int getActiveRetries() { return activeRetries; }
        public int getQueuedRetries() { return queuedRetries; }
        public double getUsageRatio() { return (double) currentSize / maxSize; }
    }
}