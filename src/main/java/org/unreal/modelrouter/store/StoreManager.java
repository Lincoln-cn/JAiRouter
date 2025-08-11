package org.unreal.modelrouter.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 配置持久化管理接口
 * 用于将初始化和后续通过web restful配置的数据进行持久化存储
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
     * 保存配置的版本
     * @param key 配置键
     * @param config 配置内容
     * @param version 版本号
     */
    default void saveConfigVersion(String key, Map<String, Object> config, int version) {
        // 默认实现为空，具体实现在子类中
    }

    /**
     * 获取配置的所有版本号
     * @param key 配置键
     * @return 版本号列表
     */
    default List<Integer> getConfigVersions(String key) {
        return new ArrayList<>();
    }

    /**
     * 获取指定版本的配置
     * @param key 配置键
     * @param version 版本号
     * @return 配置内容
     */
    default Map<String, Object> getConfigByVersion(String key, int version) {
        return null;
    }

    /**
     * 删除指定版本的配置
     * @param key 配置键
     * @param version 版本号
     */
    default void deleteConfigVersion(String key, int version) {
        // 默认实现为空
    }
}
