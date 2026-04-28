package org.unreal.modelrouter.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 异常查询请求 DTO
 * 
 * @author JAiRouter Team
 * @since 1.9.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionQueryRequest {

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 异常类型（支持模糊匹配）
     */
    private String exceptionType;

    /**
     * 操作名称（支持模糊匹配）
     */
    private String operation;

    /**
     * 错误代码
     */
    private String errorCode;

    /**
     * 错误分类
     */
    private String errorCategory;

    /**
     * 追踪 ID
     */
    private String traceId;

    /**
     * 客户端 IP
     */
    private String clientIp;

    /**
     * 是否仅查询聚合事件
     */
    private Boolean aggregatedOnly;

    /**
     * 页码（从 0 开始）
     */
    @Builder.Default
    private Integer page = 0;

    /**
     * 每页大小
     */
    @Builder.Default
    private Integer size = 20;

    /**
     * 排序字段
     */
    @Builder.Default
    private String sortBy = "occurredAt";

    /**
     * 排序方向（asc 或 desc）
     */
    @Builder.Default
    private String sortDirection = "desc";
}
