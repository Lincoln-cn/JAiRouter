package org.unreal.modelrouter.entity;

import java.util.List;
import java.util.Map;

// 内部类：合并结果
public record MergeConfigResult(Map<String, Object> mergedConfig, List<String> conflicts, List<String> warnings) {

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
