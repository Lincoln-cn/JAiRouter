package org.unreal.modelrouter.monitoring.alert;

import java.time.Instant;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * 慢查询告警信息
 * 
 * 记录慢查询告警的详细信息，包括操作信息、性能指标、统计数据等。
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Data
@Builder
public class SlowQueryAlert {
    
    /**
     * 告警唯一标识
     */
    private String alertId;
    
    /**
     * 操作名称
     */
    private String operationName;
    
    /**
     * 告警时间
     */
    private Instant timestamp;
    
    /**
     * 严重程度：critical, warning, info
     */
    private String severity;
    
    /**
     * 当前操作耗时（毫秒）
     */
    private long currentDuration;
    
    /**
     * 慢查询阈值（毫秒）
     */
    private long threshold;
    
    /**
     * 超出阈值的倍数
     */
    private double thresholdMultiplier;
    
    /**
     * 该操作的告警次数
     */
    private long alertCount;
    
    /**
     * 该操作的慢查询总次数
     */
    private long totalOccurrences;
    
    /**
     * 该操作的平均耗时
     */
    private double averageDuration;
    
    /**
     * 该操作的最大耗时
     */
    private long maxDuration;
    
    /**
     * 追踪ID
     */
    private String traceId;
    
    /**
     * Span ID
     */
    private String spanId;
    
    /**
     * 额外信息
     */
    private Map<String, Object> additionalInfo;
    
    /**
     * 获取告警描述
     * 
     * @return 告警描述
     */
    public String getDescription() {
        return String.format("操作 '%s' 执行耗时 %dms，超过阈值 %dms（%.2f倍）", 
                operationName, currentDuration, threshold, thresholdMultiplier);
    }
    
    /**
     * 获取告警摘要
     * 
     * @return 告警摘要
     */
    public String getSummary() {
        return String.format("慢查询告警：%s [%s]", operationName, severity.toUpperCase());
    }
    
    /**
     * 是否为严重告警
     * 
     * @return true表示严重告警
     */
    public boolean isCritical() {
        return "critical".equalsIgnoreCase(severity);
    }
    
    /**
     * 是否为警告告警
     * 
     * @return true表示警告告警
     */
    public boolean isWarning() {
        return "warning".equalsIgnoreCase(severity);
    }
}