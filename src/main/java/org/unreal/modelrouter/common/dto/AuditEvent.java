package org.unreal.modelrouter.common.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计事件类
 * 用于记录JWT令牌和API Key的操作审计信息
 */
public class AuditEvent {
    
    private String id;              // 事件ID
    private AuditEventType type;    // 事件类型
    private String eventCategory;   // 事件分类（认证 / API Key / 安全 / 数据保护 / 系统）
    private String riskLevel;       // 风险级别（LOW / MEDIUM / HIGH / CRITICAL）
    private String userId;          // 用户ID
    private String resourceId;      // 资源ID（令牌ID或API Key ID）
    private String action;          // 操作类型
    private String details;         // 详细信息
    private String ipAddress;       // IP地址
    private String userAgent;       // 用户代理
    private String geoLocation;     // 地理位置（由审计写入方提供，可能为空）
    private String deviceInfo;      // 设备信息（由审计写入方提供，可能为空）
    private boolean success;        // 操作是否成功
    private LocalDateTime timestamp; // 时间戳
    private Map<String, Object> metadata; // 额外元数据

    public AuditEvent() {
    }

    public AuditEvent(final String id, final AuditEventType type, final String userId, final String resourceId,
                     final String action, final String details, final String ipAddress, final String userAgent,
                     final boolean success, final LocalDateTime timestamp, final Map<String, Object> metadata) {
        this.id = id;
        this.type = type;
        this.userId = userId;
        this.resourceId = resourceId;
        this.action = action;
        this.details = details;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.success = success;
        this.timestamp = timestamp;
        this.metadata = metadata;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public AuditEventType getType() {
        return type;
    }

    public void setType(final AuditEventType type) {
        this.type = type;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(final String resourceId) {
        this.resourceId = resourceId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(final String action) {
        this.action = action;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(final String details) {
        this.details = details;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(final String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(final String userAgent) {
        this.userAgent = userAgent;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(final boolean success) {
        this.success = success;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(final Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * 事件分类。由审计实体在写入时算好（{@code SecurityAuditEventEntity.prePersist}），这里只透传。
     */
    public String getEventCategory() {
        return eventCategory;
    }

    public void setEventCategory(final String eventCategory) {
        this.eventCategory = eventCategory;
    }

    /**
     * 风险级别。由审计实体在写入时算好，这里只透传，前端不参与计算。
     */
    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(final String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getGeoLocation() {
        return geoLocation;
    }

    public void setGeoLocation(final String geoLocation) {
        this.geoLocation = geoLocation;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(final String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    @Override
    public String toString() {
        return "AuditEvent{"
                + "id='" + id + '\''
                + ", type=" + type
                + ", userId='" + userId + '\''
                + ", resourceId='" + resourceId + '\''
                + ", action='" + action + '\''
                + ", details='" + details + '\''
                + ", ipAddress='" + ipAddress + '\''
                + ", userAgent='" + userAgent + '\''
                + ", success=" + success
                + ", timestamp=" + timestamp
                + ", metadata=" + metadata
                + '}';
    }
}