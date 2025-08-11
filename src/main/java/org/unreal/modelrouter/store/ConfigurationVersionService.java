package org.unreal.modelrouter.store;

// ... existing code ...
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.config.ConfigMergeService;
import org.unreal.modelrouter.config.ConfigurationService;
import org.unreal.modelrouter.store.StoreManager;

import java.util.List;
import java.util.Map;

/**
 * 配置版本管理服务
 * 提供配置版本控制和回滚功能
 */
@Service
public class ConfigurationVersionService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationVersionService.class);
    private static final String CONFIG_KEY = "model-router-config";

    private final StoreManager storeManager;
    private final ConfigurationService configurationService;
    private final ConfigMergeService configMergeService;

    @Autowired
    public ConfigurationVersionService(StoreManager storeManager,
                                       ConfigurationService configurationService,
                                       ConfigMergeService configMergeService) {
        this.storeManager = storeManager;
        this.configurationService = configurationService;
        this.configMergeService = configMergeService;
    }

    /**
     * 获取配置的所有版本号
     * @return 版本号列表
     */
    public List<Integer> getConfigVersions() {
        return storeManager.getConfigVersions(CONFIG_KEY);
    }

    /**
     * 获取指定版本的配置
     * @param version 版本号
     * @return 配置内容
     */
    public Map<String, Object> getConfigByVersion(int version) {
        return storeManager.getConfigByVersion(CONFIG_KEY, version);
    }

    /**
     * 回滚到指定版本的配置
     * @param version 版本号
     */
    public void rollbackToVersion(int version) {
        logger.info("开始回滚配置到版本: {}", version);

        Map<String, Object> versionConfig = storeManager.getConfigByVersion(CONFIG_KEY, version);
        if (versionConfig == null) {
            throw new IllegalArgumentException("指定的版本不存在: " + version);
        }

        // 保存回滚后的配置
        storeManager.saveConfig(CONFIG_KEY, versionConfig);

        // 刷新运行时配置
        configurationService.batchUpdateConfigurations(versionConfig);

        logger.info("配置已成功回滚到版本: {}", version);
    }

    /**
     * 删除指定版本的配置
     * @param version 版本号
     */
    public void deleteConfigVersion(int version) {
        logger.info("删除配置版本: {}", version);
        storeManager.deleteConfigVersion(CONFIG_KEY, version);
        logger.info("配置版本 {} 已删除", version);
    }

    /**
     * 获取当前配置版本
     * @return 当前版本号
     */
    public int getCurrentVersion() {
        List<Integer> versions = getConfigVersions();
        return versions.isEmpty() ? 0 : versions.get(versions.size() - 1);
    }
}
