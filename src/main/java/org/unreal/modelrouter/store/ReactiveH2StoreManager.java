package org.unreal.modelrouter.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.store.entity.ConfigEntity;
import org.unreal.modelrouter.store.repository.ConfigRepository;
import org.unreal.modelrouter.util.JacksonHelper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * H2 数据库响应式存储管理器
 * 纯响应式实现，所有操作返回 Mono/Flux，不阻塞线程
 */
@Component
@RequiredArgsConstructor
public class ReactiveH2StoreManager implements ReactiveVersionedStoreManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveH2StoreManager.class);

    private final ConfigRepository configRepository;

    @Override
    public Mono<Void> saveConfig(String key, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> JacksonHelper.getObjectMapper().writeValueAsString(config))
                .flatMap(configValue ->
                        configRepository.findLatestByConfigKey(key)
                                .map(ConfigEntity::getVersion)
                                .defaultIfEmpty(0)
                                .flatMap(latestVersion -> {
                                    int newVersion = latestVersion + 1;
                                    return configRepository.markAllAsNotLatest(key)
                                            .then(createConfigEntity(key, configValue, newVersion));
                                })
                )
                .doOnSuccess(entity -> LOGGER.info("Saved config for key: {} with version: {}", key, entity.getVersion()))
                .doOnError(e -> LOGGER.error("Failed to save config for key: {}", key, e))
                .then();
    }

    @Override
    public Mono<Map<String, Object>> getConfig(String key) {
        return configRepository.findLatestByConfigKey(key)
                .flatMap(this::deserializeConfig)
                .doOnError(e -> LOGGER.error("Failed to get config for key: {}", key, e));
    }

    @Override
    public Mono<Void> deleteConfig(String key) {
        return configRepository.deleteAllByConfigKey(key)
                .doOnSuccess(v -> LOGGER.info("Deleted all versions of config for key: {}", key))
                .doOnError(e -> LOGGER.error("Failed to delete config for key: {}", key, e));
    }

    @Override
    public Flux<String> getAllKeys() {
        return configRepository.findAllLatestConfigKeys()
                .doOnError(e -> LOGGER.error("Failed to get all config keys", e));
    }

    @Override
    public Mono<Boolean> exists(String key) {
        return configRepository.existsByConfigKey(key)
                .doOnError(e -> LOGGER.error("Failed to check existence for key: {}", key, e))
                .onErrorReturn(false);
    }

    @Override
    public Mono<Void> updateConfig(String key, Map<String, Object> config) {
        // 更新和保存使用相同的操作
        return saveConfig(key, config);
    }

    @Override
    public Mono<Map<String, Object>> getLatestConfig(String configKey) {
        return getConfig(configKey);
    }

    @Override
    public Mono<Void> saveConfigVersion(String key, Map<String, Object> config, int version) {
        if (config == null || config.isEmpty()) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> JacksonHelper.getObjectMapper().writeValueAsString(config))
                .flatMap(configValue -> createConfigEntity(key, configValue, version, false))
                .doOnSuccess(entity -> LOGGER.info("Saved config version for key: {} with version: {}", key, version))
                .doOnError(e -> LOGGER.error("Failed to save config version for key: {}, version: {}", key, version, e))
                .then();
    }

    @Override
    public Flux<Integer> getConfigVersions(String key) {
        return configRepository.findAllByConfigKey(key)
                .map(ConfigEntity::getVersion)
                .doOnError(e -> LOGGER.error("Failed to get config versions for key: {}", key, e));
    }

    @Override
    public Mono<Map<String, Object>> getConfigByVersion(String key, int version) {
        return configRepository.findByConfigKeyAndVersion(key, version)
                .flatMap(this::deserializeConfig)
                .doOnError(e -> LOGGER.error("Failed to get config by version for key: {}, version: {}", key, version, e));
    }

    @Override
    public Mono<Void> deleteConfigVersion(String key, int version) {
        return configRepository.deleteByConfigKeyAndVersion(key, version)
                .doOnSuccess(v -> LOGGER.info("Deleted config version for key: {}, version: {}", key, version))
                .doOnError(e -> LOGGER.error("Failed to delete config version for key: {}, version: {}", key, version, e));
    }

    @Override
    public Mono<Boolean> versionExists(String key, int version) {
        return configRepository.existsByConfigKeyAndVersion(key, version)
                .doOnError(e -> LOGGER.error("Failed to check version existence for key: {}, version: {}", key, version, e))
                .onErrorReturn(false);
    }

    @Override
    public Mono<String> getVersionFilePath(String key, int version) {
        // H2 存储不基于文件，返回标识信息
        return Mono.just("h2://" + key + "/v" + version);
    }

    @Override
    public Mono<LocalDateTime> getVersionCreatedTime(String key, int version) {
        return configRepository.findByConfigKeyAndVersion(key, version)
                .map(ConfigEntity::getCreatedAt)
                .doOnError(e -> LOGGER.error("Failed to get version created time for key: {}, version: {}", key, version, e));
    }

    private Mono<ConfigEntity> createConfigEntity(String key, String configValue, int version) {
        return createConfigEntity(key, configValue, version, true);
    }

    private Mono<ConfigEntity> createConfigEntity(String key, String configValue, int version, boolean isLatest) {
        ConfigEntity entity = ConfigEntity.builder()
                .configKey(key)
                .configValue(configValue)
                .version(version)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isLatest(isLatest)
                .build();
        return configRepository.save(entity);
    }

    private Mono<Map<String, Object>> deserializeConfig(ConfigEntity entity) {
        return Mono.fromCallable(() ->
                JacksonHelper.getObjectMapper().readValue(
                        entity.getConfigValue(),
                        new TypeReference<Map<String, Object>>() {}
                )
        );
    }
}
