package org.unreal.modelrouter.config.core.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置比较器组件
 *
 * 负责配置内容的深度比较，包括：
 * - 标准化配置（移除时间戳等无关字段）
 * - 深度比较 Map 和 List 结构
 * - 版本配置比较
 *
 * @author AI Assistant
 * @since v2.26.0
 */
@Component
public class ConfigComparator {

    private static final Logger logger = LoggerFactory.getLogger(ConfigComparator.class);

    /**
     * 比较两个版本的配置
     *
     * @param version1 版本1
     * @param version2 版本2
     * @param config1  版本1的配置
     * @param config2  版本2的配置
     * @return 比较结果
     */
    public Map<String, Object> compareVersions(final int version1, final int version2,
                                               final Map<String, Object> config1,
                                               final Map<String, Object> config2) {
        Map<String, Object> comparison = new HashMap<>();
        comparison.put("version1", version1);
        comparison.put("version2", version2);
        comparison.put("config1", config1);
        comparison.put("config2", config2);
        comparison.put("isChanged", isChanged(config1, config2));

        logger.debug("配置比较完成：版本 {} vs {}, 是否变化={}", version1, version2, comparison.get("isChanged"));
        return comparison;
    }

    /**
     * 智能配置比较，检查配置是否真正发生变化
     *
     * @param currentConfig 当前配置
     * @param newConfig     新配置
     * @return true 如果配置发生变化，false 否则
     */
    public boolean isChanged(final Map<String, Object> currentConfig, final Map<String, Object> newConfig) {
        if (currentConfig == null && newConfig == null) {
            return false;
        }
        if (currentConfig == null || newConfig == null) {
            return true;
        }

        Map<String, Object> normalizedCurrent = normalize(currentConfig);
        Map<String, Object> normalizedNew = normalize(newConfig);

        return !deepEquals(normalizedCurrent, normalizedNew);
    }

    /**
     * 标准化配置，移除比较时不相关的字段
     *
     * @param config 原始配置
     * @return 标准化后的配置
     */
    public Map<String, Object> normalize(final Map<String, Object> config) {
        if (config == null) {
            return new HashMap<>();
        }

        Map<String, Object> normalized = new HashMap<>(config);

        // 移除元数据字段和时间戳字段
        normalized.remove("_metadata");
        normalized.remove("timestamp");
        normalized.remove("lastModified");
        normalized.remove("createdAt");
        normalized.remove("version");
        normalized.remove("versionInfo");
        normalized.remove("lastUpdated");
        normalized.remove("modifiedAt");
        normalized.remove("updatedAt");
        normalized.remove("saveTime");
        normalized.remove("createTime");

        // 递归移除嵌套对象中的时间戳字段
        removeTimestampFieldsRecursively(normalized);

        return normalized;
    }

    /**
     * 深度比较两个配置对象
     *
     * @param config1 配置1
     * @param config2 配置2
     * @return true 如果相等，false 否则
     */
    public boolean deepEquals(final Map<String, Object> config1, final Map<String, Object> config2) {
        if (config1.size() != config2.size()) {
            return false;
        }

        for (Map.Entry<String, Object> entry : config1.entrySet()) {
            String key = entry.getKey();
            Object value1 = entry.getValue();
            Object value2 = config2.get(key);

            if (value1 == null && value2 == null) {
                continue;
            }
            if (value1 == null || value2 == null) {
                return false;
            }

            if (value1 instanceof Map && value2 instanceof Map) {
                if (!deepEquals((Map<String, Object>) value1, (Map<String, Object>) value2)) {
                    return false;
                }
            } else if (value1 instanceof List && value2 instanceof List) {
                if (!deepEqualsList((List<Object>) value1, (List<Object>) value2)) {
                    return false;
                }
            } else {
                if (!value1.equals(value2)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 递归移除嵌套对象中的时间戳字段
     *
     * @param config 配置对象
     */
    @SuppressWarnings("unchecked")
    private void removeTimestampFieldsRecursively(final Map<String, Object> config) {
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                nestedMap.remove("timestamp");
                nestedMap.remove("lastModified");
                nestedMap.remove("createdAt");
                nestedMap.remove("updatedAt");
                removeTimestampFieldsRecursively(nestedMap);
            } else if (value instanceof List) {
                List<Object> list = (List<Object>) value;
                for (Object item : list) {
                    if (item instanceof Map) {
                        removeTimestampFieldsRecursively((Map<String, Object>) item);
                    }
                }
            }
        }
    }

    /**
     * 深度比较两个列表
     *
     * @param list1 列表1
     * @param list2 列表2
     * @return true 如果相等，false 否则
     */
    @SuppressWarnings("unchecked")
    private boolean deepEqualsList(final List<Object> list1, final List<Object> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }

        for (int i = 0; i < list1.size(); i++) {
            Object item1 = list1.get(i);
            Object item2 = list2.get(i);

            if (item1 == null && item2 == null) {
                continue;
            }
            if (item1 == null || item2 == null) {
                return false;
            }

            if (item1 instanceof Map && item2 instanceof Map) {
                if (!deepEquals((Map<String, Object>) item1, (Map<String, Object>) item2)) {
                    return false;
                }
            } else if (!item1.equals(item2)) {
                return false;
            }
        }

        return true;
    }
}