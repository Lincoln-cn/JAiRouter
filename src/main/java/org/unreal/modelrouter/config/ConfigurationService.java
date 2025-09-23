package org.unreal.modelrouter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.unreal.modelrouter.checker.ServerChecker;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.util.ApplicationContextProvider;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.tracing.config.SamplingConfigurationValidator;
import org.unreal.modelrouter.util.SecurityUtils;

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
    private final ServiceStateManager serviceStateManager;
    private final SamplingConfigurationValidator samplingValidator;
    private ModelServiceRegistry modelServiceRegistry; // 延迟注入避免循环依赖

    @Autowired
    public ConfigurationService(StoreManager storeManager,
                                ConfigurationHelper configurationHelper,
                                ConfigMergeService configMergeService,
                                ServiceStateManager serviceStateManager,
                                SamplingConfigurationValidator samplingValidator) {
        this.storeManager = storeManager;
        this.configurationHelper = configurationHelper;
        this.configMergeService = configMergeService;
        this.serviceStateManager = serviceStateManager;
        this.samplingValidator = samplingValidator;
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
        logger.info("开始应用配置版本: {}", version);
        
        Map<String, Object> config = getVersionConfig(version);
        if (config == null) {
            throw new IllegalArgumentException("版本不存在: " + version);
        }
        
        logger.debug("获取到版本 {} 的配置，包含 {} 个顶级配置项", version, config.size());
        
        // 添加版本元数据
        Map<String, Object> configWithMetadata = new HashMap<>(config);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", version);
        metadata.put("appliedAt", System.currentTimeMillis());
        metadata.put("operation", "apply");
        configWithMetadata.put("_metadata", metadata);
        
        logger.debug("添加元数据后配置包含 {} 个顶级配置项，元数据: {}", configWithMetadata.size(), metadata);
        
        storeManager.saveConfig(CURRENT_KEY, configWithMetadata);
        logger.info("配置已保存到存储管理器，键: {}", CURRENT_KEY);
        
        refreshRuntimeConfig();
        
        // 记录配置回滚审计日志
        logConfigurationRollback(version, config);
        
        logger.info("已应用配置版本：{}", version);
    }

    /**
     * 回滚到指定版本的配置
     * @param version 版本号
     */
    public void rollbackToVersion(int version) {
        Map<String, Object> config = getVersionConfig(version);
        if (config == null) {
            throw new IllegalArgumentException("版本不存在: " + version);
        }
        
        // 添加版本元数据
        Map<String, Object> configWithMetadata = new HashMap<>(config);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", version);
        metadata.put("appliedAt", System.currentTimeMillis());
        metadata.put("operation", "rollback");
        configWithMetadata.put("_metadata", metadata);
        
        storeManager.saveConfig(CURRENT_KEY, configWithMetadata);
        refreshRuntimeConfig();
        
        // 记录配置回滚审计日志
        logConfigurationRollback(version, config);
        
        logger.info("已回滚到配置版本：{}", version);
    }

    /**
     * 删除指定版本的配置
     * @param version 版本号
     */
    public void deleteConfigVersion(int version) {
        // 检查版本是否存在
        List<Integer> versions = getAllVersions();
        if (!versions.contains(version)) {
            throw new IllegalArgumentException("版本不存在: " + version);
        }
        
        // 调用StoreManager删除指定版本
        storeManager.deleteConfigVersion(CURRENT_KEY, version);
        logger.info("已删除配置版本：{}", version);
    }

    /**
     * 获取实际当前配置版本号
     * @return 当前配置版本号，如果不存在则返回0
     */
    public int getActualCurrentVersion() {
        // 获取当前配置的版本信息
        Map<String, Object> currentConfig = configMergeService.getPersistedConfig();
        logger.debug("获取当前配置用于版本检查，配置为空: {}", currentConfig == null);
        
        if (currentConfig != null) {
            logger.debug("当前配置包含_metadata: {}", currentConfig.containsKey("_metadata"));
            if (currentConfig.containsKey("_metadata")) {
                Map<String, Object> metadata = (Map<String, Object>) currentConfig.get("_metadata");
                logger.debug("元数据内容: {}", metadata);
                if (metadata != null && metadata.containsKey("version")) {
                    Object versionObj = metadata.get("version");
                    if (versionObj instanceof Number) {
                        int actualVersion = ((Number) versionObj).intValue();
                        logger.info("从元数据获取到实际当前版本: {}", actualVersion);
                        return actualVersion;
                    }
                }
            }
        }
        
        // 如果没有元数据信息，则返回通过版本列表计算的版本号
        int fallbackVersion = getCurrentVersion();
        logger.info("未找到元数据版本信息，使用回退版本: {}", fallbackVersion);
        return fallbackVersion;
    }

    /**
     * 获取当前最新版本号（基于版本列表）
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

        // 为每个实例添加instanceId和health属性
        if (configs != null && configs.containsKey("services")) {
            Map<String, Object> services = (Map<String, Object>) configs.get("services");
            for (Map.Entry<String, Object> serviceEntry : services.entrySet()) {
                String serviceType = serviceEntry.getKey();
                Map<String, Object> serviceConfig = (Map<String, Object>) serviceEntry.getValue();
                if (serviceConfig != null && serviceConfig.containsKey("instances")) {
                    List<Map<String, Object>> instances = (List<Map<String, Object>>) serviceConfig.get("instances");
                    for (Map<String, Object> instance : instances) {
                        if (instance != null && instance.containsKey("name") && instance.containsKey("baseUrl")) {
                            String name = (String) instance.get("name");
                            String baseUrl = (String) instance.get("baseUrl");
                            // 检查是否已存在instanceId，如果不存在才生成新的
                            if (!instance.containsKey("instanceId") || instance.get("instanceId") == null) {
                                String instanceId = buildInstanceId(name, baseUrl);
                                instance.put("instanceId", instanceId);
                            }
                            
                            // 添加健康状态信息，使用ServiceStateManager
                            boolean isHealthy = serviceStateManager.isInstanceHealthy(serviceType, name, baseUrl);
                            instance.put("health", isHealthy);
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
        List<Map<String, Object>> instances = (List<Map<String, Object>>) serviceConfig.getOrDefault("instances", new ArrayList<>());
        
        // 确保每个实例都有status字段
        for (Map<String, Object> instance : instances) {
            if (!instance.containsKey("status")) {
                instance.put("status", "active"); // 默认为active
            }
        }
        
        return instances;
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
                .map(instance -> {
                    // 添加健康状态信息
                    String baseUrl = (String) instance.get("baseUrl");
                    String name = (String) instance.get("name");
                    if (baseUrl != null && name != null) {
                        boolean isHealthy = serviceStateManager.isInstanceHealthy(serviceType, name, baseUrl);
                        instance.put("health", isHealthy);
                    }
                    // 确保status字段存在
                    if (!instance.containsKey("status")) {
                        instance.put("status", "active"); // 默认为active
                    }
                    return instance;
                })
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
     * 添加服务实例（优化版本，可选择是否保存为新版本）
     * @param serviceType 服务类型
     * @param instanceConfig 实例配置
     * @param createNewVersion 是否创建新版本
     */
    @SuppressWarnings("unchecked")
    public void addServiceInstance(String serviceType, ModelRouterProperties.ModelInstance instanceConfig, boolean createNewVersion) {
        logger.info("为服务 {} 添加实例: {}", serviceType, instanceConfig.getName());
        
        // 验证服务类型
        if (!isValidServiceType(serviceType)) {
            throw new IllegalArgumentException("无效的服务类型: " + serviceType);
        }

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
        
        // 检查是否已存在（通过name和baseUrl判断）
        String name = (String) validatedInstance.get("name");
        String baseUrl = (String) validatedInstance.get("baseUrl");
        boolean exists = instances.stream()
                .anyMatch(instance -> {
                    String instanceName = (String) instance.get("name");
                    String instanceBaseUrl = (String) instance.get("baseUrl");
                    return name.equals(instanceName) && baseUrl.equals(instanceBaseUrl);
                });

        if (exists) {
            throw new IllegalArgumentException("实例已存在: " + name + "@" + baseUrl);
        }

        instances.add(validatedInstance);
        currentConfig.put("services", services);

        if (createNewVersion) {
            // 保存为新版本并刷新配置
            saveAsNewVersion(currentConfig);
        } else {
            // 直接保存配置但不创建新版本
            storeManager.saveConfig(CURRENT_KEY, currentConfig);
        }
        refreshRuntimeConfig();

        logger.info("实例 {} 添加成功", name + "@" + baseUrl);
    }

    /**
     * 添加服务实例（默认创建新版本以保持向后兼容）
     */
    public void addServiceInstance(String serviceType, ModelRouterProperties.ModelInstance instanceConfig) {
        addServiceInstance(serviceType, instanceConfig, true);
    }

    /**
     * 更新服务实例（优化版本，可选择是否保存为新版本）
     * @param serviceType 服务类型
     * @param instanceId 实例ID
     * @param instanceConfig 新的实例配置
     * @param createNewVersion 是否创建新版本
     */
    @SuppressWarnings("unchecked")
    public void updateServiceInstance(String serviceType, String instanceId, ModelRouterProperties.ModelInstance instanceConfig, boolean createNewVersion) {
        logger.info("更新服务 {} 的实例 {}", serviceType, instanceId);
        
        // 验证服务类型
        if (!isValidServiceType(serviceType)) {
            throw new IllegalArgumentException("无效的服务类型: " + serviceType);
        }

        Map<String, Object> currentConfig = getCurrentPersistedConfig();
        Map<String, Object> services = getServicesFromConfig(currentConfig);

        if (!services.containsKey(serviceType)) {
            throw new IllegalArgumentException("服务类型不存在: " + serviceType);
        }

        Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);
        List<Map<String, Object>> instances = (List<Map<String, Object>>)
                serviceConfig.getOrDefault("instances", new ArrayList<>());

        boolean found = false;
        int targetIndex = -1;
        Map<String, Object> oldInstance = null;
        
        // 先查找实例位置和原始配置
        for (int i = 0; i < instances.size(); i++) {
            Map<String, Object> instance = instances.get(i);
            String currentInstanceId = buildInstanceId(instance);
            logger.info("比较实例ID: 请求ID={}, 配置ID={}, 匹配结果={}", instanceId, currentInstanceId, instanceId.equals(currentInstanceId));
            if (instanceId.equals(currentInstanceId)) {
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
            // 记录所有实例信息用于调试
            logger.warn("实例不存在: {}，服务 {} 中的所有实例:", instanceId, serviceType);
            for (int i = 0; i < instances.size(); i++) {
                Map<String, Object> instance = instances.get(i);
                String currentInstanceId = buildInstanceId(instance);
                logger.warn("  实例 {}: ID={}, name={}, baseUrl={}", i, currentInstanceId, instance.get("name"), instance.get("baseUrl"));
            }
            throw new IllegalArgumentException("实例不存在: " + instanceId);
        }

        // 更新服务配置
        serviceConfig.put("instances", instances);
        services.put(serviceType, serviceConfig);
        currentConfig.put("services", services);

        if (createNewVersion) {
            // 保存为新版本并刷新配置
            saveAsNewVersion(currentConfig);
        } else {
            // 直接保存配置但不创建新版本
            storeManager.saveConfig(CURRENT_KEY, currentConfig);
        }
        refreshRuntimeConfig();

        logger.info("实例 {} 更新成功", instanceId);
    }

    /**
     * 更新服务实例（默认创建新版本以保持向后兼容）
     */
    public void updateServiceInstance(String serviceType, String instanceId, ModelRouterProperties.ModelInstance instanceConfig) {
        updateServiceInstance(serviceType, instanceId, instanceConfig, true);
    }

    /**
     * 删除服务实例（优化版本，可选择是否保存为新版本）
     * @param serviceType 服务类型
     * @param instanceId 实例ID
     * @param createNewVersion 是否创建新版本
     */
    @SuppressWarnings("unchecked")
    public void deleteServiceInstance(String serviceType, String instanceId, boolean createNewVersion) {
        logger.info("删除服务 {} 的实例 {}", serviceType, instanceId);
        
        // 验证服务类型
        if (!isValidServiceType(serviceType)) {
            throw new IllegalArgumentException("无效的服务类型: " + serviceType);
        }

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

        if (createNewVersion) {
            // 保存为新版本并刷新配置
            saveAsNewVersion(currentConfig);
        } else {
            // 直接保存配置但不创建新版本
            storeManager.saveConfig(CURRENT_KEY, currentConfig);
        }
        refreshRuntimeConfig();

        logger.info("实例 {} 删除成功", instanceId);
    }

    /**
     * 删除服务实例（默认创建新版本以保持向后兼容）
     */
    public void deleteServiceInstance(String serviceType, String instanceId) {
        deleteServiceInstance(serviceType, instanceId, true);
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
     * 从配置中获取services部分，并添加健康状态信息
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getServicesFromConfig(Map<String, Object> config) {
        Object servicesObj = config.get("services");
        if (servicesObj instanceof Map) {
            return new HashMap<>((Map<String, Object>) servicesObj);
        }
        return new HashMap<>();
    }

    /**
     * 构建实例ID
     */
    private String buildInstanceId(Map<String, Object> instance) {
        String name = (String) instance.get("name");
        // 同时支持baseUrl和base-url两种字段名
        String baseUrl = (String) instance.get("baseUrl");
        if (baseUrl == null) {
            baseUrl = (String) instance.get("base-url");
        }
        // 检查是否已存在instanceId字段
        String instanceId = (String) instance.get("instanceId");
        if (instanceId != null && !instanceId.isEmpty()) {
            return instanceId;
        }
        if (name != null && baseUrl != null) {
            // 使用name和baseUrl生成一致的ID，而不是随机UUID
            return name + "@" + baseUrl;
        }
        return null;
    }
    
    /**
     * 根据模块名称和基础URL构建实例ID
     * @param moduleName 模块名称
     * @param baseUrl 基础URL
     * @return 实例ID
     */
    public String buildInstanceId(String moduleName, String baseUrl) {
        if (moduleName != null && baseUrl != null) {
            // 使用模块名称和基础URL生成一致的ID，而不是随机UUID
            return moduleName + "@" + baseUrl;
        }
        return "unknown";
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
        
        // 添加status字段的默认值
        if (!normalized.containsKey("status")) {
            normalized.put("status", "active");
        }
        
        // 确保instanceId字段存在
        if (!normalized.containsKey("instanceId") || normalized.get("instanceId") == null) {
            String name = (String) normalized.get("name");
            String baseUrl = (String) normalized.get("baseUrl");
            if (name != null && baseUrl != null) {
                normalized.put("instanceId", buildInstanceId(name, baseUrl));
            }
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
        
        // 确保instanceId字段存在
        if (!merged.containsKey("instanceId")) {
            String name = (String) merged.get("name");
            String baseUrl = (String) merged.get("baseUrl");
            if (name != null && baseUrl != null) {
                merged.put("instanceId", buildInstanceId(name, baseUrl));
            }
        }
        
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
                
                // 通知健康检查组件清理过期的实例状态缓存
                try {
                    ServerChecker serverChecker = ApplicationContextProvider.getBean(ServerChecker.class);
                    ServiceStateManager serviceStateManager = ApplicationContextProvider.getBean(ServiceStateManager.class);
                    
                    if (serverChecker != null) {
                        serverChecker.clearExpiredInstanceStates();
                    }
                    
                    if (serviceStateManager != null) {
                        serviceStateManager.clearExpiredInstanceHealthStatus();
                    }
                } catch (Exception e) {
                    logger.warn("通知健康检查组件清理缓存时发生错误: {}", e.getMessage());
                }
            } catch (Exception e) {
                logger.warn("刷新运行时配置时发生错误: {}", e.getMessage());
            }
        }
    }

    // ==================== 追踪配置管理 ====================
    
    /**
     * 获取当前追踪配置
     * @return TraceConfig对象
     */
    public TraceConfig getTraceConfig() {
        Map<String, Object> currentConfig = getAllConfigurations();
        return extractTraceConfig(currentConfig);
    }
    
    /**
     * 从配置Map中提取追踪配置
     * @param config 配置Map
     * @return TraceConfig对象
     */
    private TraceConfig extractTraceConfig(Map<String, Object> config) {
        if (config.containsKey("trace")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> traceConfigMap = (Map<String, Object>) config.get("trace");
            return TraceConfig.fromMap(traceConfigMap);
        }
        return new TraceConfig(); // 返回默认配置
    }
    
    /**
     * 更新追踪配置
     * @param traceConfig 新的追踪配置
     * @param createNewVersion 是否创建新版本
     */
    public void updateTraceConfig(TraceConfig traceConfig, boolean createNewVersion) {
        logger.info("更新追踪配置");
        
        Map<String, Object> currentConfig;
        if (createNewVersion) {
            currentConfig = getCurrentPersistedConfig();
        } else {
            currentConfig = configMergeService.getPersistedConfig();
        }
        
        // 更新配置
        currentConfig.put("trace", traceConfig.toMap());
        
        if (createNewVersion) {
            // 保存为新版本并刷新配置
            saveAsNewVersion(currentConfig);
        } else {
            // 直接保存配置但不创建新版本
            storeManager.saveConfig(CURRENT_KEY, currentConfig);
        }
        
        refreshRuntimeConfig();
        logger.info("追踪配置更新成功");
    }
    
    /**
     * 删除追踪配置
     * @param createNewVersion 是否创建新版本
     */
    public void deleteTraceConfig(boolean createNewVersion) {
        logger.info("删除追踪配置");
        
        Map<String, Object> currentConfig;
        if (createNewVersion) {
            currentConfig = getCurrentPersistedConfig();
        } else {
            currentConfig = configMergeService.getPersistedConfig();
        }
        
        // 删除追踪配置
        currentConfig.remove("trace");
        
        if (createNewVersion) {
            // 保存为新版本并刷新配置
            saveAsNewVersion(currentConfig);
        } else {
            // 直接保存配置但不创建新版本
            storeManager.saveConfig(CURRENT_KEY, currentConfig);
        }
        
        refreshRuntimeConfig();
        logger.info("追踪配置删除成功");
    }
    
    // ==================== 追踪采样配置管理 ====================
    
    /**
     * 获取追踪采样配置
     * 
     * @return 采样配置Map
     */
    public Map<String, Object> getTracingSamplingConfig() {
        Map<String, Object> currentConfig = getAllConfigurations();
        
        // 提取追踪配置
        if (currentConfig.containsKey("tracing")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tracingConfig = (Map<String, Object>) currentConfig.get("tracing");
            
            // 提取采样配置
            if (tracingConfig.containsKey("sampling")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> samplingConfig = (Map<String, Object>) tracingConfig.get("sampling");
                // 确保返回的配置包含所有默认键
                Map<String, Object> result = createDefaultSamplingConfig();
                result.putAll(samplingConfig);
                return result;
            }
        }
        
        // 返回默认配置
        return createDefaultSamplingConfig();
    }
    
    /**
     * 更新追踪采样配置
     * 
     * @param samplingConfig 新的采样配置
     * @param createNewVersion 是否创建新版本
     */
    public void updateTracingSamplingConfig(Map<String, Object> samplingConfig, boolean createNewVersion) {
        logger.info("更新追踪采样配置");
        
        // 验证配置
        if (samplingConfig != null && !samplingConfig.isEmpty()) {
            try {
                // 将Map转换为TracingConfiguration.SamplingConfig对象进行验证
                org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig config = 
                    convertMapToSamplingConfig(samplingConfig);
                
                SamplingConfigurationValidator.ValidationResult result = samplingValidator.validateSamplingConfig(config);
                if (!result.isValid()) {
                    throw new IllegalArgumentException("采样配置验证失败: " + result.getErrorMessage());
                }
                
                if (result.hasWarnings()) {
                    logger.warn("采样配置验证警告: {}", result.getWarningMessage());
                }
            } catch (Exception e) {
                logger.warn("采样配置验证过程中发生错误，跳过验证: {}", e.getMessage());
            }
        }
        
        Map<String, Object> currentConfig;
        if (createNewVersion) {
            currentConfig = getCurrentPersistedConfig();
        } else {
            currentConfig = configMergeService.getPersistedConfig();
        }
        
        // 获取或创建追踪配置
        @SuppressWarnings("unchecked")
        Map<String, Object> tracingConfig = (Map<String, Object>) currentConfig.computeIfAbsent(
                "tracing", k -> new HashMap<String, Object>());
        
        // 更新采样配置
        tracingConfig.put("sampling", samplingConfig);
        
        if (createNewVersion) {
            // 保存为新版本并刷新配置
            saveAsNewVersion(currentConfig);
        } else {
            // 直接保存配置但不创建新版本
            storeManager.saveConfig(CURRENT_KEY, currentConfig);
        }
        
        refreshRuntimeConfig();
        
        // 记录配置变更审计日志
        logConfigurationChange("tracing.sampling", "update", samplingConfig, createNewVersion);
        
        logger.info("追踪采样配置更新成功");
    }
    
    /**
     * 回滚追踪采样配置到指定版本
     * 
     * @param targetVersion 目标版本
     * @return 回滚后的采样配置
     */
    public Map<String, Object> rollbackTracingSamplingConfig(int targetVersion) {
        logger.info("回滚追踪采样配置到版本: {}", targetVersion);
        
        // 获取目标版本的配置
        Map<String, Object> targetConfig = getVersionConfig(targetVersion);
        if (targetConfig == null) {
            throw new IllegalArgumentException("目标版本不存在: " + targetVersion);
        }
        
        // 提取目标版本的采样配置
        Map<String, Object> targetSamplingConfig = extractSamplingConfigFromVersion(targetConfig);
        if (targetSamplingConfig == null) {
            logger.warn("目标版本 {} 中没有采样配置，使用默认配置", targetVersion);
            targetSamplingConfig = createDefaultSamplingConfig();
        }
        
        // 验证目标配置
        try {
            org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig config = 
                convertMapToSamplingConfig(targetSamplingConfig);
            
            SamplingConfigurationValidator.ValidationResult result = samplingValidator.validateSamplingConfig(config);
            if (!result.isValid()) {
                throw new IllegalArgumentException("目标版本的采样配置无效: " + result.getErrorMessage());
            }
            
            if (result.hasWarnings()) {
                logger.warn("目标版本的采样配置有警告: {}", result.getWarningMessage());
            }
        } catch (Exception e) {
            logger.warn("目标版本的采样配置验证过程中发生错误，跳过验证: {}", e.getMessage());
        }
        
        // 更新当前配置
        Map<String, Object> currentConfig = configMergeService.getPersistedConfig();
        
        // 获取或创建追踪配置
        @SuppressWarnings("unchecked")
        Map<String, Object> tracingConfig = (Map<String, Object>) currentConfig.computeIfAbsent(
                "tracing", k -> new HashMap<String, Object>());
        
        // 更新采样配置
        tracingConfig.put("sampling", targetSamplingConfig);
        
        // 保存配置
        storeManager.saveConfig(CURRENT_KEY, currentConfig);
        refreshRuntimeConfig();
        
        // 记录回滚审计日志
        logSamplingConfigRollback(targetVersion, targetSamplingConfig);
        
        logger.info("追踪采样配置回滚成功，目标版本: {}", targetVersion);
        return targetSamplingConfig;
    }
    
    /**
     * 从版本配置中提取采样配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSamplingConfigFromVersion(Map<String, Object> versionConfig) {
        if (versionConfig == null) {
            return null;
        }
        
        if (versionConfig.containsKey("tracing")) {
            Map<String, Object> tracingConfig = (Map<String, Object>) versionConfig.get("tracing");
            if (tracingConfig != null && tracingConfig.containsKey("sampling")) {
                return (Map<String, Object>) tracingConfig.get("sampling");
            }
        }
        
        return null;
    }
    
    /**
     * 创建默认采样配置
     * 
     * @return 默认采样配置
     */
    private Map<String, Object> createDefaultSamplingConfig() {
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("ratio", 1.0);
        defaultConfig.put("serviceRatios", new HashMap<String, Double>());
        defaultConfig.put("alwaysSample", new ArrayList<String>());
        defaultConfig.put("neverSample", new ArrayList<String>());
        defaultConfig.put("rules", new ArrayList<Map<String, Object>>());
        return defaultConfig;
    }
    
    /**
     * 将Map转换为SamplingConfig对象
     * 
     * @param configMap 配置Map
     * @return SamplingConfig对象
     */
    @SuppressWarnings("unchecked")
    private org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig convertMapToSamplingConfig(Map<String, Object> configMap) {
        org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig config = 
            new org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig();
        
        if (configMap.containsKey("ratio")) {
            Object ratioObj = configMap.get("ratio");
            if (ratioObj instanceof Number) {
                config.setRatio(((Number) ratioObj).doubleValue());
            }
        }
        
        if (configMap.containsKey("serviceRatios")) {
            Object serviceRatiosObj = configMap.get("serviceRatios");
            if (serviceRatiosObj instanceof Map) {
                Map<String, Object> serviceRatiosMap = (Map<String, Object>) serviceRatiosObj;
                Map<String, Double> serviceRatios = new HashMap<>();
                for (Map.Entry<String, Object> entry : serviceRatiosMap.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        serviceRatios.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
                config.setServiceRatios(serviceRatios);
            }
        }
        
        if (configMap.containsKey("alwaysSample")) {
            Object alwaysSampleObj = configMap.get("alwaysSample");
            if (alwaysSampleObj instanceof List) {
                config.setAlwaysSample((List<String>) alwaysSampleObj);
            }
        }
        
        if (configMap.containsKey("neverSample")) {
            Object neverSampleObj = configMap.get("neverSample");
            if (neverSampleObj instanceof List) {
                config.setNeverSample((List<String>) neverSampleObj);
            }
        }
        
        if (configMap.containsKey("rules")) {
            Object rulesObj = configMap.get("rules");
            if (rulesObj instanceof List) {
                List<Map<String, Object>> rulesList = (List<Map<String, Object>>) rulesObj;
                List<org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig.SamplingRule> rules = new ArrayList<>();
                
                for (Map<String, Object> ruleMap : rulesList) {
                    org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig.SamplingRule rule = 
                        new org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig.SamplingRule();
                    
                    if (ruleMap.containsKey("condition")) {
                        rule.setCondition((String) ruleMap.get("condition"));
                    }
                    if (ruleMap.containsKey("ratio")) {
                        Object ratioObj = ruleMap.get("ratio");
                        if (ratioObj instanceof Number) {
                            rule.setRatio(((Number) ratioObj).doubleValue());
                        }
                    }
                    rules.add(rule);
                }
                config.setRules(rules);
            }
        }
        
        // 处理自适应配置
        if (configMap.containsKey("adaptive")) {
            Object adaptiveObj = configMap.get("adaptive");
            if (adaptiveObj instanceof Map) {
                Map<String, Object> adaptiveMap = (Map<String, Object>) adaptiveObj;
                org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig.AdaptiveConfig adaptiveConfig = 
                    new org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig.AdaptiveConfig();
                
                if (adaptiveMap.containsKey("enabled")) {
                    adaptiveConfig.setEnabled((Boolean) adaptiveMap.get("enabled"));
                }
                if (adaptiveMap.containsKey("targetSpansPerSecond")) {
                    Object targetObj = adaptiveMap.get("targetSpansPerSecond");
                    if (targetObj instanceof Number) {
                        adaptiveConfig.setTargetSpansPerSecond(((Number) targetObj).longValue());
                    }
                }
                if (adaptiveMap.containsKey("minRatio")) {
                    Object minRatioObj = adaptiveMap.get("minRatio");
                    if (minRatioObj instanceof Number) {
                        adaptiveConfig.setMinRatio(((Number) minRatioObj).doubleValue());
                    }
                }
                if (adaptiveMap.containsKey("maxRatio")) {
                    Object maxRatioObj = adaptiveMap.get("maxRatio");
                    if (maxRatioObj instanceof Number) {
                        adaptiveConfig.setMaxRatio(((Number) maxRatioObj).doubleValue());
                    }
                }
                if (adaptiveMap.containsKey("adjustmentInterval")) {
                    Object intervalObj = adaptiveMap.get("adjustmentInterval");
                    if (intervalObj instanceof Number) {
                        adaptiveConfig.setAdjustmentInterval(((Number) intervalObj).longValue());
                    }
                }
                
                config.setAdaptive(adaptiveConfig);
            }
        }
        
        return config;
    }
    
    // ==================== 新增方法 ====================

    /**
     * 检查是否存在持久化配置
     * @return true如果存在持久化配置
     */
    public boolean hasPersistedConfig() {
        return configMergeService.hasPersistedConfig();
    }
    
    // ==================== 审计日志功能 ====================
    
    /**
     * 记录配置变更审计日志
     * 
     * @param configType 配置类型
     * @param action 操作类型 (create, update, delete)
     * @param configData 配置数据
     * @param createNewVersion 是否创建新版本
     */
    private void logConfigurationChange(String configType, String action, Map<String, Object> configData, boolean createNewVersion) {
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("configType", configType);
            auditData.put("action", action);
            auditData.put("timestamp", java.time.Instant.now().toString());
            auditData.put("createNewVersion", createNewVersion);
            
            if (createNewVersion) {
                auditData.put("version", getCurrentVersion());
            }
            
            // 记录配置变更的关键信息（不记录敏感数据）
            if (configData != null && !configData.isEmpty()) {
                Map<String, Object> sanitizedData = sanitizeConfigData(configData);
                auditData.put("configChanges", sanitizedData);
            }
            
            // 使用结构化日志记录审计信息
            logger.info("配置变更审计: {}", auditData);
            
        } catch (Exception e) {
            // 审计日志失败不应影响主业务流程
            logger.warn("记录配置变更审计日志失败: {}", e.getMessage());
        }
    }
    
    /**
     * 记录配置回滚审计日志
     * 
     * @param targetVersion 目标版本
     * @param config 回滚后的配置
     */
    private void logConfigurationRollback(int targetVersion, Map<String, Object> config) {
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("configType", "configuration.rollback");
            auditData.put("action", "rollback");
            auditData.put("timestamp", java.time.Instant.now().toString());
            auditData.put("targetVersion", targetVersion);
            auditData.put("currentVersion", getCurrentVersion());
            
            // 记录回滚目标配置的关键信息摘要
            Map<String, Object> configSummary = createConfigSummary(config);
            auditData.put("configSummary", configSummary);
            
            // 使用结构化日志记录审计信息
            logger.info("配置回滚审计: {}", auditData);
            
        } catch (Exception e) {
            // 审计日志失败不应影响主业务流程
            logger.warn("记录配置回滚审计日志失败: {}", e.getMessage());
        }
    }
    
    /**
     * 创建配置摘要，用于审计日志
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> createConfigSummary(Map<String, Object> config) {
        Map<String, Object> summary = new HashMap<>();
        
        if (config.containsKey("services")) {
            Map<String, Object> services = (Map<String, Object>) config.get("services");
            summary.put("serviceCount", services.size());
            summary.put("serviceTypes", services.keySet());
        }
        
        if (config.containsKey("tracing")) {
            summary.put("hasTracingConfig", true);
            Map<String, Object> tracing = (Map<String, Object>) config.get("tracing");
            if (tracing.containsKey("sampling")) {
                summary.put("hasSamplingConfig", true);
            }
        }
        
        return summary;
    }
    
    /**
     * 记录采样配置回滚审计日志
     * 
     * @param targetVersion 目标版本
     * @param samplingConfig 回滚后的采样配置
     */
    private void logSamplingConfigRollback(int targetVersion, Map<String, Object> samplingConfig) {
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("configType", "tracing.sampling.rollback");
            auditData.put("action", "rollback");
            auditData.put("timestamp", java.time.Instant.now().toString());
            auditData.put("targetVersion", targetVersion);
            auditData.put("currentVersion", getCurrentVersion());
            
            // 记录回滚后的采样配置摘要
            if (samplingConfig != null && !samplingConfig.isEmpty()) {
                Map<String, Object> sanitizedData = sanitizeConfigData(samplingConfig);
                auditData.put("rolledBackConfig", sanitizedData);
            }
            
            // 使用结构化日志记录审计信息
            logger.info("采样配置回滚审计: {}", auditData);
            
        } catch (Exception e) {
            // 审计日志失败不应影响主业务流程
            logger.warn("记录采样配置回滚审计日志失败: {}", e.getMessage());
        }
    }
    
    /**
     * 脱敏配置数据，移除敏感信息
     * 
     * @param configData 原始配置数据
     * @return 脱敏后的配置数据
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeConfigData(Map<String, Object> configData) {
        Map<String, Object> sanitized = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : configData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // 跳过敏感字段
            if (isSensitiveField(key)) {
                sanitized.put(key, "[MASKED]");
                continue;
            }
            
            // 递归处理嵌套对象
            if (value instanceof Map) {
                sanitized.put(key, sanitizeConfigData((Map<String, Object>) value));
            } else if (value instanceof List) {
                sanitized.put(key, sanitizeConfigList((List<Object>) value));
            } else {
                sanitized.put(key, value);
            }
        }
        
        return sanitized;
    }
    
    /**
     * 脱敏配置列表数据
     */
    @SuppressWarnings("unchecked")
    private List<Object> sanitizeConfigList(List<Object> configList) {
        List<Object> sanitized = new ArrayList<>();
        
        for (Object item : configList) {
            if (item instanceof Map) {
                sanitized.add(sanitizeConfigData((Map<String, Object>) item));
            } else if (item instanceof List) {
                sanitized.add(sanitizeConfigList((List<Object>) item));
            } else {
                sanitized.add(item);
            }
        }
        
        return sanitized;
    }
    
    /**
     * 判断字段是否为敏感字段
     */
    private boolean isSensitiveField(String fieldName) {
        // 追踪采样配置中暂无敏感字段，但保留扩展性
        String[] sensitiveFields = {
            "password", "secret", "key", "token", "credential"
        };
        
        String lowerFieldName = fieldName.toLowerCase();
        for (String sensitiveField : sensitiveFields) {
            if (lowerFieldName.contains(sensitiveField)) {
                return true;
            }
        }
        
        return false;
    }
}