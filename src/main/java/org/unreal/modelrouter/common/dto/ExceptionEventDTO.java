package org.unreal.modelrouter.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 异常事件 DTO
 * 用于异常管理 API 的数据传输
 * 
 * @author JAiRouter Team
 * @since 1.9.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionEventDTO {

    /**
     * 事件 ID
     */
    private String eventId;

    /**
     * 异常类型（完整类名）
     */
    private String exceptionType;

    /**
     * 异常消息
     */
    private String exceptionMessage;

    /**
     * 脱敏后的异常消息
     */
    private String sanitizedMessage;

    /**
     * 操作名称
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
     * HTTP 状态码
     */
    private String httpStatus;

    /**
     * 追踪 ID
     */
    private String traceId;

    /**
     * 客户端 IP
     */
    private String clientIp;

    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 出现次数
     */
    private Long occurrenceCount;

    /**
     * 首次出现时间
     */
    private LocalDateTime firstOccurrence;

    /**
     * 最后一次出现时间
     */
    private LocalDateTime lastOccurrence;

    /**
     * 异常发生时间
     */
    private LocalDateTime occurredAt;

    /**
     * 是否已聚合
     */
    private Boolean isAggregated;
}
