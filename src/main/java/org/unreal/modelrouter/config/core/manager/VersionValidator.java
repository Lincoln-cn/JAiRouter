package org.unreal.modelrouter.config.core.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.version.ConfigMetadata;

import java.util.List;
import java.util.Map;

/**
 * 配置版本验证器
 *
 * 负责版本操作的验证逻辑，包括版本存在性检查、删除前验证、配置内容验证等
 * 从 ConfigVersionManager 中提取，遵循单一职责原则
 *
 * @author AI Assistant
 * @since v2.27.0
 */
@Component
public class VersionValidator {

    private static final Logger logger = LoggerFactory.getLogger(VersionValidator.class);

    /**
     * 验证指定版本是否存在
     *
     * @param version  版本号
     * @param metadata 配置元数据
     * @return 如果版本存在返回 true，否则返回 false
     */
    public boolean versionExists(final int version, final ConfigMetadata metadata) {
        if (version <= 0) {
            return false;
        }

        if (metadata != null && metadata.getExistingVersions() != null) {
            return metadata.getExistingVersions().contains(version);
        }

        return false;
    }

    /**
     * 验证版本存在性，如果不存在则抛出异常
     *
     * @param version        版本号
     * @param metadata       配置元数据
     * @param allVersions    所有可用版本列表
     * @throws IllegalArgumentException 如果版本不存在
     */
    public void validateExists(final int version, final ConfigMetadata metadata,
                                final List<Integer> allVersions) {
        if (!versionExists(version, metadata)) {
            // 尝试从所有版本列表中再次验证
            if (allVersions != null && allVersions.contains(version)) {
                return; // 版本存在于列表中
            }

            String availableVersions = allVersions != null ? allVersions.toString() : "[]";
            String message = String.format("版本 %d 不存在。可用版本：%s", version, availableVersions);
            logger.warn("版本验证失败：{}", message);
            throw new IllegalArgumentException(message);
        }
        logger.debug("版本 {} 存在性验证通过", version);
    }

    /**
     * 验证是否可以删除指定版本
     *
     * 检查条件：
     * 1. 版本必须存在
     * 2. 不能删除当前正在使用的版本
     * 3. 系统至少保留一个版本
     *
     * @param version     要删除的版本号
     * @param metadata    配置元数据
     * @param allVersions 所有可用版本列表
     * @throws IllegalArgumentException 如果版本不存在
     * @throws IllegalStateException    如果违反删除约束
     */
    public void validateCanDelete(final int version, final ConfigMetadata metadata,
                                   final List<Integer> allVersions) {
        // 1. 验证版本存在性
        validateExists(version, metadata, allVersions);

        // 2. 检查是否为当前版本，禁止删除当前版本
        if (metadata != null && version == metadata.getCurrentVersion()) {
            String message = String.format("不能删除当前版本 %d。请先应用其他版本后再删除此版本", version);
            logger.warn("版本删除验证失败：{}", message);
            throw new IllegalStateException(message);
        }

        // 3. 验证删除前的完整性检查：系统至少保留一个版本
        if (allVersions != null && allVersions.size() <= 1) {
            String message = "不能删除最后一个版本，系统至少需要保留一个配置版本";
            logger.warn("版本删除验证失败：{}", message);
            throw new IllegalStateException(message);
        }

        logger.debug("版本 {} 删除验证通过", version);
    }

    /**
     * 验证配置内容不为空
     *
     * @param config  配置内容
     * @param version 版本号（用于错误信息）
     * @throws IllegalStateException 如果配置为空或无效
     */
    public void validateConfigNotEmpty(final Map<String, Object> config, final int version) {
        if (config == null || config.isEmpty()) {
            String message = String.format("无法读取版本 %d 的配置内容，配置文件可能已损坏", version);
            logger.warn("配置内容验证失败：{}", message);
            throw new IllegalStateException(message);
        }
        logger.debug("版本 {} 配置内容验证通过，包含 {} 个顶级配置项", version, config.size());
    }
}