package org.unreal.modelrouter.security.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * API Key 批量导入请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyBatchImportRequest {

    /**
     * 要导入的 API Key 列表
     */
    private List<ApiKeyImportItem> keys;

    /**
     * 导入模式：MERGE（合并，保留现有）或 REPLACE（替换，删除现有后导入）
     */
    @Builder.Default
    private ImportMode mode = ImportMode.MERGE;

    /**
     * 导入模式枚举
     */
    public enum ImportMode {
        /**
         * 合并模式：保留现有密钥，仅添加新密钥
         */
        MERGE,
        /**
         * 替换模式：删除所有现有密钥，导入新密钥
         */
        REPLACE
    }

    /**
     * 单个 API Key 导入项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiKeyImportItem {
        /**
         * API Key ID（可选，不提供则自动生成）
         */
        private String keyId;

        /**
         * 描述
         */
        private String description;

        /**
         * 权限列表
         */
        private List<String> permissions;

        /**
         * 是否启用（默认 true）
         */
        @Builder.Default
        private Boolean enabled = true;

        /**
         * 过期时间（格式：yyyy-MM-dd HH:mm:ss）
         */
        private String expiresAt;

        /**
         * 允许的 IP 地址白名单
         */
        private List<String> allowedIpAddresses;

        /**
         * 每日请求上限（0 表示不限制）
         */
        @Builder.Default
        private Long dailyRequestLimit = 0L;

        /**
         * 密钥轮换周期（天数，0 表示不自动轮换）
         */
        @Builder.Default
        private Integer rotationPeriodDays = 0;
    }
}