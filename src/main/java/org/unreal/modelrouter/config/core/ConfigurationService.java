package org.unreal.modelrouter.config.core;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.router.checker.ServerChecker;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.config.core.manager.ConfigValidator;
import org.unreal.modelrouter.config.core.manager.ConfigVersionManager;
import org.unreal.modelrouter.config.core.manager.InstanceManager;
import org.unreal.modelrouter.config.core.ServiceConfigManager;
import org.unreal.modelrouter.dto.CircuitBreakerConfig;
import org.unreal.modelrouter.dto.LoadBalanceConfig;
import org.unreal.modelrouter.dto.RateLimitConfig;
import org.unreal.modelrouter.dto.UpdateServiceConfigRequest;
import org.unreal.modelrouter.entity.ConfigMetadata;
import org.unreal.modelrouter.entity.VersionInfo;
import org.unreal.modelrouter.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.monitor.tracing.config.SamplingConfigurationValidator;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.util.ApplicationContextProvider;
import org.unreal.modelrouter.util.InstanceIdUtils;
import org.unreal.modelrouter.util.JacksonHelper;
import org.unreal.modelrouter.util.SecurityUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 配置管理服务 - 重构版 提供完整的服务配置增删改查功能 支持服务、实例的动态管理 支持配置版本管理
 */
