package org.unreal.modelrouter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.store.StoreManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 配置管理服务 - 重构版
 * 提供完整的服务配置增删改查功能
 * 支持服务、实例的动态管理
 * 支持配置版本管理
 */
@Service
public class ConfigurationService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);

    private static final String CURRENT_KEY = "model-router-config";

    private final StoreManager storeManager;
    private final ConfigurationHelper configurationHelper;
    private final ConfigMergeService configMergeService;
    private ModelServiceRegistry modelServiceRegistry; // 延迟注入避免循环依赖

    @Autowired
    public ConfigurationService(StoreManager storeManager,
                                ConfigurationHelper configurationHelper,
                                ConfigMergeService configMergeService) {
        this.storeManager = storeManager;
        this.configurationHelper = configurationHelper;
        this.configMergeService = configMergeService;
    }

    /**
     * 设置ModelServiceRegistry引用（避免循环依赖）
     */
    public void setModelServiceRegistry(ModelServiceRegistry modelServiceRegistry) {
        this.modelServiceRegistry = modelServiceRegistry;
    }

    // ==================== 版本管理 ====================

    /**
     * 获取所有配置版本号
     * @return 版本号列表
     */
    public List<Integer> getAllVersions() {
        return storeManager.getConfigVersions(CURRENT_KEY);
    }

    /**
     * 获取指定版本的配置
     * @param version 版本号，0表示YAML原始配置
     * @return 配置内容
     */
    public Map<String, Object> getVersionConfig(int version) {
        if (version == 0) {
            return configMergeService.getDefaultConfig(); // YAML 原始配置
        }
        return storeManager.getConfigByVersion(CURRENT_KEY, version);
    }

    /**
     * 保存当前配置为新版本
     * @param config 配置内容
     * @return 新版本号
     */
    public int saveAsNewVersion(Map<String, Object> config) {
        int version = getNextVersion();
        storeManager.saveConfigVersion(CURRENT_KEY, config, version);
        logger.info("已保存配置为新版本：{}", version);
        return version;
    }

    /**
     * 应用指定版本的配置
     * @param version 版本号
     */
    public void applyVersion(int version) {
        Map<String, Object> config = getVersionConfig(version);
        if (config == null) {
            throw new IllegalArgumentException("版本不存在: " + version);
        }
        storeManager.saveConfig(CURRENT_KEY, new HashMap<>(config));
        refreshRuntimeConfig();
        logger.info("已应用配置版本：{}", version);
    }

    /**
     * 获取当前最新版本号
     * @return 当前版本号
     */
    public int getCurrentVersion() {
        List<Integer> versions = getAllVersions();
        return versions.isEmpty() ? 0 : versions.get(versions.size() - 1);
    }

    /**
     * 获取下一个版本号
     * @return 下一个版本号
     */
    private int getNextVersion() {
        return getAllVersions().stream().max(Integer::compareTo).orElse(0) + 1;
    }

    // ==================== 查询操作 ====================

    /**
     * 获取所有配置（合并后的最终配置）
     * @return 完整配置Map
     */
    public Map<String, Object> getAllConfigurations() {
        Map<String, Object> configs = configMergeService.getPersistedConfig();

        // 为每个实例添加instanceId属性
        if (configs != null && configs.containsKey("services")) {
            Map<String, Object> services = (Map<String, Object>) configs.get("services");
            for (Map.Entry<String, Object> serviceEntry : services.entrySet()) {
                Map<String, Object> serviceConfig = (Map<String, Object>) serviceEntry.getValue();
                if (serviceConfig != null && serviceConfig.containsKey("instances")) {
                    List<Map<String, Object>> instances = (List<Map<String, Object>>) serviceConfig.get("instances");
                    for (Map<String, Object> instance : instances) {
                        if (instance != null && instance.containsKey("name") && instance.containsKey("baseUrl")) {
                            String name = (String) instance.get("name");
                            String baseUrl = (String) instance.get("baseUrl");
                            String instanceId = name + "@" + baseUrl;
                            instance.put("instanceId", instanceId);
                        }
                    }
                }
            }
        }
        return configs;
    }

    /**
     * 获取所有可用服务类型
     * @return 服务类型列表
     */
    public Set<String> getAvailableServiceTypes() {
        Map<String, Object> config = getAllConfigurations();
        Map<String, Object> services = getServicesFromConfig(config);
        return services.keySet();
    }

    /**
     * 获取指定服务的配置
     * @param serviceType 服务类型
     * @return 服务配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getServiceConfig(String serviceType) {
        Map<String, Object> config = getAllConfigurations();
        Map<String, Object> services = getServicesFromConfig(config);
        return (Map<String, Object>) services.get(serviceType);
    }

    /**
     * 获取指定服务的所有实例
     * @param serviceType 服务类型
     * @return 实例列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getServiceInstances(String serviceType) {
        Map<String, Object> serviceConfig = getServiceConfig(serviceType);
        if (serviceConfig == null) {
            return new ArrayList<>();
        }
        return (List<Map<String, Object>>) serviceConfig.getOrDefault("instances", new ArrayList<>());
    }

    /**
     * 获取指定服务的所有可用模型名称
     * @param serviceType 服务类型
     * @return 模型名称集合
     */
    public Set<String> getAvailableModels(String serviceType) {
        List<Map<String, Object>> instances = getServiceInstances(serviceType);
        return instances.stream()
                .map(instance -> (String) instance.get("name"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 获取指定实例的详细信息
     * @param serviceType 服务类型
     * @param instanceId 实例ID (name@baseUrl)
     * @return 实例配置
     */
    public Map<String, Object> getServiceInstance(String serviceType, String instanceId) {
        List<Map<String, Object>> instances = getServiceInstances(serviceType);
        return instances.stream()
                .filter(instance -> instanceId.equals(buildInstanceId(instance)))
                .findFirst()
                .orElse(null);
    }

    // ==================== 服务管理操作 ====================

    /**
     * 创建新服务（自动保存为新版本）
     * @param serviceType 服务类型
     * @param serviceConfig 服务配置
     */
    public void createService(String serviceType, Map<String, Object> serviceConfig) {
        logger.info("创建新服务: {}", serviceType);

        // 验证服务类型
        if (!isValidServiceType(serviceType)) {
            throw new IllegalArgumentException("无效的服务类型: " + serviceType);
        }

        Map<String, Object> currentConfig = getCurrentPersistedConfig();
        Map<String, Object> services = getServicesFromConfig(currentConfig);

        if (services.containsKey(serviceType)) {
            throw new IllegalArgumentException("服务类型已存在: " + serviceType);
        }

        // 验证和标准化服务配置
        Map<String, Object> validatedConfig = validateAndNormalizeServiceConfig(serviceConfig);
        services.put(serviceType, validatedConfig);
        currentConfig.put("services", services);

        // 保存为新版本并刷新配置
        saveAsNewVersion(currentConfig);
        refreshRuntimeConfig();

        logger.info("服务 {} 创建成功", serviceType);
    }

    /**
     * 更新服务配置（自动保存为新版本）
     * @param serviceType 服务类型
     * @param serviceConfig 新的服务配置
     */
    public void updateServiceConfig(String serviceType, Map<String, Object> serviceConfig) {
        logger.info("更新服务配置: {}", serviceType);

        Map<String, Object> currentConfig = getCurrentPersistedConfig();
        Map<String, Object> services = getServicesFromConfig(currentConfig);

        if (!services.containsKey(serviceType)) {
            throw new IllegalArgumentException("服务类型不存在: " + serviceType);
        }

        // 获取现有配置并合并更新
        Map<String, Object> existingConfig = (Map<String, Object>) services.get(serviceType);
        Map<String, Object> updatedConfig = mergeServiceConfig(existingConfig, serviceConfig);

        // 验证和标准化配置
        Map<String, Object> validatedConfig = validateAndNormalizeServiceConfig(updatedConfig);
        services.put(serviceType, validatedConfig);
        currentConfig.put("services", services);

        // 保存为新版本并刷新配置
        saveAsNewVersion(currentConfig);
        refreshRuntimeConfig();

        logger.info("服务 {} 配置更新成功", serviceType);
    }

    /**
     * 删除服务（自动保存为新版本）
     * @param serviceType 服务类型
     */
    public void deleteService(String serviceType) {
        logger.info("删除服务: {}", serviceType);

        Map<String, Object> currentConfig = getCurrentPersistedConfig();
        Map<String, Object> services = getServicesFromConfig(currentConfig);

        if (!services.containsKey(serviceType)) {
            throw new IllegalArgumentException("服务类型不存在: " + serviceType);
        }

        services.remove(serviceType);
        currentConfig.put("services", services);

        // 保存为新版本并刷新配置
        saveAsNewVersion(currentConfig);
        refreshRuntimeConfig();

        logger.info("服务 {} 删除成功", serviceType);
    }

    // ==================== 实例管理操作 ====================

    /**
     * 添加服务实例（自动保存为新版本）
     * @param serviceType 服务类型
     * @param instanceConfig 实例配置
     */
    @SuppressWarnings("unchecked")
    public void addServiceInstance(String serviceType, ModelRouterProperties.ModelInstance instanceConfig) {
        logger.info("为服务 {} 添加实例: {}", serviceType, instanceConfig.getName());

        Map<String, Object> currentConfig = getCurrentPersistedConfig();
        Map<String, Object> services = getServicesFromConfig(currentConfig);

        // 确保服务存在
        if (!services.containsKey(serviceType)) {
            // 自动创建服务
            services.put(serviceType, createDefaultServiceConfig());
        }

        Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);
        List<Map<String, Object>> instances = (List<Map<String, Object>>)
                serviceConfig.computeIfAbsent("instances", k -> new ArrayList<>());

        // 验证实例配置
        Map<String, Object> validatedInstance = validateAndNormalizeInstanceConfig(configurationHelper.convertInstanceToMap(instanceConfig));
        String instanceId = buildInstanceId(validatedInstance);

        // 检查是否已存在
        boolean exists = instances.stream()
                .anyMatch(instance -> instanceId.equals(buildInstanceId(instance)));

        if (exists) {
            throw new IllegalArgumentException("实例已存在: " + instanceId);
        }

        instances.add(validatedInstance);
        currentConfig.put("services", services);

        // 保存为新版本并刷新配置
        saveAsNewVersion(currentConfig);
        refreshRuntimeConfig();

        logger.info("实例 {} 添加成功", instanceId);
    }

    /**
     * 更新服务实例（自动保存为新版本）
     * @param serviceType 服务类型
     * @param instanceId 实例ID
     * @param instanceConfig 新的实例配置
     */
    @SuppressWarnings("unchecked")
    public void updateServiceInstance(String serviceType, String instanceId, ModelRouterProperties.ModelInstance instanceConfig) {
        logger.info("更新服务 {} 的实例 {}", serviceType, instanceId);

        Map<String, Object> currentConfig = getCurrentPersistedConfig();
        Map<String, Object> services = getServicesFromConfig(currentConfig);

        if (!services.containsKey(serviceType)) {
            throw new IllegalArgumentException("服务类型不存在: " + serviceType);
        }

        Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);
        List<Map<String, Object>> instances = (List<Map<String, Object>>)
                serviceConfig.getOrDefault("instances", new ArrayList<>());

        // 查找并更新实例
        boolean found = false;
        int targetIndex = -1;
        Map<String, Object> oldInstance = null;
        
        // 先查找实例位置和原始配置
        for (int i = 0; i < instances.size(); i++) {
            Map<String, Object> instance = instances.get(i);
            if (instanceId.equals(buildInstanceId(instance))) {
                targetIndex = i;
                oldInstance = instance;
                found = true;
                break;
            }
        }

        if (found) {
            // 合并更新配置
            Map<String, Object> updatedInstance = mergeInstanceConfig(oldInstance, configurationHelper.convertInstanceToMap(instanceConfig));
            Map<String, Object> validatedInstance = validateAndNormalizeInstanceConfig(updatedInstance);
            instances.set(targetIndex, validatedInstance);
        }

        if (!found) {
            throw new IllegalArgumentException("实例不存在: " + instanceId);
        }

        // 更新服务配置
        serviceConfig.put("instances", instances);
        services.put(serviceType, serviceConfig);
        currentConfig.put("services", services);

        // 保存为新版本并刷新配置
        saveAsNewVersion(currentConfig);
        //TODO 有bug 需要检查，会把已经修改的数据，恢复回来，新增一条
        refreshRuntimeConfig();

        logger.info("实例 {} 更新成功", instanceId);
    }

    /**
     * 删除服务实例（自动保存为新版本）
     * @param serviceType 服务类型
     * @param instanceId 实例ID
     */
    @SuppressWarnings("unchecked")
    public void deleteServiceInstance(String serviceType, String instanceId) {
        logger.info("删除服务 {} 的实例 {}", serviceType, instanceId);

        Map<String, Object> currentConfig = getCurrentPersistedConfig();
        Map<String, Object> services = getServicesFromConfig(currentConfig);

        if (!services.containsKey(serviceType)) {
            throw new IllegalArgumentException("服务类型不存在: " + serviceType);
        }

        Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);
        List<Map<String, Object>> instances = (List<Map<String, Object>>)
                serviceConfig.getOrDefault("instances", new ArrayList<>());

        // 删除匹配的实例
        boolean removed = instances.removeIf(instance -> instanceId.equals(buildInstanceId(instance)));

        if (!removed) {
            throw new IllegalArgumentException("实例不存在: " + instanceId);
        }

        // 更新服务配置
        serviceConfig.put("instances", instances);
        services.put(serviceType, serviceConfig);
        currentConfig.put("services", services);

        // 保存为新版本并刷新配置
        saveAsNewVersion(currentConfig);
        refreshRuntimeConfig();

        logger.info("实例 {} 删除成功", instanceId);
    }

    // ==================== 批量操作 ====================

    /**
     * 批量更新配置（自动保存为新版本）
     * @param configs 配置Map
     */
    public void batchUpdateConfigurations(Map<String, Object> configs) {
        logger.info("批量更新配置，包含 {} 个顶级配置项", configs.size());

        Map<String, Object> currentConfig = getCurrentPersistedConfig();

        // 深度合并配置
        for (Map.Entry<String, Object> entry : configs.entrySet()) {
            currentConfig.put(entry.getKey(), entry.getValue());
        }

        // 保存为新版本并刷新配置
        saveAsNewVersion(currentConfig);
        refreshRuntimeConfig();

        logger.info("批量配置更新成功");
    }

    /**
     * 重置配置为YAML默认值
     */
    public void resetToDefaultConfig() {
        logger.info("重置配置为YAML默认值");

        // 清除持久化配置
        configMergeService.resetToYamlConfig();

        // 刷新运行时配置
        refreshRuntimeConfig();

        logger.info("配置已重置为默认值");
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取持久化配置（优先使用最新版本的配置）
     * @return 持久化配置Map
     */
    private Map<String, Object> getCurrentPersistedConfig() {
        try {
            // 首先尝试获取最新版本的配置
            List<Integer> versions = storeManager.getConfigVersions(CURRENT_KEY);
            if (!versions.isEmpty()) {
                // 获取最大版本号
                int latestVersion = versions.stream().mapToInt(Integer::intValue).max().orElse(0);
                Map<String, Object> config = storeManager.getConfigByVersion(CURRENT_KEY, latestVersion);
                if (config != null) {
                    logger.info("成功加载最新版本持久化配置 v{}，包含 {} 个顶级配置项", latestVersion, config.size());
                    return config;
                }
            }

            // 如果没有版本配置，尝试获取当前配置
            if (storeManager.exists(CURRENT_KEY)) {
                Map<String, Object> config = storeManager.getConfig(CURRENT_KEY);
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
     * 从配置中获取services部分
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getServicesFromConfig(Map<String, Object> config) {
        Object services = config.get("services");
        if (services instanceof Map) {
            return new HashMap<>((Map<String, Object>) services);
        }
        return new HashMap<>();
    }

    /**
     * 构建实例ID
     */
    private String buildInstanceId(Map<String, Object> instance) {
        String name = (String) instance.get("name");
        String baseUrl = (String) instance.get("baseUrl");
        if (name != null && baseUrl != null) {
            return name + "@" + baseUrl;
        }
        return null;
    }

    /**
     * 验证服务类型是否有效
     */
    private boolean isValidServiceType(String serviceType) {
        try {
            configurationHelper.parseServiceType(serviceType);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 创建默认服务配置
     */
    private Map<String, Object> createDefaultServiceConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("instances", new ArrayList<>());

        // 添加默认负载均衡配置
        Map<String, Object> loadBalance = new HashMap<>();
        loadBalance.put("type", "random");
        loadBalance.put("hashAlgorithm", "md5");
        config.put("loadBalance", loadBalance);

        return config;
    }

    /**
     * 验证和标准化服务配置
     */
    private Map<String, Object> validateAndNormalizeServiceConfig(Map<String, Object> serviceConfig) {
        Map<String, Object> normalized = new HashMap<>(serviceConfig);

        // 确保instances字段存在
        if (!normalized.containsKey("instances")) {
            normalized.put("instances", new ArrayList<>());
        }

        // 验证instances是List类型
        if (!(normalized.get("instances") instanceof List)) {
            normalized.put("instances", new ArrayList<>());
        }

        return normalized;
    }

    /**
     * 验证和标准化实例配置
     */
    private Map<String, Object> validateAndNormalizeInstanceConfig(Map<String, Object> instanceConfig) {
        Map<String, Object> normalized = new HashMap<>(instanceConfig);

        // 必需字段验证
        if (!normalized.containsKey("name") || normalized.get("name") == null) {
            throw new IllegalArgumentException("实例名称不能为空");
        }

        if (!normalized.containsKey("baseUrl") || normalized.get("baseUrl") == null) {
            throw new IllegalArgumentException("实例baseUrl不能为空");
        }

        // 设置默认值
        if (!normalized.containsKey("weight")) {
            normalized.put("weight", 1);
        }

        return normalized;
    }

    /**
     * 合并服务配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeServiceConfig(Map<String, Object> existing, Map<String, Object> updates) {
        Map<String, Object> merged = new HashMap<>(existing);

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("instances".equals(key) && value instanceof List) {
                // instances字段不合并，直接替换
                merged.put(key, value);
            } else if (existing.containsKey(key) &&
                    existing.get(key) instanceof Map &&
                    value instanceof Map) {
                // 递归合并Map类型字段
                Map<String, Object> existingMap = (Map<String, Object>) existing.get(key);
                Map<String, Object> updateMap = (Map<String, Object>) value;
                merged.put(key, mergeServiceConfig(existingMap, updateMap));
            } else {
                merged.put(key, value);
            }
        }

        return merged;
    }

    /**
     * 合并实例配置
     */
    private Map<String, Object> mergeInstanceConfig(Map<String, Object> existing, Map<String, Object> updates) {
        Map<String, Object> merged = new HashMap<>(existing);
        merged.putAll(updates);
        return merged;
    }

    /**
     * 刷新运行时配置
     */
    private void refreshRuntimeConfig() {
        if (modelServiceRegistry != null) {
            try {
                // 触发ModelServiceRegistry重新加载配置
                modelServiceRegistry.refreshFromMergedConfig();
            } catch (Exception e) {
                logger.warn("刷新运行时配置时发生错误: {}", e.getMessage());
            }
        }
    }

    // ==================== 新增方法 ====================

    /**
     * 检查是否存在持久化配置
     * @return true如果存在持久化配置
     */
    public boolean hasPersistedConfig() {
        return configMergeService.hasPersistedConfig();
    }
}