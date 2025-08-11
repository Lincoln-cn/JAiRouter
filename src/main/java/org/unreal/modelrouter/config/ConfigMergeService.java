package org.unreal.modelrouter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.store.StoreManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置合并服务
 * 负责合并应用配置和持久化存储中的配置
 * 持久化存储中的配置优先级更高
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
     * 合并配置，存储中的配置优先级更高
     * @return 合并后的配置
     */
    public Map<String, Object> mergeConfigurations() {
        logger.info("开始合并YAML配置文件和存储中的持久化配置");

        try {
            // 1. 获取YAML配置（基础配置）
            Map<String, Object> yamlConfig = configurationHelper.convertModelRouterPropertiesToMap(modelRouterProperties);
            logger.debug("YAML配置加载完成，包含 {} 个顶级配置项", yamlConfig.size());

            // 2. 获取存储中的持久化配置
            Map<String, Object> persistedConfig = getPersistedConfig();

            // 3. 深度合并配置：持久化配置优先
            Map<String, Object> mergedConfig = deepMergeConfigs(yamlConfig, persistedConfig);

            logger.info("配置合并完成，最终配置包含 {} 个顶级配置项", mergedConfig.size());
            return mergedConfig;

        } catch (Exception e) {
            logger.error("合并配置时发生错误，将使用默认YAML配置", e);
            return configurationHelper.convertModelRouterPropertiesToMap(modelRouterProperties);
        }
    }

    /**
     * 获取持久化配置
     * @return 持久化配置Map
     */
    private Map<String, Object> getPersistedConfig() {
        try {
            if (storeManager.exists(CONFIG_KEY)) {
                Map<String, Object> config = storeManager.getConfig(CONFIG_KEY);
                if (config != null) {
                    logger.info("成功加载持久化配置，包含 {} 个顶级配置项", config.size());
                    return config;
                }
            }
            logger.info("未找到持久化配置，将仅使用YAML配置");
            return new HashMap<>();
        } catch (Exception e) {
            logger.warn("加载持久化配置时发生错误: {}", e.getMessage());
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
                String instanceId = getInstanceId(instanceConfig);
                if (instanceId != null) {
                    instanceMap.put(instanceId, instance);
                }
            }
        }

        // 再处理覆盖实例（添加或更新）
        for (Object instance : overrideInstances) {
            if (instance instanceof Map) {
                Map<String, Object> instanceConfig = (Map<String, Object>) instance;
                String instanceId = getInstanceId(instanceConfig);
                if (instanceId != null) {
                    instanceMap.put(instanceId, instance);
                }
            }
        }

        return new ArrayList<>(instanceMap.values());
    }

    /**
     * 获取实例ID
     * @param instanceConfig 实例配置
     * @return 实例ID
     */
    private String getInstanceId(Map<String, Object> instanceConfig) {
        String name = (String) instanceConfig.get("name");
        String baseUrl = (String) instanceConfig.get("baseUrl");
        if (name != null && baseUrl != null) {
            return name + "@" + baseUrl;
        }
        return null;
    }

    /**
     * 保存合并后的配置到持久化存储
     * @param mergedConfig 合并后的配置
     */
    public void persistMergedConfig(Map<String, Object> mergedConfig) {
        try {
            storeManager.saveConfig(CONFIG_KEY, mergedConfig);
            logger.info("合并后的配置已保存到持久化存储");
        } catch (Exception e) {
            logger.error("保存合并配置到持久化存储时发生错误", e);
        }
    }

    /**
     * 获取当前的合并配置
     * @return 当前合并配置
     */
    public Map<String, Object> getCurrentMergedConfig() {
        return mergeConfigurations();
    }

    /**
     * 清除持久化配置，恢复到YAML默认配置
     */
    public void resetToYamlConfig() {
        try {
            if (storeManager.exists(CONFIG_KEY)) {
                storeManager.deleteConfig(CONFIG_KEY);
                logger.info("已清除持久化配置，恢复到YAML默认配置");
            }
        } catch (Exception e) {
            logger.error("清除持久化配置时发生错误", e);
        }
    }

    /**
     * 检查是否存在持久化配置
     * @return true如果存在持久化配置
     */
    public boolean hasPersistedConfig() {
        return storeManager.exists(CONFIG_KEY);
    }
}