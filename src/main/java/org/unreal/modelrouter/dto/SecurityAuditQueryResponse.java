package org.unreal.modelrouter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 安全审计查询响应DTO
 */
@Schema(description = "安全审计查询响应")
public class SecurityAuditQueryResponse {

    @Schema(description = "审计事件列表")
    private List<SecurityAuditEvent> events;

    @Schema(description = "当前页码")
    private int page;

    @Schema(description = "每页大小")
    private int size;

    @Schema(description = "总元素数量")
    private int totalElements;

    @Schema(description = "查询开始时间")
    private LocalDateTime startTime;

    @Schema(description = "查询结束时间")
    private LocalDateTime endTime;

    @Schema(description = "响应生成时间")
    private LocalDateTime generatedAt = LocalDateTime.now();

    SecurityAuditQueryResponse(List<SecurityAuditEvent> events, int page, int size, int totalElements, LocalDateTime startTime, LocalDateTime endTime, LocalDateTime generatedAt) {
        this.events = events;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.startTime = startTime;
        this.endTime = endTime;
        this.generatedAt = generatedAt;
    }

    private static LocalDateTime $default$generatedAt() {
        return LocalDateTime.now();
    }

    public static SecurityAuditQueryResponseBuilder builder() {
        return new SecurityAuditQueryResponseBuilder();
    }

    @Schema(description = "是否有下一页")
    public boolean hasNext() {
        return events.size() == size;
    }

    @Schema(description = "是否有上一页")
    public boolean hasPrevious() {
        return page > 0;
    }

    public List<SecurityAuditEvent> getEvents() {
        return this.events;
    }

    public void setEvents(List<SecurityAuditEvent> events) {
        this.events = events;
    }

    public int getPage() {
        return this.page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return this.size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getTotalElements() {
        return this.totalElements;
    }

    public void setTotalElements(int totalElements) {
        this.totalElements = totalElements;
    }

    public LocalDateTime getStartTime() {
        return this.startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return this.endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public LocalDateTime getGeneratedAt() {
        return this.generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String toString() {
        return "SecurityAuditQueryResponse(events=" + this.getEvents() + ", page=" + this.getPage() + ", size=" + this.getSize() + ", totalElements=" + this.getTotalElements() + ", startTime=" + this.getStartTime() + ", endTime=" + this.getEndTime() + ", generatedAt=" + this.getGeneratedAt() + ")";
    }

    public static class SecurityAuditQueryResponseBuilder {
        private List<SecurityAuditEvent> events;
        private int page;
        private int size;
        private int totalElements;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private LocalDateTime generatedAt$value;
        private boolean generatedAt$set;

        SecurityAuditQueryResponseBuilder() {
        }

        public SecurityAuditQueryResponseBuilder events(List<SecurityAuditEvent> events) {
            this.events = events;
            return this;
        }

        public SecurityAuditQueryResponseBuilder page(int page) {
            this.page = page;
            return this;
        }

        public SecurityAuditQueryResponseBuilder size(int size) {
            this.size = size;
            return this;
        }

        public SecurityAuditQueryResponseBuilder totalElements(int totalElements) {
            this.totalElements = totalElements;
            return this;
        }

        public SecurityAuditQueryResponseBuilder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public SecurityAuditQueryResponseBuilder endTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        public SecurityAuditQueryResponseBuilder generatedAt(LocalDateTime generatedAt) {
            this.generatedAt$value = generatedAt;
            this.generatedAt$set = true;
            return this;
        }

        public SecurityAuditQueryResponse build() {
            LocalDateTime generatedAt$value = this.generatedAt$value;
            if (!this.generatedAt$set) {
                generatedAt$value = SecurityAuditQueryResponse.$default$generatedAt();
            }
            return new SecurityAuditQueryResponse(this.events, this.page, this.size, this.totalElements, this.startTime, this.endTime, generatedAt$value);
        }

        public String toString() {
            return "SecurityAuditQueryResponse.SecurityAuditQueryResponseBuilder(events=" + this.events + ", page=" + this.page + ", size=" + this.size + ", totalElements=" + this.totalElements + ", startTime=" + this.startTime + ", endTime=" + this.endTime + ", generatedAt$value=" + this.generatedAt$value + ")";
        }
    }
}