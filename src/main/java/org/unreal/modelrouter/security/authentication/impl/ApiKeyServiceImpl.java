package org.unreal.modelrouter.security.authentication.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.exception.exception.AuthenticationException;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.UsageStatistics;
import org.unreal.modelrouter.store.StoreManager;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API Key管理服务实现类
 * 提供API Key的CRUD操作、验证功能和缓存机制
 */
@Slf4j
@Service
public class ApiKeyServiceImpl implements ApiKeyService {
    
    private static final String API_KEYS_STORE_KEY = "security.api-keys";
    private static final String USAGE_STATS_STORE_KEY = "security.usage-statistics";
    
    private final StoreManager storeManager;
    private final ObjectMapper objectMapper;
    private final SecurityProperties securityProperties;
    
    // API Key缓存：keyValue -> ApiKeyInfo
    private final Map<String, ApiKeyInfo> apiKeyCache = new ConcurrentHashMap<>();
    // API Key ID索引：keyId -> keyValue
    private final Map<String, String> keyIdIndex = new ConcurrentHashMap<>();
    // 使用统计缓存：keyId -> UsageStatistics
    private final Map<String, UsageStatistics> usageStatsCache = new ConcurrentHashMap<>();
    
    @Autowired
    public ApiKeyServiceImpl(StoreManager storeManager, ObjectMapper objectMapper, SecurityProperties securityProperties) {
        this.storeManager = storeManager;
        this.objectMapper = objectMapper;
        this.securityProperties = securityProperties;
    }
    
    /**
     * 初始化方法，从存储中加载API Key数据到缓存
     */
    @PostConstruct
    public void init() {
        try {
            loadApiKeysFromConfig();
            loadApiKeysFromStore();
            loadUsageStatsFromStore();
        } catch (Exception e) {
            log.error("API Key服务初始化失败", e);
            throw e;
        }
        log.info("API Key服务初始化完成，加载了 {} 个API Key", apiKeyCache.size());
    }
    
    /**
     * 从配置文件加载API Key
     */
    private void loadApiKeysFromConfig() {
        List<ApiKeyInfo> configKeys = securityProperties.getApiKey().getKeys();
        if (configKeys != null && !configKeys.isEmpty()) {
            for (ApiKeyInfo apiKeyInfo : configKeys) {
                // 只有当密钥不在缓存中时才添加（避免重复）
                if (!apiKeyCache.containsKey(apiKeyInfo.getKeyValue())) {
                    apiKeyCache.put(apiKeyInfo.getKeyValue(), apiKeyInfo);
                    keyIdIndex.put(apiKeyInfo.getKeyId(), apiKeyInfo.getKeyValue());
                }
            }
        }
        log.debug("从配置加载了 {} 个API Key", configKeys != null ? configKeys.size() : 0);
    }
    
    @Override
    public Mono<ApiKeyInfo> validateApiKey(String keyValue) {
        return Mono.defer(() -> {
            try {
                if (keyValue == null || keyValue.trim().isEmpty()) {
                    return Mono.error(AuthenticationException.missingApiKey());
                }
                
                ApiKeyInfo apiKeyInfo = apiKeyCache.get(keyValue);
                if (apiKeyInfo == null) {
                    return Mono.error(AuthenticationException.invalidApiKey());
                }
                
                if (!apiKeyInfo.isEnabled()) {
                    return Mono.error(new AuthenticationException("API Key已被禁用", AuthenticationException.INVALID_API_KEY));
                }
                
                if (apiKeyInfo.getExpiresAt() != null && apiKeyInfo.getExpiresAt().isBefore(LocalDateTime.now())) {
                    return Mono.error(AuthenticationException.expiredApiKey());
                }
                
                log.debug("API Key验证成功: {}", apiKeyInfo.getKeyId());
                return Mono.just(apiKeyInfo);
            } catch (Exception e) {
                log.error("API Key验证过程中发生错误: {}", e.getMessage(), e);
                return Mono.error(e);
            }
        });
    }    

    @Override
    public Mono<ApiKeyInfo> createApiKey(ApiKeyInfo apiKeyInfo) {
        return Mono.fromCallable(() -> {
            if (apiKeyInfo.getKeyId() == null || apiKeyInfo.getKeyId().trim().isEmpty()) {
                throw new IllegalArgumentException("API Key ID不能为空");
            }
            
            if (apiKeyInfo.getKeyValue() == null || apiKeyInfo.getKeyValue().trim().isEmpty()) {
                throw new IllegalArgumentException("API Key值不能为空");
            }
            
            if (keyIdIndex.containsKey(apiKeyInfo.getKeyId())) {
                throw new IllegalArgumentException("API Key ID已存在: " + apiKeyInfo.getKeyId());
            }
            
            if (apiKeyCache.containsKey(apiKeyInfo.getKeyValue())) {
                throw new IllegalArgumentException("API Key值已存在");
            }
            
            // 设置创建时间
            if (apiKeyInfo.getCreatedAt() == null) {
                apiKeyInfo.setCreatedAt(LocalDateTime.now());
            }
            
            // 初始化使用统计
            if (apiKeyInfo.getUsage() == null) {
                apiKeyInfo.setUsage(UsageStatistics.builder()
                        .totalRequests(0L)
                        .successfulRequests(0L)
                        .failedRequests(0L)
                        .dailyUsage(new HashMap<>())
                        .build());
            }
            
            // 更新缓存
            apiKeyCache.put(apiKeyInfo.getKeyValue(), apiKeyInfo);
            keyIdIndex.put(apiKeyInfo.getKeyId(), apiKeyInfo.getKeyValue());
            usageStatsCache.put(apiKeyInfo.getKeyId(), apiKeyInfo.getUsage());
            
            // 持久化到存储
            saveApiKeysToStore();
            saveUsageStatsToStore();
            
            log.info("创建API Key成功: {}", apiKeyInfo.getKeyId());
            return apiKeyInfo;
        });
    }
    
