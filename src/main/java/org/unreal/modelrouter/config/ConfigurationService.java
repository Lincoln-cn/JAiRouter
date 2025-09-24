package org.unreal.modelrouter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.checker.ServerChecker;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.entity.ConfigMetadata;
import org.unreal.modelrouter.entity.VersionInfo;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.tracing.config.SamplingConfigurationValidator;
import org.unreal.modelrouter.util.ApplicationContextProvider;
import org.unreal.modelrouter.util.SecurityUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 配置管理服务 - 重构版 提供完整的服务配置增删改查功能 支持服务、实例的动态管理 支持配置版本管理
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

    private static final long REQUEST_DEDUP_WINDOW_MS = 1000; // 1秒内的重复请求将被忽略
    // 版本控制相关字段
    private final Map<String, ConfigMetadata> configMetadataMap = new HashMap<>();
    private final Map<String, List<VersionInfo>> versionHistoryMap = new HashMap<String, List<VersionInfo>>();

    // 版本创建同步锁
    private final Object versionCreationLock = new Object();

    // 实例更新锁，防止同一实例的并发更新
    private final ConcurrentHashMap<String, Object> instanceUpdateLocks = new ConcurrentHashMap<>();

    // 请求去重缓存，防止短时间内的重复请求
    private final ConcurrentHashMap<String, Long> recentUpdateRequests = new ConcurrentHashMap<>();
    // 记录最近版本创建的时间，用于检测短时间内的重复创建
    private volatile long lastVersionCreationTime = 0;
    private volatile String lastVersionDescription = "";
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
        initializeVersionControl();
    }

    /**
     * 设置ModelServiceRegistry引用 用于避免循环依赖问题
     *
     * @param modelServiceRegistry ModelServiceRegistry实例
     */
    public void setModelServiceRegistry(ModelServiceRegistry modelServiceRegistry) {
        this.modelServiceRegistry = modelServiceRegistry;
    }

    /**
     * 初始化版本控制
     */
    private void initializeVersionControl() {
        try {
            boolean metadataLoaded = false;

            // 初始化配置元数据
            if (storeManager.exists(CURRENT_KEY + ".metadata")) {
                Map<String, Object> metadataMap = storeManager.getConfig(CURRENT_KEY + ".metadata");
                if (metadataMap != null) {
                    ConfigMetadata metadata = new ConfigMetadata();
                    metadata.setConfigKey(CURRENT_KEY);
                    if (metadataMap.containsKey("currentVersion")) {
                        metadata.setCurrentVersion(((Number) metadataMap.get("currentVersion")).intValue());
                    }
                    if (metadataMap.containsKey("initialVersion")) {
                        metadata.setInitialVersion(((Number) metadataMap.get("initialVersion")).intValue());
                    }
                    if (metadataMap.containsKey("createdAt")) {
                        metadata.setCreatedAt((LocalDateTime) metadataMap.get("createdAt"));
                    }
                    if (metadataMap.containsKey("lastModified")) {
                        metadata.setLastModified((LocalDateTime) metadataMap.get("lastModified"));
                    }
                    if (metadataMap.containsKey("lastModifiedBy")) {
                        metadata.setLastModifiedBy((String) metadataMap.get("lastModifiedBy"));
                    }
                    if (metadataMap.containsKey("totalVersions")) {
                        metadata.setTotalVersions(((Number) metadataMap.get("totalVersions")).intValue());
                    }
                    if (metadataMap.containsKey("existingVersions")) {
                        @SuppressWarnings("unchecked")
                        List<Integer> versionList = (List<Integer>) metadataMap.get("existingVersions");
                        if (versionList != null) {
                            metadata.setExistingVersions(new HashSet<>(versionList));
                            logger.info("加载了 {} 个版本: {}", versionList.size(), versionList);
                        }
                    }
                    configMetadataMap.put(CURRENT_KEY, metadata);
                    metadataLoaded = true;
                    logger.info("成功加载配置元数据，当前版本: {}", metadata.getCurrentVersion());
                }
            }

            // 初始化版本历史
            List<VersionInfo> versionHistory = new ArrayList<>();
            if (storeManager.exists(CURRENT_KEY + ".history")) {
                Map<String, Object> historyWrapper = storeManager.getConfig(CURRENT_KEY + ".history");
                if (historyWrapper != null && historyWrapper.containsKey("history")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> historyList = (List<Map<String, Object>>) historyWrapper.get("history");
                    if (historyList != null) {
                        for (Map<String, Object> historyItem : historyList) {
                            VersionInfo versionInfo = new VersionInfo();
                            if (historyItem.containsKey("version")) {
                                versionInfo.setVersion(((Number) historyItem.get("version")).intValue());
                            }
                            if (historyItem.containsKey("createdAt")) {
                                Object createdAtObj = historyItem.get("createdAt");
                                if (createdAtObj instanceof LocalDateTime) {
                                    versionInfo.setCreatedAt((LocalDateTime) createdAtObj);
                                } else if (createdAtObj instanceof List) {
                                    // 处理数组格式的时间 [2025,9,23,18,50,17,964096443]
                                    @SuppressWarnings("unchecked")
                                    List<Integer> timeArray = (List<Integer>) createdAtObj;
                                    if (timeArray.size() >= 6) {
                                        LocalDateTime dateTime = LocalDateTime.of(
                                                timeArray.get(0), // year
                                                timeArray.get(1), // month
                                                timeArray.get(2), // day
                                                timeArray.get(3), // hour
                                                timeArray.get(4), // minute
                                                timeArray.get(5), // second
                                                timeArray.size() > 6 ? timeArray.get(6) : 0 // nano
                                        );
                                        versionInfo.setCreatedAt(dateTime);
                                    }
                                }
                            }
                            if (historyItem.containsKey("createdBy")) {
                                versionInfo.setCreatedBy((String) historyItem.get("createdBy"));
                            }
                            if (historyItem.containsKey("description")) {
                                versionInfo.setDescription((String) historyItem.get("description"));
                            }
                            if (historyItem.containsKey("changeType")) {
                                versionInfo.setChangeType(VersionInfo.ChangeType.valueOf((String) historyItem.get("changeType")));
                            }
                            versionHistory.add(versionInfo);
                        }
                        versionHistoryMap.put(CURRENT_KEY, versionHistory);
                        logger.info("成功加载版本历史，共 {} 个版本", versionHistory.size());
                    }
                }
            }

            // 元数据恢复机制：如果元数据丢失但历史文件存在，尝试重建元数据
            if (!metadataLoaded && !versionHistory.isEmpty()) {
                logger.warn("检测到元数据文件丢失但历史文件存在，尝试重建元数据");
                recoverMetadataFromHistory(versionHistory);
            }
        } catch (Exception e) {
            logger.warn("初始化版本控制数据时发生错误: {}", e.getMessage());
        }
    }

    /**
     * 生成下一个可用的版本号
     * 使用时间戳+随机数确保唯一性，不要求连续
     */
    private int generateNextVersionNumber() {
        // 使用时间戳的后6位 + 3位随机数生成版本号
        long timestamp = System.currentTimeMillis();
        int timestampPart = (int) (timestamp % 1000000); // 取后6位
        int randomPart = (int) (Math.random() * 1000); // 3位随机数

        int candidateVersion = timestampPart * 1000 + randomPart;

        // 确保版本号不小于当前最大版本号
        ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
        if (metadata != null && candidateVersion <= metadata.getCurrentVersion()) {
            candidateVersion = metadata.getCurrentVersion() + 1 + randomPart;
        }

        // 检查版本号是否已存在，如果存在则递增直到找到可用的
        int maxAttempts = 100;
        int attempts = 0;
        while (storeManager.versionExists(CURRENT_KEY, candidateVersion) && attempts < maxAttempts) {
            candidateVersion++;
            attempts++;
        }

        if (attempts >= maxAttempts) {
            // 如果尝试100次都找不到可用版本号，使用简单递增策略
            candidateVersion = metadata != null ? metadata.getCurrentVersion() + 1 : 1;
            while (storeManager.versionExists(CURRENT_KEY, candidateVersion)) {
                candidateVersion++;
            }
        }

        logger.debug("生成的版本号: {}", candidateVersion);
        return candidateVersion;
    }

    /**
     * 记录版本创建的时间间隔，帮助识别短时间内的重复创建
     */
    private void recordVersionCreationTiming(String description) {
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
     * 从版本历史恢复元数据
     */
    private void recoverMetadataFromHistory(List<VersionInfo> versionHistory) {
        try {
            if (versionHistory == null || versionHistory.isEmpty()) {
                logger.warn("版本历史为空，无法恢复元数据");
                return;
            }

            // 按版本号排序
            versionHistory.sort(Comparator.comparing(VersionInfo::getVersion));

            // 获取版本范围
            int minVersion = versionHistory.get(0).getVersion();
            int maxVersion = versionHistory.get(versionHistory.size() - 1).getVersion();

            // 创建恢复的元数据
            ConfigMetadata recoveredMetadata = new ConfigMetadata();
            recoveredMetadata.setConfigKey(CURRENT_KEY);
            recoveredMetadata.setInitialVersion(minVersion);
            recoveredMetadata.setCurrentVersion(maxVersion);
            recoveredMetadata.setTotalVersions(versionHistory.size());

            // 恢复版本列表
            Set<Integer> existingVersions = new HashSet<>();
            for (VersionInfo versionInfo : versionHistory) {
                existingVersions.add(versionInfo.getVersion());
            }
            recoveredMetadata.setExistingVersions(existingVersions);

            // 设置时间信息
            VersionInfo firstVersion = versionHistory.get(0);
            VersionInfo lastVersion = versionHistory.get(versionHistory.size() - 1);

            if (firstVersion.getCreatedAt() != null) {
                recoveredMetadata.setCreatedAt(firstVersion.getCreatedAt());
            } else {
                recoveredMetadata.setCreatedAt(LocalDateTime.now());
            }

            if (lastVersion.getCreatedAt() != null) {
                recoveredMetadata.setLastModified(lastVersion.getCreatedAt());
            } else {
                recoveredMetadata.setLastModified(LocalDateTime.now());
            }

            if (lastVersion.getCreatedBy() != null) {
                recoveredMetadata.setLastModifiedBy(lastVersion.getCreatedBy());
            } else {
                recoveredMetadata.setLastModifiedBy("system");
            }

            // 保存恢复的元数据
            configMetadataMap.put(CURRENT_KEY, recoveredMetadata);
            saveMetadata(CURRENT_KEY, recoveredMetadata);

            logger.info("成功从版本历史恢复元数据，版本范围: {} - {}, 总版本数: {}",
                    minVersion, maxVersion, versionHistory.size());

        } catch (Exception e) {
            logger.error("从版本历史恢复元数据失败", e);
        }
    }

    /**
     * 保存配置元数据
     */
    private void saveMetadata(String key, ConfigMetadata metadata) {
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
    private void saveVersionHistory(String key, List<VersionInfo> versionHistory) {
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
     * @return 版本号列表
     */
    public List<Integer> getAllVersions() {
        ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
        if (metadata == null) {
            return new ArrayList<>();
        }

        // 从元数据中获取版本列表
        Set<Integer> existingVersions = metadata.getExistingVersions();
        if (existingVersions.isEmpty()) {
            // 如果元数据中没有版本列表，进行一次扫描并更新元数据
            logger.info("元数据中版本列表为空，进行文件扫描");
            List<Integer> scannedVersions = scanExistingVersions();
            metadata.setExistingVersions(new HashSet<>(scannedVersions));
            saveMetadata(CURRENT_KEY, metadata);
            return scannedVersions;
        }

        // 返回排序后的版本列表
        List<Integer> sortedVersions = new ArrayList<>(existingVersions);
        sortedVersions.sort(Integer::compareTo);

        logger.debug("从元数据获取到 {} 个版本: {}", sortedVersions.size(), sortedVersions);
        return sortedVersions;
    }

    /**
     * 扫描实际存在的版本文件
     */
    private List<Integer> scanExistingVersions() {
        List<Integer> versions = new ArrayList<>();

        // 从版本历史中获取已知版本
        List<VersionInfo> versionHistory = versionHistoryMap.get(CURRENT_KEY);
        if (versionHistory != null) {
            for (VersionInfo versionInfo : versionHistory) {
                int version = versionInfo.getVersion();
                if (storeManager.versionExists(CURRENT_KEY, version)) {
                    versions.add(version);
                }
            }
        }

        // 如果版本历史为空，尝试基于元数据扫描
        if (versions.isEmpty()) {
            ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
            if (metadata != null) {
                // 扫描一个合理的范围
                int maxScan = Math.max(metadata.getCurrentVersion() + 10, 100);
                for (int i = 1; i <= maxScan; i++) {
                    if (storeManager.versionExists(CURRENT_KEY, i)) {
                        versions.add(i);
                    }
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
     *
     * @param config 配置内容
     * @return 新版本号
     */
    public int saveAsNewVersion(Map<String, Object> config) {
        return saveAsNewVersion(config, "系统自动保存", "system");
    }

    /**
     * 保存当前配置为新版本（带描述和用户信息）
     *
     * @param config      配置内容
     * @param description 描述信息
     * @param userId      用户ID
     * @return 新版本号
     */
    public int saveAsNewVersion(Map<String, Object> config, String description, String userId) {
        // 使用同步锁确保版本创建的原子性
        synchronized (versionCreationLock) {
            return saveAsNewVersionInternal(config, description, userId);
        }
    }

    /**
     * 内部版本保存方法（已在同步块内调用）
     */
    private int saveAsNewVersionInternal(Map<String, Object> config, String description, String userId) {
        try {
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
     * @param version 版本号
     */
    public void applyVersion(int version) {
        logger.info("开始应用配置版本: {}", version);

        try {
            // 1. 验证版本存在性
            if (!versionExists(version)) {
                String availableVersions = getAllVersions().toString();
                throw new IllegalArgumentException(
                        String.format("版本 %d 不存在。可用版本: %s", version, availableVersions));
            }

            // 2. 获取版本配置
            Map<String, Object> config = getVersionConfig(version);
            if (config == null || config.isEmpty()) {
                throw new IllegalStateException(
                        String.format("无法读取版本 %d 的配置内容，配置文件可能已损坏", version));
            }

            logger.debug("获取到版本 {} 的配置，包含 {} 个顶级配置项", version, config.size());

            // 3. 备份当前配置（用于错误恢复）
            Map<String, Object> backupConfig = null;
            try {
                backupConfig = getCurrentPersistedConfig();
            } catch (Exception e) {
                logger.warn("无法备份当前配置: {}", e.getMessage());
            }

            // 4. 原子性应用配置
            try {
                // 应用配置到存储
                storeManager.saveConfig(CURRENT_KEY, config);
                logger.debug("配置已成功应用到存储管理器");

                // 刷新运行时配置
                refreshRuntimeConfig();
                logger.debug("运行时配置已成功刷新");

                // 更新当前版本状态
                updateCurrentVersionAfterApply(version);

                // 记录配置应用审计日志
                logConfigurationRollback(version, config);

                logger.info("成功应用配置版本: {}", version);

            } catch (Exception e) {
                logger.error("应用配置版本 {} 时发生错误，尝试恢复", version, e);

                // 尝试恢复到备份配置
                if (backupConfig != null) {
                    try {
                        storeManager.saveConfig(CURRENT_KEY, backupConfig);
                        refreshRuntimeConfig();
                        logger.info("已恢复到应用前的配置状态");
                    } catch (Exception recoveryException) {
                        logger.error("恢复配置失败", recoveryException);
                    }
                }

                throw new RuntimeException(
                        String.format("应用配置版本 %d 失败: %s", version, e.getMessage()), e);
            }

        } catch (IllegalArgumentException | IllegalStateException e) {
            // 重新抛出验证错误
            throw e;
        } catch (Exception e) {
            logger.error("应用配置版本 {} 时发生未预期的错误", version, e);
            throw new RuntimeException(
                    String.format("应用配置版本 %d 时发生系统错误: %s", version, e.getMessage()), e);
        }
    }

    /**
     * 删除指定版本的配置
     *
     * @param version 版本号
     */
    public void deleteConfigVersion(int version) {
        logger.info("开始删除配置版本: {}", version);

        try {
            // 1. 验证版本存在性
            if (!versionExists(version)) {
                String availableVersions = getAllVersions().toString();
                throw new IllegalArgumentException(
                        String.format("版本 %d 不存在。可用版本: %s", version, availableVersions));
            }

            // 2. 检查是否为当前版本，禁止删除当前版本
            ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
            if (metadata != null && version == metadata.getCurrentVersion()) {
                throw new IllegalStateException(
                        String.format("不能删除当前版本 %d。请先应用其他版本后再删除此版本", version));
            }

            // 3. 验证删除前的完整性检查
            List<Integer> allVersions = getAllVersions();
            if (allVersions.size() <= 1) {
                throw new IllegalStateException("不能删除最后一个版本，系统至少需要保留一个配置版本");
            }

            // 4. 执行删除操作
            try {
                // 删除版本文件
                storeManager.deleteConfigVersion(CURRENT_KEY, version);
                logger.debug("已从存储中删除版本 {} 的配置文件", version);

                // 5. 更新元数据和版本范围
                if (metadata != null) {
                    metadata.setTotalVersions(Math.max(0, metadata.getTotalVersions() - 1));
                    metadata.setLastModified(LocalDateTime.now());
                    metadata.setLastModifiedBy(SecurityUtils.getCurrentUserId());
                    metadata.removeVersion(version); // 从版本列表中移除

                    // 重新计算版本范围，跳过已删除的版本
                    updateVersionRangeAfterDeletion(metadata, version);

                    saveMetadata(CURRENT_KEY, metadata);
                    logger.debug("已更新配置元数据和版本范围");
                }

                // 6. 更新版本历史记录
                List<VersionInfo> versionHistory = versionHistoryMap.get(CURRENT_KEY);
                if (versionHistory != null) {
                    boolean removed = versionHistory.removeIf(info -> info.getVersion() == version);
                    if (removed) {
                        saveVersionHistory(CURRENT_KEY, versionHistory);
                        logger.debug("已从版本历史中移除版本 {}", version);
                    }
                }

                // 7. 记录删除操作的审计日志
                logVersionDeletion(version);

                logger.info("成功删除配置版本: {}", version);

            } catch (Exception e) {
                logger.error("删除配置版本 {} 时发生错误", version, e);
                throw new RuntimeException(
                        String.format("删除配置版本 %d 失败: %s", version, e.getMessage()), e);
            }

        } catch (IllegalArgumentException | IllegalStateException e) {
            // 重新抛出验证错误
            throw e;
        } catch (Exception e) {
            logger.error("删除配置版本 {} 时发生未预期的错误", version, e);
            throw new RuntimeException(
                    String.format("删除配置版本 %d 时发生系统错误: %s", version, e.getMessage()), e);
        }
    }

    /**
     * 获取实际当前配置版本号
     *
     * @return 当前配置版本号，如果不存在则返回0
     */
    public int getActualCurrentVersion() {
        ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
        if (metadata != null) {
            return metadata.getCurrentVersion();
        }

        // 如果没有元数据信息，则返回通过版本列表计算的版本号
        int fallbackVersion = getCurrentVersion();
        logger.info("未找到元数据版本信息，使用回退版本: {}", fallbackVersion);
        return fallbackVersion;
    }

    /**
     * 获取当前最新版本号（基于版本列表）
     *
     * @return 当前版本号
     */
    public int getCurrentVersion() {
        ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
        if (metadata != null) {
            return metadata.getCurrentVersion();
        }
        return 0;
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
    private boolean versionExists(int version) {
        if (version <= 0) {
            return false;
        }

        // 检查版本是否在有效范围内
        ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
        if (metadata != null) {
            return version >= metadata.getInitialVersion() && version <= metadata.getCurrentVersion();
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
    private boolean isConfigurationChanged(Map<String, Object> currentConfig, Map<String, Object> newConfig) {
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
    private Map<String, Object> normalizeConfigForComparison(Map<String, Object> config) {
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
    private void removeTimestampFieldsRecursively(Map<String, Object> config) {
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
    private Map<String, Object> normalizeServiceConfigForComparison(Map<String, Object> serviceConfig) {
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
    private Map<String, Object> normalizeInstanceConfigForComparison(Map<String, Object> instanceConfig) {
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
    private boolean deepEquals(Object obj1, Object obj2) {
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
    public int saveAsNewVersionIfChanged(Map<String, Object> config, String description, String userId) {
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
     *
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
     *
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
     *
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
     *
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

        // 添加版本元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "updateService");
        metadata.put("operationDetail", "更新服务配置: " + serviceType);
        metadata.put("serviceType", serviceType);
        metadata.put("timestamp", System.currentTimeMillis());
        currentConfig.put("_metadata", metadata);

        // 保存为新版本并刷新配置
        saveAsNewVersion(currentConfig);
        refreshRuntimeConfig();

        logger.info("服务 {} 配置更新成功", serviceType);
    }

    /**
     * 删除服务（自动保存为新版本）
     *
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
     * @param createNewVersion 是否创建新版本
     */
    @SuppressWarnings("unchecked")
    public void addServiceInstance(String serviceType, ModelRouterProperties.ModelInstance instanceConfig) {
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
     * @param serviceType 服务类型
     * @param instanceId 实例ID
     * @param instanceConfig 新的实例配置
     */
    @SuppressWarnings("unchecked")
    public void updateServiceInstance(String serviceType, String instanceId, ModelRouterProperties.ModelInstance instanceConfig) {
        // 创建请求唯一标识，用于去重
        String requestKey = serviceType + ":" + instanceId + ":" +
                (instanceConfig != null ? instanceConfig.getStatus() : "null");

        // 检查是否为重复请求
        long currentTime = System.currentTimeMillis();
        Long lastRequestTime = recentUpdateRequests.get(requestKey);
        if (lastRequestTime != null && (currentTime - lastRequestTime) < REQUEST_DEDUP_WINDOW_MS) {
            logger.info("检测到重复的更新请求，忽略: serviceType={}, instanceId={}", serviceType, instanceId);
            return;
        }

        // 记录当前请求时间
        recentUpdateRequests.put(requestKey, currentTime);

        // 清理过期的请求记录
        cleanupExpiredRequests(currentTime);

        // 获取实例级别的锁
        Object instanceLock = instanceUpdateLocks.computeIfAbsent(instanceId, k -> new Object());

        synchronized (instanceLock) {
            try {
                logger.info("更新服务 {} 的实例 {} - 线程: {}", serviceType, instanceId, Thread.currentThread().getName());

                // 记录实例更新的详细信息，帮助分析多版本创建问题
                if (logger.isDebugEnabled()) {
                    logger.debug("实例更新详情 - 服务类型: {}, 实例ID: {}, 实例名称: {}, BaseURL: {}, 状态: {}",
                            serviceType, instanceId,
                            instanceConfig != null ? instanceConfig.getName() : "null",
                            instanceConfig != null ? instanceConfig.getBaseUrl() : "null",
                            instanceConfig != null ? instanceConfig.getStatus() : "null");
                }

                updateServiceInstanceInternal(serviceType, instanceId, instanceConfig);

            } finally {
                // 清理实例锁（如果没有其他线程在等待）
                instanceUpdateLocks.remove(instanceId, instanceLock);
            }
        }
    }

    /**
     * 清理过期的请求记录
     */
    private void cleanupExpiredRequests(long currentTime) {
        recentUpdateRequests.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > REQUEST_DEDUP_WINDOW_MS * 2);
    }

    /**
     * 内部实例更新方法
     */
    @SuppressWarnings("unchecked")
    private void updateServiceInstanceInternal(String serviceType, String instanceId, ModelRouterProperties.ModelInstance instanceConfig) {

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
     * @param serviceType 服务类型
     * @param instanceId 实例ID
     * @param createNewVersion 是否创建新版本
     */
    @SuppressWarnings("unchecked")
    public void deleteServiceInstance(String serviceType, String instanceId) {
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
        List<Map<String, Object>> instances = (List<Map<String, Object>>) serviceConfig.getOrDefault("instances", new ArrayList<>());

        // 删除匹配的实例
        boolean removed = instances.removeIf(instance -> instanceId.equals(buildInstanceId(instance)));

        if (!removed) {
            throw new IllegalArgumentException("实例不存在: " + instanceId);
        }

        // 更新服务配置
        serviceConfig.put("instances", instances);
        services.put(serviceType, serviceConfig);
        currentConfig.put("services", services);

        // 添加版本元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("operation", "deleteInstance");
        metadata.put("operationDetail", "删除服务实例: " + instanceId);
        metadata.put("serviceType", serviceType);
        metadata.put("instanceId", instanceId);
        metadata.put("timestamp", System.currentTimeMillis());
        currentConfig.put("_metadata", metadata);

        // 使用智能版本控制 - 只有在配置真正变化时才创建新版本
        String userId = SecurityUtils.getCurrentUserId();
        String description = "删除服务实例: " + instanceId;
        saveAsNewVersionIfChanged(currentConfig, description, userId);
        
        refreshRuntimeConfig();

        logger.info("实例 {} 删除成功", instanceId);
    }



    // ==================== 批量操作 ====================
    /**
     * 批量更新配置（自动保存为新版本）
     *
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
     * 批量更新服务实例（只创建一个版本）
     * 这个方法解决了多次调用问题，将多个实例操作合并为一次版本创建
     *
     * @param serviceType 服务类型
     * @param operations  实例操作列表
     */
    @SuppressWarnings("unchecked")
    public void batchUpdateServiceInstances(String serviceType, List<InstanceOperation> operations) {
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
            switch (operation.getType()) {
                case ADD:
                    addInstanceToList(instances, operation.getInstanceConfig(), operationDetails);
                    break;
                case UPDATE:
                    updateInstanceInList(instances, operation.getInstanceId(), operation.getInstanceConfig(), operationDetails);
                    break;
                case DELETE:
                    deleteInstanceFromList(instances, operation.getInstanceId(), operationDetails);
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
    private void addInstanceToList(List<Map<String, Object>> instances, ModelRouterProperties.ModelInstance instanceConfig, List<String> operationDetails) {
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
    private void updateInstanceInList(List<Map<String, Object>> instances, String instanceId, ModelRouterProperties.ModelInstance instanceConfig, List<String> operationDetails) {
        boolean found = false;

        for (int i = 0; i < instances.size(); i++) {
            Map<String, Object> instance = instances.get(i);
            String currentInstanceId = buildInstanceId(instance);
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
    private void deleteInstanceFromList(List<Map<String, Object>> instances, String instanceId, List<String> operationDetails) {
        boolean removed = instances.removeIf(instance -> instanceId.equals(buildInstanceId(instance)));

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
     * 根据模块名称和基础URL构建实例ID
     *
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
    private TraceConfig extractTraceConfig(Map<String, Object> config) {
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
     *
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
                org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig config
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
        org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig config
                = new org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig();

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
                    org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig.SamplingRule rule
                            = new org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig.SamplingRule();

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
                org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig.AdaptiveConfig adaptiveConfig
                        = new org.unreal.modelrouter.tracing.config.TracingConfiguration.SamplingConfig.AdaptiveConfig();

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

    // ==================== 新增方法 ====================

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

    // ==================== 审计日志功能 ====================

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
     * 记录版本删除审计日志
     *
     * @param deletedVersion 被删除的版本号
     */
    private void logVersionDeletion(int deletedVersion) {
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
                auditData.put("versionRange",
                        String.format("%d-%d", metadata.getInitialVersion(), metadata.getCurrentVersion()));
            }

            // 记录剩余可用版本
            List<Integer> remainingVersions = getAllVersions();
            remainingVersions.remove(Integer.valueOf(deletedVersion)); // 移除已删除的版本
            auditData.put("remainingVersions", remainingVersions);

            // 使用结构化日志记录审计信息
            logger.info("版本删除审计: {}", auditData);

        } catch (Exception e) {
            // 审计日志失败不应影响主业务流程
            logger.warn("记录版本删除审计日志失败: {}", e.getMessage());
        }
    }

    /**
     * 删除版本后更新版本范围
     *
     * @param metadata       配置元数据
     * @param deletedVersion 被删除的版本号
     */
    private void updateVersionRangeAfterDeletion(ConfigMetadata metadata, int deletedVersion) {
        try {
            // 获取实际存在的版本文件
            List<Integer> existingVersions = new ArrayList<>();

            // 检查从初始版本到当前版本范围内哪些版本实际存在
            for (int i = metadata.getInitialVersion(); i <= metadata.getCurrentVersion(); i++) {
                if (i != deletedVersion && storeManager.versionExists(CURRENT_KEY, i)) {
                    existingVersions.add(i);
                }
            }

            if (!existingVersions.isEmpty()) {
                // 更新版本范围为实际存在的版本范围
                Collections.sort(existingVersions);
                int newInitialVersion = existingVersions.get(0);
                int newCurrentVersion = existingVersions.get(existingVersions.size() - 1);

                // 如果删除的是当前版本，需要将当前版本设置为最新的存在版本
                if (deletedVersion == metadata.getCurrentVersion()) {
                    metadata.setCurrentVersion(newCurrentVersion);
                    logger.info("删除的是当前版本，已将当前版本更新为: {}", newCurrentVersion);
                }

                // 如果删除的是初始版本，需要更新初始版本
                if (deletedVersion == metadata.getInitialVersion()) {
                    metadata.setInitialVersion(newInitialVersion);
                    logger.info("删除的是初始版本，已将初始版本更新为: {}", newInitialVersion);
                }

                logger.debug("版本范围已更新: {} - {}", metadata.getInitialVersion(), metadata.getCurrentVersion());
            } else {
                logger.warn("删除版本 {} 后没有剩余版本，这不应该发生", deletedVersion);
            }

        } catch (Exception e) {
            logger.error("更新版本范围失败", e);
        }
    }

    /**
     * 应用版本后更新当前版本状态
     *
     * @param appliedVersion 应用的版本号
     */
    private void updateCurrentVersionAfterApply(int appliedVersion) {
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

    /**
     * 实例操作类型
     */
    public enum InstanceOperationType {
        ADD, UPDATE, DELETE
    }

    /**
     * 实例操作定义
     */
    public static class InstanceOperation {
        private final InstanceOperationType type;
        private final String instanceId;
        private final ModelRouterProperties.ModelInstance instanceConfig;

        public InstanceOperation(InstanceOperationType type, String instanceId, ModelRouterProperties.ModelInstance instanceConfig) {
            this.type = type;
            this.instanceId = instanceId;
            this.instanceConfig = instanceConfig;
        }

        // Getters
        public InstanceOperationType getType() {
            return type;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public ModelRouterProperties.ModelInstance getInstanceConfig() {
            return instanceConfig;
        }
    }
}
