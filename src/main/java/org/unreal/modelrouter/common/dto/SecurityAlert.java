package org.unreal.modelrouter.common.dto;

import java.time.LocalDateTime;

/**
 * 安全告警类
 */
public class SecurityAlert {
    
    private String id;
    private String alertType;
    private String severity;
    private String message;
    private String userId;
    private String ipAddress;
    private LocalDateTime timestamp;
    private boolean resolved;

    public SecurityAlert() {
    }

    public SecurityAlert(final String alertType, final String message, final String userId, final String ipAddress, final LocalDateTime timestamp) {
        this.alertType = alertType;
        this.message = message;
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.timestamp = timestamp;
    }

    public SecurityAlert(final String id, final String alertType, final String severity, final String message,
                        final String userId, final String ipAddress, final LocalDateTime timestamp, final boolean resolved) {
        this.id = id;
        this.alertType = alertType;
        this.severity = severity;
        this.message = message;
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.timestamp = timestamp;
        this.resolved = resolved;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(final String alertType) {
        this.alertType = alertType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(final String severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(final String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(final boolean resolved) {
        this.resolved = resolved;
    }

    @Override
    public String toString() {
        return "SecurityAlert{" +
                "id='" + id + '\'' +
                ", alertType='" + alertType + '\'' +
                ", severity='" + severity + '\'' +
                ", message='" + message + '\'' +
                ", userId='" + userId + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", timestamp=" + timestamp +
                ", resolved=" + resolved +
                '}';
    }
}