    @Override
    public Mono<ApiKeyInfo> updateApiKey(String keyId, ApiKeyInfo apiKeyInfo) {
        return Mono.fromCallable(() -> {
            String keyValue = keyIdIndex.get(keyId);
            if (keyValue == null) {
                throw new IllegalArgumentException("API Key不存在: " + keyId);
            }
            
            ApiKeyInfo existingKey = apiKeyCache.get(keyValue);
            if (existingKey == null) {
                throw new IllegalArgumentException("API Key缓存不一致: " + keyId);
            }
            
            // 更新字段（保持keyId和keyValue不变）
            existingKey.setDescription(apiKeyInfo.getDescription());
            existingKey.setEnabled(apiKeyInfo.isEnabled());
            existingKey.setExpiresAt(apiKeyInfo.getExpiresAt());
            existingKey.setPermissions(apiKeyInfo.getPermissions());
            existingKey.setMetadata(apiKeyInfo.getMetadata());
            
            // 持久化到存储
            saveApiKeysToStore();
            
            log.info("更新API Key成功: {}", keyId);
            return existingKey;
        });
    }
    
    @Override
    public Mono<Void> deleteApiKey(String keyId) {
        return Mono.fromRunnable(() -> {
            String keyValue = keyIdIndex.get(keyId);
            if (keyValue == null) {
                throw new IllegalArgumentException("API Key不存在: " + keyId);
            }
            
            // 从缓存中移除
            apiKeyCache.remove(keyValue);
            keyIdIndex.remove(keyId);
            usageStatsCache.remove(keyId);
            
            // 持久化到存储
            saveApiKeysToStore();
            saveUsageStatsToStore();
            
            log.info("删除API Key成功: {}", keyId);
        });
    }
    
    @Override
    public Mono<List<ApiKeyInfo>> getAllApiKeys() {
        return Mono.fromCallable(() -> new ArrayList<>(apiKeyCache.values()));
    }
    
    @Override
    public Mono<ApiKeyInfo> getApiKeyById(String keyId) {
        return Mono.fromCallable(() -> {
            String keyValue = keyIdIndex.get(keyId);
            if (keyValue == null) {
                throw new IllegalArgumentException("API Key不存在: " + keyId);
            }
            
            ApiKeyInfo apiKeyInfo = apiKeyCache.get(keyValue);
            if (apiKeyInfo == null) {
                throw new IllegalArgumentException("API Key缓存不一致: " + keyId);
            }
            
            return apiKeyInfo;
        });
    }  
  
    @Override
    public Mono<Void> updateUsageStatistics(String keyId, boolean success) {
        return Mono.fromRunnable(() -> {
            UsageStatistics stats = usageStatsCache.get(keyId);
            if (stats == null) {
                log.warn("未找到API Key的使用统计: {}", keyId);
                return;
            }
            
            // 更新统计数据
            stats.setTotalRequests(stats.getTotalRequests() + 1);
            if (success) {
                stats.setSuccessfulRequests(stats.getSuccessfulRequests() + 1);
            } else {
                stats.setFailedRequests(stats.getFailedRequests() + 1);
            }
            stats.setLastUsedAt(LocalDateTime.now());
            
            // 更新每日使用统计
            String today = LocalDateTime.now().toLocalDate().toString();
            Map<String, Long> dailyUsage = stats.getDailyUsage();
            if (dailyUsage == null) {
                dailyUsage = new HashMap<>();
                stats.setDailyUsage(dailyUsage);
            }
            dailyUsage.put(today, dailyUsage.getOrDefault(today, 0L) + 1);
            
            // 更新API Key中的使用统计
            String keyValue = keyIdIndex.get(keyId);
            if (keyValue != null) {
                ApiKeyInfo apiKeyInfo = apiKeyCache.get(keyValue);
                if (apiKeyInfo != null) {
                    apiKeyInfo.setUsage(stats);
                }
            }
            
            // 异步持久化（避免阻塞）
            saveUsageStatsToStoreAsync();
            
            log.debug("更新API Key使用统计: {} (成功: {})", keyId, success);
        });
    }
    
