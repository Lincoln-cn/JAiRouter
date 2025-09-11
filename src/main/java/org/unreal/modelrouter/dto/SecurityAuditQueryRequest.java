package org.unreal.modelrouter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安全审计查询请求DTO
 */
@Data
@Schema(description = "安全审计查询请求")
public class SecurityAuditQueryRequest {
    
    @Schema(description = "开始时间", example = "2024-01-01T00:00:00")
    private LocalDateTime startTime;
    
    @Schema(description = "结束时间", example = "2024-01-02T00:00:00")
    private LocalDateTime endTime;
    
    @Schema(description = "事件类型", example = "AUTHENTICATION_FAILURE")
    private String eventType;
    
    @Schema(description = "用户ID", example = "user123")
    private String userId;
    
    @Schema(description = "客户端IP地址", example = "192.168.1.100")
    private String clientIp;
    
    @Schema(description = "操作是否成功", example = "false")
    private Boolean success;
    
    @Schema(description = "执行的操作", example = "AUTHENTICATE")
    private String action;
    
    @Schema(description = "访问的资源", example = "/api/v1/chat")
    private String resource;
    
    @Schema(description = "失败原因关键词", example = "invalid")
    private String failureReason;
    
    @Schema(description = "页码，从0开始", example = "0")
    private int page = 0;
    
    @Schema(description = "每页大小，最大100", example = "20")
    private int size = 20;
    
    @Schema(description = "排序字段", example = "timestamp")
    private String sortBy = "timestamp";
    
    @Schema(description = "排序方向", example = "DESC", allowableValues = {"ASC", "DESC"})
    private String sortDirection = "DESC";
}