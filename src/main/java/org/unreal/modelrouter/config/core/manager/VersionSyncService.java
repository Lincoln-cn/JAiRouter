package org.unreal.modelrouter.config.core.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.version.ConfigMetadata;
import org.unreal.modelrouter.config.version.VersionInfo;
import org.unreal.modelrouter.persistence.store.StoreManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 版本同步服务
 *
 * 负责从数据库同步版本信息到内存，处理版本控制初始化逻辑
 * 从 ConfigVersionManager 中提取，遵循单一职责原则
 *
 * @author AI Assistant
 * @since v2.28.0
 */
@Component
public class VersionSyncService {

    private static final Logger logger = LoggerFactory.getLogger(VersionSyncService.class);

    private final StoreManager storeManager;
    private final VersionMetadataManager metadataManager;

    public VersionSyncService(final StoreManager storeManager,
                              final VersionMetadataManager metadataManager) {
        this.storeManager = storeManager;
        this.metadataManager = metadataManager;
    }

    /**
     * 从数据库同步版本信息
     *
     * @param configKey 配置键
     */
    public void syncFromDatabase(final String configKey) {
        try {
            logger.info("初始化版本控制，从数据库同步版本信息...");

            List<Integer> dbVersions = storeManager.getConfigVersions(configKey);
            logger.info("从数据库获取到 {} 个版本：{}", dbVersions.size(), dbVersions);

            ConfigMetadata metadata = metadataManager.getOrCreateMetadata(configKey);

            if (!dbVersions.isEmpty()) {
                syncExistingVersions(configKey, metadata, dbVersions);
            } else {
                initializeEmptyMetadata(configKey, metadata);
            }

            metadataManager.saveMetadata(configKey, metadata);
            logger.info("版本控制初始化完成");

        } catch (Exception e) {
            logger.error("初始化版本控制失败", e);
            throw new RuntimeException("初始化版本控制失败", e);
        }
    }

    /**
     * 同步已存在的版本信息
     */
    private void syncExistingVersions(final String configKey,
                                       final ConfigMetadata metadata,
                                       final List<Integer> dbVersions) {
        int maxVersion = dbVersions.stream().max(Integer::compareTo).orElse(0);
        int minVersion = dbVersions.stream().min(Integer::compareTo).orElse(1);

        metadata.setCurrentVersion(maxVersion);
        metadata.setInitialVersion(minVersion);
        metadata.setTotalVersions(dbVersions.size());
        metadata.setExistingVersions(new HashSet<>(dbVersions));
        metadata.setLastModified(LocalDateTime.now());
        metadata.setLastModifiedBy("system-sync");

        logger.info("已从数据库同步版本元数据：当前版本={}, 总版本数={}, 版本列表={}",
                maxVersion, dbVersions.size(), dbVersions);

        buildVersionHistory(configKey, dbVersions);
    }

    /**
     * 构建版本历史记录
     */
    private void buildVersionHistory(final String configKey, final List<Integer> dbVersions) {
        List<VersionInfo> versionHistory = new ArrayList<>();

        for (Integer version : dbVersions) {
            VersionInfo versionInfo = createVersionInfo(configKey, version);
            versionHistory.add(versionInfo);
        }

        metadataManager.saveVersionHistory(configKey, versionHistory);
    }

    /**
     * 创建版本信息对象
     */
    private VersionInfo createVersionInfo(final String configKey, final int version) {
        VersionInfo versionInfo = new VersionInfo();
        versionInfo.setVersion(version);
        versionInfo.setCreatedAt(storeManager.getVersionCreatedTime(configKey, version));
        versionInfo.setCreatedBy("system");
        versionInfo.setDescription("配置版本 " + version);
        versionInfo.setChangeType(version == 1 ? VersionInfo.ChangeType.INITIAL : VersionInfo.ChangeType.UPDATE);

        enrichVersionInfoFromConfig(configKey, version, versionInfo);

        return versionInfo;
    }

    /**
     * 从配置中丰富版本信息
     */
    @SuppressWarnings("unchecked")
    private void enrichVersionInfoFromConfig(final String configKey, final int version,
                                              final VersionInfo versionInfo) {
        Map<String, Object> config = storeManager.getConfigByVersion(configKey, version);
        if (config != null && config.containsKey("_metadata")) {
            Map<String, Object> configMetadata = (Map<String, Object>) config.get("_metadata");
            if (configMetadata != null && configMetadata.containsKey("operation")) {
                versionInfo.setDescription((String) configMetadata.get("operationDetail"));
            }
        }
    }

    /**
     * 初始化空元数据
     */
    private void initializeEmptyMetadata(final String configKey, final ConfigMetadata metadata) {
        metadata.setInitialVersion(1);
        metadata.setCurrentVersion(0);
        metadata.setTotalVersions(0);
        metadata.setExistingVersions(new HashSet<>());
        metadata.setLastModified(LocalDateTime.now());
        metadata.setLastModifiedBy("system-init");

        metadataManager.saveVersionHistory(configKey, new ArrayList<>());
        logger.info("数据库中没有版本记录，创建初始元数据");
    }
}