    @Override
    public Mono<UsageStatistics> getUsageStatistics(String keyId) {
        return Mono.fromCallable(() -> {
            UsageStatistics stats = usageStatsCache.get(keyId);
            if (stats == null) {
                throw new IllegalArgumentException("未找到API Key的使用统计: " + keyId);
            }
            return stats;
        });
    }
    
    /**
     * 从存储中加载API Key数据到缓存
     */
    private void loadApiKeysFromStore() {
        try {
            //如果不存在配置文件，则创建默认空配置文件
            if (!storeManager.exists(API_KEYS_STORE_KEY)) {
                storeManager.saveConfig(API_KEYS_STORE_KEY, Collections.emptyMap());
                log.info("创建默认API Key配置文件");
            }
            Map<String, Object> config = storeManager.getConfig(API_KEYS_STORE_KEY);
            if (config != null && !config.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> apiKeyMaps = (List<Map<String, Object>>) config.get("apiKeys");
                if (apiKeyMaps != null) {
                    for (Map<String, Object> apiKeyMap : apiKeyMaps) {
                        ApiKeyInfo apiKeyInfo = objectMapper.convertValue(apiKeyMap, ApiKeyInfo.class);
                        // 只有当密钥不在配置中时才添加（避免重复）
                        if (!apiKeyCache.containsKey(apiKeyInfo.getKeyValue())) {
                            apiKeyCache.put(apiKeyInfo.getKeyValue(), apiKeyInfo);
                            keyIdIndex.put(apiKeyInfo.getKeyId(), apiKeyInfo.getKeyValue());
                        }
                    }
                }
            }

            log.debug("从存储加载了 {} 个API Key", apiKeyCache.size());
        } catch (Exception e) {
            log.error("加载API Key数据失败，可能由于JSON文件损坏，请检查文件格式", e);
            // 尝试重新创建配置文件
            try {
                log.info("尝试重新创建API Key配置文件");
                storeManager.saveConfig(API_KEYS_STORE_KEY, Collections.emptyMap());
                log.info("API Key配置文件已重新创建");
            } catch (Exception recreateException) {
                log.error("重新创建API Key配置文件失败", recreateException);
            }
        }
    }
    
    /**
     * 从存储中加载使用统计数据到缓存
     */
    private void loadUsageStatsFromStore() {
        try {
            if (!storeManager.exists(USAGE_STATS_STORE_KEY)) {
                storeManager.saveConfig(USAGE_STATS_STORE_KEY, Collections.emptyMap());
                log.info("创建默认使用统计配置文件");
            }
            Map<String, Object> config = storeManager.getConfig(USAGE_STATS_STORE_KEY);
            if (config != null && !config.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> statsMap = (Map<String, Map<String, Object>>) config.get("usageStats");
                if (statsMap != null) {
                    for (Map.Entry<String, Map<String, Object>> entry : statsMap.entrySet()) {
                        String keyId = entry.getKey();
                        UsageStatistics stats = objectMapper.convertValue(entry.getValue(), UsageStatistics.class);
                        usageStatsCache.put(keyId, stats);
                    }
                }
            }
            log.debug("从存储加载了 {} 个使用统计", usageStatsCache.size());
        } catch (Exception e) {
            log.error("加载使用统计数据失败", e);
            // 尝试重新创建配置文件
            try {
                log.info("尝试重新创建使用统计配置文件");
                storeManager.saveConfig(USAGE_STATS_STORE_KEY, Collections.emptyMap());
                log.info("使用统计配置文件已重新创建");
            } catch (Exception recreateException) {
                log.error("重新创建使用统计配置文件失败", recreateException);
            }
        }
    }
    
    /**
     * 保存API Key数据到存储
     */
    private void saveApiKeysToStore() {
        try {
            List<ApiKeyInfo> apiKeys = new ArrayList<>(apiKeyCache.values());
            Map<String, Object> config = new HashMap<>();
            config.put("apiKeys", apiKeys);
            storeManager.saveConfig(API_KEYS_STORE_KEY, config);
            log.debug("保存了 {} 个API Key到存储", apiKeys.size());
        } catch (Exception e) {
            log.error("保存API Key数据失败", e);
        }
    }
    
    /**
     * 保存使用统计数据到存储
     */
    private void saveUsageStatsToStore() {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("usageStats", usageStatsCache);
            storeManager.saveConfig(USAGE_STATS_STORE_KEY, config);
            log.debug("保存了 {} 个使用统计到存储", usageStatsCache.size());
        } catch (Exception e) {
            log.error("保存使用统计数据失败", e);
        }
    }
    
    /**
     * 异步保存使用统计数据到存储
     */
    private void saveUsageStatsToStoreAsync() {
        // 在实际应用中，这里可以使用线程池异步执行
        // 为了简化实现，这里直接调用同步方法
        try {
            saveUsageStatsToStore();
        } catch (Exception e) {
            log.error("异步保存使用统计数据失败", e);
        }
    }
}