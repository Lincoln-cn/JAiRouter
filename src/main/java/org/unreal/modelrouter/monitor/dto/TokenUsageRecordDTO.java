package org.unreal.modelrouter.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Token 使用量记录 DTO
 * 用于记录单次 Token 使用量
 *
 * @author JAiRouter Team
 * @since 1.9.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsageRecordDTO {

    /**
     * 追踪 ID
     */
    private String traceId;

    /**
     * 服务类型 (chat, embedding, rerank, tts, stt, imgGen 等)
     */
    private String serviceType;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型提供商/适配器类型
     */
    private String provider;

    /**
     * 实例名称
     */
    private String instanceName;

    /**
     * 实例基础 URL
     */
    private String instanceUrl;

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
     * 是否成功
     */
    private Boolean isSuccess;

    /**
     * 错误代码
     */
    private String errorCode;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 响应时间 (毫秒)
     */
    private Long responseTimeMs;

    /**
     * 请求时间
     */
    private LocalDateTime occurredAt;

    /**
     * 元数据
     */
    private String metadata;
}
