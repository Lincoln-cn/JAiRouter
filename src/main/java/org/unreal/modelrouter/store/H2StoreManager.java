package org.unreal.modelrouter.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.store.entity.ConfigEntity;
import org.unreal.modelrouter.store.repository.ConfigRepository;
import org.unreal.modelrouter.util.JacksonHelper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * H2 数据库存储管理器
 */
@RequiredArgsConstructor
public class H2StoreManager extends BaseStoreManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(H2StoreManager.class);

    private final ConfigRepository configRepository;

    @Override
    protected void doSaveConfig(String key, Map<String, Object> config) {
        try {
            String configValue = JacksonHelper.getObjectMapper().writeValueAsString(config);
            
            // 获取当前最新版本号
            Integer latestVersion = configRepository.findLatestByConfigKey(key)
                    .map(ConfigEntity::getVersion)
                    .blockOptional()
                    .orElse(0);
            
            int newVersion = latestVersion + 1;
            
            // 将所有旧版本标记为非最新
            configRepository.markAllAsNotLatest(key).block();
            
            // 保存新版本
            ConfigEntity entity = ConfigEntity.builder()
                    .configKey(key)
                    .configValue(configValue)
                    .version(newVersion)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .isLatest(true)
                    .build();
            
            configRepository.save(entity).block();
            LOGGER.info("Saved config for key: {} with version: {}", key, newVersion);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize config for key: " + key, e);
            throw new RuntimeException("Failed to save config", e);
        }
    }

    @Override
    protected Map<String, Object> doGetConfig(String key) {
        try {
            return configRepository.findLatestByConfigKey(key)
                    .map(entity -> {
                        try {
                            return JacksonHelper.getObjectMapper().readValue(
                                    entity.getConfigValue(),
                                    new TypeReference<Map<String, Object>>() {}
                            );
                        } catch (JsonProcessingException e) {
                            LOGGER.error("Failed to deserialize config for key: " + key, e);
                            throw new RuntimeException("Failed to read config", e);
                        }
                    })
                    .blockOptional()
                    .orElse(null);
        } catch (Exception e) {
            LOGGER.error("Failed to get config for key: " + key, e);
            return null;
        }
    }

    @Override
    protected void doDeleteConfig(String key) {
        configRepository.deleteAllByConfigKey(key).block();
        LOGGER.info("Deleted all versions of config for key: {}", key);
    }

    @Override
    protected boolean doExists(String key) {
        return Boolean.TRUE.equals(configRepository.existsByConfigKey(key).block());
    }

    @Override
    protected void doUpdateConfig(String key, Map<String, Object> config) {
        doSaveConfig(key, config);
    }

    @Override
    public Iterable<String> getAllKeys() {
        return configRepository.findAllLatestConfigKeys()
                .collectList()
                .block();
    }

    @Override
    public void saveConfigVersion(String key, Map<String, Object> config, int version) {
        if (config == null || config.isEmpty()) {
            return;
        }
        
        try {
            String configValue = JacksonHelper.getObjectMapper().writeValueAsString(config);
            
            ConfigEntity entity = ConfigEntity.builder()
                    .configKey(key)
                    .configValue(configValue)
                    .version(version)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .isLatest(false)
                    .build();
            
            configRepository.save(entity).block();
            LOGGER.info("Saved config version for key: {} with version: {}", key, version);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to save config version for key: " + key + ", version: " + version, e);
        }
    }

    @Override
    public List<Integer> getConfigVersions(String key) {
        return configRepository.findAllByConfigKey(key)
                .map(ConfigEntity::getVersion)
                .collectList()
                .blockOptional()
                .orElse(new ArrayList<>());
    }

    @Override
    public Map<String, Object> getConfigByVersion(String key, int version) {
        try {
            return configRepository.findByConfigKeyAndVersion(key, version)
                    .map(entity -> {
                        try {
                            return JacksonHelper.getObjectMapper().readValue(
                                    entity.getConfigValue(),
                                    new TypeReference<Map<String, Object>>() {}
                            );
                        } catch (JsonProcessingException e) {
                            LOGGER.error("Failed to deserialize config for key: " + key + ", version: " + version, e);
                            return null;
                        }
                    })
                    .blockOptional()
                    .orElse(null);
        } catch (Exception e) {
            LOGGER.error("Failed to get config version for key: " + key + ", version: " + version, e);
            return null;
        }
    }

    @Override
    public void deleteConfigVersion(String key, int version) {
        configRepository.deleteByConfigKeyAndVersion(key, version).block();
        LOGGER.info("Deleted config version for key: {}, version: {}", key, version);
    }

    @Override
    public boolean versionExists(String key, int version) {
        return Boolean.TRUE.equals(
                configRepository.existsByConfigKeyAndVersion(key, version).block()
        );
    }

    @Override
    public LocalDateTime getVersionCreatedTime(String key, int version) {
        return configRepository.findByConfigKeyAndVersion(key, version)
                .map(ConfigEntity::getCreatedAt)
                .blockOptional()
                .orElse(null);
    }

    @Override
    public Map<String, Object> getLatestConfig(String configKey) {
        return doGetConfig(configKey);
    }
}
