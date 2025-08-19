package org.unreal.modelrouter.security.authentication.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.cache.ApiKeyCache;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.UsageStatistics;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 带缓存的API Key服务实现
 * 提供API Key的缓存功能以提升性能
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "jairouter.security.cache.enabled", havingValue = "true", matchIfMissing = true)
public class CachedApiKeyServiceImpl implements ApiKeyService {
    
    private final ApiKeyService delegateService;
    private final ApiKeyCache apiKeyCache;
    private final SecurityProperties securityProperties;
    
    public CachedApiKeyServiceImpl(ApiKeyServiceImpl delegateService, 
                                   ApiKeyCache apiKeyCache,
                                   SecurityProperties securityProperties) {
        this.delegateService = delegateService;
        this.apiKeyCache = apiKeyCache;
        this.securityProperties = securityProperties;
        log.info("带缓存的API Key服务初始化完成");
    }
    
    @Override
    public Mono<ApiKeyInfo> validateApiKey(String keyValue) {
        return apiKeyCache.get(keyValue)
                .switchIfEmpty(
                    // 缓存未命中，从原始服务获取
                    delegateService.validateApiKey(keyValue)
                            .flatMap(apiKeyInfo -> {
                                // 缓存有效的API Key
                                Duration cacheTtl = calculateCacheTtl(apiKeyInfo);
                                return apiKeyCache.put(keyValue, apiKeyInfo, cacheTtl)
                                        .thenReturn(apiKeyInfo);
                            })
                )
                .doOnNext(apiKeyInfo -> log.debug("API Key验证完成: {} (来源: 缓存)", apiKeyInfo.getKeyId()))
                .doOnError(error -> log.error("API Key验证失败: {}", keyValue, error));
    }
    
    @Override
    public Mono<ApiKeyInfo> getApiKeyById(String keyId) {
        // 对于根据ID查询，直接委托给原始服务，不使用缓存
        return delegateService.getApiKeyById(keyId);
    }
    
    @Override
    public Mono<List<ApiKeyInfo>> getAllApiKeys() {
        return delegateService.getAllApiKeys();
    }
    
    @Override
    public Mono<ApiKeyInfo> createApiKey(ApiKeyInfo apiKeyInfo) {
        return delegateService.createApiKey(apiKeyInfo)
                .flatMap(created -> {
                    // 创建后立即缓存
                    Duration cacheTtl = calculateCacheTtl(created);
                    return apiKeyCache.put(created.getKeyValue(), created, cacheTtl)
                            .thenReturn(created);
                })
                .doOnSuccess(created -> log.info("API Key创建并缓存: {}", created.getKeyId()));
    }
    
    @Override
    public Mono<ApiKeyInfo> updateApiKey(String keyId, ApiKeyInfo apiKeyInfo) {
        return delegateService.updateApiKey(keyId, apiKeyInfo)
                .flatMap(updated -> {
                    // 更新后刷新缓存
                    Duration cacheTtl = calculateCacheTtl(updated);
                    return apiKeyCache.put(updated.getKeyValue(), updated, cacheTtl)
                            .thenReturn(updated);
                })
                .doOnSuccess(updated -> log.info("API Key更新并刷新缓存: {}", updated.getKeyId()));
    }
    
    @Override
    public Mono<Void> deleteApiKey(String keyId) {
        return delegateService.getApiKeyById(keyId)
                .flatMap(apiKeyInfo -> 
                    // 先从缓存中移除
                    apiKeyCache.evict(apiKeyInfo.getKeyValue())
                            .then(delegateService.deleteApiKey(keyId))
                )
                .doOnSuccess(unused -> log.info("API Key删除并从缓存移除: {}", keyId));
    }
    
    @Override
    public Mono<Void> updateUsageStatistics(String keyId, boolean success) {
        return delegateService.updateUsageStatistics(keyId, success)
                .then(
                    // 更新使用统计后，从缓存中移除以确保下次获取最新数据
                    delegateService.getApiKeyById(keyId)
                            .flatMap(apiKeyInfo -> apiKeyCache.evict(apiKeyInfo.getKeyValue()))
                )
                .doOnSuccess(unused -> log.debug("API Key使用统计更新，缓存已失效: {}", keyId));
    }
    
    @Override
    public Mono<UsageStatistics> getUsageStatistics(String keyId) {
        return delegateService.getUsageStatistics(keyId);
    }
    
    /**
     * 检查API Key是否具有指定权限
     */
    public Mono<Boolean> hasPermission(String keyValue, String permission) {
        return validateApiKey(keyValue)
                .map(apiKeyInfo -> apiKeyInfo.getPermissions().contains(permission))
                .defaultIfEmpty(false);
    }
    
    /**
     * 计算缓存TTL
     * 基于API Key的过期时间和配置的缓存策略
     */
    private Duration calculateCacheTtl(ApiKeyInfo apiKeyInfo) {
        if (apiKeyInfo.getExpiresAt() == null) {
            // 如果API Key没有过期时间，使用默认缓存时间
            return Duration.ofHours(1);
        }
        
        Duration timeToExpiry = Duration.between(LocalDateTime.now(), apiKeyInfo.getExpiresAt());
        Duration defaultCacheTtl = Duration.ofHours(1);
        
        // 缓存时间不超过API Key剩余有效时间，也不超过默认缓存时间
        return timeToExpiry.compareTo(defaultCacheTtl) < 0 ? timeToExpiry : defaultCacheTtl;
    }
    
    /**
     * 清空所有API Key缓存
     * 用于配置更新或系统维护
     */
    public Mono<Void> clearCache() {
        return apiKeyCache.clear()
                .doOnSuccess(unused -> log.info("所有API Key缓存已清空"));
    }
    
    /**
     * 预热缓存
     * 将所有有效的API Key加载到缓存中
     */
    public Mono<Void> warmupCache() {
        return delegateService.getAllApiKeys()
                .flatMapMany(Flux::fromIterable)
                .filter(apiKeyInfo -> apiKeyInfo.isEnabled() && 
                        (apiKeyInfo.getExpiresAt() == null || apiKeyInfo.getExpiresAt().isAfter(LocalDateTime.now())))
                .flatMap(apiKeyInfo -> {
                    Duration cacheTtl = calculateCacheTtl(apiKeyInfo);
                    return apiKeyCache.put(apiKeyInfo.getKeyValue(), apiKeyInfo, cacheTtl);
                })
                .then()
                .doOnSuccess(unused -> log.info("API Key缓存预热完成"));
    }
}