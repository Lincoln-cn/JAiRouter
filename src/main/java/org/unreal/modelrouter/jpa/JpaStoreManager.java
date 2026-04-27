package org.unreal.modelrouter.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.jpa.entity.ConfigEntity;
import org.unreal.modelrouter.jpa.repository.ConfigRepository;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.util.JacksonHelper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA 存储管理器
 * v1.5.1: 完全替代 R2DBC 的 H2StoreManager
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaStoreManager implements StoreManager {

    private final ConfigRepository configRepository;

    @Override
    @Transactional
    public void saveConfig(final String key,final Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return;
        }
        try {
            String configValue = JacksonHelper.getObjectMapper().writeValueAsString(config);

            // 检查是否已存在配置（不自动创建新版本，只更新当前活跃配置）
            Optional<ConfigEntity> existingEntity = configRepository.findFirstByConfigKeyAndIsLatestTrue(key);
            
            if (existingEntity.isPresent()) {
                // 更新现有配置（不创建新版本）
                ConfigEntity entity = existingEntity.get();
                entity.setConfigValue(configValue);
                entity.setUpdatedAt(LocalDateTime.now());
                configRepository.save(entity);
                log.info("Updated config for key: {} with version: {}", key, entity.getVersion());
            } else {
                // 创建新配置
                ConfigEntity entity = ConfigEntity.builder()
                        .configKey(key)
                        .configValue(configValue)
                        .version(1)
                        .isLatest(true)
                        .build();
                configRepository.save(entity);
                log.info("Created new config for key: {} with version: 1", key);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize config for key: {}", key, e);
            throw new RuntimeException("Failed to save config", e);
        }
    }

    @Override
    public Map<String, Object> getConfig(final String key) {
        return configRepository.findFirstByConfigKeyAndIsLatestTrue(key)
                .map(this::deserializeConfig)
                .orElse(null);
    }

    @Override
    @Transactional
    public void deleteConfig(final String key) {
        configRepository.deleteAllByConfigKey(key);
        log.info("Deleted all versions of config for key: {}", key);
    }

    @Override
    public Iterable<String> getAllKeys() {
        return configRepository.findAllLatestConfigKeys();
    }

    @Override
    public boolean exists(final String key) {
        return configRepository.existsByConfigKeyAndIsLatestTrue(key);
    }

    @Override
    public void updateConfig(final String key,final Map<String, Object> config) {
        // 更新操作与保存相同
        saveConfig(key, config);
    }

    @Override
    @Transactional
    public void saveConfigVersion(final String key,final Map<String, Object> config,final int version) {
        if (config == null || config.isEmpty()) {
            return;
        }
        try {
            String configValue = JacksonHelper.getObjectMapper().writeValueAsString(config);
            
            // v1.5.4: 标记所有现有版本为非最新，新版本标记为最新
            // 这样 saveConfig 调用时能找到这个版本进行更新，而不是创建新版本
            configRepository.markAllAsNotLatest(key);
            
            ConfigEntity entity = ConfigEntity.builder()
                    .configKey(key)
                    .configValue(configValue)
                    .version(version)
                    .isLatest(true)
                    .build();
            
            configRepository.save(entity);
            log.info("Saved config version for key: {} with version: {} (marked as latest)", key, version);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize config for key: {} version: {}", key, version, e);
            throw new RuntimeException("Failed to save config version", e);
        }
    }

    @Override
    public List<Integer> getConfigVersions(final String key) {
        return configRepository.findAllVersionsByConfigKey(key);
    }

    @Override
    public Map<String, Object> getConfigByVersion(final String key,final int version) {
        return configRepository.findByConfigKeyAndVersion(key, version)
                .map(this::deserializeConfig)
                .orElse(null);
    }

    @Override
    @Transactional
    public void deleteConfigVersion(final String key,final int version) {
        configRepository.deleteByConfigKeyAndVersion(key, version);
        log.info("Deleted config version for key: {}, version: {}", key, version);
    }

    @Override
    public boolean versionExists(final String key, int version) {
        return configRepository.existsByConfigKeyAndVersion(key, version);
    }

    @Override
    public String getVersionFilePath(final String key,final int version) {
        // JPA 存储不基于文件，返回标识信息
        return "jpa://" + key + "/v" + version;
    }

    @Override
    public LocalDateTime getVersionCreatedTime(final String key,final int version) {
        return configRepository.findByConfigKeyAndVersion(key, version)
                .map(ConfigEntity::getCreatedAt)
                .orElse(null);
    }

    @Override
    public Map<String, Object> getLatestConfig(final String configKey) {
        return getConfig(configKey);
    }

    private Map<String, Object> deserializeConfig(final ConfigEntity entity) {
        try {
            return JacksonHelper.getObjectMapper().readValue(
                    entity.getConfigValue(),
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize config for key: {} version: {}", 
                    entity.getConfigKey(), entity.getVersion(), e);
            throw new RuntimeException("Failed to deserialize config", e);
        }
    }
}
