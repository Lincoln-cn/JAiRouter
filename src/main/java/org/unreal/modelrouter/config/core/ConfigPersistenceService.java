package org.unreal.modelrouter.config.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.config.core.event.ConfigurationChangedEvent;
import org.unreal.modelrouter.jpa.JpaStoreManager;

import java.util.Map;

/**
 * 配置持久化服务 (JPA 版本)
 * v1.5.x: 破坏性修改，使用 JPA 替代 R2DBC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigPersistenceService {

    private static final String CURRENT_KEY = "model-router-config";

    private final JpaStoreManager storeManager;
    private final ApplicationEventPublisher eventPublisher;
    private final VersionControlService versionControlService;

    /**
     * 获取当前配置
     */
    public Map<String, Object> getCurrentConfig() {
        try {
            Map<String, Object> config = storeManager.getConfig(CURRENT_KEY);
            log.debug("获取当前配置成功");
            return config;
        } catch (Exception e) {
            log.error("获取当前配置失败", e);
            throw new RuntimeException("获取配置失败", e);
        }
    }

    /**
     * 保存配置（不创建新版本）
     */
    @Transactional
    public void saveConfig(final Map<String, Object> config) {
        storeManager.saveConfig(CURRENT_KEY, config);
        log.debug("配置保存成功");
    }

    /**
     * 保存配置并创建新版本
     */
    @Transactional
    public Integer saveConfigWithVersion(final Map<String, Object> config, final String description, final String userId) {
        Integer version = versionControlService.createNewVersion(config, description, userId);
        storeManager.saveConfig(CURRENT_KEY, config);
        publishConfigChangedEvent(config, version, description, userId);
        return version;
    }

    /**
     * 发布配置变更事件
     */
    private void publishConfigChangedEvent(final Map<String, Object> config, final Integer version,
                                          final String description, final String userId) {
        try {
            eventPublisher.publishEvent(new ConfigurationChangedEvent(
                    this, CURRENT_KEY, config, version,
                    org.unreal.modelrouter.config.core.event.ConfigurationChangedEvent.ChangeType.UPDATE,
                    userId));
            log.debug("配置变更事件已发布，版本: {}", version);
        } catch (Exception e) {
            log.error("发布配置变更事件失败", e);
        }
    }
}
