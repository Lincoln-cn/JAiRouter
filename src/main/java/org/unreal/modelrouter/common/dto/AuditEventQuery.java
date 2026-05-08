package org.unreal.modelrouter.common.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计事件查询条件类
 */
public class AuditEventQuery {
    
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<AuditEventType> eventTypes;
    private String userId;
    private String resourceId;
    private String action;
    private String ipAddress;
    private Boolean success;
    private int page = 0;
    private int size = 20;
    private String sortBy = "timestamp";
    private String sortDirection = "DESC";

    public AuditEventQuery() {
    }

    // Getters and Setters
    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(final LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(final LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public List<AuditEventType> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(final List<AuditEventType> eventTypes) {
        this.eventTypes = eventTypes;
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

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(final String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(final Boolean success) {
        this.success = success;
    }

    public int getPage() {
        return page;
    }

    public void setPage(final int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(final int size) {
        this.size = size;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(final String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(final String sortDirection) {
        this.sortDirection = sortDirection;
    }

    @Override
    public String toString() {
        return "AuditEventQuery{"
                + "startTime=" + startTime
                + ", endTime=" + endTime
                + ", eventTypes=" + eventTypes
                + ", userId='" + userId + '\''
                + ", resourceId='" + resourceId + '\''
                + ", action='" + action + '\''
                + ", ipAddress='" + ipAddress + '\''
                + ", success=" + success
                + ", page=" + page
                + ", size=" + size
                + ", sortBy='" + sortBy + '\''
                + ", sortDirection='" + sortDirection + '\''
                + '}';
    }
}