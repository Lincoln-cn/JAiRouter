package org.unreal.modelrouter.config.version;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 版本生成上下文
 * 封装版本生成所需的所有信息
 */
@Data
@Builder
public class VersionContext {

    /**
     * 配置键
     */
    private final String configKey;

    /**
     * 当前版本号
     */
    private final int currentVersion;

    /**
     * 所有历史版本号列表
     */
    private final List<Integer> existingVersions;

    /**
     * 版本总数
     */
    private final int totalVersions;

    /**
     * 操作描述
     */
    private final String operation;

    /**
     * 操作者ID
     */
    private final String operatorId;

    /**
     * 检查版本号是否已存在
     *
     * @param version 待检查的版本号
     * @return true 如果版本号已存在
     */
    public boolean versionExists(final int version) {
        return existingVersions != null && existingVersions.contains(version);
    }

    /**
     * 获取最大版本号
     *
     * @return 最大版本号，如果没有版本则返回 0
     */
    public int getMaxVersion() {
        if (existingVersions == null || existingVersions.isEmpty()) {
            return 0;
        }
        return existingVersions.stream()
                .max(Integer::compareTo)
                .orElse(0);
    }
}
