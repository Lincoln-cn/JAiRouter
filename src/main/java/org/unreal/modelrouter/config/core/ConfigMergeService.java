package org.unreal.modelrouter.config.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.common.util.InstanceIdUtils;

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

    /**
     * 重置前的持久化配置备份键。
     *
     * <p>重置必须删除 {@link #CONFIG_KEY} 的全部版本（否则 {@link #getPersistedConfig()} 会回落到
     * 最新版本，重置不会生效），因此重置前先把当前生效配置写入该键；运行时配置合并只读
     * {@link #CONFIG_KEY}，不读该键，备份内容不会回流到运行时配置。</p>
     */
    public static final String PRE_RESET_BACKUP_KEY = "config_pre_reset_backup";

    private final StoreManager storeManager;
    private final ModelRouterProperties modelRouterProperties;
    private final ConfigConverterHelper configConverterHelper;

    @Autowired
    public ConfigMergeService(final StoreManager storeManager,
                              final ModelRouterProperties modelRouterProperties,
                              final ConfigConverterHelper configConverterHelper) {
        this.storeManager = storeManager;
        this.modelRouterProperties = modelRouterProperties;
        this.configConverterHelper = configConverterHelper;
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
    public Map<String, Object> deepMergeConfigs(
            final Map<String, Object> baseConfig,
            final Map<String, Object> overrideConfig) {
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
    private List<Object> mergeInstanceLists(final List<?> baseInstances, final List<?> overrideInstances) {
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
     * 清除持久化配置，恢复到YAML默认配置
     *
     * <p>重置前先把当前生效的持久化配置备份到 {@link #PRE_RESET_BACKUP_KEY}。该备份
     * <b>没有恢复入口</b>（issue #780）：仓库里没有读取/恢复该键的端点，控制台也不提供一键恢复，
     * 恢复必须直接操作配置存储（如存储层里的 {@code config_pre_reset_backup} 记录）。
     * 另注意键是固定名 ⇒ 每次重置都会覆盖上一次的备份，只保留最近一次；
     * 备份失败会直接抛出，此时还没有删除任何配置。清除过程中任一删除失败都会抛出
     * {@link IllegalStateException}（原始异常保留为 cause），因为此时配置可能只被部分清除，
     * 不能当作成功返回。</p>
     *
     * @return 备份键；没有持久化配置可备份时返回 {@code null}
     * @throws IllegalStateException 清除持久化配置失败
     */
    public String resetToYamlConfig() {
        final Map<String, Object> effectiveConfig = readEffectivePersistedConfig();
        String backupKey = null;
        if (effectiveConfig != null && !effectiveConfig.isEmpty()) {
            storeManager.saveConfig(PRE_RESET_BACKUP_KEY, effectiveConfig);
            backupKey = PRE_RESET_BACKUP_KEY;
            logger.info("已备份重置前的持久化配置到键 {}", backupKey);
        }

        try {
            // 删除所有版本的配置：只删当前配置的话 getPersistedConfig() 会回落到最新版本
            final List<Integer> versions = storeManager.getConfigVersions(CONFIG_KEY);
            for (Integer version : versions) {
                storeManager.deleteConfigVersion(CONFIG_KEY, version);
            }

            // 删除当前配置
            if (storeManager.exists(CONFIG_KEY)) {
                storeManager.deleteConfig(CONFIG_KEY);
            }

            logger.info("已清除所有持久化配置，恢复到YAML默认配置");
            return backupKey;
        } catch (Exception e) {
            logger.error("清除持久化配置时发生错误", e);
            throw new IllegalStateException("清除持久化配置失败，配置可能只被部分清除", e);
        }
    }

    /**
     * 读取当前生效的持久化配置，顺序与 {@link #getPersistedConfig()} 一致：
     * 优先当前配置，其次最新版本；都没有时返回 {@code null}（表示没有可备份内容）。
     */
    private Map<String, Object> readEffectivePersistedConfig() {
        if (storeManager.exists(CONFIG_KEY)) {
            final Map<String, Object> current = storeManager.getConfig(CONFIG_KEY);
            if (current != null && !current.isEmpty()) {
                return current;
            }
        }
        final List<Integer> versions = storeManager.getConfigVersions(CONFIG_KEY);
        if (!versions.isEmpty()) {
            final int latestVersion = versions.stream().mapToInt(Integer::intValue).max().orElse(0);
            return storeManager.getConfigByVersion(CONFIG_KEY, latestVersion);
        }
        return null;
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
        return configConverterHelper.convertModelRouterPropertiesToMap(modelRouterProperties);
    }
}
