package org.unreal.modelrouter.security.cache.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.security.cache.ApiKeyCache;
import org.unreal.modelrouter.security.cache.CacheMetrics;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 基于内存的API Key缓存实现
 * 提供本地缓存支持，适用于单机部署
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jairouter.security.cache.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryApiKeyCache implements ApiKeyCache {
    
    private static final Duration DEFAULT_TTL = Duration.ofHours(1); // 默认1小时过期
    
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final CacheMetrics cacheMetrics;
    
    public InMemoryApiKeyCache(@Autowired(required = false) CacheMetrics cacheMetrics) {
        this.cacheMetrics = cacheMetrics;
        // 启动定期清理过期缓存的任务
        scheduler.scheduleAtFixedRate(this::cleanupExpiredEntries, 5, 5, TimeUnit.MINUTES);
        log.info("内存API Key缓存初始化完成，启动定期清理任务");
    }
    
    @Override
    public Mono<ApiKeyInfo> get(String keyValue) {
        long startTime = System.nanoTime();
        
        return Mono.fromCallable(() -> {
            CacheEntry entry = cache.get(keyValue);
            if (entry == null) {
                if (cacheMetrics != null) {
                    cacheMetrics.recordCacheMiss();
                }
                return null;
            }
            
            if (entry.isExpired()) {
                cache.remove(keyValue);
                if (cacheMetrics != null) {
                    cacheMetrics.recordCacheEviction();
                    cacheMetrics.recordCacheMiss();
                }
                log.debug("内存缓存中的API Key已过期，已移除: {}", keyValue);
                return null;
            }
            
            if (cacheMetrics != null) {
                cacheMetrics.recordCacheHit();
            }
            log.debug("从内存缓存获取API Key: {}", entry.apiKeyInfo.getKeyId());
            return entry.apiKeyInfo;
        })
        .doFinally(signalType -> {
            if (cacheMetrics != null) {
                Duration duration = Duration.ofNanos(System.nanoTime() - startTime);
                cacheMetrics.recordReadDuration(duration);
            }
        });
    }
    
    @Override
    public Mono<Void> put(String keyValue, ApiKeyInfo apiKeyInfo, Duration ttl) {
        long startTime = System.nanoTime();
        
        return Mono.<Void>fromRunnable(() -> {
            LocalDateTime expiresAt = LocalDateTime.now().plus(ttl);
            CacheEntry entry = new CacheEntry(apiKeyInfo, expiresAt);
            CacheEntry previous = cache.put(keyValue, entry);
            boolean isNew = previous == null;
            
            if (cacheMetrics != null) {
                cacheMetrics.recordCacheWrite();
                if (isNew) {
                    cacheMetrics.incrementCacheSize();
                }
            }
            log.debug("API Key缓存到内存: {} (TTL: {})", apiKeyInfo.getKeyId(), ttl);
        })
        .doFinally(signalType -> {
            if (cacheMetrics != null) {
                Duration duration = Duration.ofNanos(System.nanoTime() - startTime);
                cacheMetrics.recordWriteDuration(duration);
            }
        });
    }
    
    @Override
    public Mono<Void> put(String keyValue, ApiKeyInfo apiKeyInfo) {
        return put(keyValue, apiKeyInfo, DEFAULT_TTL);
    }
    
    @Override
    public Mono<Void> evict(String keyValue) {
        return Mono.fromRunnable(() -> {
            CacheEntry removed = cache.remove(keyValue);
            if (removed != null) {
                if (cacheMetrics != null) {
                    cacheMetrics.recordCacheEviction();
                }
                log.debug("从内存缓存移除API Key: {}", removed.apiKeyInfo.getKeyId());
            }
        });
    }
    
    @Override
    public Mono<Void> clear() {
        return Mono.fromRunnable(() -> {
            int size = cache.size();
            cache.clear();
            if (cacheMetrics != null) {
                cacheMetrics.updateCacheSize(0);
            }
            log.info("清空内存中的所有API Key缓存，共清理 {} 个条目", size);
        });
    }
    
    @Override
    public Mono<Boolean> exists(String keyValue) {
        return Mono.fromCallable(() -> {
            CacheEntry entry = cache.get(keyValue);
            if (entry == null) {
                return false;
            }
            
            if (entry.isExpired()) {
                cache.remove(keyValue);
                return false;
            }
            
            return true;
        });
    }
    
    @Override
    public Mono<Void> expire(String keyValue, Duration ttl) {
        return Mono.fromRunnable(() -> {
            CacheEntry entry = cache.get(keyValue);
            if (entry != null) {
                LocalDateTime newExpiresAt = LocalDateTime.now().plus(ttl);
                CacheEntry updatedEntry = new CacheEntry(entry.apiKeyInfo, newExpiresAt);
                cache.put(keyValue, updatedEntry);
                log.debug("更新内存缓存API Key过期时间: {} -> {}", keyValue, ttl);
            }
        });
    }
    
    /**
     * 清理过期的缓存条目
     */
    private void cleanupExpiredEntries() {
        try {
            int initialSize = cache.size();
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            int finalSize = cache.size();
            int removed = initialSize - finalSize;
            
            if (removed > 0) {
                if (cacheMetrics != null) {
                    cacheMetrics.updateCacheSize(finalSize);
                }
                log.debug("清理过期的API Key缓存条目: {} 个", removed);
            }
        } catch (Exception e) {
            log.error("清理过期缓存条目时发生错误", e);
        }
    }
    
    /**
     * 缓存条目
     */
    private static class CacheEntry {
        private final ApiKeyInfo apiKeyInfo;
        private final LocalDateTime expiresAt;
        
        public CacheEntry(ApiKeyInfo apiKeyInfo, LocalDateTime expiresAt) {
            this.apiKeyInfo = apiKeyInfo;
            this.expiresAt = expiresAt;
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }
}