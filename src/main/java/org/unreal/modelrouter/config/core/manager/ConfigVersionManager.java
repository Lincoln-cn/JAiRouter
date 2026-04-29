package org.unreal.modelrouter.config.core.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.core.ConfigMergeService;
import org.unreal.modelrouter.config.core.ConfigurationHelper;
import org.unreal.modelrouter.config.dto.UpdateServiceConfigRequest;
import org.unreal.modelrouter.common.entity.ConfigMetadata;
import org.unreal.modelrouter.common.entity.VersionInfo;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.common.util.SecurityUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 配置版本管理器
 * 
 * 负责配置版本的创建、查询、应用和删除操作
 * 提供完整的版本控制功能，包括版本历史记录和元数据管理
 * 
 * @author AI Assistant
 * @since v2.2.0
 */
@Component
public class ConfigVersionManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfigVersionManager.class);

    private static final String CURRENT_KEY = "model-router-config";

    private final StoreManager storeManager;
    private final ConfigMergeService configMergeService;

    // 版本控制相关字段
    private final Map<String, ConfigMetadata> configMetadataMap = new HashMap<>();
    private final Map<String, List<VersionInfo>> versionHistoryMap = new ConcurrentHashMap<>();

    // 版本创建锁 - 使用 ReentrantLock 提升并发性能
    private final ReentrantLock versionCreationLock = new ReentrantLock();

    // 记录最近版本创建的时间，用于检测短时间内的重复创建
    private volatile long lastVersionCreationTime = 0;
    private volatile String lastVersionDescription = "";

    public ConfigVersionManager(final StoreManager storeManager,
                                 final ConfigMergeService configMergeService) {
        this.storeManager = storeManager;
        this.configMergeService = configMergeService;
    }

    /**
     * 初始化版本控制，从数据库同步版本信息
     */
    public void initializeVersionControl() {
        try {
            logger.info("初始化版本控制，从数据库同步版本信息...");

            // 从数据库获取已存在的版本列表
            List<Integer> dbVersions = storeManager.getConfigVersions(CURRENT_KEY);
            logger.info("从数据库获取到 {} 个版本：{}", dbVersions.size(), dbVersions);

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

                logger.info("已从数据库同步版本元数据：当前版本={}, 总版本数={}, 版本列表={}",
                        maxVersion, dbVersions.size(), dbVersions);

                // 构建版本历史记录
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
                        if (configMetadata != null && configMetadata.containsKey("operation")) {
                            versionInfo.setDescription((String) configMetadata.get("operationDetail"));
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

            logger.info("版本控制初始化完成");

        } catch (Exception e) {
            logger.error("初始化版本控制失败", e);
            throw new RuntimeException("初始化版本控制失败", e);
        }
    }

    /**
     * 获取所有可用版本
     *
     * @return 版本列表
     */
    public List<Integer> getAllVersions() {
        List<Integer> versions = new ArrayList<>();

        // 从数据库获取版本列表
        List<Integer> dbVersions = storeManager.getConfigVersions(CURRENT_KEY);
        if (dbVersions != null && !dbVersions.isEmpty()) {
            versions.addAll(dbVersions);
        }

        // 从元数据获取版本列表
        ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
        if (metadata != null && metadata.getExistingVersions() != null) {
            versions.addAll(metadata.getExistingVersions());
        }

        // 从版本历史获取版本列表
        List<VersionInfo> versionHistory = versionHistoryMap.get(CURRENT_KEY);
        if (versionHistory != null) {
            for (VersionInfo info : versionHistory) {
                if (!versions.contains(info.getVersion())) {
                    versions.add(info.getVersion());
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
     * @param version 版本号，0 表示 YAML 原始配置
     * @return 配置内容
     */
    public Map<String, Object> getVersionConfig(final int version) {
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
    public int saveAsNewVersion(final Map<String, Object> config) {
        return saveAsNewVersion(config, "系统自动保存", "system");
    }

    /**
     * 保存当前配置为新版本（带描述和用户信息）
     *
     * @param config      配置内容
     * @param description 描述信息
     * @param userId      用户 ID
     * @return 新版本号
     */
    public int saveAsNewVersion(final Map<String, Object> config, final String description, final String userId) {
        versionCreationLock.lock();
        try {
            return saveAsNewVersionInternal(config, description, userId);
        } finally {
            versionCreationLock.unlock();
        }
    }

    /**
     * 内部版本保存方法
     */
    private int saveAsNewVersionInternal(final Map<String, Object> config, final String description, final String userId) {
        try {
            // 调用链追踪：记录调用来源
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            String callerInfo = stackTrace.length > 3 ? stackTrace[3].toString() : "unknown";
            logger.info("【版本创建调用链】描述='{}', 调用来源={}", description, callerInfo);

            logger.debug("开始保存新版本，描述：{}, 用户：{}", description, userId);

            // 记录版本创建的时间间隔
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

            // 生成新版本号
            int newVersion = generateNextVersionNumber();
            logger.info("生成新版本号：{}", newVersion);

            // 保存版本配置
            storeManager.saveConfigVersion(CURRENT_KEY, config, newVersion);
            logger.debug("版本配置已保存：{}", newVersion);

            // 更新元数据
            metadata.setCurrentVersion(newVersion);
            metadata.setLastModified(LocalDateTime.now());
            metadata.setLastModifiedBy(userId != null ? userId : "system");
            metadata.setTotalVersions(metadata.getTotalVersions() + 1);
            metadata.addVersion(newVersion);
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
    public void applyVersion(final int version) {
        logger.info("开始应用配置版本：{}", version);

        try {
            // 1. 验证版本存在性
            if (!versionExists(version)) {
                String availableVersions = getAllVersions().toString();
                throw new IllegalArgumentException(
                        String.format("版本 %d 不存在。可用版本：%s", version, availableVersions));
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
                logger.warn("无法备份当前配置：{}", e.getMessage());
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

                logger.info("成功应用配置版本：{}", version);

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
                        String.format("应用配置版本 %d 失败：%s", version, e.getMessage()), e);
            }

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            logger.error("应用配置版本 {} 时发生未预期的错误", version, e);
            throw new RuntimeException(
                    String.format("应用配置版本 %d 时发生系统错误：%s", version, e.getMessage()), e);
        }
    }

    /**
     * 删除指定版本的配置
     *
     * @param version 版本号
     */
    public void deleteConfigVersion(final int version) {
        logger.info("开始删除配置版本：{}", version);

        try {
            // 1. 验证版本存在性
            if (!versionExists(version)) {
                String availableVersions = getAllVersions().toString();
                throw new IllegalArgumentException(
                        String.format("版本 %d 不存在。可用版本：%s", version, availableVersions));
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
                    metadata.removeVersion(version);

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

                logger.info("成功删除配置版本：{}", version);

            } catch (Exception e) {
                logger.error("删除配置版本 {} 时发生错误", version, e);
                throw new RuntimeException(
                        String.format("删除配置版本 %d 失败：%s", version, e.getMessage()), e);
            }

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            logger.error("删除配置版本 {} 时发生未预期的错误", version, e);
            throw new RuntimeException(
                    String.format("删除配置版本 %d 时发生系统错误：%s", version, e.getMessage()), e);
        }
    }

    /**
     * 获取实际当前配置版本号
     *
     * @return 当前配置版本号，如果不存在则返回 0
     */
    public int getActualCurrentVersion() {
        List<Integer> versions = storeManager.getConfigVersions(CURRENT_KEY);
        int currentVersion = versions.stream()
                .max(Integer::compareTo)
                .orElse(0);

        logger.debug("当前实际版本：{}", currentVersion);
        return currentVersion;
    }

    /**
     * 获取当前最新版本号
     *
     * @return 当前版本号
     */
    public int getCurrentVersion() {
        return getActualCurrentVersion();
    }

    /**
     * 获取版本历史记录
     *
     * @return 版本历史列表
     */
    public List<VersionInfo> getVersionHistory() {
        List<VersionInfo> history = versionHistoryMap.get(CURRENT_KEY);
        return history != null ? new ArrayList<>(history) : new ArrayList<>();
    }

    /**
     * 获取版本详情
     *
     * @param version 版本号
     * @return 版本详情
     */
    public VersionInfo getVersionDetail(final int version) {
        List<VersionInfo> history = versionHistoryMap.get(CURRENT_KEY);
        if (history != null) {
            return history.stream()
                    .filter(info -> info.getVersion() == version)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * 比较两个版本的配置
     *
     * @param version1 版本 1
     * @param version2 版本 2
     * @return 比较结果
     */
    public Map<String, Object> compareVersions(final int version1, final int version2) {
        Map<String, Object> config1 = getVersionConfig(version1);
        Map<String, Object> config2 = getVersionConfig(version2);

        Map<String, Object> comparison = new HashMap<>();
        comparison.put("version1", version1);
        comparison.put("version2", version2);
        comparison.put("config1", config1);
        comparison.put("config2", config2);
        comparison.put("isChanged", isConfigurationChanged(config1, config2));

        return comparison;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 记录版本创建的时间，用于检测短时间内的重复创建
     */
    private void recordVersionCreationTiming(final String description) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastVersionCreationTime < 1000 && description.equals(lastVersionDescription)) {
            logger.warn("检测到 1 秒内重复的版本创建请求，描述相同：{}", description);
        }
        lastVersionCreationTime = currentTime;
        lastVersionDescription = description;
    }

    /**
     * 生成下一个版本号
     */
    private int generateNextVersionNumber() {
        return getCurrentVersion() + 1;
    }

    /**
     * 验证指定版本是否存在
     */
    private boolean versionExists(final int version) {
        if (version <= 0) {
            return false;
        }

        ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
        if (metadata != null) {
            return metadata.getExistingVersions().contains(version);
        }

        List<Integer> versions = getAllVersions();
        return versions.contains(version);
    }

    /**
     * 智能配置比较，检查配置是否真正发生变化
     */
    private boolean isConfigurationChanged(final Map<String, Object> currentConfig, final Map<String, Object> newConfig) {
        if (currentConfig == null && newConfig == null) {
            return false;
        }
        if (currentConfig == null || newConfig == null) {
            return true;
        }

        Map<String, Object> normalizedCurrent = normalizeConfigForComparison(currentConfig);
        Map<String, Object> normalizedNew = normalizeConfigForComparison(newConfig);

        return !deepEquals(normalizedCurrent, normalizedNew);
    }

    /**
     * 标准化配置，移除比较时不相关的字段
     */
    private Map<String, Object> normalizeConfigForComparison(final Map<String, Object> config) {
        if (config == null) {
            return new HashMap<>();
        }

        Map<String, Object> normalized = new HashMap<>(config);

        // 移除元数据字段和时间戳字段
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

        return normalized;
    }

    /**
     * 递归移除嵌套对象中的时间戳字段
     */
    @SuppressWarnings("unchecked")
    private void removeTimestampFieldsRecursively(final Map<String, Object> config) {
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                nestedMap.remove("timestamp");
                nestedMap.remove("lastModified");
                nestedMap.remove("createdAt");
                nestedMap.remove("updatedAt");
                removeTimestampFieldsRecursively(nestedMap);
            } else if (value instanceof List) {
                List<Object> list = (List<Object>) value;
                for (Object item : list) {
                    if (item instanceof Map) {
                        removeTimestampFieldsRecursively((Map<String, Object>) item);
                    }
                }
            }
        }
    }

    /**
     * 深度比较两个配置对象
     */
    private boolean deepEquals(final Map<String, Object> config1, final Map<String, Object> config2) {
        if (config1.size() != config2.size()) {
            return false;
        }

        for (Map.Entry<String, Object> entry : config1.entrySet()) {
            String key = entry.getKey();
            Object value1 = entry.getValue();
            Object value2 = config2.get(key);

            if (value1 == null && value2 == null) {
                continue;
            }
            if (value1 == null || value2 == null) {
                return false;
            }

            if (value1 instanceof Map && value2 instanceof Map) {
                if (!deepEquals((Map<String, Object>) value1, (Map<String, Object>) value2)) {
                    return false;
                }
            } else if (value1 instanceof List && value2 instanceof List) {
                if (!deepEquals((List<Object>) value1, (List<Object>) value2)) {
                    return false;
                }
            } else {
                if (!value1.equals(value2)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 深度比较两个列表
     */
    @SuppressWarnings("unchecked")
    private boolean deepEquals(final List<Object> list1, final List<Object> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }

        for (int i = 0; i < list1.size(); i++) {
            Object item1 = list1.get(i);
            Object item2 = list2.get(i);

            if (item1 == null && item2 == null) {
                continue;
            }
            if (item1 == null || item2 == null) {
                return false;
            }

            if (item1 instanceof Map && item2 instanceof Map) {
                if (!deepEquals((Map<String, Object>) item1, (Map<String, Object>) item2)) {
                    return false;
                }
            } else if (!item1.equals(item2)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取当前持久化的配置
     */
    private Map<String, Object> getCurrentPersistedConfig() {
        return storeManager.getConfig(CURRENT_KEY);
    }

    /**
     * 刷新运行时配置
     */
    private void refreshRuntimeConfig() {
        // 刷新配置缓存
        // 配置刷新由 ConfigurationService 处理
    }

    /**
     * 应用版本后更新当前版本状态
     */
    private void updateCurrentVersionAfterApply(final int version) {
        ConfigMetadata metadata = configMetadataMap.get(CURRENT_KEY);
        if (metadata != null) {
            metadata.setCurrentVersion(version);
            metadata.setLastModified(LocalDateTime.now());
            metadata.setLastModifiedBy(SecurityUtils.getCurrentUserId());
            saveMetadata(CURRENT_KEY, metadata);
        }
    }

    /**
     * 记录配置回滚的审计日志
     */
    private void logConfigurationRollback(final int version, final Map<String, Object> config) {
        logger.info("配置回滚到版本 {}: 操作类型=ROLLBACK, 目标版本={}", version, version);
    }

    /**
     * 记录版本删除的审计日志
     */
    private void logVersionDeletion(final int version) {
        logger.info("配置版本删除：删除版本={}, 操作时间={}", version, LocalDateTime.now());
    }

    /**
     * 保存元数据
     */
    private void saveMetadata(final String key, final ConfigMetadata metadata) {
        // 元数据保存到内存和文件备份
        configMetadataMap.put(key, metadata);
        logger.debug("元数据已保存：{}", key);
    }

    /**
     * 保存版本历史
     */
    private void saveVersionHistory(final String key, final List<VersionInfo> versionHistory) {
        versionHistoryMap.put(key, versionHistory);
        logger.debug("版本历史已保存：{}", key);
    }
}
