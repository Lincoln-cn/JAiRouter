package org.unreal.modelrouter.monitoring.alert;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 慢查询告警配置属性
 * 
 * 定义慢查询告警系统的配置参数，支持从配置文件中读取。
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "jairouter.monitoring.slow-query-alert")
public class SlowQueryAlertProperties {
    
    /**
     * 是否启用慢查询告警
     */
    private boolean enabled = true;
    
    /**
     * 全局默认配置
     */
    private GlobalConfig global = new GlobalConfig();
    
    /**
     * 按操作类型的特定配置
     */
    private Map<String, OperationConfig> operations = new HashMap<>();
    
    /**
     * 告警通知配置
     */
    private NotificationConfig notification = new NotificationConfig();
    
    /**
     * 获取指定操作的告警配置
     * 
     * @param operationName 操作名称
     * @return 告警配置
     */
    public SlowQueryAlertConfig getAlertConfig(String operationName) {
        OperationConfig operationConfig = operations.get(operationName);
        
        // 如果没有特定配置，使用全局配置
        if (operationConfig == null) {
            return SlowQueryAlertConfig.builder()
                    .enabled(enabled && global.enabled)
                    .minIntervalMs(global.minIntervalMs)
                    .minOccurrences(global.minOccurrences)
                    .enabledSeverities(global.enabledSeverities)
                    .suppressionWindowMs(global.suppressionWindowMs)
                    .maxAlertsPerHour(global.maxAlertsPerHour)
                    .build();
        }
        
        // 合并全局配置和操作特定配置
        return SlowQueryAlertConfig.builder()
                .enabled(enabled && operationConfig.enabled)
                .minIntervalMs(operationConfig.minIntervalMs != null ? operationConfig.minIntervalMs : global.minIntervalMs)
                .minOccurrences(operationConfig.minOccurrences != null ? operationConfig.minOccurrences : global.minOccurrences)
                .enabledSeverities(operationConfig.enabledSeverities != null ? operationConfig.enabledSeverities : global.enabledSeverities)
                .suppressionWindowMs(operationConfig.suppressionWindowMs != null ? operationConfig.suppressionWindowMs : global.suppressionWindowMs)
                .maxAlertsPerHour(operationConfig.maxAlertsPerHour != null ? operationConfig.maxAlertsPerHour : global.maxAlertsPerHour)
                .build();
    }
    
    /**
     * 全局配置
     */
    @Data
    public static class GlobalConfig {
        private boolean enabled = true;
        private long minIntervalMs = 5 * 60 * 1000L; // 5分钟
        private long minOccurrences = 3L;
        private Set<String> enabledSeverities = Set.of("critical", "warning");
        private long suppressionWindowMs = 60 * 60 * 1000L; // 1小时
        private int maxAlertsPerHour = 10;
    }
    
    /**
     * 操作特定配置
     */
    @Data
    public static class OperationConfig {
        private Boolean enabled;
        private Long minIntervalMs;
        private Long minOccurrences;
        private Set<String> enabledSeverities;
        private Long suppressionWindowMs;
        private Integer maxAlertsPerHour;
    }
    
    /**
     * 通知配置
     */
    @Data
    public static class NotificationConfig {
        private boolean enablePrometheusMetrics = true;
        private boolean enableStructuredLogging = true;
        private boolean enableWebhook = false;
        private String webhookUrl;
        private Map<String, String> webhookHeaders = new HashMap<>();
    }
}