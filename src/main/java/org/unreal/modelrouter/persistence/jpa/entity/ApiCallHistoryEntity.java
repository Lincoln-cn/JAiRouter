package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * API 调用历史实体类
 * 记录每次 API 请求的完整信息（请求、路由、响应、性能）
 *
 * @author JAiRouter Team
 * @since 2.7.8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "api_call_history", indexes = {
    @Index(name = "idx_call_history_trace_id", columnList = "trace_id"),
    @Index(name = "idx_call_history_created_at", columnList = "created_at"),
    @Index(name = "idx_call_history_model", columnList = "model_name"),
    @Index(name = "idx_call_history_api_key", columnList = "api_key_id"),
    @Index(name = "idx_call_history_service_type", columnList = "service_type"),
    @Index(name = "idx_call_history_status", columnList = "http_status_code"),
    @Index(name = "idx_call_history_date", columnList = "request_date, request_hour")
})
public class ApiCallHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 分布式追踪 ID
     */
    @Column(name = "trace_id", nullable = false, length = 100)
    private String traceId;

    /**
     * 请求唯一 ID
     */
    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    // ========== 请求信息 ==========

    /**
     * HTTP 请求方法 (GET, POST, PUT, DELETE)
     */
    @Column(name = "request_method", nullable = false, length = 10)
    private String requestMethod;

    /**
     * 请求路径 (如 /v1/chat/completions)
     */
    @Column(name = "request_path", nullable = false, length = 500)
    private String requestPath;

    /**
     * 请求体摘要（前200字符 + token估算）
     */
    @Column(name = "request_body_summary", length = 1000)
    private String requestBodySummary;

    /**
     * Content-Type
     */
    @Column(name = "content_type", length = 100)
    private String contentType;

    // ========== 路由信息 ==========

    /**
     * 服务类型 (chat, embedding, rerank, tts, stt, imgGen)
     */
    @Column(name = "service_type", nullable = false, length = 50)
    private String serviceType;

    /**
     * 模型名称
     */
    @Column(name = "model_name", nullable = false, length = 255)
    private String modelName;

    /**
     * 适配器类型 (gpustack, ollama, vllm 等)
     */
    @Column(name = "provider", length = 100)
    private String provider;

    /**
     * 路由到的实例名称
     */
    @Column(name = "instance_name", length = 255)
    private String instanceName;

    /**
     * 实例 URL
     */
    @Column(name = "instance_url", length = 500)
    private String instanceUrl;

    // ========== 响应信息 ==========

    /**
     * HTTP 响应状态码
     */
    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    /**
     * 响应体摘要（错误消息或成功标志）
     */
    @Column(name = "response_body_summary", length = 500)
    private String responseBodySummary;

    // ========== Token 统计 ==========

    /**
     * 输入 token 数
     */
    @Column(name = "prompt_tokens", nullable = false)
    @Builder.Default
    private Long promptTokens = 0L;

    /**
     * 输出 token 数
     */
    @Column(name = "completion_tokens", nullable = false)
    @Builder.Default
    private Long completionTokens = 0L;

    /**
     * 总 token 数
     */
    @Column(name = "total_tokens", nullable = false)
    @Builder.Default
    private Long totalTokens = 0L;

    // ========== 性能指标 ==========

    /**
     * 响应时间 (毫秒)
     */
    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    /**
     * 请求是否成功
     */
    @Column(name = "is_success", nullable = false)
    @Builder.Default
    private Boolean isSuccess = true;

    /**
     * 错误码
     */
    @Column(name = "error_code", length = 100)
    private String errorCode;

    /**
     * 错误消息
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    // ========== 调用者信息 ==========

    /**
     * API Key ID
     */
    @Column(name = "api_key_id", length = 255)
    private String apiKeyId;

    /**
     * 用户 ID
     */
    @Column(name = "user_id", length = 255)
    private String userId;

    /**
     * 客户端 IP
     */
    @Column(name = "client_ip", length = 50)
    private String clientIp;

    /**
     * User-Agent
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    // ========== 限流/熔断 ==========

    /**
     * 是否被限流
     */
    @Column(name = "rate_limited", nullable = false)
    @Builder.Default
    private Boolean rateLimited = false;

    /**
     * 是否触发熔断
     */
    @Column(name = "circuit_broken", nullable = false)
    @Builder.Default
    private Boolean circuitBroken = false;

    // ========== 时间字段 ==========

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 请求日期 (YYYY-MM-DD，用于按日查询)
     */
    @Column(name = "request_date", nullable = false, length = 10)
    private String requestDate;

    /**
     * 请求小时 (0-23，用于按小时统计)
     */
    @Column(name = "request_hour", nullable = false)
    private Integer requestHour;

    /**
     * 在保存前自动设置时间相关字段
     */
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (requestDate == null) {
            requestDate = createdAt.toLocalDate().toString();
        }
        if (requestHour == null) {
            requestHour = createdAt.getHour();
        }
    }
}
