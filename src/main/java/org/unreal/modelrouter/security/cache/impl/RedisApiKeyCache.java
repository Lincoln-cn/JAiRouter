package org.unreal.modelrouter.security.cache.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.security.cache.ApiKeyCache;
import org.unreal.modelrouter.security.cache.CacheMetrics;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 基于Redis的API Key缓存实现
 * 提供分布式缓存支持
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jairouter.security.cache.redis.enabled", havingValue = "true")
public class RedisApiKeyCache implements ApiKeyCache {
    
    private static final String CACHE_KEY_PREFIX = "jairouter:security:apikey:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(1); // 默认1小时过期
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheMetrics cacheMetrics;
    
    public RedisApiKeyCache(ReactiveRedisTemplate<String, String> redisTemplate, 
                           ObjectMapper objectMapper,
                           @Autowired(required = false) CacheMetrics cacheMetrics) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheMetrics = cacheMetrics;
    }
    
    @Override
    public Mono<ApiKeyInfo> get(String keyValue) {
        long startTime = System.nanoTime();
        String cacheKey = buildCacheKey(keyValue);
        
        return redisTemplate.opsForValue()
                .get(cacheKey)
                .flatMap(this::deserializeApiKeyInfo)
                .doOnNext(apiKeyInfo -> {
                    if (cacheMetrics != null) {
                        cacheMetrics.recordCacheHit();
                    }
                    log.debug("从Redis缓存获取API Key: {}", apiKeyInfo.getKeyId());
                })
                .switchIfEmpty(Mono.fromRunnable(() -> {
                    if (cacheMetrics != null) {
                        cacheMetrics.recordCacheMiss();
                    }
                }))
                .doOnError(error -> {
                    if (cacheMetrics != null) {
                        cacheMetrics.recordCacheError();
                    }
                    log.error("从Redis缓存获取API Key失败: {}", keyValue, error);
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
        String cacheKey = buildCacheKey(keyValue);
        
        return serializeApiKeyInfo(apiKeyInfo)
                .flatMap(serialized -> redisTemplate.opsForValue().set(cacheKey, serialized, ttl))
                .then()
                .doOnSuccess(unused -> {
                    if (cacheMetrics != null) {
                        cacheMetrics.recordCacheWrite();
                    }
                    log.debug("API Key缓存到Redis: {} (TTL: {})", apiKeyInfo.getKeyId(), ttl);
                })
                .doOnError(error -> {
                    if (cacheMetrics != null) {
                        cacheMetrics.recordCacheError();
                    }
                    log.error("API Key缓存到Redis失败: {}", keyValue, error);
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
        String cacheKey = buildCacheKey(keyValue);
        return redisTemplate.opsForValue()
                .delete(cacheKey)
                .then()
                .doOnSuccess(unused -> {
                    if (cacheMetrics != null) {
                        cacheMetrics.recordCacheEviction();
                    }
                    log.debug("从Redis缓存移除API Key: {}", keyValue);
                })
                .doOnError(error -> {
                    if (cacheMetrics != null) {
                        cacheMetrics.recordCacheError();
                    }
                    log.error("从Redis缓存移除API Key失败: {}", keyValue, error);
                });
    }
    
    @Override
    public Mono<Void> clear() {
        String pattern = CACHE_KEY_PREFIX + "*";
        return redisTemplate.keys(pattern)
                .flatMap(redisTemplate::delete)
                .then()
                .doOnSuccess(unused -> log.info("清空Redis中的所有API Key缓存"))
                .doOnError(error -> log.error("清空Redis API Key缓存失败", error));
    }
    
    @Override
    public Mono<Boolean> exists(String keyValue) {
        String cacheKey = buildCacheKey(keyValue);
        return redisTemplate.hasKey(cacheKey)
                .doOnNext(exists -> log.debug("检查Redis缓存中API Key是否存在: {} -> {}", keyValue, exists))
                .doOnError(error -> log.error("检查Redis缓存中API Key是否存在失败: {}", keyValue, error));
    }
    
    @Override
    public Mono<Void> expire(String keyValue, Duration ttl) {
        String cacheKey = buildCacheKey(keyValue);
        return redisTemplate.expire(cacheKey, ttl)
                .then()
                .doOnSuccess(unused -> log.debug("设置Redis缓存API Key过期时间: {} -> {}", keyValue, ttl))
                .doOnError(error -> log.error("设置Redis缓存API Key过期时间失败: {}", keyValue, error));
    }
    
    /**
     * 构建缓存键
     */
    private String buildCacheKey(String keyValue) {
        return CACHE_KEY_PREFIX + keyValue;
    }
    
    /**
     * 序列化API Key信息
     */
    private Mono<String> serializeApiKeyInfo(ApiKeyInfo apiKeyInfo) {
        return Mono.fromCallable(() -> {
            try {
                return objectMapper.writeValueAsString(apiKeyInfo);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("序列化API Key信息失败", e);
            }
        });
    }
    
    /**
     * 反序列化API Key信息
     */
    private Mono<ApiKeyInfo> deserializeApiKeyInfo(String serialized) {
        return Mono.fromCallable(() -> {
            try {
                return objectMapper.readValue(serialized, ApiKeyInfo.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("反序列化API Key信息失败", e);
            }
        });
    }
}