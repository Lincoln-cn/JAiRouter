package org.unreal.modelrouter.security.cache;

import org.unreal.modelrouter.security.config.properties.ApiKey;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * API Key缓存接口
 * 提供API Key的缓存操作
 */
public interface ApiKeyCache {
    
    /**
     * 从缓存中获取API Key信息
     * @param keyValue API Key值
     * @return API Key信息，如果不存在则返回empty
     */
    Mono<ApiKey> get(String keyValue);
    
    /**
     * 将API Key信息存入缓存
     * @param keyValue API Key值
     * @param apiKey API Key信息
     * @param ttl 缓存过期时间
     * @return 缓存操作结果
     */
    Mono<Void> put(String keyValue, ApiKey apiKey, Duration ttl);
    
    /**
     * 将API Key信息存入缓存（使用默认过期时间）
     * @param keyValue API Key值
     * @param apiKey API Key信息
     * @return 缓存操作结果
     */
    Mono<Void> put(String keyValue, ApiKey apiKey);
    
    /**
     * 从缓存中移除API Key
     * @param keyValue API Key值
     * @return 移除操作结果
     */
    Mono<Void> evict(String keyValue);
    
    /**
     * 清空所有API Key缓存
     * @return 清空操作结果
     */
    Mono<Void> clear();
    
    /**
     * 检查API Key是否在缓存中
     * @param keyValue API Key值
     * @return 是否存在
     */
    Mono<Boolean> exists(String keyValue);
    
    /**
     * 设置API Key的过期时间
     * @param keyValue API Key值
     * @param ttl 过期时间
     * @return 设置操作结果
     */
    Mono<Void> expire(String keyValue, Duration ttl);
}