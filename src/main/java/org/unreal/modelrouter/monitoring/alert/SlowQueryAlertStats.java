package org.unreal.modelrouter.monitoring.alert;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * 慢查询告警统计信息
 * 
 * 记录慢查询告警系统的运行统计数据，用于监控和分析告警系统的效果。
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Data
@Builder
public class SlowQueryAlertStats {
    
    /**
     * 总共触发的告警次数
     */
    private long totalAlertsTriggered;
    
    /**
     * 总共被抑制的告警次数
     */
    private long totalAlertsSuppressed;
    
    /**
     * 当前活跃的告警键数量
     */
    private int activeAlertKeys;
    
    /**
     * 当前活跃的操作集合
     */
    private Set<String> activeOperations;
    
    /**
     * 获取告警触发率
     * 
     * @return 告警触发率（0.0-1.0）
     */
    public double getAlertTriggerRate() {
        long total = totalAlertsTriggered + totalAlertsSuppressed;
        return total > 0 ? (double) totalAlertsTriggered / total : 0.0;
    }
    
    /**
     * 获取告警抑制率
     * 
     * @return 告警抑制率（0.0-1.0）
     */
    public double getAlertSuppressionRate() {
        long total = totalAlertsTriggered + totalAlertsSuppressed;
        return total > 0 ? (double) totalAlertsSuppressed / total : 0.0;
    }
    
    /**
     * 获取平均每个操作的告警次数
     * 
     * @return 平均告警次数
     */
    public double getAverageAlertsPerOperation() {
        return activeOperations.size() > 0 ? 
                (double) totalAlertsTriggered / activeOperations.size() : 0.0;
    }
}