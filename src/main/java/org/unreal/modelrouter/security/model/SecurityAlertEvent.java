package org.unreal.modelrouter.security.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 安全告警事件数据模型
 */
@Data
@Builder
public class SecurityAlertEvent {
    
    /**
     * 告警唯一标识符
     */
    private String alertId;
    
    /**
     * 告警类型
     */
    private String alertType;
    
    /**
     * 告警标题
     */
    private String title;
    
    /**
     * 告警描述
     */
    private String description;
    
    /**
     * 告警严重程度 (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String severity;
    
    /**
     * 告警发生时间
     */
    private LocalDateTime timestamp;
    
    /**
     * 告警相关数据
     */
    private Map<String, Object> alertData;
    
    /**
     * 告警状态 (ACTIVE, ACKNOWLEDGED, RESOLVED)
     */
    @Builder.Default
    private String status = "ACTIVE";
    
    /**
     * 处理人员
     */
    private String assignedTo;
    
    /**
     * 处理备注
     */
    private String notes;
    
    /**
     * 解决时间
     */
    private LocalDateTime resolvedAt;
}