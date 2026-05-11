package org.unreal.modelrouter.config.core.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.version.ConfigMetadata;
import org.unreal.modelrouter.config.version.VersionInfo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 版本元数据管理器
 *
 * 负责配置版本元数据和版本历史记录的管理
 * 从 ConfigVersionManager 中提取，遵循单一职责原则
 *
 * @author AI Assistant
 * @since v2.28.0
 */
@Component
public class VersionMetadataManager {

    private static final Logger logger = LoggerFactory.getLogger(VersionMetadataManager.class);

    private static final String CURRENT_KEY = "model-router-config";

    // 版本元数据映射表
    private final Map<String, ConfigMetadata> configMetadataMap = new HashMap<>();

    // 版本历史记录映射表
    private final Map<String, List<VersionInfo>> versionHistoryMap = new ConcurrentHashMap<>();

    /**
     * 获取或创建配置元数据
     *
     * @param key 配置键
     * @return 配置元数据
     */
    public ConfigMetadata getOrCreateMetadata(final String key) {
        return configMetadataMap.computeIfAbsent(key, k -> {
            ConfigMetadata newMetadata = new ConfigMetadata();
            newMetadata.setConfigKey(k);
            newMetadata.setCreatedAt(LocalDateTime.now());
            logger.debug("创建新的配置元数据：{}", k);
            return newMetadata;
        });
    }

    /**
     * 获取配置元数据
     *
     * @param key 配置键
     * @return 配置元数据，如果不存在返回 null
     */
    public ConfigMetadata getMetadata(final String key) {
        return configMetadataMap.get(key);
    }

    /**
     * 保存元数据
     *
     * @param key      配置键
     * @param metadata 元数据
     */
    public void saveMetadata(final String key, final ConfigMetadata metadata) {
        configMetadataMap.put(key, metadata);
        logger.debug("元数据已保存：{}", key);
    }

    /**
     * 获取版本历史记录
     *
     * @param key 配置键
     * @return 版本历史列表
     */
    public List<VersionInfo> getVersionHistory(final String key) {
        List<VersionInfo> history = versionHistoryMap.get(key);
        return history != null ? new ArrayList<>(history) : new ArrayList<>();
    }

    /**
     * 获取版本历史记录（原始引用）
     *
     * @param key 配置键
     * @return 版本历史列表（可修改）
     */
    public List<VersionInfo> getVersionHistoryForUpdate(final String key) {
        return versionHistoryMap.computeIfAbsent(key, k -> new ArrayList<>());
    }

    /**
     * 获取版本详情
     *
     * @param key     配置键
     * @param version 版本号
     * @return 版本详情，如果不存在返回 null
     */
    public VersionInfo getVersionDetail(final String key, final int version) {
        List<VersionInfo> history = versionHistoryMap.get(key);
        if (history != null) {
            return history.stream()
                    .filter(info -> info.getVersion() == version)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * 保存版本历史
     *
     * @param key            配置键
     * @param versionHistory 版本历史
     */
    public void saveVersionHistory(final String key, final List<VersionInfo> versionHistory) {
        versionHistoryMap.put(key, versionHistory);
        logger.debug("版本历史已保存：{}，版本数：{}", key, versionHistory.size());
    }

    /**
     * 添加版本到历史记录
     *
     * @param key         配置键
     * @param versionInfo 版本信息
     */
    public void addVersionToHistory(final String key, final VersionInfo versionInfo) {
        List<VersionInfo> history = versionHistoryMap.computeIfAbsent(key, k -> new ArrayList<>());
        history.add(versionInfo);
        logger.debug("版本已添加到历史：{} - 版本 {}", key, versionInfo.getVersion());
    }

    /**
     * 从历史记录中移除版本
     *
     * @param key     配置键
     * @param version 版本号
     * @return 是否移除成功
     */
    public boolean removeVersionFromHistory(final String key, final int version) {
        List<VersionInfo> history = versionHistoryMap.get(key);
        if (history != null) {
            boolean removed = history.removeIf(info -> info.getVersion() == version);
            if (removed) {
                logger.debug("版本已从历史中移除：{} - 版本 {}", key, version);
            }
            return removed;
        }
        return false;
    }

    /**
     * 初始化元数据
     *
     * @param key            配置键
     * @param initialVersion 初始版本号
     * @return 创建的元数据
     */
    public ConfigMetadata initializeMetadata(final String key, final int initialVersion) {
        ConfigMetadata metadata = new ConfigMetadata();
        metadata.setConfigKey(key);
        metadata.setInitialVersion(initialVersion);
        metadata.setCurrentVersion(0);
        metadata.setCreatedAt(LocalDateTime.now());
        metadata.setTotalVersions(0);
        saveMetadata(key, metadata);
        logger.info("已初始化配置元数据：{}", key);
        return metadata;
    }

    /**
     * 更新元数据的版本信息
     *
     * @param key            配置键
     * @param newVersion     新版本号
     * @param userId         操作用户
     */
    public void updateMetadataVersion(final String key, final int newVersion, final String userId) {
        ConfigMetadata metadata = getMetadata(key);
        if (metadata != null) {
            metadata.setCurrentVersion(newVersion);
            metadata.setLastModified(LocalDateTime.now());
            metadata.setLastModifiedBy(userId != null ? userId : "system");
            metadata.setTotalVersions(metadata.getTotalVersions() + 1);
            metadata.addVersion(newVersion);
            saveMetadata(key, metadata);
        }
    }

    /**
     * 更新元数据的当前版本（用于版本应用）
     *
     * @param key     配置键
     * @param version 版本号
     * @param userId  操作用户
     */
    public void updateCurrentVersion(final String key, final int version, final String userId) {
        ConfigMetadata metadata = getMetadata(key);
        if (metadata != null) {
            metadata.setCurrentVersion(version);
            metadata.setLastModified(LocalDateTime.now());
            metadata.setLastModifiedBy(userId);
            saveMetadata(key, metadata);
        }
    }

    /**
     * 减少元数据的版本计数
     *
     * @param key     配置键
     * @param version 要移除的版本号
     * @param userId  操作用户
     */
    public void decrementVersionCount(final String key, final int version, final String userId) {
        ConfigMetadata metadata = getMetadata(key);
        if (metadata != null) {
            metadata.setTotalVersions(Math.max(0, metadata.getTotalVersions() - 1));
            metadata.setLastModified(LocalDateTime.now());
            metadata.setLastModifiedBy(userId);
            metadata.removeVersion(version);
            saveMetadata(key, metadata);
        }
    }

    /**
     * 获取当前配置键
     *
     * @return 当前配置键
     */
    public String getCurrentKey() {
        return CURRENT_KEY;
    }
}