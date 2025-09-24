package org.unreal.modelrouter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.util.InstanceIdUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置合并服务 负责合并应用配置和持久化存储中的配置 持久化存储中的配置优先级更高
 */
@Service
public class ConfigMergeService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigMergeService.class);
    private static final String CONFIG_KEY = "model-router-config";

    private final StoreManager storeManager;
    private final ModelRouterProperties modelRouterProperties;
    private final ConfigurationHelper configurationHelper;

    @Autowired
    public ConfigMergeService(StoreManager storeManager,
                              ModelRouterProperties modelRouterProperties,
                              ConfigurationHelper configurationHelper) {
        this.storeManager = storeManager;
        this.modelRouterProperties = modelRouterProperties;
        this.configurationHelper = configurationHelper;
    }

    /**
     * 获取持久化配置（仅用于配置合并，不处理版本控制）
     *
     * @return 持久化配置Map
     */
    public Map<String, Object> getPersistedConfig() {
        try {
            // 首先尝试获取当前配置（最新应用的配置）
            if (storeManager.exists(CONFIG_KEY)) {
                Map<String, Object> config = storeManager.getConfig(CONFIG_KEY);
                if (config != null) {
                    logger.info("成功加载当前持久化配置，包含 {} 个顶级配置项", config.size());
                    return config;
                }
            }

            // 如果没有当前配置，尝试获取最新版本的配置
            List<Integer> versions = storeManager.getConfigVersions(CONFIG_KEY);
            if (!versions.isEmpty()) {
                // 获取最大版本号
                int latestVersion = versions.stream().mapToInt(Integer::intValue).max().orElse(0);
                Map<String, Object> config = storeManager.getConfigByVersion(CONFIG_KEY, latestVersion);
                if (config != null) {
                    logger.info("成功加载最新版本持久化配置 v{}，包含 {} 个顶级配置项", latestVersion, config.size());
                    return config;
                }
            }

            logger.info("未找到持久化配置，将使用默认YAML配置");
            return getDefaultConfig();
        } catch (Exception e) {
            logger.warn("加载持久化配置时发生错误: {}", e.getMessage());
            return getDefaultConfig();
        }
    }

    /**
     * 深度合并两个配置Map
     *
     * @param baseConfig 基础配置（YAML配置）
     * @param overrideConfig 覆盖配置（持久化配置）
     * @return 合并后的配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> deepMergeConfigs(Map<String, Object> baseConfig, Map<String, Object> overrideConfig) {
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
                    // 对于List，特殊处理services配置
                    if ("instances".equals(key)) {
                        result.put(key, mergeInstanceLists((List<?>) baseValue, (List<?>) overrideValue));
                    } else {
                        // 其他List直接覆盖
                        result.put(key, overrideValue);
                    }
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
     * 合并实例列表，支持实例的增删改
     *
     * @param baseInstances YAML中的实例列表
     * @param overrideInstances 持久化的实例列表
     * @return 合并后的实例列表
     */
    @SuppressWarnings("unchecked")
    private List<Object> mergeInstanceLists(List<?> baseInstances, List<?> overrideInstances) {
        Map<String, Object> instanceMap = new HashMap<>();

        // 先添加基础实例
        for (Object instance : baseInstances) {
            if (instance instanceof Map) {
                Map<String, Object> instanceConfig = (Map<String, Object>) instance;
                String instanceId = InstanceIdUtils.getInstanceId(instanceConfig);
                if (instanceId != null) {
                    // 确保instanceId字段存在
                    if (!instanceConfig.containsKey("instanceId")) {
                        instanceConfig.put("instanceId", instanceId);
                    }
                    instanceMap.put(instanceId, instance);
                }
            }
        }

        // 再处理覆盖实例（添加或更新）
        for (Object instance : overrideInstances) {
            if (instance instanceof Map) {
                Map<String, Object> instanceConfig = (Map<String, Object>) instance;
                String instanceId = InstanceIdUtils.getInstanceId(instanceConfig);
                if (instanceId != null) {
                    // 确保instanceId字段存在
                    if (!instanceConfig.containsKey("instanceId")) {
                        instanceConfig.put("instanceId", instanceId);
                    }
                    instanceMap.put(instanceId, instance);
                }
            }
        }

        return new ArrayList<>(instanceMap.values());
    }

    /**
     * 清除持久化配置，恢复到YAML默认配置 注意：此方法仅用于清除存储中的配置，版本控制由ConfigurationService处理
     */
    public void resetToYamlConfig() {
        try {
            // 删除所有版本的配置
            List<Integer> versions = storeManager.getConfigVersions(CONFIG_KEY);
            for (Integer version : versions) {
                storeManager.deleteConfigVersion(CONFIG_KEY, version);
            }

            // 删除当前配置
            if (storeManager.exists(CONFIG_KEY)) {
                storeManager.deleteConfig(CONFIG_KEY);
            }

            logger.info("已清除所有持久化配置，恢复到YAML默认配置");
        } catch (Exception e) {
            logger.error("清除持久化配置时发生错误", e);
        }
    }

    /**
     * 检查是否存在持久化配置
     *
     * @return true如果存在持久化配置
     */
    public boolean hasPersistedConfig() {
        try {
            // 检查是否有版本配置
            List<Integer> versions = storeManager.getConfigVersions(CONFIG_KEY);
            if (!versions.isEmpty()) {
                return true;
            }

            // 检查是否有当前配置
            return storeManager.exists(CONFIG_KEY);
        } catch (Exception e) {
            logger.warn("检查持久化配置存在性时发生错误: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取默认配置（YAML配置）
     *
     * @return 默认配置Map
     */
    public Map<String, Object> getDefaultConfig() {
        return configurationHelper.convertModelRouterPropertiesToMap(modelRouterProperties);
    }
}
