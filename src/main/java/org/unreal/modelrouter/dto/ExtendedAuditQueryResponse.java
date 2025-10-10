package org.unreal.modelrouter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 扩展审计查询响应类
 * 用于返回JWT和API Key审计查询结果
 */
@Data
@Builder
@Schema(description = "扩展审计查询响应")
public class ExtendedAuditQueryResponse {
    
    @Schema(description = "审计事件列表")
    private List<AuditEvent> events;
    
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
    
    @Schema(description = "事件分类")
    private String eventCategory;
    
    @Schema(description = "响应生成时间")
    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();
    
    @Schema(description = "是否有更多数据")
    public boolean hasMore() {
        return events != null && events.size() == size;
    }
    
    @Schema(description = "下一页页码")
    public int getNextPage() {
        return hasMore() ? page + 1 : page;
    }
}