package org.unreal.modelrouter.auth.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.exception.AuthenticationException;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.model.UsageStatistics;
import org.unreal.modelrouter.persistence.store.StoreManager;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API Key 配置持久化管理器
 * 负责 API Key 配置的加载、保存和版本管理
 */
@Slf4j
@Component
public class ApiKeyConfigManager {

    private final StoreManager storeManager;
    private final String apiKeysStoreKey = "security.api-keys";
    private final String storeApiKeys = "apiKeys";

    public ApiKeyConfigManager(final StoreManager storeManager) {
        this.storeManager = storeManager;
    }

    /**
     * 保存 API Key 数据到存储
     */
    public void saveApiKeysToStore(final List<ApiKey> apiKeys) {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put(storeApiKeys, apiKeys);
            storeManager.saveConfig(apiKeysStoreKey, config);
            log.debug("保存了 {} 个 API Key 到存储", apiKeys.size());
        } catch (Exception e) {
            log.error("保存 API Key 数据失败", e);
        }
    }

    /**
     * 检查是否存在持久化的账户配置
     */
    public boolean hasPersistedAccountConfig() {
        try {
            List<Integer> versions = storeManager.getConfigVersions(apiKeysStoreKey);
            if (!versions.isEmpty()) {
                return true;
            }
            return storeManager.exists(apiKeysStoreKey);
        } catch (Exception e) {
            log.warn("检查持久化 ApiKey 存在性时发生错误：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 从 YAML 配置初始化 API Key
     */
    public Map<String, Object> loadFromYaml(final List<ApiKey> yamlKeys) {
        Map<String, Object> config = new HashMap<>();
        config.put(storeApiKeys, yamlKeys);
        return config;
    }

    /**
     * 获取版本配置
     */
    public Map<String, Object> getVersionConfig(final int version) {
        if (version == 0) {
            Map<String, Object> config = new HashMap<>();
            config.put(storeApiKeys, new java.util.ArrayList<>());
            return config;
        }
        return storeManager.getConfigByVersion(apiKeysStoreKey, version);
    }

    /**
     * 保存新版本配置
     */
    public int saveNewVersion(final Map<String, Object> config) {
        int version = getCurrentVersion() + 1;
        storeManager.saveConfigVersion(apiKeysStoreKey, config, version);
        log.info("已保存 API Key 配置为新版本：{}", version);
        return version;
    }

    /**
     * 获取所有版本
     */
    public List<Integer> getAllVersions() {
        return storeManager.getConfigVersions(apiKeysStoreKey);
    }

    /**
     * 获取当前版本
     */
    public int getCurrentVersion() {
        List<Integer> versions = getAllVersions();
        return versions.isEmpty() ? 0 : versions.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /**
     * 从版本配置中加载 API Keys
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> loadApiKeysFromVersionConfig(final Map<String, Object> versionConfig) {
        List<Map<String, Object>> keys = (List<Map<String, Object>>) versionConfig.get(storeApiKeys);
        return keys != null ? keys : new java.util.ArrayList<>();
    }
}
