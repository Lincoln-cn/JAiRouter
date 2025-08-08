package org.unreal.modelrouter.store;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * 基于内存的配置存储实现
 * 主要用于测试或临时存储场景
 */
public class MemoryStoreManager extends BaseStoreManager {

    private final Map<String, Map<String, Object>> storage = new HashMap<>();

    @Override
    protected void doSaveConfig(String key, Map<String, Object> config) {
        storage.put(key, new HashMap<>(config));
    }

    @Override
    protected Map<String, Object> doGetConfig(String key) {
        return storage.get(key);
    }

    @Override
    protected void doDeleteConfig(String key) {
        storage.remove(key);
    }

    @Override
    protected boolean doExists(String key) {
        return storage.containsKey(key);
    }

    @Override
    protected void doUpdateConfig(String key, Map<String, Object> config) {
        Map<String, Object> existing = storage.get(key);
        if (existing != null) {
            existing.putAll(config);
        } else {
            storage.put(key, new HashMap<>(config));
        }
    }

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
}
