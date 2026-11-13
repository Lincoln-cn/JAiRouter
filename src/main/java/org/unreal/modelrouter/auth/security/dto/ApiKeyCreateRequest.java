package org.unreal.modelrouter.auth.security.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建 API Key 请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ApiKeyCreateRequest {

    /**
     * API Key ID（可选，如果不提供则自动生成）
     */
    @Size(max = 64, message = "密钥ID长度不能超过64字符")
    private String keyId;

    /**
     * API Key 描述信息
     */
    @Size(max = 128, message = "描述长度不能超过128字符")
    private String description;

    /**
     * 权限列表 —— 这里填的是<b>服务类型</b>（chat / embedding / rerank / tts / stt / imgGen / imgEdit），
     * 不是 READ / WRITE / DELETE 这类动作。
     *
     * <p>留空（或不传）等价于「不限服务」：服务端会展开成全部服务类型（v3.8.6 起）。此前留空会
     * 原样落库，Key 建得出来却调不通任何服务（403 {@code Caller does not have permission for
     * service: ...}）。旧格式 READ / WRITE / DELETE / ADMIN 仍被接受，同样展开成全部服务类型。</p>
     */
    private List<String> permissions;

    /**
     * 是否启用（默认启用）
     */
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 过期时间（可选，不设置则永不过期）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime expiresAt;

    /**
     * 允许使用的 IP 白名单（可选）
     */
    private List<String> allowedIpAddresses;

    /**
     * 每小时请求上限（可选，0 表示无限制）
     */
    @Builder.Default
    private Long hourlyRequestLimit = 0L;

    /**
     * 每小时 Token 使用上限（可选，0 表示无限制）
     */
    @Builder.Default
    private Long hourlyTokenLimit = 0L;

    /**
     * 每日请求上限（可选，0 表示无限制）
     */
    @Builder.Default
    private Long dailyRequestLimit = 0L;

    /**
     * 每日 Token 使用上限（可选，0 表示无限制）
     */
    @Builder.Default
    private Long dailyTokenLimit = 0L;

    /**
     * 每月请求上限（可选，0 表示无限制）
     */
    @Builder.Default
    private Long monthlyRequestLimit = 0L;

    /**
     * 每月 Token 使用上限（可选，0 表示无限制）
     */
    @Builder.Default
    private Long monthlyTokenLimit = 0L;

    /**
     * 每分钟请求速率限制（可选，0 表示无限制）
     */
    @Builder.Default
    private Integer rateLimitPerMinute = 0;

    /**
     * 配额告警阈值（0.0-1.0，达到此比例时触发告警，默认 0.8 即 80%）
     */
    @Builder.Default
    private Double quotaAlertThreshold = 0.8;

    /**
     * 密钥轮换周期（天数），0 表示不自动轮换
     */
    @Builder.Default
    private Integer rotationPeriodDays = 0;
}