package org.unreal.modelrouter.config.core;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.config.event.ConfigSyncEvent;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.config.core.manager.ConfigValidator;
import org.unreal.modelrouter.config.core.manager.ConfigVersionManager;
import org.unreal.modelrouter.config.core.manager.InstanceManager;
import org.unreal.modelrouter.config.core.manager.TracingConfigManager;
import org.unreal.modelrouter.config.dto.UpdateServiceConfigRequest;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.monitor.tracing.config.SamplingConfigurationValidator;
import org.unreal.modelrouter.common.util.SecurityUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.unreal.modelrouter.config.core.manager.ConfigComparisonService;
import org.unreal.modelrouter.config.core.service.ConfigQueryService;
import org.unreal.modelrouter.config.core.service.InstanceOperationService;
import org.unreal.modelrouter.config.core.service.ServiceConfigUpdateService;

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
 *   <li>saveAsNewVersionIfChanged() - 保存为新版本（委托到 InstanceConfigService）</li>
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
 *   <li>addServiceInstance() - 添加服务实例（委托到 InstanceConfigService）</li>
 *   <li>batchUpdateServiceInstances() - 批量更新服务实例（委托到 InstanceConfigService）</li>
 *   <li>updateTraceConfig() - 更新追踪配置</li>
 *   <li>deleteTraceConfig() - 删除追踪配置</li>
 *   <li>updateTracingSamplingConfig() - 更新追踪采样配置</li>
 * </ul>
 * 
 * <h2>依赖组件</h2>
 * <ul>
 *   <li>InstanceConfigService - 实例配置管理服务</li>
 *   <li>ConfigMergeService - 配置合并服务</li>
 *   <li>ServiceConfigManager - 服务配置管理器</li>
 *   <li>InstanceManager - 实例管理器</li>
 *   <li>ConfigVersionManager - 配置版本管理器</li>
 *   <li>StoreManager - 存储管理器</li>
 * </ul>
 * 
 * @since v1.0.0
 * @see InstanceConfigService
 * @see ConfigMergeService
 * @see ServiceConfigManager
 * @see InstanceManager
 */
