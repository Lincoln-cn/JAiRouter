package org.unreal.modelrouter.store;

import org.unreal.modelrouter.entity.ConfigMetadata;
import org.unreal.modelrouter.entity.VersionInfo;

import java.util.List;
import java.util.Map;

/**
 * 配置版本管理接口 提供清晰的配置生命周期管理
 */
public interface ConfigVersionManager {

    /**
     * 初始化配置（仅在首次启动或配置不存在时调用）
     *
     * @param key 配置键
     * @param config 初始配置内容
     * @param description 初始化描述
     * @return 初始版本号（通常为1）
     */
    int initializeConfig(String key, Map<String, Object> config, String description);

    /**
     * 更新配置（运行时调用，自动创建新版本）
     *
     * @param key 配置键
     * @param config 新配置内容
     * @param description 变更描述
     * @param userId 操作用户
     * @return 新版本号，如果配置无变化则返回当前版本号
     */
    int updateConfig(String key, Map<String, Object> config, String description, String userId);

    /**
     * 获取最新配置
     *
     * @param key 配置键
     * @return 最新版本的配置内容
     */
    Map<String, Object> getLatestConfig(String key);

    /**
     * 获取指定版本配置
     *
     * @param key 配置键
     * @param version 版本号
     * @return 指定版本的配置内容
     */
    Map<String, Object> getConfigByVersion(String key, int version);


    /**
     * 获取配置元数据
     *
     * @param key 配置键
     * @return 配置元数据
     */
    ConfigMetadata getConfigMetadata(String key);

    /**
     * 获取配置变更历史
     *
     * @param key 配置键
     * @param limit 限制数量
     * @return 版本历史列表
     */
    List<VersionInfo> getVersionHistory(String key, int limit);

    /**
     * 检查配置是否已初始化
     *
     * @param key 配置键
     * @return 是否已初始化
     */
    boolean isConfigInitialized(String key);

    /**
     * 清理旧版本（保留指定数量的最新版本）
     *
     * @param key 配置键
     * @param keepVersions 保留的版本数量
     * @return 清理的版本数量
     */
    int cleanupOldVersions(String key, int keepVersions);


}
