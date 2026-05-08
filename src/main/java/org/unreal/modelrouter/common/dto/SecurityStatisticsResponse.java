package org.unreal.modelrouter.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 安全统计响应DTO
 */
@Schema(description = "安全统计响应")
public class SecurityStatisticsResponse {

    @Schema(description = "统计开始时间")
    private LocalDateTime startTime;

    @Schema(description = "统计结束时间")
    private LocalDateTime endTime;

    @Schema(description = "审计统计信息")
    private Map<String, Object> auditStatistics;

    @Schema(description = "告警统计信息")
    private Map<String, Object> alertStatistics;

    @Schema(description = "统计生成时间")
    private LocalDateTime generatedAt;

    SecurityStatisticsResponse(final LocalDateTime startTime, final LocalDateTime endTime, final Map<String, Object> auditStatistics, final Map<String, Object> alertStatistics, final LocalDateTime generatedAt) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.auditStatistics = auditStatistics;
        this.alertStatistics = alertStatistics;
        this.generatedAt = generatedAt;
    }

    public static SecurityStatisticsResponseBuilder builder() {
        return new SecurityStatisticsResponseBuilder();
    }

    public LocalDateTime getStartTime() {
        return this.startTime;
    }

    public void setStartTime(final LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return this.endTime;
    }

    public void setEndTime(final LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Map<String, Object> getAuditStatistics() {
        return this.auditStatistics;
    }

    public void setAuditStatistics(final Map<String, Object> auditStatistics) {
        this.auditStatistics = auditStatistics;
    }

    public Map<String, Object> getAlertStatistics() {
        return this.alertStatistics;
    }

    public void setAlertStatistics(final Map<String, Object> alertStatistics) {
        this.alertStatistics = alertStatistics;
    }

    public LocalDateTime getGeneratedAt() {
        return this.generatedAt;
    }

    public void setGeneratedAt(final LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String toString() {
        return "SecurityStatisticsResponse(startTime=" + this.getStartTime() + ", endTime=" + this.getEndTime() + ", auditStatistics=" + this.getAuditStatistics() + ", alertStatistics=" + this.getAlertStatistics() + ", generatedAt=" + this.getGeneratedAt() + ")";
    }

    public static class SecurityStatisticsResponseBuilder {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Map<String, Object> auditStatistics;
        private Map<String, Object> alertStatistics;
        private LocalDateTime generatedAt;

        SecurityStatisticsResponseBuilder() {
        }

        public SecurityStatisticsResponseBuilder startTime(final LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public SecurityStatisticsResponseBuilder endTime(final LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        public SecurityStatisticsResponseBuilder auditStatistics(final Map<String, Object> auditStatistics) {
            this.auditStatistics = auditStatistics;
            return this;
        }

        public SecurityStatisticsResponseBuilder alertStatistics(final Map<String, Object> alertStatistics) {
            this.alertStatistics = alertStatistics;
            return this;
        }

        public SecurityStatisticsResponseBuilder generatedAt(final LocalDateTime generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public SecurityStatisticsResponse build() {
            return new SecurityStatisticsResponse(this.startTime, this.endTime, this.auditStatistics, this.alertStatistics, this.generatedAt);
        }

        public String toString() {
            return "SecurityStatisticsResponse.SecurityStatisticsResponseBuilder(startTime=" + this.startTime + ", endTime=" + this.endTime + ", auditStatistics=" + this.auditStatistics + ", alertStatistics=" + this.alertStatistics + ", generatedAt=" + this.generatedAt + ")";
        }
    }
}