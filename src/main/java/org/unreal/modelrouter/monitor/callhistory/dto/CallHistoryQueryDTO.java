package org.unreal.modelrouter.monitor.callhistory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 调用历史查询请求 DTO
 *
 * @author JAiRouter Team
 * @since 2.7.8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallHistoryQueryDTO {

    /**
     * 开始时间 (ISO-8601)
     */
    private String startTime;

    /**
     * 结束时间 (ISO-8601)
     */
    private String endTime;

    /**
     * 模型名称（精确匹配）
     */
    private String modelName;

    /**
     * 服务类型
     */
    private String serviceType;

    /**
     * API Key ID
     */
    private String apiKeyId;

    /**
     * 是否成功（true/false/null 表示全部）
     */
    private Boolean isSuccess;

    /**
     * HTTP 状态码
     */
    private Integer httpStatusCode;

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
    private String sortField = "createdAt";

    /**
     * 排序方向（asc/desc）
     */
    @Builder.Default
    private String sortDirection = "desc";
}
