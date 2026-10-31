package org.unreal.modelrouter.config.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.persistence.store.StoreManager;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 配置版本号与历史管理。
 *
 * <p>#876：本类此前是「进程内自增计数器 + 什么都不做的回滚」的空壳 —— 重启后版本号从 1
 * 重新开始，与库里已有的历史版本号撞号；{@code rollbackToVersion} 只打一行日志就把「当前
 * 配置」原样返回，调用方会以为回滚成功。现在版本号一律由存储层的持久化历史推导
 * （{@link StoreManager#getConfigVersions}），写版本号走
 * {@link StoreManager#saveConfigVersion}，回滚是真的把历史快照追加成新版本。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionControlService {

    /** 配置在存储层的主键；与 {@code ConfigPersistenceService} 共用同一个 key。 */
    static final String CURRENT_KEY = "model-router-config";

    private final StoreManager storeManager;

    /**
     * 创建新版本：版本号 = 持久化历史里的最大值 + 1（无历史时从 1 开始），
     * 并把这份配置按该版本号写入历史（标记为最新）。
     *
     * @return 新版本号
     */
    public Integer createNewVersion(final Map<String, Object> config, final String description,
            final String userId) {
        final int newVersion = nextVersion();
        storeManager.saveConfigVersion(CURRENT_KEY, config, newVersion);
        log.info("创建新版本: {}, 描述: {}, 用户: {}", newVersion, description, userId);
        return newVersion;
    }

    /**
     * 当前版本号 = 持久化历史里的最大值（无历史时返回 0）。
     */
    public Integer getCurrentVersion() {
        return maxVersion();
    }

    /**
     * 回滚到指定版本：把该版本的历史快照**追加**为新版本（不删除历史）。
     *
     * @param version 历史版本号
     * @return 被恢复的配置快照
     * @throws IllegalArgumentException 版本号为空或该版本不存在
     */
    public Map<String, Object> rollbackToVersion(final Integer version) {
        if (version == null) {
            throw new IllegalArgumentException("回滚版本号不能为空");
        }
        final Map<String, Object> snapshot = storeManager.getConfigByVersion(CURRENT_KEY, version);
        if (snapshot == null) {
            throw new IllegalArgumentException("配置版本不存在: " + version);
        }
        final int restoredAs = nextVersion();
        storeManager.saveConfigVersion(CURRENT_KEY, snapshot, restoredAs);
        log.info("回滚到版本: {}，已作为新版本 {} 追加", version, restoredAs);
        return snapshot;
    }

    private int nextVersion() {
        return maxVersion() + 1;
    }

    private int maxVersion() {
        final List<Integer> versions = storeManager.getConfigVersions(CURRENT_KEY);
        if (versions == null || versions.isEmpty()) {
            return 0;
        }
        return versions.stream()
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }
}
