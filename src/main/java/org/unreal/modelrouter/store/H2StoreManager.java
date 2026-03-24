package org.unreal.modelrouter.store;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * H2 数据库存储管理器
 * 委托给 ReactiveH2StoreManager 实现，在边界处订阅
 */
@RequiredArgsConstructor
public class H2StoreManager extends BaseStoreManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(H2StoreManager.class);

    private final ReactiveH2StoreManager reactiveStoreManager;

    @Override
    protected void doSaveConfig(String key, Map<String, Object> config) {
        reactiveStoreManager.saveConfig(key, config).block();
        LOGGER.info("Saved config for key: {}", key);
    }

    @Override
    protected Map<String, Object> doGetConfig(String key) {
        return reactiveStoreManager.getConfig(key).block();
    }

    @Override
    protected void doDeleteConfig(String key) {
        reactiveStoreManager.deleteConfig(key).block();
        LOGGER.info("Deleted all versions of config for key: {}", key);
    }

    @Override
    protected boolean doExists(String key) {
        return Boolean.TRUE.equals(reactiveStoreManager.exists(key).block());
    }

    @Override
    protected void doUpdateConfig(String key, Map<String, Object> config) {
        doSaveConfig(key, config);
    }

    @Override
    public Iterable<String> getAllKeys() {
        return reactiveStoreManager.getAllKeys().collectList().block();
    }

    @Override
    public void saveConfigVersion(String key, Map<String, Object> config, int version) {
        if (config == null || config.isEmpty()) {
            return;
        }
        reactiveStoreManager.saveConfigVersion(key, config, version).block();
        LOGGER.info("Saved config version for key: {} with version: {}", key, version);
    }

    @Override
    public List<Integer> getConfigVersions(String key) {
        return reactiveStoreManager.getConfigVersions(key).collectList().block();
    }

    @Override
    public Map<String, Object> getConfigByVersion(String key, int version) {
        return reactiveStoreManager.getConfigByVersion(key, version).block();
    }

    @Override
    public void deleteConfigVersion(String key, int version) {
        reactiveStoreManager.deleteConfigVersion(key, version).block();
        LOGGER.info("Deleted config version for key: {}, version: {}", key, version);
    }

    @Override
    public boolean versionExists(String key, int version) {
        return Boolean.TRUE.equals(reactiveStoreManager.versionExists(key, version).block());
    }

    @Override
    public LocalDateTime getVersionCreatedTime(String key, int version) {
        return reactiveStoreManager.getVersionCreatedTime(key, version).block();
    }

    @Override
    public Map<String, Object> getLatestConfig(String configKey) {
        return doGetConfig(configKey);
    }
}
