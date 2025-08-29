package org.unreal.modelrouter.monitoring.alert;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * 慢查询告警配置
 * 
 * 定义慢查询告警的各种配置参数，包括告警阈值、频率控制、严重程度等。
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Data
@Builder
public class SlowQueryAlertConfig {
    
    /**
     * 最小告警间隔（毫秒）
     */
    @Builder.Default
    private long minIntervalMs = 5 * 60 * 1000L; // 5分钟
    
    /**
     * 触发告警所需的最小慢查询次数
     */
    @Builder.Default
    private long minOccurrences = 3L;
    
    /**
     * 启用告警的严重程度级别
     */
    @Builder.Default
    private Set<String> enabledSeverities = Set.of("critical", "warning");
    
    /**
     * 是否启用告警
     */
    @Builder.Default
    private boolean enabled = true;
    
    /**
     * 告警抑制时间窗口（毫秒）
     */
    @Builder.Default
    private long suppressionWindowMs = 60 * 60 * 1000L; // 1小时
    
    /**
     * 最大告警频率（每小时）
     */
    @Builder.Default
    private int maxAlertsPerHour = 10;
    
    /**
     * 检查指定严重程度是否启用告警
     * 
     * @param severity 严重程度
     * @return true表示启用告警
     */
    public boolean isEnabledForSeverity(String severity) {
        return enabled && enabledSeverities.contains(severity.toLowerCase());
    }
    
    /**
     * 获取默认配置
     * 
     * @return 默认告警配置
     */
    public static SlowQueryAlertConfig defaultConfig() {
        return SlowQueryAlertConfig.builder().build();
    }
    
    /**
     * 获取严格配置（仅严重告警）
     * 
     * @return 严格告警配置
     */
    public static SlowQueryAlertConfig strictConfig() {
        return SlowQueryAlertConfig.builder()
                .enabledSeverities(Set.of("critical"))
                .minOccurrences(1L)
                .minIntervalMs(2 * 60 * 1000L) // 2分钟
                .maxAlertsPerHour(5)
                .build();
    }
    
    /**
     * 获取宽松配置（包含所有严重程度）
     * 
     * @return 宽松告警配置
     */
    public static SlowQueryAlertConfig lenientConfig() {
        return SlowQueryAlertConfig.builder()
                .enabledSeverities(Set.of("critical", "warning", "info"))
                .minOccurrences(5L)
                .minIntervalMs(10 * 60 * 1000L) // 10分钟
                .maxAlertsPerHour(20)
                .build();
    }
}