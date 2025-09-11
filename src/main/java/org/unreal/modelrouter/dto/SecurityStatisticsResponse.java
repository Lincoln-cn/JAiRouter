package org.unreal.modelrouter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 安全统计响应DTO
 */
@Data
@Builder
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
}