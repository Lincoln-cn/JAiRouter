package org.unreal.modelrouter.config.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.persistence.jpa.JpaStoreManager;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 版本控制服务 (JPA 版本)
 * v1.5.x: 破坏性修改，使用 JPA 替代 R2DBC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionControlService {

    private static final String CURRENT_KEY = "model-router-config";
    private final AtomicInteger versionCounter = new AtomicInteger(0);

    private final JpaStoreManager storeManager;

    /**
     * 创建新版本
     */
    public Integer createNewVersion(final Map<String, Object> config, final String description, final String userId) {
        int newVersion = versionCounter.incrementAndGet();
        log.info("创建新版本: {}, 描述: {}, 用户: {}", newVersion, description, userId);
        return newVersion;
    }

    /**
     * 获取当前版本
     */
    public Integer getCurrentVersion() {
        return versionCounter.get();
    }

    /**
     * 回滚到指定版本
     */
    public Map<String, Object> rollbackToVersion(final Integer version) {
        log.info("回滚到版本: {}", version);
        // JPA 版本简化实现
        return storeManager.getConfig(CURRENT_KEY);
    }
}
