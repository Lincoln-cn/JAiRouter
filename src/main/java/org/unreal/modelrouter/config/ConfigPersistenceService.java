package org.unreal.modelrouter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.config.event.ConfigurationChangedEvent;
import org.unreal.modelrouter.store.ReactiveVersionedStoreManager;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 配置持久化服务
 * 负责配置的保存、加载和应用，并发布配置变更事件
 */
@Service
public class ConfigPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigPersistenceService.class);
    private static final String CURRENT_KEY = "model-router-config";

    private final ReactiveVersionedStoreManager storeManager;
    private final ApplicationEventPublisher eventPublisher;
    private final VersionControlService versionControlService;

    public ConfigPersistenceService(ReactiveVersionedStoreManager storeManager,
                                    ApplicationEventPublisher eventPublisher,
                                    VersionControlService versionControlService) {
        this.storeManager = storeManager;
        this.eventPublisher = eventPublisher;
        this.versionControlService = versionControlService;
    }

    /**
     * 获取当前配置
     */
    public Mono<Map<String, Object>> getCurrentConfig() {
        return storeManager.getConfig(CURRENT_KEY)
                .doOnSuccess(config -> logger.debug("获取当前配置成功"))
                .doOnError(e -> logger.error("获取当前配置失败", e));
    }

    /**
     * 保存配置（不创建新版本）
     */
    public Mono<Void> saveConfig(Map<String, Object> config) {
        return storeManager.saveConfig(CURRENT_KEY, config)
                .doOnSuccess(v -> logger.debug("配置保存成功"))
                .doOnError(e -> logger.error("配置保存失败", e));
    }

    /**
     * 保存配置并创建新版本
     */
    public Mono<Integer> saveConfigWithVersion(Map<String, Object> config, String description, String userId) {
        return versionControlService.createNewVersion(config, description, userId)
                .flatMap(version ->
                        storeManager.saveConfig(CURRENT_KEY, config)
                                .then(publishConfigChangedEvent(config, version, description, userId))
                                .thenReturn(version)
                )
                .doOnSuccess(version -> logger.info("配置已保存为新版本: {}", version))
                .doOnError(e -> logger.error("保存配置新版本失败", e));
    }

    /**
     * 应用指定版本的配置
     */
    public Mono<Void> applyVersion(int version, String userId) {
        return versionControlService.versionExists(version)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new IllegalArgumentException(
                                String.format("版本 %d 不存在", version)));
                    }
                    return versionControlService.getVersionConfig(version);
                })
                .flatMap(config -> {
                    if (config == null || config.isEmpty()) {
                        return Mono.error(new IllegalStateException(
                                String.format("无法读取版本 %d 的配置内容", version)));
                    }
                    return storeManager.saveConfig(CURRENT_KEY, config)
                            .then(publishConfigChangedEvent(config, version, "应用版本 " + version, userId));
                })
                .doOnSuccess(v -> logger.info("成功应用配置版本: {}", version))
                .doOnError(e -> logger.error("应用配置版本 {} 失败", version, e));
    }

    /**
     * 发布配置变更事件
     */
    private Mono<Void> publishConfigChangedEvent(Map<String, Object> config, Integer version,
                                                  String description, String userId) {
        return Mono.fromRunnable(() -> {
            ConfigurationChangedEvent event = new ConfigurationChangedEvent(
                    this,
                    CURRENT_KEY,
                    config,
                    version,
                    ConfigurationChangedEvent.ChangeType.UPDATE,
                    userId != null ? userId : "system"
            );
            eventPublisher.publishEvent(event);
            logger.debug("已发布配置变更事件，版本: {}", version);
        });
    }

    /**
     * 检查配置是否存在
     */
    public Mono<Boolean> configExists() {
        return storeManager.exists(CURRENT_KEY);
    }

    /**
     * 删除当前配置
     */
    public Mono<Void> deleteConfig() {
        return storeManager.deleteConfig(CURRENT_KEY)
                .doOnSuccess(v -> logger.info("配置已删除"))
                .doOnError(e -> logger.error("删除配置失败", e));
    }
}
