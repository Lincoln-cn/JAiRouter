package org.unreal.modelrouter.config.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.common.util.SecurityUtils;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.config.core.manager.ConfigComparisonService;
import org.unreal.modelrouter.config.core.manager.ConfigValidator;
import org.unreal.modelrouter.config.core.manager.ConfigVersionManager;
import org.unreal.modelrouter.config.core.service.InstanceOperationService;
import org.unreal.modelrouter.config.event.ConfigSyncEvent;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实例配置管理服务
 *
 * 从 ConfigurationService 中提取的实例管理方法，提供实例的增删改查和批量操作功能。
 *
 * @since v2.30.0
 */
@Service
public class InstanceConfigService {

    private static final Logger logger = LoggerFactory.getLogger(InstanceConfigService.class);

    private static final String CURRENT_KEY = "model-router-config";

    private final StoreManager storeManager;
    private final ConfigConverterHelper configConverterHelper;
    private final ConfigValidator configValidator;
    private final InstanceOperationService instanceOperationService;
    private final ConfigVersionManager configVersionManager;
    private final ConfigComparisonService configComparisonService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public InstanceConfigService(final StoreManager storeManager,
                                 final ConfigConverterHelper configConverterHelper,
                                 final ConfigValidator configValidator,
                                 final InstanceOperationService instanceOperationService,
                                 final ConfigVersionManager configVersionManager,
                                 final ConfigComparisonService configComparisonService,
                                 final ApplicationEventPublisher eventPublisher) {
        this.storeManager = storeManager;
        this.configConverterHelper = configConverterHelper;
        this.configValidator = configValidator;
        this.instanceOperationService = instanceOperationService;
        this.configVersionManager = configVersionManager;
        this.configComparisonService = configComparisonService;
        this.eventPublisher = eventPublisher;
    }

    // ==================== 实例管理操作 ====================

    /**
     * 添加服务实例（优化版本，可选择是否保存为新版本）
     *
     * @param serviceType 服务类型
     * @param instanceConfig 实例配置
     */
    @SuppressWarnings("unchecked")
    public void addServiceInstance(final String serviceType, final ModelRouterProperties.ModelInstance instanceConfig) {
        logger.info("为服务 {} 添加实例: {}", serviceType, instanceConfig.getName());

        // 验证服务类型
        if (!configValidator.isValidServiceType(serviceType)) {
            throw new IllegalArgumentException("无效的服务类型: " + serviceType);
        }

        Map<String, Object> currentConfig = getCurrentPersistedConfig();
        Map<String, Object> services = getServicesFromConfig(currentConfig);

        // 确保服务存在
        if (!services.containsKey(serviceType)) {
            services.put(serviceType, configValidator.createDefaultServiceConfig());
        }

        Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);
        List<Map<String, Object>> instances =
                (List<Map<String, Object>>) serviceConfig.computeIfAbsent("instances", k -> new ArrayList<>());

        // 委托到 InstanceOperationService
        Map<String, Object> instanceMap = configConverterHelper.convertInstanceToMap(instanceConfig);
        String detail = instanceOperationService.addInstance(instances, instanceMap);

        currentConfig.put("services", services);

        // 添加版本元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "addInstance");
        metadata.put("operationDetail", detail);
        metadata.put("serviceType", serviceType);
        metadata.put("timestamp", System.currentTimeMillis());
        currentConfig.put("_metadata", metadata);

        // 使用智能版本控制
        String userId = SecurityUtils.getCurrentUserId();
        saveAsNewVersionIfChanged(currentConfig, detail, userId);

        refreshRuntimeConfig();

