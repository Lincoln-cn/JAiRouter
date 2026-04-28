package org.unreal.modelrouter.config.version.diff;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.persistence.store.StoreManager;

import java.util.*;

/**
 * 版本对比服务
 * 提供两个配置版本之间的差异比较功能
 */
@Service
public class VersionDiffService {

    private static final Logger logger = LoggerFactory.getLogger(VersionDiffService.class);

    private static final String CONFIG_KEY = "model-router-config";

    private final StoreManager storeManager;

    public VersionDiffService(final StoreManager storeManager) {
        this.storeManager = storeManager;
    }

    /**
     * 比较两个版本之间的差异
     *
     * @param sourceVersion 源版本号（较旧版本）
     * @param targetVersion 目标版本号（较新版本）
     * @return 差异结果
     */
    public ConfigDiff compareVersions(final int sourceVersion, final int targetVersion) {
        logger.info("开始比较配置版本: {} vs {}", sourceVersion, targetVersion);

        // 读取两个版本的配置
        Map<String, Object> sourceConfig = loadVersionConfig(sourceVersion);
        Map<String, Object> targetConfig = loadVersionConfig(targetVersion);

        if (sourceConfig == null) {
            throw new IllegalArgumentException("源版本不存在: " + sourceVersion);
        }
        if (targetConfig == null) {
            throw new IllegalArgumentException("目标版本不存在: " + targetVersion);
        }

        // 执行深度比较
        ConfigDiff diff = deepCompare(sourceConfig, targetConfig, sourceVersion, targetVersion);

        logger.info("版本比较完成: {} vs {}，共发现 {} 处差异",
                sourceVersion, targetVersion, diff.getTotalChanges());

        return diff;
    }

    /**
     * 比较指定版本与当前配置（未保存的草稿）的差异
     *
     * @param baseVersion 基准版本号
     * @param currentConfig 当前配置（可能未保存）
     * @return 差异结果
     */
    public ConfigDiff compareWithCurrent(final int baseVersion, final Map<String, Object> currentConfig) {
        logger.info("比较版本 {} 与当前未保存配置", baseVersion);

        Map<String, Object> baseConfig = loadVersionConfig(baseVersion);
        if (baseConfig == null) {
            throw new IllegalArgumentException("基准版本不存在: " + baseVersion);
        }

        return deepCompare(baseConfig, currentConfig, baseVersion, 0);
    }

    /**
     * 获取版本配置
     *
     * @param version 版本号，0 表示获取当前活跃配置
     * @return 配置内容
     */
    private Map<String, Object> loadVersionConfig(final int version) {
        if (version == 0) {
            return storeManager.getConfig(CONFIG_KEY);
        }
        return storeManager.getConfigByVersion(CONFIG_KEY, version);
    }

    /**
     * 深度比较两个配置对象
     *
     * @param source 源配置
     * @param target 目标配置
     * @param sourceVersion 源版本号
     * @param targetVersion 目标版本号
     * @return 差异结果
     */
    private ConfigDiff deepCompare(final Map<String, Object> source,
                                   final Map<String, Object> target,
                                   final int sourceVersion,
                                   final int targetVersion) {

        ConfigDiff.ConfigDiffBuilder diffBuilder = ConfigDiff.builder()
                .sourceVersion(sourceVersion)
                .targetVersion(targetVersion);

        List<ConfigDiff.DiffItem> added = new ArrayList<>();
        List<ConfigDiff.DiffItem> removed = new ArrayList<>();
        List<ConfigDiff.DiffItem> modified = new ArrayList<>();

        // 收集所有键（包括嵌套路径）
        Set<String> sourcePaths = collectPaths(source, "");
        Set<String> targetPaths = collectPaths(target, "");

        // 找出新增的键
        for (String path : targetPaths) {
            if (!sourcePaths.contains(path)) {
                Object value = getValueByPath(target, path);
                added.add(ConfigDiff.DiffItem.builder()
                        .path(path)
                        .oldValue(null)
                        .newValue(value)
                        .changeType(ConfigDiff.DiffItem.ChangeType.ADDED)
                        .valueType(getValueType(value))
                        .build());
            }
        }

        // 找出删除的键
        for (String path : sourcePaths) {
            if (!targetPaths.contains(path)) {
                Object value = getValueByPath(source, path);
                removed.add(ConfigDiff.DiffItem.builder()
                        .path(path)
                        .oldValue(value)
                        .newValue(null)
                        .changeType(ConfigDiff.DiffItem.ChangeType.REMOVED)
                        .valueType(getValueType(value))
                        .build());
            }
        }

        // 找出修改的键
        for (String path : sourcePaths) {
            if (targetPaths.contains(path)) {
                Object sourceValue = getValueByPath(source, path);
                Object targetValue = getValueByPath(target, path);

                if (!Objects.equals(sourceValue, targetValue)) {
                    modified.add(ConfigDiff.DiffItem.builder()
                            .path(path)
                            .oldValue(sourceValue)
                            .newValue(targetValue)
                            .changeType(ConfigDiff.DiffItem.ChangeType.MODIFIED)
                            .valueType(getValueType(targetValue))
                            .build());
                }
            }
        }

        // 计算未变化的项数
        int unchangedCount = sourcePaths.size() - removed.size() - modified.size();

        return diffBuilder
                .added(added)
                .removed(removed)
                .modified(modified)
                .unchangedCount(unchangedCount)
                .build();
    }

    /**
     * 递归收集所有配置路径
     *
     * @param config 配置对象
     * @param prefix 路径前缀
     * @return 所有路径集合
     */
    @SuppressWarnings("unchecked")
    private Set<String> collectPaths(final Map<String, Object> config, final String prefix) {
        Set<String> paths = new HashSet<>();

        if (config == null) {
            return paths;
        }

        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String fullPath = prefix.isEmpty() ? key : prefix + "." + key;

            paths.add(fullPath);

            // 递归处理嵌套 Map
            if (value instanceof Map) {
                paths.addAll(collectPaths((Map<String, Object>) value, fullPath));
            }
        }

        return paths;
    }

    /**
     * 根据路径获取值
     *
     * @param config 配置对象
     * @param path 路径，如 "services.openai.url"
     * @return 值
     */
    @SuppressWarnings("unchecked")
    private Object getValueByPath(final Map<String, Object> config, final String path) {
        if (config == null || path == null || path.isEmpty()) {
            return null;
        }

        String[] parts = path.split("\\.");
        Object current = config;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
                if (current == null) {
                    return null;
                }
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * 获取值类型描述
     *
     * @param value 值
     * @return 类型描述
     */
    private String getValueType(final Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map) {
            return "object";
        }
        if (value instanceof List) {
            return "array[" + ((List<?>) value).size() + "]";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        return value.getClass().getSimpleName();
    }
}