@Service
@DependsOn("jpaDatabaseInitializer")
public class ConfigurationService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);

    private static final String CURRENT_KEY = "model-router-config";

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
    private ModelServiceRegistry modelServiceRegistry; // 延迟注入避免循环依赖
    // v1.5.6: 配置同步服务，用于版本回滚时同步实例到数据库
    private ConfigSyncService configSyncService;
    // v1.7.1: 实例仓库，用于从数据库获取健康状态
    @Autowired(required = false)
    private ServiceInstanceRepository serviceInstanceRepository;

    private static final long REQUEST_DEDUP_WINDOW_MS = 1000; // 1秒内的重复请求将被忽略
    // 版本控制相关字段
    private final Map<String, ConfigMetadata> configMetadataMap = new HashMap<>();
    private final Map<String, List<VersionInfo>> versionHistoryMap = new HashMap<String, List<VersionInfo>>();

    // 版本创建锁 - v2.0.0: 使用 ReentrantLock 替代 synchronized 提升并发性能
    private final ReentrantLock versionCreationLock = new ReentrantLock();

    // 实例更新锁，防止同一实例的并发更新
    private final ConcurrentHashMap<String, Object> instanceUpdateLocks = new ConcurrentHashMap<>();

    // 请求去重缓存，防止短时间内的重复请求
    private final ConcurrentHashMap<String, Long> recentUpdateRequests = new ConcurrentHashMap<>();
    // 记录最近版本创建的时间，用于检测短时间内的重复创建
    private volatile long lastVersionCreationTime = 0;
    private volatile String lastVersionDescription = "";

    @Autowired
    public ConfigurationService(final StoreManager storeManager,
                                final ConfigurationHelper configurationHelper,
                                final ConfigMergeService configMergeService,
                                final ServiceStateManager serviceStateManager,
                                final SamplingConfigurationValidator samplingValidator,
                                final ServiceConfigManager serviceConfigManager,
                                final InstanceManager instanceManager,
                                final ConfigVersionManager configVersionManager,
                                final ConfigValidator configValidator) {
        this.storeManager = storeManager;
        this.configurationHelper = configurationHelper;
        this.configMergeService = configMergeService;
        this.serviceStateManager = serviceStateManager;
        this.samplingValidator = samplingValidator;
        this.serviceConfigManager = serviceConfigManager;
        this.instanceManager = instanceManager;
        this.configVersionManager = configVersionManager;
        this.configValidator = configValidator;
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
     * 初始化版本控制
     * v1.5.2: 从数据库同步版本信息，不再依赖文件存储
     */
    private void initializeVersionControl() {
        try {
            logger.info("初始化版本控制，从数据库同步版本信息...");
            
            // 从数据库获取已存在的版本列表
            List<Integer> dbVersions = storeManager.getConfigVersions(CURRENT_KEY);
            logger.info("从数据库获取到 {} 个版本: {}", dbVersions.size(), dbVersions);
            
            // 创建或更新元数据
            ConfigMetadata metadata = configMetadataMap.computeIfAbsent(CURRENT_KEY, k -> {
                ConfigMetadata newMetadata = new ConfigMetadata();
                newMetadata.setConfigKey(CURRENT_KEY);
                newMetadata.setCreatedAt(LocalDateTime.now());
                return newMetadata;
            });
            
            // 从数据库版本列表同步元数据
            if (!dbVersions.isEmpty()) {
                // 当前版本为数据库中的最大版本号
                int maxVersion = dbVersions.stream().max(Integer::compareTo).orElse(0);
                metadata.setCurrentVersion(maxVersion);
                metadata.setInitialVersion(dbVersions.stream().min(Integer::compareTo).orElse(1));
                metadata.setTotalVersions(dbVersions.size());
                metadata.setExistingVersions(new HashSet<>(dbVersions));
                metadata.setLastModified(LocalDateTime.now());
                metadata.setLastModifiedBy("system-sync");
                
                logger.info("已从数据库同步版本元数据: 当前版本={}, 总版本数={}, 版本列表={}",
                        maxVersion, dbVersions.size(), dbVersions);
                
                // 构建版本历史记录（从数据库实体获取创建时间等信息）
                List<VersionInfo> versionHistory = new ArrayList<>();
                for (Integer version : dbVersions) {
                    VersionInfo versionInfo = new VersionInfo();
                    versionInfo.setVersion(version);
                    versionInfo.setCreatedAt(storeManager.getVersionCreatedTime(CURRENT_KEY, version));
                    versionInfo.setCreatedBy("system");
                    versionInfo.setDescription("配置版本 " + version);
                    versionInfo.setChangeType(version == 1 ? VersionInfo.ChangeType.INITIAL : VersionInfo.ChangeType.UPDATE);
                    
                    // 尝试从配置中获取 metadata 信息
                    Map<String, Object> config = storeManager.getConfigByVersion(CURRENT_KEY, version);
                    if (config != null && config.containsKey("_metadata")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> configMetadata = (Map<String, Object>) config.get("_metadata");
                        if (configMetadata != null) {
                            if (configMetadata.containsKey("operation")) {
                                versionInfo.setDescription((String) configMetadata.get("operationDetail"));
                            }
                        }
                    }
                    versionHistory.add(versionInfo);
                }
                versionHistoryMap.put(CURRENT_KEY, versionHistory);
            } else {
                // 数据库中没有版本，创建初始元数据
                metadata.setInitialVersion(1);
                metadata.setCurrentVersion(0);
                metadata.setTotalVersions(0);
                metadata.setExistingVersions(new HashSet<>());
                metadata.setLastModified(LocalDateTime.now());
                metadata.setLastModifiedBy("system-init");
                
                versionHistoryMap.put(CURRENT_KEY, new ArrayList<>());
                logger.info("数据库中没有版本记录，创建初始元数据");
            }
            
            configMetadataMap.put(CURRENT_KEY, metadata);
            
            // 保存元数据到文件（作为备份）
            saveMetadata(CURRENT_KEY, metadata);
            saveVersionHistory(CURRENT_KEY, versionHistoryMap.get(CURRENT_KEY));
            
            logger.info("版本控制初始化完成，当前版本: {}", metadata.getCurrentVersion());
        } catch (Exception e) {
            logger.warn("初始化版本控制数据时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 生成下一个可用的版本号
     * v1.5.2: 使用简单的递增策略，直接从数据库获取最大版本号+1
     */
    private int generateNextVersionNumber() {
        // 从数据库获取当前所有版本
        List<Integer> existingVersions = storeManager.getConfigVersions(CURRENT_KEY);
        
        // 找到最大版本号
        int maxVersion = existingVersions.stream()
                .max(Integer::compareTo)
                .orElse(0);
        
        // 新版本号 = 最大版本号 + 1
        int newVersion = maxVersion + 1;
        
        logger.debug("生成新版本号: {} (当前最大版本: {})", newVersion, maxVersion);
        return newVersion;
    }

    /**
     * 记录版本创建的时间间隔，帮助识别短时间内的重复创建
     */
    private void recordVersionCreationTiming(final String description) {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastCreation = currentTime - lastVersionCreationTime;

        if (lastVersionCreationTime > 0) {
            // 如果两次版本创建间隔小于5秒，记录警告
            if (timeSinceLastCreation < 5000) {
                logger.warn("检测到短时间内重复版本创建！间隔: {}ms, 上次描述: '{}', 本次描述: '{}'",
                        timeSinceLastCreation, lastVersionDescription, description);

                // 如果描述相同且间隔很短，可能是重复请求
                if (description.equals(lastVersionDescription) && timeSinceLastCreation < 1000) {
                    logger.error("疑似重复请求！相同描述的版本创建间隔仅 {}ms: '{}'",
                            timeSinceLastCreation, description);
                }
            } else {
                logger.debug("版本创建时间间隔正常: {}ms", timeSinceLastCreation);
            }
        }

        lastVersionCreationTime = currentTime;
        lastVersionDescription = description;
    }


    /**
     * 保存配置元数据
     */
    private void saveMetadata(final String key, final ConfigMetadata metadata) {
        try {
            Map<String, Object> metadataMap = new HashMap<>();
            metadataMap.put("configKey", metadata.getConfigKey());
            metadataMap.put("currentVersion", metadata.getCurrentVersion());
            metadataMap.put("initialVersion", metadata.getInitialVersion());
            metadataMap.put("createdAt", metadata.getCreatedAt());
            metadataMap.put("lastModified", metadata.getLastModified());
            metadataMap.put("lastModifiedBy", metadata.getLastModifiedBy());
            metadataMap.put("totalVersions", metadata.getTotalVersions());
            metadataMap.put("existingVersions", new ArrayList<>(metadata.getExistingVersions())); // 保存版本列表
            storeManager.saveConfig(key + ".metadata", metadataMap);
            configMetadataMap.put(key, metadata);
        } catch (Exception e) {
            logger.error("保存配置元数据失败: " + key, e);
        }
    }

    /**
     * 保存版本历史
     */
    private void saveVersionHistory(final String key, final List<VersionInfo> versionHistory) {
        try {
            List<Map<String, Object>> historyList = new ArrayList<>();
            for (VersionInfo versionInfo : versionHistory) {
                Map<String, Object> historyItem = new HashMap<>();
                historyItem.put("version", versionInfo.getVersion());
                historyItem.put("createdAt", versionInfo.getCreatedAt());
                historyItem.put("createdBy", versionInfo.getCreatedBy());
                historyItem.put("description", versionInfo.getDescription());
                historyItem.put("changeType", versionInfo.getChangeType().name());
                historyList.add(historyItem);
            }
            storeManager.saveConfig(key + ".history", Collections.singletonMap("history", historyList));
            versionHistoryMap.put(key, versionHistory);
        } catch (Exception e) {
            logger.error("保存版本历史失败: " + key, e);
        }
    }

    // ==================== 版本管理 ====================

    /**
     * 获取所有配置版本号
     *
     * @deprecated 此方法已委托给 {@link ConfigVersionManager#getAllVersions()}。
     *             请直接使用 ConfigVersionManager 进行版本管理。
     *             此方法将在 v3.0 版本中移除。
     * @see ConfigVersionManager#getAllVersions()
     * @since v2.5.3.1 标注废弃，委托实现
     * @return 版本号列表
     */
    @Deprecated(since = "2.5.3.1", forRemoval = true)
    public List<Integer> getAllVersions() {
        return configVersionManager.getAllVersions();
    }

    /**
     * 扫描实际存在的版本文件
     */
    private List<Integer> scanExistingVersions() {
        List<Integer> versions = new ArrayList<>();

        // 首先从 storeManager 获取所有版本（支持 H2 数据库和文件存储）
        List<Integer> storeVersions = storeManager.getConfigVersions(CURRENT_KEY);
        if (storeVersions != null && !storeVersions.isEmpty()) {
            versions.addAll(storeVersions);
            logger.debug("从 storeManager 获取到 {} 个版本", storeVersions.size());
        }

        // 从版本历史中获取已知版本（作为补充）
        List<VersionInfo> versionHistory = versionHistoryMap.get(CURRENT_KEY);
        if (versionHistory != null) {
            for (VersionInfo versionInfo : versionHistory) {
                int version = versionInfo.getVersion();
                if (storeManager.versionExists(CURRENT_KEY, version) && !versions.contains(version)) {
                    versions.add(version);
                }
            }
        }
        // 排序并去重
        versions = versions.stream().distinct().sorted().collect(Collectors.toList());
        return versions;
    }

    /**
     * 获取指定版本的配置
     *
     * @deprecated 此方法已委托给 {@link ConfigVersionManager#getVersionConfig(int)}。
     *             请直接使用 ConfigVersionManager 进行版本配置获取。
     *             此方法将在 v3.0 版本中移除。
     * @see ConfigVersionManager#getVersionConfig(int)
     * @since v2.5.3.1 标注废弃，委托实现
     * @param version 版本号，0表示YAML原始配置
     * @return 配置内容
     */
    @Deprecated(since = "2.5.3.1", forRemoval = true)
    public Map<String, Object> getVersionConfig(final int version) {
        return configVersionManager.getVersionConfig(version);
    }

    /**
     * 保存当前配置为新版本
     *
     * @deprecated 此方法已委托给 {@link ConfigVersionManager#saveAsNewVersion(Map)}。
     *             请直接使用 ConfigVersionManager 进行版本保存。
     *             此方法将在 v3.0 版本中移除。
     * @see ConfigVersionManager#saveAsNewVersion(Map)
     * @since v2.5.3.1 标注废弃，委托实现
     * @param config 配置内容
     * @return 新版本号
     */
    @Deprecated(since = "2.5.3.1", forRemoval = true)
    public int saveAsNewVersion(final Map<String, Object> config) {
        return configVersionManager.saveAsNewVersion(config);
    }

    /**
     * 保存当前配置为新版本（带描述和用户信息）
     *
     * @deprecated 此方法已委托给 {@link ConfigVersionManager#saveAsNewVersion(Map, String, String)}。
     *             请直接使用 ConfigVersionManager 进行版本保存。
     *             此方法将在 v3.0 版本中移除。
     * @see ConfigVersionManager#saveAsNewVersion(Map, String, String)
     * @since v2.5.3.1 标注废弃，委托实现
     * @param config      配置内容
     * @param description 描述信息
     * @param userId      用户ID
     * @return 新版本号
     */
    @Deprecated(since = "2.5.3.1", forRemoval = true)
    public int saveAsNewVersion(final Map<String, Object> config, final String description, final String userId) {
        return configVersionManager.saveAsNewVersion(config, description, userId);
    }

    /**
     * 内部版本保存方法（已在同步块内调用）
     */
    private int saveAsNewVersionInternal(final Map<String, Object> config, final String description, final String userId) {
        try {
            // 调用链追踪：记录调用来源（用于调试重复版本问题）
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            String callerInfo = stackTrace.length > 3 ? stackTrace[3].toString() : "unknown";
            logger.info("【版本创建调用链】描述='{}', 调用来源={}", description, callerInfo);

            logger.debug("开始保存新版本，描述: {}, 用户: {}", description, userId);

            // 记录版本创建的时间间隔，帮助识别短时间内的重复创建
            recordVersionCreationTiming(description);

            // 获取或创建配置元数据
            ConfigMetadata metadata = configMetadataMap.computeIfAbsent(CURRENT_KEY, k -> {
                ConfigMetadata newMetadata = new ConfigMetadata();
                newMetadata.setConfigKey(CURRENT_KEY);
                newMetadata.setInitialVersion(1);
                newMetadata.setCurrentVersion(0);
                newMetadata.setCreatedAt(LocalDateTime.now());
                newMetadata.setTotalVersions(0);
                logger.info("创建新的配置元数据");
                return newMetadata;
            });

            // 生成新版本号 - 不要求连续，只要求唯一
            int newVersion = generateNextVersionNumber();
            logger.info("生成新版本号: {}", newVersion);

            // 保存版本配置
            storeManager.saveConfigVersion(CURRENT_KEY, config, newVersion);
            logger.debug("版本配置已保存: {}", newVersion);

            // 更新元数据
            metadata.setCurrentVersion(newVersion);
            metadata.setLastModified(LocalDateTime.now());
            metadata.setLastModifiedBy(userId != null ? userId : "system");
            metadata.setTotalVersions(metadata.getTotalVersions() + 1);
            metadata.addVersion(newVersion); // 添加到版本列表
            saveMetadata(CURRENT_KEY, metadata);
            logger.debug("元数据已更新");

            // 创建版本历史记录
            VersionInfo versionInfo = new VersionInfo();
            versionInfo.setVersion(newVersion);
            versionInfo.setCreatedAt(LocalDateTime.now());
            versionInfo.setCreatedBy(userId != null ? userId : "system");
            versionInfo.setDescription(description != null ? description : "配置更新");
            versionInfo.setChangeType(VersionInfo.ChangeType.UPDATE);

            List<VersionInfo> versionHistory = versionHistoryMap.computeIfAbsent(CURRENT_KEY, k -> new ArrayList<>());
            versionHistory.add(versionInfo);
            saveVersionHistory(CURRENT_KEY, versionHistory);
            logger.debug("版本历史已更新");

            // 更新当前活跃配置
            storeManager.saveConfig(CURRENT_KEY, config);
            logger.debug("当前活跃配置已更新");

            logger.info("已保存配置为新版本：{}", newVersion);
            return newVersion;
        } catch (Exception e) {
            logger.error("保存新版本配置失败", e);
            throw new RuntimeException("保存新版本配置失败", e);
        }
    }

    /**
     * 应用指定版本的配置
     *
     * @deprecated 此方法已委托给 {@link ConfigVersionManager#applyVersion(int)}。
     *             请直接使用 ConfigVersionManager 进行版本应用。
     *             此方法将在 v3.0 版本中移除。
     * @see ConfigVersionManager#applyVersion(int)
     * @since v2.5.3.2 标注废弃，委托实现
     * @param version 版本号
     */
    @Deprecated(since = "2.5.3.2", forRemoval = true)
    public void applyVersion(final int version) {
        configVersionManager.applyVersion(version);
    }

    /**
     * 删除指定版本的配置
     *
     * @deprecated 此方法已委托给 {@link ConfigVersionManager#deleteConfigVersion(int)}。
     *             请直接使用 ConfigVersionManager 进行版本删除。
     *             此方法将在 v3.0 版本中移除。
     * @see ConfigVersionManager#deleteConfigVersion(int)
     * @since v2.5.3.2 标注废弃，委托实现
     * @param version 版本号
     */
    @Deprecated(since = "2.5.3.2", forRemoval = true)
    public void deleteConfigVersion(final int version) {
        configVersionManager.deleteConfigVersion(version);
    }

    /**
     * 获取实际当前配置版本号
     *
     * @deprecated 此方法已委托给 {@link ConfigVersionManager#getActualCurrentVersion()}。
     *             请直接使用 ConfigVersionManager 进行版本查询。
     *             此方法将在 v3.0 版本中移除。
     * @see ConfigVersionManager#getActualCurrentVersion()
     * @since v2.5.3.2 标注废弃，委托实现
     * @return 当前配置版本号，如果不存在则返回0
     */
    @Deprecated(since = "2.5.3.2", forRemoval = true)
    public int getActualCurrentVersion() {
        return configVersionManager.getActualCurrentVersion();
    }

    /**
     * 获取当前最新版本号（基于版本列表）
     *
     * @deprecated 此方法已委托给 {@link ConfigVersionManager#getCurrentVersion()}。
     *             请直接使用 ConfigVersionManager 进行版本查询。
     *             此方法将在 v3.0 版本中移除。
     * @see ConfigVersionManager#getCurrentVersion()
     * @since v2.5.3.2 标注废弃，委托实现
     * @return 当前版本号
     */
    @Deprecated(since = "2.5.3.2", forRemoval = true)
    public int getCurrentVersion() {
        return configVersionManager.getCurrentVersion();
    }

    /**
     * 获取下一个版本号
     *
     * @return 下一个版本号
     */
    private int getNextVersion() {
        return getCurrentVersion() + 1;
    }

    /**
     * 验证指定版本是否存在
     *
     * @param version 版本号
     * @return true如果版本存在，false如果不存在
     */
    private boolean versionExists(final int version) {
        if (version <= 0) {
            return false;
        }

        // 检查版本是否在有效范围内
        ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
        if (metadata != null) {
            return metadata.getExistingVersions().contains(version);
        }

        // 如果没有元数据，检查版本列表
        List<Integer> versions = getAllVersions();
        return versions.contains(version);
    }

    // ==================== 智能配置比较机制 ====================

    /**
     * 智能配置比较，检查配置是否真正发生变化
     * 排除元数据字段和自动生成的字段，只比较业务逻辑相关的配置
     *
     * @param currentConfig 当前配置
     * @param newConfig     新配置
     * @return true如果配置发生了实质性变化，false如果配置相同
     */
    private boolean isConfigurationChanged(final Map<String, Object> currentConfig, final Map<String, Object> newConfig) {
        if (currentConfig == null && newConfig == null) {
            return false;
        }
        if (currentConfig == null || newConfig == null) {
            return true;
        }

        // 标准化配置，移除比较时不相关的字段
        Map<String, Object> normalizedCurrent = normalizeConfigForComparison(currentConfig);
        Map<String, Object> normalizedNew = normalizeConfigForComparison(newConfig);

        // 深度比较配置内容
        return !deepEquals(normalizedCurrent, normalizedNew);
    }

    /**
     * 标准化配置，移除比较时不相关的字段
     * 包括元数据字段、时间戳、自动生成的ID等
     *
     * @param config 原始配置
     * @return 标准化后的配置
     */
    private Map<String, Object> normalizeConfigForComparison(final Map<String, Object> config) {
        if (config == null) {
            return new HashMap<>();
        }

        Map<String, Object> normalized = new HashMap<>(config);

        // 移除元数据字段和所有时间戳相关字段
        normalized.remove("_metadata");
        normalized.remove("timestamp");
        normalized.remove("lastModified");
        normalized.remove("createdAt");
        normalized.remove("version");
        normalized.remove("versionInfo");
        normalized.remove("lastUpdated");
        normalized.remove("modifiedAt");
        normalized.remove("updatedAt");
        normalized.remove("saveTime");
        normalized.remove("createTime");

        // 递归移除嵌套对象中的时间戳字段
        removeTimestampFieldsRecursively(normalized);

        // 标准化服务实例配置
        if (normalized.containsKey("services")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) normalized.get("services");
            if (services != null) {
                Map<String, Object> normalizedServices = new HashMap<>();
                for (Map.Entry<String, Object> serviceEntry : services.entrySet()) {
                    String serviceType = serviceEntry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> serviceConfig = (Map<String, Object>) serviceEntry.getValue();
                    if (serviceConfig != null) {
                        normalizedServices.put(serviceType, normalizeServiceConfigForComparison(serviceConfig));
                    }
                }
                normalized.put("services", normalizedServices);
            }
        }

        return normalized;
    }

    /**
     * 递归移除配置中的时间戳字段
     */
    @SuppressWarnings("unchecked")
    private void removeTimestampFieldsRecursively(final Map<String, Object> config) {
        if (config == null) {
            return;
        }

        // 定义所有可能的时间戳字段名
        Set<String> timestampFields = Set.of(
                "timestamp", "lastModified", "createdAt", "lastUpdated",
                "modifiedAt", "updatedAt", "saveTime", "createTime",
                "lastHealthCheck", "lastError", "lastSeen"
        );

        // 移除当前层级的时间戳字段
        timestampFields.forEach(config::remove);

        // 递归处理嵌套的Map和List
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                removeTimestampFieldsRecursively((Map<String, Object>) value);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map) {
                        removeTimestampFieldsRecursively((Map<String, Object>) item);
                    }
                }
            }
        }
    }

    /**
     * 标准化服务配置，统一实例ID格式和移除不相关字段
     *
     * @param serviceConfig 服务配置
     * @return 标准化后的服务配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeServiceConfigForComparison(final Map<String, Object> serviceConfig) {
        Map<String, Object> normalized = new HashMap<>(serviceConfig);

        // 标准化实例列表
        if (normalized.containsKey("instances")) {
            List<Map<String, Object>> instances = (List<Map<String, Object>>) normalized.get("instances");
            if (instances != null) {
                List<Map<String, Object>> normalizedInstances = new ArrayList<>();
                for (Map<String, Object> instance : instances) {
                    normalizedInstances.add(normalizeInstanceConfigForComparison(instance));
                }
                // 按照name和baseUrl排序，确保比较时顺序一致
                normalizedInstances.sort((a, b) -> {
                    String nameA = (String) a.get("name");
                    String nameB = (String) b.get("name");
                    String urlA = (String) a.get("baseUrl");
                    String urlB = (String) b.get("baseUrl");

                    int nameCompare = (nameA != null ? nameA : "").compareTo(nameB != null ? nameB : "");
                    if (nameCompare != 0) {
                        return nameCompare;
                    }
                    return (urlA != null ? urlA : "").compareTo(urlB != null ? urlB : "");
                });
                normalized.put("instances", normalizedInstances);
            }
        }

        return normalized;
    }

    /**
     * 标准化实例配置，统一ID格式和移除动态字段
     *
     * @param instanceConfig 实例配置
     * @return 标准化后的实例配置
     */
    private Map<String, Object> normalizeInstanceConfigForComparison(final Map<String, Object> instanceConfig) {
        Map<String, Object> normalized = new HashMap<>(instanceConfig);

        // 移除动态字段和时间戳字段
        normalized.remove("health");
        normalized.remove("lastHealthCheck");
        normalized.remove("healthCheckCount");
        normalized.remove("lastError");
        normalized.remove("timestamp");
        normalized.remove("lastModified");
        normalized.remove("createdAt");
        normalized.remove("lastUpdated");
        normalized.remove("modifiedAt");
        normalized.remove("updatedAt");
        normalized.remove("saveTime");
        normalized.remove("createTime");

        // 移除状态字段 - 状态变化不应该触发新版本创建
        // 状态变化通常是运行时的动态变化，不是配置变化
        normalized.remove("status");

        // 统一实例ID格式
        String name = (String) normalized.get("name");
        String baseUrl = (String) normalized.get("baseUrl");
        if (name != null && baseUrl != null) {
            // 使用标准格式的instanceId
            String standardInstanceId = name + "@" + baseUrl;
            normalized.put("instanceId", standardInstanceId);
        }

        // 确保weight字段有默认值
        if (!normalized.containsKey("weight")) {
            normalized.put("weight", 1);
        }

        return normalized;
    }

    /**
     * 深度比较两个对象是否相等
     * 支持Map、List、基本类型的递归比较
     *
     * @param obj1 对象1
     * @param obj2 对象2
     * @return true如果对象相等，false如果不相等
     */
    @SuppressWarnings("unchecked")
    private boolean deepEquals(final Object obj1, final Object obj2) {
        if (obj1 == obj2) {
            return true;
        }
        if (obj1 == null || obj2 == null) {
            return false;
        }

        // 如果类型不同，直接返回false
        if (!obj1.getClass().equals(obj2.getClass())) {
            return false;
        }

        // Map类型的深度比较
        if (obj1 instanceof Map) {
            Map<String, Object> map1 = (Map<String, Object>) obj1;
            Map<String, Object> map2 = (Map<String, Object>) obj2;

            if (map1.size() != map2.size()) {
                return false;
            }

            for (Map.Entry<String, Object> entry : map1.entrySet()) {
                String key = entry.getKey();
                if (!map2.containsKey(key)) {
                    return false;
                }
                if (!deepEquals(entry.getValue(), map2.get(key))) {
                    return false;
                }
            }
            return true;
        }

        // List类型的深度比较
        if (obj1 instanceof List) {
            List<Object> list1 = (List<Object>) obj1;
            List<Object> list2 = (List<Object>) obj2;

            if (list1.size() != list2.size()) {
                return false;
            }

            for (int i = 0; i < list1.size(); i++) {
                if (!deepEquals(list1.get(i), list2.get(i))) {
                    return false;
                }
            }
            return true;
        }

        // 基本类型比较
        return obj1.equals(obj2);
    }

    /**
     * 条件性版本保存 - 只有在配置真正变化时才创建新版本
     *
     * @param config      配置内容
     * @param description 描述信息
     * @param userId      用户ID
     * @return 版本号（如果创建了新版本则返回新版本号，否则返回当前版本号）
     */
    public int saveAsNewVersionIfChanged(final Map<String, Object> config, final String description, final String userId) {
        // 使用同步锁确保配置比较和版本创建的原子性
        synchronized (versionCreationLock) {
            try {
                logger.debug("开始条件性版本保存，描述: {}, 用户: {}", description, userId);

                // 获取当前配置用于比较
                Map<String, Object> currentConfig = getCurrentPersistedConfig();

                // 使用智能配置比较
                if (!isConfigurationChanged(currentConfig, config)) {
                    logger.info("配置未发生变化，不创建新版本");
                    ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
                    return metadata != null ? metadata.getCurrentVersion() : 0;
                }

                // 配置发生变化，创建新版本
                logger.info("检测到配置变化，创建新版本");

                // 记录条件性版本保存的调用栈
                if (logger.isDebugEnabled()) {
                    logger.debug("条件性版本保存触发 - 描述: {}, 用户: {}", description, userId);
                }

                // 注意：这里不需要再次同步，因为已经在同步块内
                return saveAsNewVersionInternal(config, description, userId);

            } catch (Exception e) {
                logger.error("条件性版本保存失败", e);
                throw new RuntimeException("条件性版本保存失败", e);
            }
        }
    }

    // ==================== 查询操作 ====================

    /**
     * 获取所有配置（合并后的最终配置）
     *
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
                                String instanceId = InstanceIdUtils.getInstanceId(instance);
                                instance.put("instanceId", instanceId);
                            }

                            // v1.7.1: 从数据库获取健康状态，而非内存缓存
                            // 优先使用数据库中的 healthStatus 字段
                            String healthStatus = getHealthStatusFromDatabase(name);
                            if (healthStatus != null) {
                                instance.put("health", "HEALTHY".equals(healthStatus));
                                instance.put("healthStatus", healthStatus);
                            } else {
                                // 如果数据库中没有记录，默认为未知状态
                                instance.put("health", true); // 默认健康
                                instance.put("healthStatus", "UNKNOWN");
                            }
                        }
                    }
                }
            }
        }
        return configs;
    }

    /**
     * 从数据库获取实例的健康状态
     *
     * @param instanceName 实例名称
     * @return 健康状态字符串 (HEALTHY, UNHEALTHY, UNKNOWN)，如果找不到返回 null
     */
    private String getHealthStatusFromDatabase(final String instanceName) {
        if (serviceInstanceRepository == null) {
            return null;
        }
        try {
            return serviceInstanceRepository.findByInstanceName(instanceName)
                    .map(entity -> entity.getHealthStatus())
                    .orElse(null);
        } catch (Exception e) {
            logger.warn("从数据库获取实例健康状态失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取所有可用服务类型
     *
     * @return 服务类型列表
     */
    public Set<String> getAvailableServiceTypes() {
        Map<String, Object> config = getAllConfigurations();
        Map<String, Object> services = getServicesFromConfig(config);
        return services.keySet();
    }

    /**
     * 获取指定服务的配置
     *
     * @deprecated 建议使用 {@link ServiceConfigManager#getServiceConfig(String)} 或
     *             {@link ServiceConfigManager#getServiceConfiguration(String)} 获取服务配置。
     *             <p>迁移说明：</p>
     *             <ul>
     *               <li>ServiceConfigManager 返回强类型 DTO/领域对象，比 Map 更安全</li>
     *               <li>getServiceConfig 返回 Optional&lt;ServiceConfigDTO&gt;</li>
     *               <li>getServiceConfiguration 返回 ServiceConfiguration 领域对象</li>
     *             </ul>
     *             <p>迁移示例：</p>
     *             <pre>{@code
     *             // 旧代码 - 返回 Map
     *             Map<String, Object> config = configurationService.getServiceConfig(serviceType);
     *             
     *             // 新代码 - 返回 DTO
     *             Optional<ServiceConfigDTO> config = serviceConfigManager.getServiceConfig(serviceType);
     *             ServiceConfigDTO dto = config.orElseThrow(() -> new IllegalArgumentException("服务不存在"));
     *             
     *             // 新代码 - 返回领域对象
     *             ServiceConfiguration domain = serviceConfigManager.getServiceConfiguration(serviceType);
     *             }</pre>
     *             此方法将在 v3.0 版本中移除。
     * @see ServiceConfigManager#getServiceConfig(String)
     * @see ServiceConfigManager#getServiceConfiguration(String)
     * @since v2.5.3.6 标注废弃
     * @param serviceType 服务类型
     * @return 服务配置
     */
    @Deprecated(since = "2.5.3.6", forRemoval = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> getServiceConfig(final String serviceType) {
        // v1.5.1: 从 StoreManager 读取配置
        Map<String, Object> config = getAllConfigurations();
        Map<String, Object> services = getServicesFromConfig(config);
        return (Map<String, Object>) services.get(serviceType);
    }

    /**
     * 获取指定服务的所有实例
     *
     * @deprecated 此方法已委托给 {@link InstanceManager#getServiceInstancesAsMap(String)}。
     *             请直接使用 InstanceManager 进行实例查询。
     *             此方法将在 v3.0 版本中移除。
     * @see InstanceManager#getServiceInstancesAsMap(String)
     * @since v2.5.3.3 标注废弃，委托实现
     * @param serviceType 服务类型
     * @return 实例列表
     */
    @Deprecated(since = "2.5.3.3", forRemoval = true)
    public List<Map<String, Object>> getServiceInstances(final String serviceType) {
        return instanceManager.getServiceInstancesAsMap(serviceType);
    }

    /**
     * 获取指定服务的所有可用模型名称
     *
     * @param serviceType 服务类型
     * @return 模型名称集合
     */
    public Set<String> getAvailableModels(final String serviceType) {
        List<Map<String, Object>> instances = getServiceInstances(serviceType);
        return instances.stream()
                .map(instance -> (String) instance.get("name"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 获取指定实例的详细信息
     *
     * @deprecated 此方法已简化实现，建议直接使用 InstanceManager 和 ServiceStateManager。
     *             <p>迁移说明：</p>
     *             <pre>{@code
     *             // 旧代码
     *             Map<String, Object> instance = configurationService.getServiceInstance(serviceType, instanceId);
     *             
     *             // 新代码 - 获取实例配置
     *             ModelInstanceConfiguration instance = instanceManager.getServiceInstance(serviceType, instanceId);
     *             // 新代码 - 获取健康状态
     *             String healthStatus = serviceStateManager.getInstanceHealthStatus(serviceType + ":" + instanceId);
     *             }</pre>
     *             此方法将在 v3.0 版本中移除。
     * @see InstanceManager#getServiceInstance(String, String)
     * @see ServiceStateManager#getInstanceHealthStatus(String)
     * @since v2.5.3.3 标注废弃，简化实现
     * @param serviceType 服务类型
     * @param instanceId 实例ID
     * @return 实例配置
     */
    @Deprecated(since = "2.5.3.3", forRemoval = true)
    public Map<String, Object> getServiceInstance(final String serviceType, final String instanceId) {
        List<Map<String, Object>> instances = instanceManager.getServiceInstancesAsMap(serviceType);
        return instances.stream()
                .filter(instance -> instanceId.equals(InstanceIdUtils.getInstanceId(instance)))
                .map(instance -> {
                    String healthKey = serviceType + ":" + instanceId;
                    String healthStatus = serviceStateManager.getInstanceHealthStatus(healthKey);
                    instance.put("health", "HEALTHY".equals(healthStatus));
                    instance.put("healthStatus", healthStatus);
                    if (!instance.containsKey("status")) {
                        instance.put("status", "active");
                    }
                    return instance;
                })
                .findFirst()
                .orElse(null);
    }

    // ==================== 服务管理操作 ====================

    /**
     * 创建新服务（自动保存为新版本）
     *
     * @deprecated 建议使用 {@link ServiceConfigManager#createService(String, ServiceConfiguration)}。
     *             <p>迁移说明：</p>
     *             <ul>
     *               <li>ServiceConfigManager 使用强类型 ServiceConfiguration 参数</li>
     *               <li>ConfigurationService 使用弱类型 Map 参数</li>
     *               <li>强类型参数更安全，避免运行时类型错误</li>
     *             </ul>
     *             <p>迁移示例：</p>
     *             <pre>{@code
     *             // 旧代码 - 使用 Map
     *             Map<String, Object> config = new HashMap<>();
     *             config.put("adapter", "ollama");
     *             config.put("instances", instancesList);
     *             configurationService.createService(serviceType, config);
     *             
     *             // 新代码 - 使用 ServiceConfiguration
     *             ServiceConfiguration config = ServiceConfiguration.builder()
     *                 .adapter(AdapterType.OLLAMA)
     *                 .instances(instances)
     *                 .build();
     *             serviceConfigManager.createService(serviceType, config);
     *             }</pre>
     *             此方法将在 v3.0 版本中移除。
     * @see ServiceConfigManager#createService(String, ServiceConfiguration)
     * @since v2.5.3.7 标注废弃
     * @param serviceType 服务类型
     * @param serviceConfig 服务配置
     */
    @Deprecated(since = "2.5.3.7", forRemoval = true)
    public void createService(final String serviceType, final Map<String, Object> serviceConfig) {
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

        // 添加版本元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "createService");
        metadata.put("operationDetail", "创建新服务: " + serviceType);
        metadata.put("serviceType", serviceType);
        metadata.put("timestamp", System.currentTimeMillis());
        currentConfig.put("_metadata", metadata);

        // 保存为新版本并刷新配置
        saveAsNewVersion(currentConfig);
        refreshRuntimeConfig();

        logger.info("服务 {} 创建成功", serviceType);
    }

    /**
     * 更新服务配置（自动保存为新版本）
     *
     * @deprecated 建议使用 {@link ServiceConfigManager#updateServiceConfig(String, UpdateServiceConfigRequest)}
     *             或 {@link ServiceConfigManager#updateServiceConfig(String, ServiceConfiguration)}。
     *             <p>迁移说明：</p>
     *             <ul>
     *               <li>ServiceConfigManager 使用强类型参数（DTO或领域对象）</li>
     *               <li>ConfigurationService 使用弱类型 Map 参数</li>
     *               <li>强类型参数更安全，避免运行时类型错误</li>
     *             </ul>
     *             <p>迁移示例：</p>
     *             <pre>{@code
     *             // 旧代码 - 使用 Map
     *             Map<String, Object> config = new HashMap<>();
     *             config.put("adapter", "ollama");
     *             configurationService.updateServiceConfig(serviceType, config);
     *             
     *             // 新代码 - 使用 UpdateServiceConfigRequest
     *             UpdateServiceConfigRequest request = new UpdateServiceConfigRequest(...);
     *             serviceConfigManager.updateServiceConfig(serviceType, request);
     *             
     *             // 新代码 - 使用 ServiceConfiguration
     *             ServiceConfiguration config = ServiceConfiguration.builder()
     *                 .adapter(AdapterType.OLLAMA)
     *                 .build();
     *             serviceConfigManager.updateServiceConfig(serviceType, config);
     *             }</pre>
     *             此方法将在 v3.0 版本中移除。
     * @see ServiceConfigManager#updateServiceConfig(String, UpdateServiceConfigRequest)
     * @see ServiceConfigManager#updateServiceConfig(String, ServiceConfiguration)
     * @since v2.5.3.8 标注废弃
     * @param serviceType 服务类型
     * @param serviceConfig 新的服务配置
     * @return 更新后的完整服务配置
     */
    @Deprecated(since = "2.5.3.8", forRemoval = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateServiceConfig(final String serviceType, final Map<String, Object> serviceConfig) {
        logger.info("更新服务配置：{}", serviceType);

        try {
            Map<String, Object> currentConfig = getAllConfigurations();
            if (currentConfig == null) {
                throw new IllegalStateException("无法获取当前配置");
            }
            
            Map<String, Object> services = getServicesFromConfig(currentConfig);
            Map<String, Object> existingServiceConfig = (Map<String, Object>) services.get(serviceType);
            
            if (existingServiceConfig != null) {
                // 简单方案：保留 instances，用新配置覆盖其他字段
                Object instances = existingServiceConfig.get("instances");
                services.put(serviceType, serviceConfig);
                if (instances != null) {
                    serviceConfig.put("instances", instances);
                }
            } else {
                services.put(serviceType, serviceConfig);
            }
            
            storeManager.saveConfig("model-router-config", currentConfig);
            saveAsNewVersion(currentConfig, "更新服务配置: " + serviceType, SecurityUtils.getCurrentUserId());
            refreshRuntimeConfig();

            logger.info("服务 {} 配置更新成功", serviceType);
            return serviceConfig;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("更新服务配置失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            throw new RuntimeException("更新服务配置失败：" + e.getMessage(), e);
        }
    }

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
            saveAsNewVersion(currentConfig, "更新服务配置: " + serviceType, SecurityUtils.getCurrentUserId());
            refreshRuntimeConfig();

            logger.info("服务 {} 配置更新成功", serviceType);

        } catch (Exception e) {
            logger.error("更新服务配置失败：serviceType={}, error={}", serviceType, e.getMessage(), e);
            throw new RuntimeException("更新服务配置失败：" + e.getMessage(), e);
        }
    }

    /**
     * 删除服务（自动保存为新版本）
     *
     * @deprecated 建议使用 {@link ServiceConfigManager#deleteService(String)} 删除服务。
     *             <p>迁移说明：</p>
     *             <ul>
     *               <li>ServiceConfigManager.deleteService 提供相同功能</li>
     *               <li>ServiceConfigManager 使用事务管理，更可靠</li>
     *               <li>直接调用 ServiceConfigManager 更简洁</li>
     *             </ul>
     *             <p>迁移示例：</p>
     *             <pre>{@code
     *             // 旧代码 - 通过 ConfigurationService
     *             configurationService.deleteService(serviceType);
     *             
     *             // 新代码 - 直接使用 ServiceConfigManager
     *             serviceConfigManager.deleteService(serviceType);
     *             }</pre>
     *             此方法将在 v3.0 版本中移除。
     * @see ServiceConfigManager#deleteService(String)
     * @since v2.5.3.8 标注废弃
     * @param serviceType 服务类型
     */
    @Deprecated(since = "2.5.3.8", forRemoval = true)
    public void deleteService(final String serviceType) {
        logger.info("删除服务: {}", serviceType);

        Map<String, Object> currentConfig = getCurrentPersistedConfig();
        Map<String, Object> services = getServicesFromConfig(currentConfig);

        if (!services.containsKey(serviceType)) {
            throw new IllegalArgumentException("服务类型不存在: " + serviceType);
        }

        services.remove(serviceType);
        currentConfig.put("services", services);

        // 添加版本元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "deleteService");
        metadata.put("operationDetail", "删除服务: " + serviceType);
        metadata.put("serviceType", serviceType);
        metadata.put("timestamp", System.currentTimeMillis());
        currentConfig.put("_metadata", metadata);

        // 保存为新版本并刷新配置
        saveAsNewVersion(currentConfig);
        refreshRuntimeConfig();

        logger.info("服务 {} 删除成功", serviceType);
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
        List<Map<String, Object>> instances = (List<Map<String, Object>>) serviceConfig.computeIfAbsent("instances", k -> new ArrayList<>());

        // 验证实例配置
        Map<String, Object> instanceMap = configurationHelper.convertInstanceToMap(instanceConfig);
        logger.debug("转换后的实例配置Map: {}", instanceMap);
        logger.debug("实例配置中的headers字段: {}", instanceMap != null ? instanceMap.get("headers") : "null");
        Map<String, Object> validatedInstance = validateAndNormalizeInstanceConfig(instanceMap);

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
     * 更新服务实例（优化版本，可选择是否保存为新版本）
     *
     * @deprecated 此方法已委托给 {@link InstanceManager#updateServiceInstance(String, String, ModelRouterProperties.ModelInstance)}。
     *             请直接使用 InstanceManager 进行实例更新。
     *             <p>InstanceManager 提供相同的去重和锁机制，确保并发安全。</p>
     *             此方法将在 v3.0 版本中移除。
     * @see InstanceManager#updateServiceInstance(String, String, ModelRouterProperties.ModelInstance)
     * @since v2.5.3.4 标注废弃，委托实现
     * @param serviceType 服务类型
     * @param instanceId 实例ID
     * @param instanceConfig 新的实例配置
     */
    @Deprecated(since = "2.5.3.4", forRemoval = true)
    public void updateServiceInstance(final String serviceType, final String instanceId, final ModelRouterProperties.ModelInstance instanceConfig) {
        instanceManager.updateServiceInstance(serviceType, instanceId, instanceConfig);
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
        if (!isValidServiceType(serviceType)) {
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
            Map<String, Object> updatedInstance = mergeInstanceConfig(oldInstance, newInstanceMap);
            logger.debug("更新实例 - 合并后的实例配置: {}", updatedInstance);
            logger.debug("更新实例 - 合并后的headers字段: {}", updatedInstance.get("headers"));
            Map<String, Object> validatedInstance = validateAndNormalizeInstanceConfig(updatedInstance);
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


    /**
     * 删除服务实例（优化版本，可选择是否保存为新版本）
     *
     * @deprecated 此方法已委托给 {@link InstanceManager#deleteServiceInstance(String, String)}。
     *             请直接使用 InstanceManager 进行实例删除。
     *             此方法将在 v3.0 版本中移除。
     * @see InstanceManager#deleteServiceInstance(String, String)
     * @since v2.5.3.5 标注废弃，委托实现
     * @param serviceType 服务类型
     * @param instanceId 实例ID
     */
    @Deprecated(since = "2.5.3.5", forRemoval = true)
    public void deleteServiceInstance(final String serviceType, final String instanceId) {
        instanceManager.deleteServiceInstance(serviceType, instanceId);
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
        saveAsNewVersion(currentConfig);
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
        if (!isValidServiceType(serviceType)) {
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

        // 执行所有操作
        for (InstanceOperation operation : operations) {
            switch (operation.type()) {
                case ADD:
                    addInstanceToList(instances, operation.instanceConfig(), operationDetails);
                    break;
                case UPDATE:
                    updateInstanceInList(instances, operation.instanceId(), operation.instanceConfig(), operationDetails);
                    break;
                case DELETE:
                    deleteInstanceFromList(instances, operation.instanceId(), operationDetails);
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

    /**
     * 在实例列表中添加实例
     */
    private void addInstanceToList(final List<Map<String, Object>> instances, final ModelRouterProperties.ModelInstance instanceConfig, final List<String> operationDetails) {
        Map<String, Object> validatedInstance = validateAndNormalizeInstanceConfig(configurationHelper.convertInstanceToMap(instanceConfig));

        String name = (String) validatedInstance.get("name");
        String baseUrl = (String) validatedInstance.get("baseUrl");

        // 检查是否已存在
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
        operationDetails.add("添加 " + name + "@" + baseUrl);
    }

    /**
     * 在实例列表中更新实例
     */
    private void updateInstanceInList(final List<Map<String, Object>> instances, final String instanceId, final ModelRouterProperties.ModelInstance instanceConfig,final List<String> operationDetails) {
        boolean found = false;

        for (int i = 0; i < instances.size(); i++) {
            Map<String, Object> instance = instances.get(i);
            String currentInstanceId = InstanceIdUtils.getInstanceId(instance);
            if (instanceId.equals(currentInstanceId)) {
                Map<String, Object> updatedInstance = mergeInstanceConfig(instance, configurationHelper.convertInstanceToMap(instanceConfig));
                Map<String, Object> validatedInstance = validateAndNormalizeInstanceConfig(updatedInstance);
                instances.set(i, validatedInstance);
                found = true;
                operationDetails.add("更新 " + instanceId);
                break;
            }
        }

        if (!found) {
            throw new IllegalArgumentException("实例不存在: " + instanceId);
        }
    }

    /**
     * 从实例列表中删除实例
     */
    private void deleteInstanceFromList(final List<Map<String, Object>> instances, final String instanceId, final List<String> operationDetails) {
        boolean removed = instances.removeIf(instance -> instanceId.equals(InstanceIdUtils.getInstanceId(instance)));

        if (!removed) {
            throw new IllegalArgumentException("实例不存在: " + instanceId);
        }

        operationDetails.add("删除 " + instanceId);
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
     * 验证和标准化实例配置
     */
    private Map<String, Object> validateAndNormalizeInstanceConfig(final Map<String, Object> instanceConfig) {
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
                normalized.put("instanceId", InstanceIdUtils.getInstanceId(instanceConfig));
            }
        }

        logger.debug("验证和标准化实例配置 - 输入: {}", instanceConfig);
        logger.debug("验证和标准化实例配置 - 输出: {}", normalized);
        logger.debug("验证和标准化实例配置 - headers字段: {}", normalized.get("headers"));

        return normalized;
    }

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
     * 合并服务配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeServiceConfig(final Map<String, Object> existing, final Map<String, Object> updates) {
        Map<String, Object> merged = new HashMap<>(existing);

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("instances".equals(key) && value instanceof List) {
                // instances字段不合并，直接替换
                merged.put(key, value);
            } else if (existing.containsKey(key)
                    && existing.get(key) instanceof Map
                    && value instanceof Map) {
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
     * 验证服务类型是否有效
     */
    private boolean isValidServiceType(final String serviceType) {
        if (serviceType == null) {
            return false;
        }

        try {
            // 标准化处理：转小写，移除空格、下划线和连字符
            String normalizedKey = serviceType.toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[\\s_-]+", "");
            // 直接匹配枚举值
            ModelServiceRegistry.ServiceType.valueOf(normalizedKey);
            return true;
        } catch (Exception e) {
            // 处理常见的别名映射
            return isValidServiceTypeAlias(serviceType);
        }
    }

    /**
     * 检查是否是有效的服务类型别名
     */
    private boolean isValidServiceTypeAlias(final String serviceType) {
        String lowerServiceType = serviceType.toLowerCase(java.util.Locale.ROOT);
        
        // 使用常量类进行匹配
        if (lowerServiceType.equals(org.unreal.modelrouter.constants.ServiceTypeConstants.CHAT)
            || lowerServiceType.equals("chat-completion")
            || lowerServiceType.equals("chat-completions")) {
            return true;
        }
        
        if (lowerServiceType.equals(org.unreal.modelrouter.constants.ServiceTypeConstants.EMBEDDING)
            || lowerServiceType.equals("embeddings")) {
            return true;
        }
        
        if (lowerServiceType.equals(org.unreal.modelrouter.constants.ServiceTypeConstants.RERANK)
            || lowerServiceType.equals("re-rank")) {
            return true;
        }
        
        if (lowerServiceType.equals(org.unreal.modelrouter.constants.ServiceTypeConstants.TTS)
            || lowerServiceType.equals("text-to-speech")) {
            return true;
        }
        
        if (lowerServiceType.equals(org.unreal.modelrouter.constants.ServiceTypeConstants.STT)
            || lowerServiceType.equals("speech-to-text")) {
            return true;
        }
        
        if (lowerServiceType.equals(org.unreal.modelrouter.constants.ServiceTypeConstants.IMG_GEN)
            || lowerServiceType.equals("imggen")
            || lowerServiceType.equals("image-generation")
            || lowerServiceType.equals("image-generate")) {
            return true;
        }
        
        if (lowerServiceType.equals(org.unreal.modelrouter.constants.ServiceTypeConstants.IMG_EDIT)
            || lowerServiceType.equals("image-edit")
            || lowerServiceType.equals("image-editing")) {
            return true;
        }
        
        return false;
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
     * 合并实例配置
     */
    private Map<String, Object> mergeInstanceConfig(final Map<String, Object> existing, final Map<String, Object> updates) {
        Map<String, Object> merged = new HashMap<>(existing);
        merged.putAll(updates);

        // 确保instanceId字段存在
        if (!merged.containsKey("instanceId")) {
            String name = (String) merged.get("name");
            String baseUrl = (String) merged.get("baseUrl");
            if (name != null && baseUrl != null) {
                merged.put("instanceId", InstanceIdUtils.getInstanceId(existing));
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

    /**
     * 获取当前追踪配置
     *
     * @return TraceConfig对象
     */
    public TraceConfig getTraceConfig() {
        Map<String, Object> currentConfig = getAllConfigurations();
        return extractTraceConfig(currentConfig);
    }

    /**
     * 从配置Map中提取追踪配置
     *
     * @param config 配置Map
     * @return TraceConfig对象
     */
    private TraceConfig extractTraceConfig(final Map<String, Object> config) {
        if (config.containsKey("trace")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> traceConfigMap = (Map<String, Object>) config.get("trace");
            return TraceConfig.fromMap(traceConfigMap);
        }
        return new TraceConfig(); // 返回默认配置
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
            saveAsNewVersion(currentConfig);
        } else {
            // 直接保存配置但不创建新版本
            storeManager.saveConfig(CURRENT_KEY, currentConfig);
        }

        refreshRuntimeConfig();
        logger.info("追踪配置删除成功");
    }

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
    public void updateTracingSamplingConfig(final Map<String, Object> samplingConfig, final boolean createNewVersion) {
        logger.info("更新追踪采样配置");

        // 验证配置
        if (samplingConfig != null && !samplingConfig.isEmpty()) {
            // 处理前端发送的serviceConfigs字段，转换为后端需要的serviceRatios字段
            if (samplingConfig.containsKey("serviceConfigs")) {
                Object serviceConfigsObj = samplingConfig.get("serviceConfigs");
                if (serviceConfigsObj instanceof List) {
                    List<Map<String, Object>> serviceConfigsList = (List<Map<String, Object>>) serviceConfigsObj;
                    Map<String, Double> serviceRatios = new HashMap<>();
                    for (Map<String, Object> serviceConfig : serviceConfigsList) {
                        if (serviceConfig.containsKey("service") && serviceConfig.containsKey("rate")) {
                            Object serviceObj = serviceConfig.get("service");
                            Object rateObj = serviceConfig.get("rate");
                            if (serviceObj instanceof String && rateObj instanceof Number) {
                                String service = (String) serviceObj;
                                Double rate = ((Number) rateObj).doubleValue() / 100.0; // 前端以百分比形式发送
                                serviceRatios.put(service, rate);
                            }
                        }
                    }
                    // 移除serviceConfigs字段，添加serviceRatios字段
                    samplingConfig.remove("serviceConfigs");
                    samplingConfig.put("serviceRatios", serviceRatios);
                }
            }

            try {
                // 将Map转换为TracingConfiguration.SamplingConfig对象进行验证
                org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration.SamplingConfig config
                        = convertMapToSamplingConfig(samplingConfig);

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

        // 添加版本元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "updateTracingSampling");
        metadata.put("operationDetail", "更新追踪采样配置");
        metadata.put("timestamp", System.currentTimeMillis());
        currentConfig.put("_metadata", metadata);

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

    // ==================== 追踪采样配置管理 ====================

    /**
     * 从版本配置中提取采样配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSamplingConfigFromVersion(final Map<String, Object> versionConfig) {
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
    private TracingConfiguration.SamplingConfig convertMapToSamplingConfig(final Map<String, Object> configMap) {
        TracingConfiguration.SamplingConfig config
                = new TracingConfiguration.SamplingConfig();

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
                List<org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration.SamplingConfig.SamplingRule> rules = new ArrayList<>();

                for (Map<String, Object> ruleMap : rulesList) {
                    org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration.SamplingConfig.SamplingRule rule
                            = new org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration.SamplingConfig.SamplingRule();

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
                org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration.SamplingConfig.AdaptiveConfig adaptiveConfig
                        = new org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration.SamplingConfig.AdaptiveConfig();

                if (adaptiveMap.containsKey("enabled")) {
                    Object enabledObj = adaptiveMap.get("enabled");
                    if (enabledObj instanceof Boolean) {
                        adaptiveConfig.setEnabled((Boolean) enabledObj);
                    }
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

    /**
     * 检查是否存在持久化配置
     *
     * @return true如果存在持久化配置
     */
    public boolean hasPersistedConfig() {
        return configMergeService.hasPersistedConfig();
    }

    /**
     * 记录配置变更审计日志
     *
     * @param configType 配置类型
     * @param action 操作类型 (create, update, delete)
     * @param configData 配置数据
     * @param createNewVersion 是否创建新版本
     */
    private void logConfigurationChange(final String configType, final String action, final Map<String, Object> configData,final boolean createNewVersion) {
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

    // ==================== 新增方法 ====================

    /**
     * 记录配置回滚审计日志
     *
     * @param targetVersion 目标版本
     * @param config 回滚后的配置
     */
    private void logConfigurationRollback(final int targetVersion, final Map<String, Object> config) {
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

    // ==================== 审计日志功能 ====================

    /**
     * 创建配置摘要，用于审计日志
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> createConfigSummary(final Map<String, Object> config) {
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
    private void logSamplingConfigRollback(final int targetVersion, final Map<String, Object> samplingConfig) {
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
     * 记录版本删除审计日志
     *
     * @param deletedVersion 被删除的版本号
     */
    private void logVersionDeletion(final int deletedVersion) {
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("configType", "configuration.version.deletion");
            auditData.put("action", "delete_version");
            auditData.put("timestamp", java.time.Instant.now().toString());
            auditData.put("deletedVersion", deletedVersion);
            auditData.put("currentVersion", getCurrentVersion());
            auditData.put("userId", SecurityUtils.getCurrentUserId());

            // 记录删除前的版本统计信息
            ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
            if (metadata != null) {
                auditData.put("totalVersionsAfterDeletion", metadata.getTotalVersions());
                auditData.put("existingVersions",
                        metadata.getExistingVersions());
            }

            // 记录剩余可用版本
            auditData.put("remainingVersions", getAllVersions());

            // 使用结构化日志记录审计信息
            logger.info("版本删除审计: {}", auditData);

        } catch (Exception e) {
            // 审计日志失败不应影响主业务流程
            logger.warn("记录版本删除审计日志失败: {}", e.getMessage());
        }
    }

    /**
     * 应用版本后更新当前版本状态
     *
     * @param appliedVersion 应用的版本号
     */
    private void updateCurrentVersionAfterApply(final int appliedVersion) {
        try {
            ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
            if (metadata != null) {
                // 更新当前版本为应用的版本
                metadata.setCurrentVersion(appliedVersion);
                metadata.setLastModified(LocalDateTime.now());
                metadata.setLastModifiedBy(SecurityUtils.getCurrentUserId());

                // 保存更新后的元数据
                saveMetadata(CURRENT_KEY, metadata);

                logger.info("已更新当前版本状态为: {}", appliedVersion);
            } else {
                logger.warn("未找到配置元数据，无法更新当前版本状态");
            }
        } catch (Exception e) {
            logger.error("更新当前版本状态失败", e);
        }
    }

    /**
     * 脱敏配置数据，移除敏感信息
     *
     * @param configData 原始配置数据
     * @return 脱敏后的配置数据
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeConfigData(final Map<String, Object> configData) {
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
    private List<Object> sanitizeConfigList(final List<Object> configList) {
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
    private boolean isSensitiveField(final String fieldName) {
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

    public void cleanVersion() {
        ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
        List<VersionInfo> versionInfos = versionHistoryMap.get(CURRENT_KEY);

        metadata.clean();
        saveMetadata(CURRENT_KEY, metadata);
        versionInfos.clear();
        saveVersionHistory(CURRENT_KEY, versionInfos);


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
