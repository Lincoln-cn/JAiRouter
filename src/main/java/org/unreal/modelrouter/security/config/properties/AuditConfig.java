package org.unreal.modelrouter.security.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 审计配置类
 */
@Data
public class AuditConfig {
    /**
     * 是否启用审计
     */
    private boolean enabled = true;

    /**
     * 日志级别
     */
    @NotBlank
    @Pattern(regexp = "^(TRACE|DEBUG|INFO|WARN|ERROR)$", message = "不支持的日志级别")
    private String logLevel = "INFO";

    /**
     * 是否包含请求体
     */
    private boolean includeRequestBody = false;

    /**
     * 是否包含响应体
     */
    private boolean includeResponseBody = false;

    /**
     * 审计日志保留天数
     */
    @Min(1)
    @Max(3650)
    private int retentionDays = 90;

    /**
     * 是否启用实时告警
     */
    private boolean alertEnabled = false;

    /**
     * 告警阈值配置
     */
    @Valid
    @NotNull
    private AlertThresholds alertThresholds = new AlertThresholds();

    /**
     * 告警阈值配置
     */
    @Data
    public static class AlertThresholds {
        /**
         * 认证失败次数阈值（每分钟）
         */
        @Min(1)
        @Max(1000)
        private int authFailuresPerMinute = 10;

        /**
         * 脱敏操作次数阈值（每分钟）
         */
        @Min(1)
        @Max(10000)
        private int sanitizationOperationsPerMinute = 100;
    }
}
