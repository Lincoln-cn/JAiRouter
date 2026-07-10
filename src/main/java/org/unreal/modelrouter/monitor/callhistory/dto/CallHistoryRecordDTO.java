package org.unreal.modelrouter.monitor.callhistory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 调用历史记录请求 DTO
 *
 * @author JAiRouter Team
 * @since 2.7.8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallHistoryRecordDTO {

    /**
     * 分布式追踪 ID
     */
    private String traceId;

    /**
     * 请求唯一 ID
     */
    private String requestId;

    /**
     * HTTP 请求方法
     */
    private String requestMethod;

    /**
     * 请求路径
     */
    private String requestPath;

    /**
     * 请求体（用于生成摘要）
     */
    private String requestBody;

    /**
     * Content-Type
     */
    private String contentType;

    /**
     * 服务类型
     */
    private String serviceType;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 适配器类型
     */
    private String provider;

    /**
     * 实例名称
     */
    private String instanceName;

    /**
     * 实例 URL
     */
    private String instanceUrl;

    /**
     * HTTP 响应状态码
     */
    private Integer httpStatusCode;

    /**
     * 响应体（用于生成摘要）
     */
    private String responseBody;

    /**
     * 输入 token 数
     */
    private Long promptTokens;

    /**
     * 输出 token 数
     */
    private Long completionTokens;

    /**
     * 总 token 数
     */
    private Long totalTokens;

    /**
     * 响应时间 (毫秒)
     */
    private Long responseTimeMs;

    /**
     * 请求是否成功
     */
    private Boolean isSuccess;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * API Key ID
     */
    private String apiKeyId;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 客户端 IP
     */
    private String clientIp;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 是否被限流
     */
    private Boolean rateLimited;

    /**
     * 是否触发熔断
     */
    private Boolean circuitBroken;
}
