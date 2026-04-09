package org.unreal.modelrouter.security.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 所量导出响应 DTO
 * 注意：导出的数据不包含 keyValue 和 keyHash，仅包含可恢复的配置信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyBatchExportVO {

    /**
     * 导出时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime exportTime;

    /**
     * 导出的密钥总数
     */
    private int total;

    /**
     * 导出的密钥列表
     */
    private List<ExportedKey> keys;

    /**
     * 单个导出的密钥信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExportedKey {
        /**
         * API Key ID
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
         * 是否启用
         */
        private boolean enabled;

        /**
         * 创建时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdAt;

        /**
         * 创建者用户名
         */
        private String createdBy;

        /**
         * 过期时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime expiresAt;

        /**
         * 允许的 IP 地址白名单
         */
        private List<String> allowedIpAddresses;

        /**
         * 每日请求上限
         */
        private long dailyRequestLimit;

        /**
         * 密钥轮换周期（天数）
         */
        private int rotationPeriodDays;

        /**
         * 上次轮换时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime lastRotatedAt;
    }
}