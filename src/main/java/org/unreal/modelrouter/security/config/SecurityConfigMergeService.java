package org.unreal.modelrouter.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.store.StoreManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 安全配置合并服务
 * 负责合并应用配置和持久化存储中的安全配置
 * 持久化存储中的配置优先级更高
 * 
 * 参考 ConfigMergeService 的实现模式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityConfigMergeService {

    private static final String SECURITY_CONFIG_KEY = "security-config";
    
    private final StoreManager storeManager;
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    /**
     * 获取持久化的安全配置（优先使用最新版本的配置）
     * @return 持久化配置Map
     */
    public Map<String, Object> getPersistedSecurityConfig() {
        try {
            // 首先尝试获取最新版本的配置
            List<Integer> versions = storeManager.getConfigVersions(SECURITY_CONFIG_KEY);
            if (!versions.isEmpty()) {
                // 获取最大版本号
                int latestVersion = versions.stream().mapToInt(Integer::intValue).max().orElse(0);
                Map<String, Object> config = storeManager.getConfigByVersion(SECURITY_CONFIG_KEY, latestVersion);
                if (config != null) {
                    log.info("成功加载最新版本安全配置 v{}，包含 {} 个配置项", latestVersion, config.size());
                    return config;
                }
            }
            
            // 如果没有版本配置，尝试获取当前配置
            if (storeManager.exists(SECURITY_CONFIG_KEY)) {
                Map<String, Object> config = storeManager.getConfig(SECURITY_CONFIG_KEY);
                if (config != null) {
                    log.info("成功加载持久化安全配置，包含 {} 个配置项", config.size());
                    return config;
                }
            }
            
            log.info("未找到持久化安全配置，将仅使用YAML配置");
            return new HashMap<>();
        } catch (Exception e) {
            log.warn("加载持久化安全配置时发生错误: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 获取默认安全配置（从YAML加载的配置）
     * @return 默认配置Map
     */
    public Map<String, Object> getDefaultSecurityConfig() {
        try {
            return convertSecurityPropertiesToMap(securityProperties);
        } catch (Exception e) {
            log.error("转换默认安全配置失败", e);
            return new HashMap<>();
        }
    }

    /**
     * 获取合并后的安全配置
     * @return 合并后的配置Map
     */
    public Map<String, Object> getMergedSecurityConfig() {
        Map<String, Object> defaultConfig = getDefaultSecurityConfig();
        Map<String, Object> persistedConfig = getPersistedSecurityConfig();
        
        if (persistedConfig.isEmpty()) {
            return defaultConfig;
        }
        
        return deepMergeConfigs(defaultConfig, persistedConfig);
    }

    /**
     * 检查是否存在持久化安全配置
     * @return true如果存在持久化配置
     */
    public boolean hasPersistedSecurityConfig() {
        try {
            // 检查是否有版本配置
            List<Integer> versions = storeManager.getConfigVersions(SECURITY_CONFIG_KEY);
            if (!versions.isEmpty()) {
                return true;
            }
            
            // 检查是否有当前配置
            return storeManager.exists(SECURITY_CONFIG_KEY);
        } catch (Exception e) {
            log.warn("检查持久化安全配置存在性时发生错误: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 清除持久化安全配置，恢复到YAML默认配置
     */
    public void resetToYamlSecurityConfig() {
        try {
            // 删除所有版本的配置
            List<Integer> versions = storeManager.getConfigVersions(SECURITY_CONFIG_KEY);
            for (Integer version : versions) {
                storeManager.deleteConfigVersion(SECURITY_CONFIG_KEY, version);
            }
            
            // 删除当前配置
            if (storeManager.exists(SECURITY_CONFIG_KEY)) {
                storeManager.deleteConfig(SECURITY_CONFIG_KEY);
            }
            
            log.info("已清除所有持久化安全配置，恢复到YAML默认配置");
        } catch (Exception e) {
            log.error("清除持久化安全配置时发生错误", e);
        }
    }

    /**
     * 将SecurityProperties转换为Map格式
     */
    private Map<String, Object> convertSecurityPropertiesToMap(SecurityProperties properties) {
        try {
            return objectMapper.convertValue(properties, Map.class);
        } catch (Exception e) {
            log.error("转换SecurityProperties为Map失败", e);
            return new HashMap<>();
        }
    }

    /**
     * 深度合并两个配置Map
     * @param baseConfig 基础配置（YAML配置）
     * @param overrideConfig 覆盖配置（持久化配置）
     * @return 合并后的配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deepMergeConfigs(Map<String, Object> baseConfig, Map<String, Object> overrideConfig) {
        if (overrideConfig.isEmpty()) {
            return new HashMap<>(baseConfig);
        }

        Map<String, Object> result = new HashMap<>(baseConfig);

        for (Map.Entry<String, Object> entry : overrideConfig.entrySet()) {
            String key = entry.getKey();
            Object overrideValue = entry.getValue();

            if (result.containsKey(key)) {
                Object baseValue = result.get(key);

                // 如果两个值都是Map，则递归合并
                if (baseValue instanceof Map && overrideValue instanceof Map) {
                    Map<String, Object> mergedMap = deepMergeConfigs(
                            (Map<String, Object>) baseValue,
                            (Map<String, Object>) overrideValue
                    );
                    result.put(key, mergedMap);
                } else if (baseValue instanceof List && overrideValue instanceof List) {
                    // 对于List类型，安全配置中主要是API Keys和脱敏规则
                    // 直接覆盖（因为安全配置的列表通常是完整替换）
                    result.put(key, overrideValue);
                } else {
                    // 基本类型直接覆盖
                    result.put(key, overrideValue);
                }
            } else {
                // 新增配置项
                result.put(key, overrideValue);
            }
        }

        return result;
    }

    /**
     * 获取安全配置的存储键
     */
    public String getSecurityConfigKey() {
        return SECURITY_CONFIG_KEY;
    }
}