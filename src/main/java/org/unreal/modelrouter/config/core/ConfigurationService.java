package org.unreal.modelrouter.config.core;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.config.event.ConfigSyncEvent;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.config.core.manager.ConfigValidator;
import org.unreal.modelrouter.config.core.manager.ConfigVersionManager;
import org.unreal.modelrouter.config.core.manager.InstanceManager;
import org.unreal.modelrouter.config.core.manager.TracingConfigManager;
import org.unreal.modelrouter.common.dto.CircuitBreakerConfig;
import org.unreal.modelrouter.common.dto.LoadBalanceConfig;
import org.unreal.modelrouter.common.dto.RateLimitConfig;
import org.unreal.modelrouter.config.dto.UpdateServiceConfigRequest;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.monitor.tracing.config.SamplingConfigurationValidator;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.common.util.InstanceIdUtils;
import org.unreal.modelrouter.common.util.SecurityUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.unreal.modelrouter.config.core.manager.ConfigComparisonService;
import org.unreal.modelrouter.config.core.service.ConfigQueryService;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置管理服务 - 重构版
 * 
 * 提供完整的服务配置增删改查功能，支持服务、实例的动态管理，支持配置版本管理。
 * 
 * <h2>职责分层</h2>
 * 
 * <h3>【配置协调层】协调器职责，管理配置生命周期</h3>
 * <ul>
 *   <li>postConstructInit() - 初始化配置</li>
 *   <li>setModelServiceRegistry() / setConfigSyncService() - 延迟注入避免循环依赖</li>
 *   <li>saveAsNewVersionIfChanged() - 保存为新版本</li>
 *   <li>batchUpdateConfigurations() - 批量更新配置</li>
 *   <li>resetToDefaultConfig() - 重置为默认配置</li>
 *   <li>hasPersistedConfig() - 检查是否有持久化配置</li>
 *   <li>cleanVersion() - 清理版本</li>
 * </ul>
 * 
 * <h3>【配置查询层】提供配置查询功能</h3>
 * <ul>
 *   <li>getAllConfigurations() - 获取所有配置</li>
 *   <li>getAvailableServiceTypes() - 获取可用服务类型</li>
 *   <li>getAvailableModels() - 获取指定服务类型的可用模型</li>
 *   <li>getTraceConfig() - 获取追踪配置</li>
 *   <li>getTracingSamplingConfig() - 获取追踪采样配置</li>
 * </ul>
 * 
 * <h3>【配置更新层】提供配置更新功能</h3>
 * <ul>
 *   <li>updateServiceConfigDto() - 更新服务配置DTO</li>
 *   <li>addServiceInstance() - 添加服务实例</li>
 *   <li>batchUpdateServiceInstances() - 批量更新服务实例</li>
 *   <li>updateTraceConfig() - 更新追踪配置</li>
 *   <li>deleteTraceConfig() - 删除追踪配置</li>
 *   <li>updateTracingSamplingConfig() - 更新追踪采样配置</li>
 * </ul>
 * 
 * <h2>依赖组件</h2>
 * <ul>
 *   <li>ConfigurationHelper - 配置辅助工具</li>
 *   <li>ConfigMergeService - 配置合并服务</li>
 *   <li>ServiceConfigManager - 服务配置管理器</li>
 *   <li>InstanceManager - 实例管理器</li>
 *   <li>ConfigVersionManager - 配置版本管理器</li>
 *   <li>ConfigValidator - 配置验证器</li>
 *   <li>StoreManager - 存储管理器</li>
 * </ul>
 * 
 * @since v1.0.0
 * @see ConfigurationHelper
 * @see ConfigMergeService
 * @see ServiceConfigManager
 * @see InstanceManager
 */
