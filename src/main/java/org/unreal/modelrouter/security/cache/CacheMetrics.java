package org.unreal.modelrouter.security.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存性能指标收集器
 * 收集API Key缓存的性能指标用于监控
 */
@Slf4j
@Component
public class CacheMetrics {
    
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter cacheWrites;
    private final Counter cacheEvictions;
    private final Counter cacheErrors;
    
    private final Timer cacheReadTimer;
    private final Timer cacheWriteTimer;
    
    private final AtomicLong cacheSize = new AtomicLong(0);
    
    public CacheMetrics(MeterRegistry meterRegistry) {
        // 缓存命中率指标
        this.cacheHits = Counter.builder("jairouter.security.cache.hits")
                .description("API Key缓存命中次数")
                .register(meterRegistry);
        
        this.cacheMisses = Counter.builder("jairouter.security.cache.misses")
                .description("API Key缓存未命中次数")
                .register(meterRegistry);
        
        // 缓存操作指标
        this.cacheWrites = Counter.builder("jairouter.security.cache.writes")
                .description("API Key缓存写入次数")
                .register(meterRegistry);
        
        this.cacheEvictions = Counter.builder("jairouter.security.cache.evictions")
                .description("API Key缓存失效次数")
                .register(meterRegistry);
        
        this.cacheErrors = Counter.builder("jairouter.security.cache.errors")
                .description("API Key缓存错误次数")
                .register(meterRegistry);
        
        // 缓存操作耗时指标
        this.cacheReadTimer = Timer.builder("jairouter.security.cache.read.duration")
                .description("API Key缓存读取耗时")
                .register(meterRegistry);
        
        this.cacheWriteTimer = Timer.builder("jairouter.security.cache.write.duration")
                .description("API Key缓存写入耗时")
                .register(meterRegistry);
        
        // 缓存大小指标
        meterRegistry.gauge("jairouter.security.cache.size", cacheSize, AtomicLong::get);
        
        log.info("缓存性能指标收集器初始化完成");
    }
    
    /**
     * 记录缓存命中
     */
    public void recordCacheHit() {
        cacheHits.increment();
        log.debug("记录缓存命中");
    }
    
    /**
     * 记录缓存未命中
     */
    public void recordCacheMiss() {
        cacheMisses.increment();
        log.debug("记录缓存未命中");
    }
    
    /**
     * 记录缓存写入
     */
    public void recordCacheWrite() {
        cacheWrites.increment();
        log.debug("记录缓存写入");
    }
    
    /**
     * 记录缓存失效
     */
    public void recordCacheEviction() {
        cacheEvictions.increment();
        cacheSize.decrementAndGet();
        log.debug("记录缓存失效");
    }
    
    /**
     * 记录缓存错误
     */
    public void recordCacheError() {
        cacheErrors.increment();
        log.debug("记录缓存错误");
    }
    
    /**
     * 记录缓存读取耗时
     */
    public void recordReadDuration(Duration duration) {
        cacheReadTimer.record(duration);
        log.debug("记录缓存读取耗时: {} ms", duration.toMillis());
    }
    
    /**
     * 记录缓存写入耗时
     */
    public void recordWriteDuration(Duration duration) {
        cacheWriteTimer.record(duration);
        log.debug("记录缓存写入耗时: {} ms", duration.toMillis());
    }
    
    /**
     * 更新缓存大小
     */
    public void updateCacheSize(long size) {
        cacheSize.set(size);
        log.debug("更新缓存大小: {}", size);
    }
    
    /**
     * 增加缓存大小
     */
    public void incrementCacheSize() {
        cacheSize.incrementAndGet();
    }
    
    /**
     * 减少缓存大小
     */
    public void decrementCacheSize() {
        cacheSize.decrementAndGet();
    }
    
    /**
     * 获取缓存命中率
     */
    public double getCacheHitRate() {
        double hits = cacheHits.count();
        double misses = cacheMisses.count();
        double total = hits + misses;
        
        if (total == 0) {
            return 0.0;
        }
        
        return hits / total;
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStatistics getCacheStatistics() {
        return CacheStatistics.builder()
                .hits(cacheHits.count())
                .misses(cacheMisses.count())
                .writes(cacheWrites.count())
                .evictions(cacheEvictions.count())
                .errors(cacheErrors.count())
                .hitRate(getCacheHitRate())
                .size(cacheSize.get())
                .build();
    }
    
    /**
     * 缓存统计信息
     */
    public static class CacheStatistics {
        private final double hits;
        private final double misses;
        private final double writes;
        private final double evictions;
        private final double errors;
        private final double hitRate;
        private final long size;
        
        private CacheStatistics(Builder builder) {
            this.hits = builder.hits;
            this.misses = builder.misses;
            this.writes = builder.writes;
            this.evictions = builder.evictions;
            this.errors = builder.errors;
            this.hitRate = builder.hitRate;
            this.size = builder.size;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public double getHits() { return hits; }
        public double getMisses() { return misses; }
        public double getWrites() { return writes; }
        public double getEvictions() { return evictions; }
        public double getErrors() { return errors; }
        public double getHitRate() { return hitRate; }
        public long getSize() { return size; }
        
        @Override
        public String toString() {
            return String.format("CacheStatistics{hits=%.0f, misses=%.0f, writes=%.0f, " +
                    "evictions=%.0f, errors=%.0f, hitRate=%.2f%%, size=%d}", 
                    hits, misses, writes, evictions, errors, hitRate * 100, size);
        }
        
        public static class Builder {
            private double hits;
            private double misses;
            private double writes;
            private double evictions;
            private double errors;
            private double hitRate;
            private long size;
            
            public Builder hits(double hits) { this.hits = hits; return this; }
            public Builder misses(double misses) { this.misses = misses; return this; }
            public Builder writes(double writes) { this.writes = writes; return this; }
            public Builder evictions(double evictions) { this.evictions = evictions; return this; }
            public Builder errors(double errors) { this.errors = errors; return this; }
            public Builder hitRate(double hitRate) { this.hitRate = hitRate; return this; }
            public Builder size(long size) { this.size = size; return this; }
            
            public CacheStatistics build() {
                return new CacheStatistics(this);
            }
        }
    }
}