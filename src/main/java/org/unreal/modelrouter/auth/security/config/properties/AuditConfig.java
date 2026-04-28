package org.unreal.modelrouter.auth.security.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 审计日志配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "jairouter.security.audit")
public class AuditConfig {

    /**
     * 是否启用审计日志
     */
    private boolean enabled = true;

    /**
     * 审计日志存储类型: memory, database
     */
    private String storage = "database";

    /**
     * 默认保留天数
     */
    private int retentionDays = 90;

    /**
     * 日志级别: DEBUG, INFO, WARN, ERROR
     */
    private String logLevel = "INFO";

    /**
     * 是否在审计日志中包含请求体
     */
    private boolean includeRequestBody = false;

    /**
     * 是否在审计日志中包含响应体
     */
    private boolean includeResponseBody = false;

    /**
     * 是否启用告警
     */
    private boolean alertEnabled = true;

    /**
     * 清理任务执行时间（Cron表达式）
     */
    private String cleanupSchedule = "0 0 2 * * *";  // 每天凌晨2点

    /**
     * 每次清理的最大记录数
     */
    private int cleanupBatchSize = 10000;

    /**
     * 最大存储大小（MB），0表示不限制
     */
    private long maxStorageSizeMb = 0;

    /**
     * 是否启用压缩归档
     */
    private boolean enableArchive = true;

    /**
     * 归档路径
     */
    private String archivePath = "logs/audit/archive";

    /**
     * 按风险等级的保留策略
     */
    private Map<String, Integer> retentionByRiskLevel = new HashMap<>() {{
        put("LOW", 30);
        put("MEDIUM", 90);
        put("HIGH", 365);
        put("CRITICAL", 730);  // 2年
    }};

    /**
     * 按事件类型的保留策略
     */
    private Map<String, Integer> retentionByEventType = new HashMap<>();

    /**
     * 告警配置
     */
    private AlertConfig alert = new AlertConfig();

    /**
     * 导出配置
     */
    private ExportConfig export = new ExportConfig();

    /**
     * 告警阈值配置
     */
    private AlertThresholds alertThresholds = new AlertThresholds();

    /**
     * 告警阈值配置
     */
    @Data
    public static class AlertThresholds {
        /**
         * 每分钟认证失败阈值
         */
        private int authFailuresPerMinute = 10;

        /**
         * 每分钟脱敏操作阈值
         */
        private int sanitizationOperationsPerMinute = 1000;

        /**
         * 每分钟可疑活动阈值
         */
        private int suspiciousActivitiesPerMinute = 5;
    }

    /**
     * 告警配置
     */
    @Data
    public static class AlertConfig {
        /**
         * 是否启用告警
         */
        private boolean enabled = true;

        /**
         * 认证失败阈值
         */
        private int authFailureThreshold = 5;

        /**
         * 认证失败时间窗口（分钟）
         */
        private int authFailureWindowMinutes = 5;

        /**
         * 可疑IP阈值
         */
        private int suspiciousIpThreshold = 10;

        /**
         * 可疑IP时间窗口（分钟）
         */
        private int suspiciousIpWindowMinutes = 10;

        /**
         * 告警冷却时间（分钟）
         */
        private int alertCooldownMinutes = 15;

        /**
         * 是否发送通知
         */
        private boolean enableNotification = false;

        /**
         * 通知渠道: email, webhook, slack
         */
        private String notificationChannel = "";
    }

    /**
     * 导出配置
     */
    @Data
    public static class ExportConfig {
        /**
         * 最大导出记录数
         */
        private int maxExportRecords = 100000;

        /**
         * 导出文件格式
         */
        private String defaultFormat = "CSV";

        /**
         * 导出临时目录
         */
        private String tempPath = "logs/audit/export";

        /**
         * 导出文件保留时间（小时）
         */
        private int fileRetentionHours = 24;
    }

    /**
     * 根据风险等级获取保留天数
     */
    public int getRetentionDaysByRiskLevel(final String riskLevel) {
        return retentionByRiskLevel.getOrDefault(riskLevel, retentionDays);
    }

    /**
     * 根据事件类型获取保留天数
     */
    public int getRetentionDaysByEventType(final String eventType) {
        return retentionByEventType.getOrDefault(eventType, retentionDays);
    }
}