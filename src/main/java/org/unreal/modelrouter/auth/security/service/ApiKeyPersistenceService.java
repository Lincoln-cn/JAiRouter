package org.unreal.modelrouter.auth.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.model.UsageStatistics;
import org.unreal.modelrouter.auth.security.util.ApiKeyHashUtil;
import org.unreal.modelrouter.persistence.store.StoreManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API Key 持久化服务
 * 负责 API Key 的存储、加载和版本管理
 *
 * @since v2.14.2
 */
@Slf4j
@Service
public class ApiKeyPersistenceService {

    private static final String API_KEYS_STORE_KEY = "security.api-keys";
    public static final String STORE_API_KEYS = "apiKeys";

    private final StoreManager storeManager;
    private final ObjectMapper objectMapper;

    // API Key 配置管理器（用于版本管理）
    @Autowired(required = false)
    private ApiKeyConfigManager apiKeyConfigManager;

    public ApiKeyPersistenceService(final StoreManager storeManager,
                                     final ObjectMapper objectMapper) {
        this.storeManager = storeManager;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存 API Key 数据到存储
     *
     * @param apiKeyCache API Key 缓存（keyHash -> ApiKey）
     */
    public void saveApiKeysToStore(final Map<String, ApiKey> apiKeyCache) {
        try {
            List<ApiKey> apiKeys = new ArrayList<>(apiKeyCache.values());
            Map<String, Object> config = new HashMap<>();
            config.put(STORE_API_KEYS, apiKeys);
            storeManager.saveConfig(API_KEYS_STORE_KEY, config);
            log.debug("保存了 {} 个API Key到存储", apiKeys.size());
        } catch (Exception e) {
            log.error("保存API Key数据失败", e);
        }
    }

    /**
     * 检查是否有持久化的 API Key 配置
     *
     * @return 是否存在持久化配置
     */
    public boolean hasPersistedAccountConfig() {
        try {
            List<Integer> versions = storeManager.getConfigVersions(API_KEYS_STORE_KEY);
            if (!versions.isEmpty()) {
                return true;
            }
            return storeManager.exists(API_KEYS_STORE_KEY);
        } catch (Exception e) {
            log.warn("检查持久化ApiKey存在性时发生错误: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从 YAML 配置初始化 API Key
     *
     * @param apiKeyCache    API Key 缓存
     * @param keyIdIndex     ID 索引
     * @param yamlApiKeys    YAML 配置中的 API Keys
     */
    public void initializeApiKeyFromYaml(final Map<String, ApiKey> apiKeyCache,
                                          final Map<String, String> keyIdIndex,
                                          final List<ApiKey> yamlApiKeys) {
        log.info("首次启动，将YAML API Key配置保存为版本1");

        try {
            Map<String, Object> defaultConfig = new HashMap<>();
            defaultConfig.put(STORE_API_KEYS, yamlApiKeys);

            // 保存配置版本
            if (apiKeyConfigManager != null) {
                apiKeyConfigManager.saveNewVersion(defaultConfig);
            } else {
                // 兼容旧实现：直接使用 storeManager
                List<Integer> versions = storeManager.getConfigVersions(API_KEYS_STORE_KEY);
                int version = versions.isEmpty() ? 1 : versions.stream().max(Integer::compareTo).orElse(0) + 1;
                storeManager.saveConfigVersion(API_KEYS_STORE_KEY, defaultConfig, version);
            }

            // 迁移并加载到缓存
            yamlApiKeys.forEach(item -> {
                // 对旧的明文 keyValue 进行哈希迁移
                if (item.getKeyValue() != null && !ApiKeyHashUtil.isHashedFormat(item.getKeyValue())) {
                    String keyHash = ApiKeyHashUtil.hashApiKey(item.getKeyValue());
                    item.setKeyHash(keyHash);
                    item.setKeyValue(null);  // 清除明文
                    item.setKeyPrefix("sk-");
                    log.info("迁移API Key {} 从明文存储到哈希存储", item.getKeyId());
                }

                if (item.getKeyHash() != null && !apiKeyCache.containsKey(item.getKeyHash())) {
                    apiKeyCache.put(item.getKeyHash(), item);
                    keyIdIndex.put(item.getKeyId(), item.getKeyHash());
                }
            });

            // 保存迁移后的配置
            saveApiKeysToStore(apiKeyCache);
            log.info("YAML API Key配置已保存为版本1，并完成哈希迁移");
        } catch (Exception e) {
            log.error("从YAML配置初始化API Key配置失败", e);
            throw new RuntimeException("Failed to initialize API keys from YAML config", e);
        }
    }

    /**
     * 加载最新的 API Key 配置
     *
     * @param apiKeyCache    API Key 缓存
     * @param keyIdIndex     ID 索引
     * @param yamlApiKeys    YAML 配置中的 API Keys（用于回退）
     */
    public void loadLatestApiKeyConfig(final Map<String, ApiKey> apiKeyCache,
                                         final Map<String, String> keyIdIndex,
                                         final List<ApiKey> yamlApiKeys) {
        log.info("发现持久化API Key配置，加载最新版本");

        try {
            int currentVersion = apiKeyConfigManager != null
                    ? apiKeyConfigManager.getCurrentVersion()
                    : storeManager.getConfigVersions(API_KEYS_STORE_KEY).stream().max(Integer::compareTo).orElse(0);

            Map<String, Object> versionConfig = apiKeyConfigManager != null
                    ? apiKeyConfigManager.getVersionConfig(currentVersion)
                    : storeManager.getConfigByVersion(API_KEYS_STORE_KEY, currentVersion);

            List<Map<String, Object>> keys = (List<Map<String, Object>>) versionConfig.get(STORE_API_KEYS);
            log.debug("从版本 {} 加载了 {} 个API Key配置", currentVersion, keys != null ? keys.size() : 0);

            // 如果持久化数据为空或无效，从 YAML 重新初始化
            if (keys == null || keys.isEmpty()) {
                log.warn("持久化API Key配置为空，从YAML重新初始化");
                initializeApiKeyFromYaml(apiKeyCache, keyIdIndex, yamlApiKeys);
                return;
            }

            keys.stream()
                    .map(item -> objectMapper.convertValue(item, ApiKey.class))
                    .forEach(item -> {
                        initializeApiKeyFields(item);

                        // 对旧的明文 keyValue 进行哈希迁移
                        if (item.getKeyValue() != null && !ApiKeyHashUtil.isHashedFormat(item.getKeyValue())) {
                            String keyHash = ApiKeyHashUtil.hashApiKey(item.getKeyValue());
                            item.setKeyHash(keyHash);
                            item.setKeyValue(null);
                            item.setKeyPrefix("sk-");
                            log.info("迁移API Key {} 从明文存储到哈希存储", item.getKeyId());
                        }

                        if (item.getKeyHash() != null && !apiKeyCache.containsKey(item.getKeyHash())) {
                            apiKeyCache.put(item.getKeyHash(), item);
                            keyIdIndex.put(item.getKeyId(), item.getKeyHash());
                        } else if (item.getKeyHash() == null && item.getKeyValue() == null) {
                            log.warn("API Key {} 没有有效的keyHash或keyValue，跳过", item.getKeyId());
                        }
                    });

            // 如果缓存仍然为空，说明数据无效，从YAML重新初始化
            if (apiKeyCache.isEmpty()) {
                log.warn("加载后的API Key缓存为空，从YAML重新初始化");
                initializeApiKeyFromYaml(apiKeyCache, keyIdIndex, yamlApiKeys);
                return;
            }

            // 保存迁移后的配置
            saveApiKeysToStore(apiKeyCache);
            log.info("已加载API Key配置版本 {}，共 {} 个密钥", currentVersion, apiKeyCache.size());
        } catch (Exception e) {
            log.error("加载持久化API Key配置失败", e);
            throw new RuntimeException("Failed to load persisted API key config", e);
        }
    }
    
    /**
     * 加载持久化配置并与 YAML 配置合并（YAML 优先）
     * v2.x 修复：解决 YAML 中定义的 API Key 无法生效的问题
     *
     * @param apiKeyCache    API Key 缓存
     * @param keyIdIndex     ID 索引
     * @param yamlApiKeys    YAML 配置中的 API Keys
     */
    public void loadAndMergeWithYaml(final Map<String, ApiKey> apiKeyCache,
                                      final Map<String, String> keyIdIndex,
                                      final List<ApiKey> yamlApiKeys) {
        log.info("合并持久化API Key配置与YAML配置（YAML优先）");
        
        try {
            // 1. 先加载持久化配置
            int currentVersion = apiKeyConfigManager != null
                    ? apiKeyConfigManager.getCurrentVersion()
                    : storeManager.getConfigVersions(API_KEYS_STORE_KEY).stream().max(Integer::compareTo).orElse(0);
            
            Map<String, Object> versionConfig = null;
            if (currentVersion > 0) {
                versionConfig = apiKeyConfigManager != null
                        ? apiKeyConfigManager.getVersionConfig(currentVersion)
                        : storeManager.getConfigByVersion(API_KEYS_STORE_KEY, currentVersion);
            }
            
            List<Map<String, Object>> persistedKeys = versionConfig != null 
                    ? (List<Map<String, Object>>) versionConfig.get(STORE_API_KEYS) 
                    : null;
            
            // 2. 加载持久化的 API Key 到缓存
            if (persistedKeys != null && !persistedKeys.isEmpty()) {
                persistedKeys.stream()
                        .map(item -> objectMapper.convertValue(item, ApiKey.class))
                        .forEach(item -> {
                            initializeApiKeyFields(item);

                            // 对旧的明文 keyValue 进行哈希迁移
                            if (item.getKeyValue() != null && !ApiKeyHashUtil.isHashedFormat(item.getKeyValue())) {
                                String keyHash = ApiKeyHashUtil.hashApiKey(item.getKeyValue());
                                item.setKeyHash(keyHash);
                                item.setKeyValue(null);
                                item.setKeyPrefix("sk-");
                                log.info("迁移API Key {} 从明文存储到哈希存储", item.getKeyId());
                            }

                            if (item.getKeyHash() != null && !apiKeyCache.containsKey(item.getKeyHash())) {
                                apiKeyCache.put(item.getKeyHash(), item);
                                keyIdIndex.put(item.getKeyId(), item.getKeyHash());
                            }
                        });
                log.debug("从持久化加载了 {} 个API Key", apiKeyCache.size());
            }
            
            // 3. 合并 YAML 配置（YAML 优先，覆盖同名 keyId）
            int mergedCount = 0;
            for (ApiKey yamlKey : yamlApiKeys) {
                String keyId = yamlKey.getKeyId();
                
                // 处理明文 keyValue
                if (yamlKey.getKeyValue() != null && !ApiKeyHashUtil.isHashedFormat(yamlKey.getKeyValue())) {
                    String keyHash = ApiKeyHashUtil.hashApiKey(yamlKey.getKeyValue());
                    yamlKey.setKeyHash(keyHash);
                    yamlKey.setKeyValue(null);
                    yamlKey.setKeyPrefix("sk-");
                }
                
                // 如果已存在同名 keyId，先移除旧的
                String existingKeyHash = keyIdIndex.get(keyId);
                if (existingKeyHash != null) {
                    apiKeyCache.remove(existingKeyHash);
                    log.debug("移除旧的API Key: {} (keyId={})", existingKeyHash, keyId);
                }
                
                // 添加 YAML 中的新配置
                if (yamlKey.getKeyHash() != null) {
                    apiKeyCache.put(yamlKey.getKeyHash(), yamlKey);
                    keyIdIndex.put(keyId, yamlKey.getKeyHash());
                    mergedCount++;
                    log.info("合并YAML API Key: {} (keyId={})", yamlKey.getKeyHash().substring(0, 16) + "...", keyId);
                }
            }
            
            log.info("合并完成：持久化 {} 个，YAML 合并 {} 个，总计 {} 个API Key", 
                    persistedKeys != null ? persistedKeys.size() : 0, mergedCount, apiKeyCache.size());
            
            // 4. 保存合并后的配置
            if (mergedCount > 0) {
                saveApiKeysToStore(apiKeyCache);
                log.info("已保存合并后的API Key配置");
            }
        } catch (Exception e) {
            log.error("合并API Key配置失败，回退到YAML初始化: {}", e.getMessage());
            // 失败时回退到 YAML 初始化
            initializeApiKeyFromYaml(apiKeyCache, keyIdIndex, yamlApiKeys);
        }
    }

    /**
     * 获取下一个版本号
     *
     * @return 下一个版本号
     */
    public int getNextAccountVersion() {
        List<Integer> versions = storeManager.getConfigVersions(API_KEYS_STORE_KEY);
        return versions.isEmpty() ? 1 : versions.stream().max(Integer::compareTo).orElse(0) + 1;
    }

    /**
     * 获取当前版本号
     *
     * @return 当前版本号
     */
    public int getCurrentVersion() {
        return storeManager.getConfigVersions(API_KEYS_STORE_KEY).stream()
                .max(Integer::compareTo)
                .orElse(0);
    }

    /**
     * 获取所有版本列表
     *
     * @return 版本号列表
     */
    public List<Integer> getAllVersions() {
        return storeManager.getConfigVersions(API_KEYS_STORE_KEY);
    }

    /**
     * 加载指定版本的配置
     *
     * @param version 版本号
     * @return 配置数据
     */
    public Map<String, Object> getVersionConfig(final int version) {
        if (apiKeyConfigManager != null) {
            return apiKeyConfigManager.getVersionConfig(version);
        }
        return storeManager.getConfigByVersion(API_KEYS_STORE_KEY, version);
    }

    /**
     * 初始化使用统计
     *
     * @param apiKey ApiKey 实体
     */
    public void initializeUsageStatistics(final ApiKey apiKey) {
        if (apiKey.getUsage() == null) {
            apiKey.setUsage(UsageStatistics.builder()
                    .dailyUsage(new HashMap<>())
                    .dailyTokenUsage(new HashMap<>())
                    .failedRequests(0L)
                    .lastUsedAt(null)
                    .successfulRequests(0L)
                    .totalRequests(0L)
                    .build());
        } else {
            if (apiKey.getUsage().getDailyUsage() == null) {
                apiKey.getUsage().setDailyUsage(new HashMap<>());
            }
            if (apiKey.getUsage().getDailyTokenUsage() == null) {
                apiKey.getUsage().setDailyTokenUsage(new HashMap<>());
            }
        }
    }

    /**
     * 初始化 ApiKey 基本字段
     *
     * @param apiKey ApiKey 实体
     */
    public void initializeApiKeyFields(final ApiKey apiKey) {
        if (apiKey.getCreatedAt() == null) {
            apiKey.setCreatedAt(java.time.LocalDateTime.now());
        }
        if (apiKey.getUsage() == null) {
            initializeUsageStatistics(apiKey);
        }
        if (apiKey.getKeyPrefix() == null) {
            apiKey.setKeyPrefix("sk-");
        }
        if (apiKey.getDailyRequestLimit() <= 0L) {
            apiKey.setDailyRequestLimit(0L);
        }
    }
}