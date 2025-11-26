package org.unreal.modelrouter.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 合并配置文件结果
 */
public record MergeResult(boolean success, String message, int mergedFilesCount, int newVersionCount,
                          List<String> mergedFiles, List<String> errors) {
    public MergeResult(boolean success, String message, int mergedFilesCount,
                       int newVersionCount, List<String> mergedFiles, List<String> errors) {
        this.success = success;
        this.message = message;
        this.mergedFilesCount = mergedFilesCount;
        this.newVersionCount = newVersionCount;
        this.mergedFiles = mergedFiles != null ? mergedFiles : new ArrayList<>();
        this.errors = errors != null ? errors : new ArrayList<>();
    }
}
