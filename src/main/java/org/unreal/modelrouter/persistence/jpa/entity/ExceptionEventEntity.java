package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 异常事件实体类
 * 存储所有异常事件的详细信息
 * 
 * @author JAiRouter Team
 * @since 1.9.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "exception_events", indexes = {
    @Index(name = "idx_exception_event_id", columnList = "event_id"),
    @Index(name = "idx_exception_type", columnList = "exception_type"),
    @Index(name = "idx_exception_occurred_at", columnList = "occurred_at"),
    @Index(name = "idx_exception_operation", columnList = "operation"),
    @Index(name = "idx_exception_error_code", columnList = "error_code"),
    @Index(name = "idx_exception_error_category", columnList = "error_category"),
    // #1189：异常带归属两列，按归属过滤是 self 读的唯一手段，必须走索引
    @Index(name = "idx_exception_user", columnList = "user_id"),
    @Index(name = "idx_exception_api_key", columnList = "api_key_id"),
    @Index(name = "idx_exception_trace_id", columnList = "trace_id"),
    @Index(name = "idx_exception_aggregated", columnList = "is_aggregated"),
    @Index(name = "idx_exception_last_occurrence", columnList = "last_occurrence")
})
public class ExceptionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 事件唯一标识符
     */
    @Column(name = "event_id", unique = true, nullable = false, length = 255)
    private String eventId;

    /**
     * 异常类型（完整类名）
     */
    @Column(name = "exception_type", nullable = false, length = 500)
    private String exceptionType;

    /**
     * 异常消息
     */
    @Column(name = "exception_message", length = 1000)
    private String exceptionMessage;

    /**
     * 脱敏后的异常消息
     */
    @Column(name = "sanitized_message", length = 1000)
    private String sanitizedMessage;

    /**
     * 脱敏后的堆栈跟踪
     */
    @Column(name = "sanitized_stack_trace", columnDefinition = "TEXT")
    private String sanitizedStackTrace;

    /**
     * 操作名称
     */
    @Column(name = "operation", nullable = false, length = 255)
    private String operation;

    /**
     * 错误代码
     */
    @Column(name = "error_code", length = 100)
    private String errorCode;

    /**
     * 错误分类
     */
    @Column(name = "error_category", length = 50)
    private String errorCategory;

    /**
     * HTTP 状态码
     */
    @Column(name = "http_status", length = 10)
    private String httpStatus;

    /**
     * 触发该异常的账号（#1189）。
     *
     * <p>异常列表与异常统计要按账号切割（{@code monitoring:exceptions:self:read}），
     * 而此前这张表只有 {@code trace_id}、没有归属 —— 导致「本人读」码只能是死码。
     * 新异常由 {@code ReactiveGlobalExceptionHandler} 从 exchange 属性写入；
     * 历史行由 V19 迁移按 {@code trace_id} 关联 {@code api_call_history} 回填，
     * 关联不上的留空（那是事实上的「不知道属于谁」，不猜）。</p>
     */
    @Column(name = "user_id", length = 255)
    private String userId;

    /**
     * 触发该异常的 API Key（#1189）。语义与 {@link #userId} 相同，二者至少有一个可为空。
     */
    @Column(name = "api_key_id", length = 255)
    private String apiKeyId;

    /**
     * 追踪 ID
     */
    @Column(name = "trace_id", length = 100)
    private String traceId;

    /**
     * 跨度 ID
     */
    @Column(name = "span_id", length = 100)
    private String spanId;

    /**
     * 请求 ID
     */
    @Column(name = "request_id", length = 255)
    private String requestId;

    /**
     * 客户端 IP
     */
    @Column(name = "client_ip", length = 50)
    private String clientIp;

    /**
     * 用户代理
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * 服务名称
     */
    @Column(name = "service_name", length = 100)
    private String serviceName;

    /**
     * 服务类型（chat/embedding/rerank/tts 等）
     */
    @Column(name = "service_type", length = 50)
    private String serviceType;

    /**
     * 模型名称
     */
    @Column(name = "model_name", length = 255)
    private String modelName;

    /**
     * 适配器/提供商类型（gpuStack/ollama/vllm 等）
     */
    @Column(name = "provider", length = 100)
    private String provider;

    /**
     * 实例名称
     */
    @Column(name = "instance_name", length = 255)
    private String instanceName;

    /**
     * 响应时间（毫秒）
     */
    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    /**
     * 方法名
     */
    @Column(name = "method_name", length = 255)
    private String methodName;

    /**
     * 类名
     */
    @Column(name = "class_name", length = 500)
    private String className;

    /**
     * 行号
     */
    @Column(name = "line_number")
    private Integer lineNumber;

    /**
     * 出现次数（用于聚合）
     */
    @Column(name = "occurrence_count", nullable = false)
    @Builder.Default
    private Long occurrenceCount = 1L;

    /**
     * 首次出现时间
     */
    @Column(name = "first_occurrence", nullable = false)
    private LocalDateTime firstOccurrence;

    /**
     * 最后一次出现时间
     */
    @Column(name = "last_occurrence", nullable = false)
    private LocalDateTime lastOccurrence;

    /**
     * 元数据（JSON 格式）
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * 异常发生时间
     */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 是否已聚合
     */
    @Column(name = "is_aggregated", nullable = false)
    @Builder.Default
    private Boolean isAggregated = false;

    /**
     * 更新最后出现时间和出现次数
     */
    public void incrementOccurrence() {
        this.occurrenceCount = this.occurrenceCount + 1;
        this.lastOccurrence = LocalDateTime.now();
    }

    /**
     * 设置聚合标记
     */
    public void markAsAggregated() {
        this.isAggregated = true;
    }
}
