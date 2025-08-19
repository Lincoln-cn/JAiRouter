package org.unreal.modelrouter.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 安全审计查询响应DTO
 */
@Data
@Builder
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
    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();
    
    @Schema(description = "是否有下一页")
    public boolean hasNext() {
        return events.size() == size;
    }
    
    @Schema(description = "是否有上一页")
    public boolean hasPrevious() {
        return page > 0;
    }
}