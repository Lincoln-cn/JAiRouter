package org.unreal.modelrouter.store;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的配置存储实现
 * 主要用于测试或临时存储场景
 */
public class MemoryStoreManager extends BaseStoreManager {

    private final Map<String, Map<String, Object>> storage = new HashMap<>();
    private final Map<String, Map<Integer, Map<String, Object>>> versionStorage = new ConcurrentHashMap<>();

    /**
     * 保存配置信息
     * @param key 配置键
     * @param config 配置内容
     */
    @Override
    protected void doSaveConfig(final String key, final Map<String, Object> config) {
        storage.put(key, new HashMap<>(config));
    }

    /**
     * 获取配置信息
     * @param key 配置键
     * @return 配置内容
     */
    @Override
    protected Map<String, Object> doGetConfig(final String key) {
        return storage.get(key);
    }

    /**
     * 删除配置信息
     * @param key 配置键
     */
    @Override
    protected void doDeleteConfig(final String key) {
        storage.remove(key);
    }

    /**
     * 检查配置是否存在
     * @param key 配置键
     * @return 是否存在
     */
    @Override
    protected boolean doExists(final String key) {
        return storage.containsKey(key);
    }

    /**
     * 更新配置信息
     * @param key 配置键
     * @param config 配置内容
     */
    @Override
    protected void doUpdateConfig(final String key, final Map<String, Object> config) {
        Map<String, Object> existing = storage.get(key);
        if (existing != null) {
            existing.putAll(config);
        } else {
            storage.put(key, new HashMap<>(config));
        }
    }

    /**
     * 获取所有配置键
     * @return 所有配置键的集合
     */
    @Override
    public Iterable<String> getAllKeys() {
        return new HashSet<>(storage.keySet());
    }

    /**
     * 清空所有配置（仅用于测试）
     */
    public void clear() {
        storage.clear();
    }

    /**
     * 保存配置的版本
     * @param key 配置键
     * @param config 配置内容
     * @param version 版本号
     */
    @Override
    public void saveConfigVersion(String key, Map<String, Object> config, int version) {
        versionStorage.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                     .put(version, new HashMap<>(config));
    }

    /**
     * 获取配置的所有版本号
     * @param key 配置键
     * @return 版本号列表
     */
    @Override
    public List<Integer> getConfigVersions(String key) {
        if (versionStorage.containsKey(key)) {
            List<Integer> versions = new ArrayList<>(versionStorage.get(key).keySet());
            Collections.sort(versions);
            return versions;
        }
        return new ArrayList<>();
    }

    /**
     * 获取指定版本的配置
     * @param key 配置键
     * @param version 版本号
     * @return 配置内容
     */
    @Override
    public Map<String, Object> getConfigByVersion(String key, int version) {
        if (versionStorage.containsKey(key) && versionStorage.get(key).containsKey(version)) {
            return new HashMap<>(versionStorage.get(key).get(version));
        }
        return null;
    }

    /**
     * 删除指定版本的配置
     * @param key 配置键
     * @param version 版本号
     */
    @Override
    public void deleteConfigVersion(String key, int version) {
        if (versionStorage.containsKey(key)) {
            versionStorage.get(key).remove(version);
        }
    }
}