        logger.info("实例添加成功: {}", detail);
    }

    /**
     * 内部实例更新方法
     * v2.6.15: 简化实现，委托到 InstanceOperationService
     */
    @SuppressWarnings("unchecked")
    public void updateServiceInstanceInternal(
            final String serviceType, final String instanceId,
            final ModelRouterProperties.ModelInstance instanceConfig) {

        // 验证服务类型
        if (!configValidator.isValidServiceType(serviceType)) {
            throw new IllegalArgumentException("无效的服务类型: " + serviceType);
        }

        Map<String, Object> currentConfig = getCurrentPersistedConfig();
        Map<String, Object> services = getServicesFromConfig(currentConfig);

        if (!services.containsKey(serviceType)) {
            throw new IllegalArgumentException("服务类型不存在: " + serviceType);
        }

        Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);
        List<Map<String, Object>> instances =
                (List<Map<String, Object>>) serviceConfig.getOrDefault("instances", new ArrayList<>());

        // 委托到 InstanceOperationService
        Map<String, Object> instanceMap = configConverterHelper.convertInstanceToMap(instanceConfig);
        String detail = instanceOperationService.updateInstance(instances, instanceId, instanceMap);

        // 更新配置
        serviceConfig.put("instances", instances);
        services.put(serviceType, serviceConfig);
        currentConfig.put("services", services);

        // 添加版本元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "updateInstance");
        metadata.put("operationDetail", detail);
        metadata.put("serviceType", serviceType);
        metadata.put("timestamp", System.currentTimeMillis());
        currentConfig.put("_metadata", metadata);

        // 使用智能版本控制
        String userId = SecurityUtils.getCurrentUserId();
        saveAsNewVersionIfChanged(currentConfig, detail, userId);

        refreshRuntimeConfig();

        logger.info("实例更新成功: {}", instanceId);
    }

    /**
     * 批量更新服务实例（只创建一个版本）
     * 这个方法解决了多次调用问题，将多个实例操作合并为一次版本创建
     *
     * @param serviceType 服务类型
     * @param operations  实例操作列表
     */
    @SuppressWarnings("unchecked")
    public void batchUpdateServiceInstances(final String serviceType, final List<InstanceOperation> operations) {
        logger.info("批量更新服务 {} 的实例，操作数量: {}", serviceType, operations.size());

        // 验证服务类型
        if (!configValidator.isValidServiceType(serviceType)) {
            throw new IllegalArgumentException("无效的服务类型: " + serviceType);
        }

        Map<String, Object> currentConfig = getCurrentPersistedConfig();
        Map<String, Object> services = getServicesFromConfig(currentConfig);

        // 确保服务存在
        if (!services.containsKey(serviceType)) {
            services.put(serviceType, configValidator.createDefaultServiceConfig());
        }

        Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);
        List<Map<String, Object>> instances =
                (List<Map<String, Object>>) serviceConfig.computeIfAbsent("instances", k -> new ArrayList<>());

        List<String> operationDetails = new ArrayList<>();

        // 执行所有操作（委托到 InstanceOperationService）
        for (InstanceOperation operation : operations) {
            String detail;
            switch (operation.type()) {
                case ADD:
                    detail = instanceOperationService.addInstance(
                            instances,
                            configConverterHelper.convertInstanceToMap(operation.instanceConfig()));
                    operationDetails.add(detail);
                    break;
                case UPDATE:
                    detail = instanceOperationService.updateInstance(
                            instances,
                            operation.instanceId(),
                            configConverterHelper.convertInstanceToMap(operation.instanceConfig()));
                    operationDetails.add(detail);
                    break;
                case DELETE:
                    detail = instanceOperationService.deleteInstance(instances, operation.instanceId());
                    operationDetails.add(detail);
                    break;
            }
        }

        // 更新配置
        serviceConfig.put("instances", instances);
        services.put(serviceType, serviceConfig);
        currentConfig.put("services", services);

        // 添加版本元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "batchUpdateInstances");
        metadata.put("operationDetail", "批量更新服务实例: " + String.join(", ", operationDetails));
        metadata.put("serviceType", serviceType);
        metadata.put("operationCount", operations.size());
        metadata.put("timestamp", System.currentTimeMillis());
        currentConfig.put("_metadata", metadata);

        // 使用智能版本控制 - 只创建一个版本
        String userId = SecurityUtils.getCurrentUserId();
        String description = String.format("批量更新服务 %s 的 %d 个实例", serviceType, operations.size());
        saveAsNewVersionIfChanged(currentConfig, description, userId);

        refreshRuntimeConfig();

        logger.info("批量更新服务 {} 的实例完成，操作详情: {}", serviceType, String.join(", ", operationDetails));
    }

    // ==================== 共享辅助方法 ====================

    /**
     * 获取持久化配置（优先使用最新版本的配置）
     *
     * @return 持久化配置Map
     */
    public Map<String, Object> getCurrentPersistedConfig() {
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
    public Map<String, Object> getServicesFromConfig(final Map<String, Object> config) {
        Object servicesObj = config.get("services");
        if (servicesObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) servicesObj;
            return services; // 直接返回原 Map，以便修改后影响原 config
        }
        // 如果不存在 services，创建新的并添加到 config
        Map<String, Object> services = new HashMap<>();
        config.put("services", services);
        return services;
    }

    /**
     * 刷新运行时配置
     * v2.12.4: 改为事件发布模式，简化实现
     */
    public void refreshRuntimeConfig() {
        Map<String, Object> config = getCurrentPersistedConfig();
        eventPublisher.publishEvent(ConfigSyncEvent.refresh(config));
        logger.debug("已发布配置同步事件 ConfigSyncEvent.refresh");
    }

    /**
     * 条件性保存为新版本（如果配置发生变化）
     * v2.28.0: 简化实现，委托到 ConfigVersionManager
     *
     * @param config      配置内容
     * @param description 描述信息
     * @param userId      用户ID
     * @return 版本号（如果创建了新版本则返回新版本号，否则返回当前版本号）
     */
    public int saveAsNewVersionIfChanged(final Map<String, Object> config,
                                         final String description, final String userId) {
        Map<String, Object> currentConfig = getCurrentPersistedConfig();
        if (!configComparisonService.isConfigurationChanged(currentConfig, config)) {
            logger.info("配置未发生变化，不创建新版本");
            return configVersionManager.getCurrentVersion();
        }
        logger.info("检测到配置变化，创建新版本");
        return configVersionManager.saveAsNewVersion(config, description, userId);
    }
}
