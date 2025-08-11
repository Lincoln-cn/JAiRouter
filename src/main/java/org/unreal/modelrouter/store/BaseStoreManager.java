package org.unreal.modelrouter.store;

import java.util.Map;

/**
 * StoreManager的抽象实现类
 * 提供基本的实现框架
 */
public abstract class BaseStoreManager implements StoreManager {

    /**
     * 保存配置
     * @param key 配置键
     * @param config 配置内容
     */
    @Override
    public void saveConfig(final String key, final Map<String, Object> config) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        doSaveConfig(key, config);
    }

    /**
     * 获取配置
     * @param key 配置键
     * @return 配置内容
     */
    @Override
    public Map<String, Object> getConfig(final String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        return doGetConfig(key);
    }

    /**
     * 删除配置
     * @param key 配置键
     */
    @Override
    public void deleteConfig(final String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        doDeleteConfig(key);
    }

    /**
     * 检查配置是否存在
     * @param key 配置键
     * @return 是否存在
     */
    @Override
    public boolean exists(final String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        return doExists(key);
    }

    /**
     * 更新配置
     * @param key 配置键
     * @param config 配置内容
     */
    @Override
    public void updateConfig(final String key, final Map<String, Object> config) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        doUpdateConfig(key, config);
    }

    /**
     * 实际保存配置的抽象方法
     * @param key 配置键
     * @param config 配置内容
     */
    protected abstract void doSaveConfig(String key, Map<String, Object> config);

    /**
     * 实际获取配置的抽象方法
     * @param key 配置键
     * @return 配置内容
     */
    protected abstract Map<String, Object> doGetConfig(String key);

    /**
     * 实际删除配置的抽象方法
     * @param key 配置键
     */
    protected abstract void doDeleteConfig(String key);

    /**
     * 实际检查配置是否存在的抽象方法
     * @param key 配置键
     * @return 是否存在
     */
    protected abstract boolean doExists(String key);

    /**
     * 实际更新配置的抽象方法
     * @param key 配置键
     * @param config 配置内容
     */
    protected abstract void doUpdateConfig(String key, Map<String, Object> config);

    /**
     * 获取所有配置键
     * @return 配置键列表
     */
    @Override
    public abstract Iterable<String> getAllKeys();
}