@Service
@DependsOn("jpaDatabaseInitializer")
public class ConfigurationService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);

    private final StoreManager storeManager;
    private final ConfigConverterHelper configConverterHelper;
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
    private final InstanceOperationService instanceOperationService;
    private final ServiceConfigUpdateService serviceConfigUpdateService;
    private ModelServiceRegistry modelServiceRegistry; // 延迟注入避免循环依赖
    // v1.5.6: 配置同步服务，用于版本回滚时同步实例到数据库
    private ConfigSyncService configSyncService;
    private final ConfigComparisonService configComparisonService;

    // v2.12.4: 事件发布器，用于发布配置同步事件
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // v2.30.0: 实例配置管理服务，从本类提取实例管理方法
    private InstanceConfigService instanceConfigService;

    @Autowired
    public ConfigurationService(final StoreManager storeManager,
                                final ConfigConverterHelper configConverterHelper,
                                final ConfigMergeService configMergeService,
                                final ServiceStateManager serviceStateManager,
                                final SamplingConfigurationValidator samplingValidator,
                                final ServiceConfigManager serviceConfigManager,
                                final InstanceManager instanceManager,
                                final ConfigVersionManager configVersionManager,
                                final ConfigValidator configValidator,
                                final TracingConfigManager tracingConfigManager,
                                final ConfigComparisonService configComparisonService,
                                final ConfigQueryService configQueryService,
                                final InstanceOperationService instanceOperationService,
                                final ServiceConfigUpdateService serviceConfigUpdateService) {
        this.storeManager = storeManager;
        this.configConverterHelper = configConverterHelper;
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
        this.instanceOperationService = instanceOperationService;
        this.serviceConfigUpdateService = serviceConfigUpdateService;
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

    /**
     * 设置InstanceConfigService引用
     * v2.30.0: 实例管理方法已迁移到 InstanceConfigService
     *
     * @param instanceConfigService InstanceConfigService实例
     */
    @Autowired
    public void setInstanceConfigService(final InstanceConfigService instanceConfigService) {
        this.instanceConfigService = instanceConfigService;
    }

    // ==================== 版本管理 ====================
    // v2.28.0: 版本管理方法已迁移到 ConfigVersionManager

    /**
     * 条件性保存为新版本（如果配置发生变化）
     * v2.30.0: 委托到 InstanceConfigService
     *
     * @param config      配置内容
     * @param description 描述信息
     * @param userId      用户ID
     * @return 版本号（如果创建了新版本则返回新版本号，否则返回当前版本号）
     */
    public int saveAsNewVersionIfChanged(final Map<String, Object> config,
                                         final String description, final String userId) {
        return instanceConfigService.saveAsNewVersionIfChanged(config, description, userId);
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
     * v2.6.15: 委托到 ServiceConfigUpdateService
     */
    @SuppressWarnings("unchecked")
    public void updateServiceConfigDto(final String serviceType, final UpdateServiceConfigRequest request) {
        logger.info("更新服务配置(DTO)：serviceType={}", serviceType);

        try {
            Map<String, Object> currentConfig = instanceConfigService.getCurrentPersistedConfig();
            if (currentConfig == null) {
                currentConfig = configMergeService.getDefaultConfig();
            }

            Map<String, Object> services = instanceConfigService.getServicesFromConfig(currentConfig);
            Map<String, Object> existingServiceConfig = (Map<String, Object>) services.get(serviceType);

            // 委托到 ServiceConfigUpdateService
            Map<String, Object> newConfig =
                    serviceConfigUpdateService.buildServiceConfigUpdate(existingServiceConfig, request);

            services.put(serviceType, newConfig);
            storeManager.saveConfig("model-router-config", currentConfig);
            configVersionManager.saveAsNewVersion(
                    currentConfig, "更新服务配置: " + serviceType, SecurityUtils.getCurrentUserId());
            instanceConfigService.refreshRuntimeConfig();

            logger.info("服务 {} 配置更新成功", serviceType);

        } catch (Exception e) {
            logger.error("更新服务配置失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            throw new RuntimeException("更新服务配置失败：" + e.getMessage(), e);
        }
    }

    // ==================== 实例管理操作 ====================

    /**
     * 添加服务实例（优化版本，可选择是否保存为新版本）
     * v2.30.0: 委托到 InstanceConfigService
     *
     * @param serviceType 服务类型
     * @param instanceConfig 实例配置
     */
    public void addServiceInstance(final String serviceType, final ModelRouterProperties.ModelInstance instanceConfig) {
        instanceConfigService.addServiceInstance(serviceType, instanceConfig);
    }

    /**
     * 批量更新服务实例（只创建一个版本）
     * v2.30.0: 委托到 InstanceConfigService
     *
     * @param serviceType 服务类型
     * @param operations  实例操作列表
     */
    public void batchUpdateServiceInstances(final String serviceType, final List<InstanceOperation> operations) {
        instanceConfigService.batchUpdateServiceInstances(serviceType, operations);
    }

    // ==================== 批量操作 ====================

    /**
     * 批量更新配置（自动保存为新版本）
     *
     * @param configs 配置Map
     */
    public void batchUpdateConfigurations(final Map<String, Object> configs) {
        logger.info("批量更新配置，包含 {} 个顶级配置项", configs.size());

        Map<String, Object> currentConfig = instanceConfigService.getCurrentPersistedConfig();

        // 深度合并配置
        for (Map.Entry<String, Object> entry : configs.entrySet()) {
            currentConfig.put(entry.getKey(), entry.getValue());
        }

        // 保存为新版本并刷新配置
        configVersionManager.saveAsNewVersion(currentConfig);
        instanceConfigService.refreshRuntimeConfig();

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
        instanceConfigService.refreshRuntimeConfig();

        logger.info("配置已重置为默认值");
    }

    // ==================== 辅助方法 ====================

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
     * v2.6.15: 委托到 TracingConfigManager
     */
    public void updateTraceConfig(final TraceConfig traceConfig, final boolean createNewVersion) {
        tracingConfigManager.updateTraceConfig(traceConfig.toMap(), createNewVersion);
        instanceConfigService.refreshRuntimeConfig();
    }

    /**
     * 删除追踪配置
     * v2.6.15: 委托到 TracingConfigManager
     */
    public void deleteTraceConfig(final boolean createNewVersion) {
        tracingConfigManager.deleteTraceConfig(createNewVersion);
        instanceConfigService.refreshRuntimeConfig();
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
        instanceConfigService.refreshRuntimeConfig();
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
}
