package org.unreal.modelrouter.version.diff;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置差异结果
 * 保存两个配置版本之间的完整差异信息
 */
@Data
@Builder
public class ConfigDiff {

    /**
     * 源版本号
     */
    private final int sourceVersion;

    /**
     * 目标版本号
     */
    private final int targetVersion;

    /**
     * 新增的配置项
     */
    @Builder.Default
    private List<DiffItem> added = new ArrayList<>();

    /**
     * 删除的配置项
     */
    @Builder.Default
    private List<DiffItem> removed = new ArrayList<>();

    /**
     * 修改的配置项
     */
    @Builder.Default
    private List<DiffItem> modified = new ArrayList<>();

    /**
     * 未变化的配置项数量
     */
    private int unchangedCount;

    /**
     * 是否有差异
     *
     * @return true 如果存在任何差异
     */
    public boolean hasDifferences() {
        return !added.isEmpty() || !removed.isEmpty() || !modified.isEmpty();
    }

    /**
     * 获取差异项总数
     *
     * @return 差异项总数
     */
    public int getTotalChanges() {
        return added.size() + removed.size() + modified.size();
    }

    /**
     * 差异项
     */
    @Data
    @Builder
    public static class DiffItem {

        /**
         * 配置项路径，使用点号分隔
         * 例如：services.openai.models.gpt-4
         */
        private final String path;

        /**
         * 旧值（如果是新增则为 null）
         */
        private final Object oldValue;

        /**
         * 新值（如果是删除则为 null）
         */
        private final Object newValue;

        /**
         * 变更类型
         */
        private final ChangeType changeType;

        /**
         * 值类型描述
         */
        private final String valueType;

        public enum ChangeType {
            ADDED,      // 新增
            REMOVED,    // 删除
            MODIFIED    // 修改
        }

        /**
         * 获取简化的值描述
         */
        public String getChangeDescription() {
            switch (changeType) {
                case ADDED:
                    return String.format("添加 %s = %s", path, formatValue(newValue));
                case REMOVED:
                    return String.format("删除 %s (原值: %s)", path, formatValue(oldValue));
                case MODIFIED:
                    return String.format("修改 %s: %s → %s",
                            path, formatValue(oldValue), formatValue(newValue));
                default:
                    return path;
            }
        }

        private String formatValue(final Object value) {
            if (value == null) {
                return "null";
            }
            String str = value.toString();
            if (str.length() > 100) {
                return str.substring(0, 100) + "...";
            }
            return str;
        }
    }
}
