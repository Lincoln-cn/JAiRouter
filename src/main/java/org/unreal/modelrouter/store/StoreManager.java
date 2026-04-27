package org.unreal.modelrouter.store;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 存储管理器接口
 * v1.5.1: 简化为只支持基本配置操作
 */
public interface StoreManager {

    /**
     * 保存配置
     * @param key 配置键
     * @param config 配置内容
     */
    void saveConfig(String key, Map<String, Object> config);

    /**
     * 获取配置
     * @param key 配置键
     * @return 配置内容
     */
    Map<String, Object> getConfig(String key);

    /**
     * 删除配置
     * @param key 配置键
     */
    void deleteConfig(String key);

    /**
     * 获取所有配置键
     * @return 配置键列表
     */
    Iterable<String> getAllKeys();

    /**
     * 检查配置是否存在
     * @param key 配置键
     * @return 是否存在
     */
    boolean exists(String key);

    /**
     * 更新配置
     * @param key 配置键
     * @param config 配置内容
     */
    void updateConfig(String key, Map<String, Object> config);

    /**
     * 保存配置的特定版本
     * @param key 配置键
     * @param config 配置内容
     * @param version 版本号
     */
    default void saveConfigVersion(final String key,final Map<String, Object> config,final int version) {
        // 默认实现为空，子类可以覆盖
    }

    /**
     * 获取配置的所有版本号
     * @param key 配置键
     * @return 版本号列表
     */
    default List<Integer> getConfigVersions(final String key) {
        return new ArrayList<>();
    }

    /**
     * 获取指定版本的配置
     * @param key 配置键
     * @param version 版本号
     * @return 配置内容
     */
    default Map<String, Object> getConfigByVersion(final String key,final int version) {
        return null;
    }

    /**
     * 删除指定版本的配置
     * @param key 配置键
     * @param version 版本号
     */
    default void deleteConfigVersion(final String key,final int version) {
        // 默认实现为空
    }

    /**
     * 验证指定版本是否存在
     * @param key 配置键
     * @param version 版本号
     * @return 版本是否存在
     */
    default boolean versionExists(final String key, final int version) {
        return false;
    }

    /**
     * 获取指定版本的文件路径
     * @param key 配置键
     * @param version 版本号
     * @return 版本文件的实际路径
     */
    default String getVersionFilePath(final String key,final int version) {
        return null;
    }

    /**
     * 获取指定版本的创建时间
     * @param key 配置键
     * @param version 版本号
     * @return 版本创建时间
     */
    default LocalDateTime getVersionCreatedTime(final String key,final int version) {
        return null;
    }

    /**
     * 获取最新配置
     * @param configKey 配置键
     * @return 最新配置
     */
    Map<String, Object> getLatestConfig(String configKey);
}
