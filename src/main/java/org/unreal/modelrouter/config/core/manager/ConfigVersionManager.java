package org.unreal.modelrouter.config.core.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.core.ConfigMergeService;
import org.unreal.modelrouter.config.version.ConfigMetadata;
import org.unreal.modelrouter.config.version.VersionInfo;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.common.util.SecurityUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final ConfigComparator configComparator;
    private final VersionValidator versionValidator;
    private final VersionMetadataManager metadataManager;
    private final VersionSyncService syncService;

    // 版本创建锁 - 使用 ReentrantLock 提升并发性能
    private final ReentrantLock versionCreationLock = new ReentrantLock();

    // 记录最近版本创建的时间，用于检测短时间内的重复创建
    private volatile long lastVersionCreationTime = 0;
    private volatile String lastVersionDescription = "";

    public ConfigVersionManager(final StoreManager storeManager,
                                 final ConfigMergeService configMergeService,
                                 final ConfigComparator configComparator,
                                 final VersionValidator versionValidator,
                                 final VersionMetadataManager metadataManager,
                                 final VersionSyncService syncService) {
        this.storeManager = storeManager;
        this.configMergeService = configMergeService;
        this.configComparator = configComparator;
        this.versionValidator = versionValidator;
        this.metadataManager = metadataManager;
        this.syncService = syncService;
    }

    /**
     * 初始化版本控制，从数据库同步版本信息
     */
    public void initializeVersionControl() {
        syncService.syncFromDatabase(CURRENT_KEY);
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
        ConfigMetadata metadata = metadataManager.getMetadata(CURRENT_KEY);
        if (metadata != null && metadata.getExistingVersions() != null) {
            versions.addAll(metadata.getExistingVersions());
        }

        // 从版本历史获取版本列表
        List<VersionInfo> versionHistory = metadataManager.getVersionHistory(CURRENT_KEY);
        for (VersionInfo info : versionHistory) {
            if (!versions.contains(info.getVersion())) {
                versions.add(info.getVersion());
            }
        }

        // 排序并去重
        return versions.stream().distinct().sorted().collect(Collectors.toList());
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
    private int saveAsNewVersionInternal(final Map<String, Object> config,
                                          final String description, final String userId) {
        try {
            // 调用链追踪：记录调用来源
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            String callerInfo = stackTrace.length > 3 ? stackTrace[3].toString() : "unknown";
            logger.info("【版本创建调用链】描述='{}', 调用来源={}", description, callerInfo);

            logger.debug("开始保存新版本，描述：{}, 用户：{}", description, userId);

            // 记录版本创建的时间间隔
            recordVersionCreationTiming(description);

            // 获取或创建配置元数据
            ConfigMetadata metadata = metadataManager.getOrCreateMetadata(CURRENT_KEY);
            if (metadata.getTotalVersions() == 0) {
                metadata.setInitialVersion(1);
                logger.info("创建新的配置元数据");
            }

            // 内联生成新版本号
            int newVersion = getCurrentVersion() + 1;
            logger.info("生成新版本号：{}", newVersion);

            // 保存版本配置
            storeManager.saveConfigVersion(CURRENT_KEY, config, newVersion);
            logger.debug("版本配置已保存：{}", newVersion);

            // 更新元数据
            String effectiveUserId = userId != null ? userId : "system";
            metadataManager.updateMetadataVersion(CURRENT_KEY, newVersion, effectiveUserId);
            logger.debug("元数据已更新");

            // 创建版本历史记录
            VersionInfo versionInfo = new VersionInfo();
            versionInfo.setVersion(newVersion);
            versionInfo.setCreatedAt(LocalDateTime.now());
            versionInfo.setCreatedBy(effectiveUserId);
            versionInfo.setDescription(description != null ? description : "配置更新");
            versionInfo.setChangeType(VersionInfo.ChangeType.UPDATE);

            metadataManager.addVersionToHistory(CURRENT_KEY, versionInfo);
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
            // 1. 获取元数据和版本列表
            ConfigMetadata metadata = metadataManager.getMetadata(CURRENT_KEY);
            List<Integer> allVersions = getAllVersions();

            // 2. 验证版本存在性（委托给 VersionValidator）
            versionValidator.validateExists(version, metadata, allVersions);

            // 3. 获取版本配置
            Map<String, Object> config = getVersionConfig(version);

            // 4. 验证配置内容不为空（委托给 VersionValidator）
            versionValidator.validateConfigNotEmpty(config, version);

            logger.debug("获取到版本 {} 的配置，包含 {} 个顶级配置项", version, config.size());

            // 5. 备份当前配置（用于错误恢复）- 内联调用
            Map<String, Object> backupConfig = null;
            try {
                backupConfig = storeManager.getConfig(CURRENT_KEY);
            } catch (Exception e) {
                logger.warn("无法备份当前配置：{}", e.getMessage());
            }

            // 6. 原子性应用配置
            try {
                storeManager.saveConfig(CURRENT_KEY, config);
                logger.debug("配置已成功应用到存储管理器");

                // 更新当前版本状态
                metadataManager.updateCurrentVersion(CURRENT_KEY, version, SecurityUtils.getCurrentUserId());

                logger.info("成功应用配置版本：{}", version);

            } catch (Exception e) {
                logger.error("应用配置版本 {} 时发生错误，尝试恢复", version, e);

                // 尝试恢复到备份配置
                if (backupConfig != null) {
                    try {
                        storeManager.saveConfig(CURRENT_KEY, backupConfig);
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
            // 1. 获取元数据和版本列表
            ConfigMetadata metadata = metadataManager.getMetadata(CURRENT_KEY);
            List<Integer> allVersions = getAllVersions();

            // 2. 验证是否可以删除（委托给 VersionValidator）
            versionValidator.validateCanDelete(version, metadata, allVersions);

            // 3. 执行删除操作
            try {
                // 删除版本文件
                storeManager.deleteConfigVersion(CURRENT_KEY, version);
                logger.debug("已从存储中删除版本 {} 的配置文件", version);

                // 4. 更新元数据和版本范围
                if (metadata != null) {
                    metadataManager.decrementVersionCount(CURRENT_KEY, version,
                            SecurityUtils.getCurrentUserId());
                    logger.debug("已更新配置元数据和版本范围");
                }

                // 5. 更新版本历史记录
                metadataManager.removeVersionFromHistory(CURRENT_KEY, version);

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
        return metadataManager.getVersionHistory(CURRENT_KEY);
    }

    /**
     * 获取版本详情
     *
     * @param version 版本号
     * @return 版本详情
     */
    public VersionInfo getVersionDetail(final int version) {
        return metadataManager.getVersionDetail(CURRENT_KEY, version);
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

        return configComparator.compareVersions(version1, version2, config1, config2);
    }

    /**
     * 清理版本元数据和历史记录
     * v2.28.0: 从 ConfigurationService 迁移
     */
    public void cleanVersion() {
        logger.info("开始清理版本元数据和历史记录");

        // 清理元数据
        ConfigMetadata metadata = metadataManager.getMetadata(CURRENT_KEY);
        if (metadata != null) {
            metadata.clean();
            metadataManager.saveMetadata(CURRENT_KEY, metadata);
            logger.debug("元数据已清理");
        }

        // 清理版本历史
        List<VersionInfo> versionHistory = metadataManager.getVersionHistoryForUpdate(CURRENT_KEY);
        versionHistory.clear();
        metadataManager.saveVersionHistory(CURRENT_KEY, versionHistory);
        logger.debug("版本历史已清理");

        logger.info("版本元数据和历史记录清理完成");
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
}