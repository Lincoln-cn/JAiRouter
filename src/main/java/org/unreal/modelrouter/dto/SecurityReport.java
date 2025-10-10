package org.unreal.modelrouter.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 安全报告类
 * 用于生成安全审计报告
 */
public class SecurityReport {
    
    private LocalDateTime reportPeriodStart;
    private LocalDateTime reportPeriodEnd;
    private long totalJwtOperations;
    private long totalApiKeyOperations;
    private long failedAuthentications;
    private long suspiciousActivities;
    private Map<String, Long> operationsByType;
    private Map<String, Long> operationsByUser;
    private List<String> topIpAddresses;
    private List<SecurityAlert> alerts;

    public SecurityReport() {
    }

    public SecurityReport(LocalDateTime reportPeriodStart, LocalDateTime reportPeriodEnd, 
                         long totalJwtOperations, long totalApiKeyOperations, 
                         long failedAuthentications, long suspiciousActivities,
                         Map<String, Long> operationsByType, Map<String, Long> operationsByUser,
                         List<String> topIpAddresses, List<SecurityAlert> alerts) {
        this.reportPeriodStart = reportPeriodStart;
        this.reportPeriodEnd = reportPeriodEnd;
        this.totalJwtOperations = totalJwtOperations;
        this.totalApiKeyOperations = totalApiKeyOperations;
        this.failedAuthentications = failedAuthentications;
        this.suspiciousActivities = suspiciousActivities;
        this.operationsByType = operationsByType;
        this.operationsByUser = operationsByUser;
        this.topIpAddresses = topIpAddresses;
        this.alerts = alerts;
    }

    // Getters and Setters
    public LocalDateTime getReportPeriodStart() {
        return reportPeriodStart;
    }

    public void setReportPeriodStart(LocalDateTime reportPeriodStart) {
        this.reportPeriodStart = reportPeriodStart;
    }

    public LocalDateTime getReportPeriodEnd() {
        return reportPeriodEnd;
    }

    public void setReportPeriodEnd(LocalDateTime reportPeriodEnd) {
        this.reportPeriodEnd = reportPeriodEnd;
    }

    public long getTotalJwtOperations() {
        return totalJwtOperations;
    }

    public void setTotalJwtOperations(long totalJwtOperations) {
        this.totalJwtOperations = totalJwtOperations;
    }

    public long getTotalApiKeyOperations() {
        return totalApiKeyOperations;
    }

    public void setTotalApiKeyOperations(long totalApiKeyOperations) {
        this.totalApiKeyOperations = totalApiKeyOperations;
    }

    public long getFailedAuthentications() {
        return failedAuthentications;
    }

    public void setFailedAuthentications(long failedAuthentications) {
        this.failedAuthentications = failedAuthentications;
    }

    public long getSuspiciousActivities() {
        return suspiciousActivities;
    }

    public void setSuspiciousActivities(long suspiciousActivities) {
        this.suspiciousActivities = suspiciousActivities;
    }

    public Map<String, Long> getOperationsByType() {
        return operationsByType;
    }

    public void setOperationsByType(Map<String, Long> operationsByType) {
        this.operationsByType = operationsByType;
    }

    public Map<String, Long> getOperationsByUser() {
        return operationsByUser;
    }

    public void setOperationsByUser(Map<String, Long> operationsByUser) {
        this.operationsByUser = operationsByUser;
    }

    public List<String> getTopIpAddresses() {
        return topIpAddresses;
    }

    public void setTopIpAddresses(List<String> topIpAddresses) {
        this.topIpAddresses = topIpAddresses;
    }

    public List<SecurityAlert> getAlerts() {
        return alerts;
    }

    public void setAlerts(List<SecurityAlert> alerts) {
        this.alerts = alerts;
    }

    @Override
    public String toString() {
        return "SecurityReport{" +
                "reportPeriodStart=" + reportPeriodStart +
                ", reportPeriodEnd=" + reportPeriodEnd +
                ", totalJwtOperations=" + totalJwtOperations +
                ", totalApiKeyOperations=" + totalApiKeyOperations +
                ", failedAuthentications=" + failedAuthentications +
                ", suspiciousActivities=" + suspiciousActivities +
                ", operationsByType=" + operationsByType +
                ", operationsByUser=" + operationsByUser +
                ", topIpAddresses=" + topIpAddresses +
                ", alerts=" + alerts +
                '}';
    }
}