@Service
@DependsOn("jpaDatabaseInitializer")
public class ConfigurationService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);

    private static final String CURRENT_KEY = "model-router-config";

    // 请求去重窗口时间（毫秒）
    private static final long REQUEST_DEDUP_WINDOW_MS = 1000; // 1秒内的重复请求将被忽略

    private final StoreManager storeManager;
    private final ConfigurationHelper configurationHelper;
    private final ConfigMergeService configMergeService;
    private final ServiceStateManager serviceStateManager;
    private final SamplingConfigurationValidator samplingValidator;
    private final ServiceConfigManager serviceConfigManager;
    private final InstanceManager instanceManager;
    // v1.5.1: 移除 DatabaseConfigService 依赖
    private final ConfigVersionManager configVersionManager;
    private final ConfigValidator configValidator;
    private final TracingConfigManager tracingConfigManager;
    private final ConfigQueryService configQueryService;
    private ModelServiceRegistry modelServiceRegistry; // 延迟注入避免循环依赖
    // v1.5.6: 配置同步服务，用于版本回滚时同步实例到数据库
    private ConfigSyncService configSyncService;
private final ConfigComparisonService configComparisonService;

    // v2.12.4: 事件发布器，用于发布配置同步事件
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // 实例更新锁，防止同一实例的并发更新
    private final ConcurrentHashMap<String, Object> instanceUpdateLocks = new ConcurrentHashMap<>();

    // 请求去重缓存，防止短时间内的重复请求
    private final ConcurrentHashMap<String, Long> recentUpdateRequests = new ConcurrentHashMap<>();

    @Autowired
    public ConfigurationService(final StoreManager storeManager,
                                final ConfigurationHelper configurationHelper,
                                final ConfigMergeService configMergeService,
                                final ServiceStateManager serviceStateManager,
                                final SamplingConfigurationValidator samplingValidator,
                                final ServiceConfigManager serviceConfigManager,
                                final InstanceManager instanceManager,
                                final ConfigVersionManager configVersionManager,
                                final ConfigValidator configValidator,
                                final TracingConfigManager tracingConfigManager,
                                final ConfigComparisonService configComparisonService,
                                final ConfigQueryService configQueryService) {
        this.storeManager = storeManager;
        this.configurationHelper = configurationHelper;
        this.configMergeService = configMergeService;
        this.serviceStateManager = serviceStateManager;
        this.samplingValidator = samplingValidator;
        this.serviceConfigManager = serviceConfigManager;
        this.instanceManager = instanceManager;
        this.configVersionManager = configVersionManager;
        this.configValidator = configValidator;
        this.tracingConfigManager = tracingConfigManager;
        this.configComparisonService = configComparisonService;
        this.configQueryService = configQueryService;
        // 版本控制初始化移到 @PostConstruct 中，确保 JpaDatabaseInitializer 先执行
    }

    /**
     * 初始化版本控制（在 JpaDatabaseInitializer 之后执行）
     * v1.5.5: 移到 @PostConstruct 确保初始化顺序正确
     */
    @PostConstruct
    public void postConstructInit() {
        configVersionManager.initializeVersionControl();
    }

    /**
     * 设置ModelServiceRegistry引用 用于避免循环依赖问题
     *
     * @param modelServiceRegistry ModelServiceRegistry实例
     */
    public void setModelServiceRegistry(final ModelServiceRegistry modelServiceRegistry) {
        this.modelServiceRegistry = modelServiceRegistry;
    }

    /**
     * 设置ConfigSyncService引用 用于避免循环依赖问题
     * v1.5.6: 用于版本回滚时同步实例到数据库
     *
     * @param configSyncService ConfigSyncService实例
     */
    @Autowired
    public void setConfigSyncService(final ConfigSyncService configSyncService) {
        this.configSyncService = configSyncService;
    }

    // ==================== 版本管理 ====================
    // v2.28.0: 版本管理方法已迁移到 ConfigVersionManager

    /**
     * 条件性保存为新版本（如果配置发生变化）
     * v2.28.0: 简化实现，委托到 ConfigVersionManager
     *
     * @param config      配置内容
     * @param description 描述信息
     * @param userId      用户ID
     * @return 版本号（如果创建了新版本则返回新版本号，否则返回当前版本号）
     */
    public int saveAsNewVersionIfChanged(final Map<String, Object> config, final String description, final String userId) {
        Map<String, Object> currentConfig = getCurrentPersistedConfig();
        if (!configComparisonService.isConfigurationChanged(currentConfig, config)) {
            logger.info("配置未发生变化，不创建新版本");
            return configVersionManager.getCurrentVersion();
        }
        logger.info("检测到配置变化，创建新版本");
        return configVersionManager.saveAsNewVersion(config, description, userId);
    }

    // ==================== 查询操作 ====================

    /**
     * 获取所有配置（合并后的最终配置）
     * v2.6.14: 委托到 ConfigQueryService
     *
     * @return 完整配置Map
     */
    public Map<String, Object> getAllConfigurations() {
        return configQueryService.getAllConfigurations();
    }

    /**
     * 获取所有可用服务类型
     * v2.6.14: 委托到 ConfigQueryService
     *
     * @return 服务类型列表
     */
    public Set<String> getAvailableServiceTypes() {
        return configQueryService.getAvailableServiceTypes();
    }



    /**
     * 获取指定服务的所有可用模型名称
     * v2.6.14: 委托到 ConfigQueryService
     *
     * @param serviceType 服务类型
     * @return 模型名称集合
     */
    public Set<String> getAvailableModels(final String serviceType) {
        return configQueryService.getAvailableModels(serviceType);
    }


    // ==================== 服务管理操作 ====================



    /**
     * 使用强类型 DTO 更新服务配置
     * 简单方案：只更新传入的配置项，保留 instances
     */
    @SuppressWarnings("unchecked")
    public void updateServiceConfigDto(final String serviceType, final UpdateServiceConfigRequest request) {
        logger.info("更新服务配置(DTO)：serviceType={}", serviceType);

        try {
            Map<String, Object> currentConfig = getCurrentPersistedConfig();
            if (currentConfig == null) {
                currentConfig = configMergeService.getDefaultConfig();
            }
            
            Map<String, Object> services = getServicesFromConfig(currentConfig);
            Map<String, Object> existingServiceConfig = (Map<String, Object>) services.get(serviceType);
            
            // 构建新配置
            Map<String, Object> newConfig = new HashMap<>();
            
            // 保留现有的 instances
            if (existingServiceConfig != null && existingServiceConfig.containsKey("instances")) {
                newConfig.put("instances", existingServiceConfig.get("instances"));
            }
            
            // 更新传入的配置
            if (request.getAdapter() != null) {
                newConfig.put("adapter", request.getAdapter());
            }
            
            LoadBalanceConfig lb = request.getLoadBalance();
            if (lb != null) {
                Map<String, Object> lbMap = new HashMap<>();
                lbMap.put("type", lb.getType());
                if (lb.getHashAlgorithm() != null) {
                    lbMap.put("hashAlgorithm", lb.getHashAlgorithm());
                }
                newConfig.put("loadBalance", lbMap);
            }
            
            RateLimitConfig rl = request.getRateLimit();
            if (rl != null) {
                Map<String, Object> rlMap = new HashMap<>();
                rlMap.put("enabled", rl.getEnabled());
                rlMap.put("algorithm", rl.getAlgorithm());
                rlMap.put("capacity", rl.getCapacity());
                rlMap.put("rate", rl.getRate());
                rlMap.put("scope", rl.getScope());
                rlMap.put("key", rl.getKey());
                rlMap.put("clientIpEnable", rl.getClientIpEnable());
                newConfig.put("rateLimit", rlMap);
            }
            
            CircuitBreakerConfig cb = request.getCircuitBreaker();
            if (cb != null) {
                Map<String, Object> cbMap = new HashMap<>();
                cbMap.put("enabled", cb.getEnabled());
                cbMap.put("failureThreshold", cb.getFailureThreshold());
                cbMap.put("timeout", cb.getTimeout());
                cbMap.put("successThreshold", cb.getSuccessThreshold());
                newConfig.put("circuitBreaker", cbMap);
            }
            
            services.put(serviceType, newConfig);
            storeManager.saveConfig("model-router-config", currentConfig);
            configVersionManager.saveAsNewVersion(currentConfig, "更新服务配置: " + serviceType, SecurityUtils.getCurrentUserId());
            refreshRuntimeConfig();

            logger.info("服务 {} 配置更新成功", serviceType);

        } catch (Exception e) {
            logger.error("更新服务配置失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            throw new RuntimeException("更新服务配置失败：" + e.getMessage(), e);
        }
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
            // 自动创建服务
            services.put(serviceType, createDefaultServiceConfig());
        }

        Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);
        List<Map<String, Object>> instances = (List<Map<String, Object>>) serviceConfig.computeIfAbsent("instances", k -> new ArrayList<>());

        // 验证实例配置
        Map<String, Object> instanceMap = configurationHelper.convertInstanceToMap(instanceConfig);
        logger.debug("转换后的实例配置Map: {}", instanceMap);
        logger.debug("实例配置中的headers字段: {}", instanceMap != null ? instanceMap.get("headers") : "null");
        Map<String, Object> validatedInstance = configValidator.validateAndNormalizeInstanceConfig(instanceMap);

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

        // 添加版本元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "addInstance");
        metadata.put("operationDetail", "添加服务实例: " + name + "@" + baseUrl);
        metadata.put("serviceType", serviceType);
        metadata.put("instanceName", name);
        metadata.put("instanceUrl", baseUrl);
        metadata.put("timestamp", System.currentTimeMillis());
        currentConfig.put("_metadata", metadata);

        // 使用智能版本控制 - 只有在配置真正变化时才创建新版本
        String userId = SecurityUtils.getCurrentUserId();
        String description = "添加服务实例: " + name + "@" + baseUrl;
        saveAsNewVersionIfChanged(currentConfig, description, userId);

        refreshRuntimeConfig();

        logger.info("实例 {} 添加成功", name + "@" + baseUrl);
    }



    /**
     * 清理过期的请求记录
     */
    private void cleanupExpiredRequests(final long currentTime) {
        recentUpdateRequests.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > REQUEST_DEDUP_WINDOW_MS * 2);
    }

    /**
     * 内部实例更新方法
     */
    @SuppressWarnings("unchecked")
    private void updateServiceInstanceInternal(final String serviceType, final String instanceId, final ModelRouterProperties.ModelInstance instanceConfig) {

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
        List<Map<String, Object>> instances = (List<Map<String, Object>>) serviceConfig.getOrDefault("instances", new ArrayList<>());

        boolean found = false;
        int targetIndex = -1;
        Map<String, Object> oldInstance = null;

        // 先查找实例位置和原始配置
        for (int i = 0; i < instances.size(); i++) {
            Map<String, Object> instance = instances.get(i);
            String currentInstanceId = InstanceIdUtils.getInstanceId(instance);
            logger.info("比较实例ID: 请求ID={}, 配置ID={}, 匹配结果={}", instanceId, currentInstanceId, instanceId.equals(currentInstanceId));
            if (instanceId.equals(currentInstanceId)) {
                targetIndex = i;
                oldInstance = instance;
                found = true;
                break;
            }
        }

        if (found) {
            instanceConfig.setInstanceId(instanceId);
            // 合并更新配置
            Map<String, Object> newInstanceMap = configurationHelper.convertInstanceToMap(instanceConfig);
            logger.debug("更新实例 - 转换后的实例配置Map: {}", newInstanceMap);
            logger.debug("更新实例 - 实例配置中的headers字段: {}", newInstanceMap != null ? newInstanceMap.get("headers") : "null");
            Map<String, Object> updatedInstance = configComparisonService.mergeInstanceConfig(oldInstance, newInstanceMap);
            logger.debug("更新实例 - 合并后的实例配置: {}", updatedInstance);
            logger.debug("更新实例 - 合并后的headers字段: {}", updatedInstance.get("headers"));
            Map<String, Object> validatedInstance = configValidator.validateAndNormalizeInstanceConfig(updatedInstance);
            logger.debug("更新实例 - 验证后的实例配置: {}", validatedInstance);
            logger.debug("更新实例 - 验证后的headers字段: {}", validatedInstance.get("headers"));
            instances.set(targetIndex, validatedInstance);
        }

        if (!found) {
            // 记录所有实例信息用于调试
            logger.warn("实例不存在: {}，服务 {} 中的所有实例:", instanceId, serviceType);
            for (int i = 0; i < instances.size(); i++) {
                Map<String, Object> instance = instances.get(i);
                String currentInstanceId = InstanceIdUtils.getInstanceId(instance);
                logger.warn("  实例 {}: ID={}, name={}, baseUrl={}", i, currentInstanceId, instance.get("name"), instance.get("baseUrl"));
            }
            throw new IllegalArgumentException("实例不存在: " + instanceId);
        }

        // 更新服务配置
        serviceConfig.put("instances", instances);
        services.put(serviceType, serviceConfig);
        currentConfig.put("services", services);

        // 添加版本元数据
        String name = (String) configurationHelper.convertInstanceToMap(instanceConfig).get("name");
        String baseUrl = (String) configurationHelper.convertInstanceToMap(instanceConfig).get("baseUrl");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "updateInstance");
        metadata.put("operationDetail", "更新服务实例: " + name + "@" + baseUrl);
        metadata.put("serviceType", serviceType);
        metadata.put("instanceName", name);
        metadata.put("instanceUrl", baseUrl);
        metadata.put("timestamp", System.currentTimeMillis());
        currentConfig.put("_metadata", metadata);

        // 使用智能版本控制 - 只有在配置真正变化时才创建新版本
        String userId = SecurityUtils.getCurrentUserId();
        String description = "更新服务实例: " + name + "@" + baseUrl;
        saveAsNewVersionIfChanged(currentConfig, description, userId);

        refreshRuntimeConfig();

        logger.info("实例 {} 更新成功", instanceId);
    }




    // ==================== 批量操作 ====================

    /**
     * 批量更新配置（自动保存为新版本）
     *
     * @param configs 配置Map
     */
    public void batchUpdateConfigurations(final Map<String, Object> configs) {
        logger.info("批量更新配置，包含 {} 个顶级配置项", configs.size());

        Map<String, Object> currentConfig = getCurrentPersistedConfig();

        // 深度合并配置
        for (Map.Entry<String, Object> entry : configs.entrySet()) {
            currentConfig.put(entry.getKey(), entry.getValue());
        }

        // 保存为新版本并刷新配置
        configVersionManager.saveAsNewVersion(currentConfig);
        refreshRuntimeConfig();

        logger.info("批量配置更新成功");
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
            services.put(serviceType, createDefaultServiceConfig());
        }

        Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceType);
        List<Map<String, Object>> instances = (List<Map<String, Object>>) serviceConfig.computeIfAbsent("instances", k -> new ArrayList<>());

        List<String> operationDetails = new ArrayList<>();

        // 执行所有操作（内联实现）
        for (InstanceOperation operation : operations) {
            switch (operation.type()) {
                case ADD: {
                    Map<String, Object> validatedInstance = configValidator.validateAndNormalizeInstanceConfig(
                            configurationHelper.convertInstanceToMap(operation.instanceConfig()));
                    String name = (String) validatedInstance.get("name");
                    String baseUrl = (String) validatedInstance.get("baseUrl");
                    boolean exists = instances.stream().anyMatch(inst ->
                            name.equals(inst.get("name")) && baseUrl.equals(inst.get("baseUrl")));
                    if (exists) {
                        throw new IllegalArgumentException("实例已存在: " + name + "@" + baseUrl);
                    }
                    instances.add(validatedInstance);
                    operationDetails.add("添加 " + name + "@" + baseUrl);
                    break;
                }
                case UPDATE: {
                    boolean found = false;
                    for (int i = 0; i < instances.size(); i++) {
                        Map<String, Object> instance = instances.get(i);
                        if (operation.instanceId().equals(InstanceIdUtils.getInstanceId(instance))) {
                            Map<String, Object> updated = configComparisonService.mergeInstanceConfig(
                                    instance, configurationHelper.convertInstanceToMap(operation.instanceConfig()));
                            instances.set(i, configValidator.validateAndNormalizeInstanceConfig(updated));
                            found = true;
                            operationDetails.add("更新 " + operation.instanceId());
                            break;
                        }
                    }
                    if (!found) {
                        throw new IllegalArgumentException("实例不存在: " + operation.instanceId());
                    }
                    break;
                }
                case DELETE: {
                    boolean removed = instances.removeIf(inst ->
                            operation.instanceId().equals(InstanceIdUtils.getInstanceId(inst)));
                    if (!removed) {
                        throw new IllegalArgumentException("实例不存在: " + operation.instanceId());
                    }
                    operationDetails.add("删除 " + operation.instanceId());
                    break;
                }
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

    /**
     * 获取持久化配置（优先使用最新版本的配置）
     *
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
     * 从配置中获取services部分，并添加健康状态信息
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getServicesFromConfig(final Map<String, Object> config) {
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
    private Map<String, Object> validateAndNormalizeServiceConfig(final Map<String, Object> serviceConfig) {
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
     * 刷新运行时配置
     * v2.12.4: 改为事件发布模式，简化实现
     */
    private void refreshRuntimeConfig() {
        Map<String, Object> config = getCurrentPersistedConfig();
        eventPublisher.publishEvent(ConfigSyncEvent.refresh(config));
        logger.debug("已发布配置同步事件 ConfigSyncEvent.refresh");
    }

    /**
     * 获取当前追踪配置
     * v2.6.14: 委托到 ConfigQueryService
     *
     * @return TraceConfig对象
     */
    public TraceConfig getTraceConfig() {
        return configQueryService.getTraceConfig();
    }

    // ==================== 追踪配置管理 ====================

    /**
     * 更新追踪配置
     *
     * @param traceConfig 新的追踪配置
     * @param createNewVersion 是否创建新版本
     */
    public void updateTraceConfig(final TraceConfig traceConfig, final boolean createNewVersion) {
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
            configVersionManager.saveAsNewVersion(currentConfig);
        } else {
            // 直接保存配置但不创建新版本
            storeManager.saveConfig(CURRENT_KEY, currentConfig);
        }

        refreshRuntimeConfig();
        logger.info("追踪配置更新成功");
    }

    /**
     * 删除追踪配置
     *
     * @param createNewVersion 是否创建新版本
     */
    public void deleteTraceConfig(final boolean createNewVersion) {
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
            configVersionManager.saveAsNewVersion(currentConfig);
        } else {
            // 直接保存配置但不创建新版本
            storeManager.saveConfig(CURRENT_KEY, currentConfig);
        }

        refreshRuntimeConfig();
        logger.info("追踪配置删除成功");
    }

    /**
     * 获取追踪采样配置
     * v2.22.0: 委托到 TracingConfigManager
     *
     * @return 采样配置Map
     */
    public Map<String, Object> getTracingSamplingConfig() {
        return tracingConfigManager.getTracingSamplingConfig();
    }

    /**
     * 更新追踪采样配置
     * v2.22.0: 委托到 TracingConfigManager
     *
     * @param samplingConfig 新的采样配置
     * @param createNewVersion 是否创建新版本
     */
    public void updateTracingSamplingConfig(final Map<String, Object> samplingConfig, final boolean createNewVersion) {
        tracingConfigManager.updateTracingSamplingConfig(samplingConfig, createNewVersion);
        refreshRuntimeConfig();
    }

    /**
     * 检查是否存在持久化配置
     * v2.6.14: 委托到 ConfigQueryService
     *
     * @return true如果存在持久化配置
     */
    public boolean hasPersistedConfig() {
        return configQueryService.hasPersistedConfig();
    }

    /**
     * 清理版本元数据和历史记录
     * v2.28.0: 委托到 ConfigVersionManager
     */
    public void cleanVersion() {
        configVersionManager.cleanVersion();
    }

    /**
     * 实例操作类型
     */
    public enum InstanceOperationType {
        ADD, UPDATE, DELETE
    }

    /**
     * 实例操作定义
     */
    public record InstanceOperation(InstanceOperationType type, String instanceId,
                                    ModelRouterProperties.ModelInstance instanceConfig) {
    }
}
