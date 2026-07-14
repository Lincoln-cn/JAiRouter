package org.unreal.modelrouter.auth.security.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 信息 VO（不包含敏感的 keyValue）
 * 用于 API Key 列表和详情查询返回
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyVO {

    /**
     * API Key 唯一标识符
     */
    private String keyId;

    /**
     * API Key 描述信息
     */
    private String description;

    /**
     * 创建者用户名
     */
    private String createdBy;

    /**
     * 创建者 IP 地址
     */
    private String creatorIpAddress;

    /**
     * 权限列表
     */
    private List<String> permissions;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 是否已过期
     */
    private boolean expired;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createdAt;

    /**
     * 密钥轮换周期（天数），0 表示不自动轮换
     */
    private int rotationPeriodDays;

    /**
     * 上次轮换时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime lastRotatedAt;

    /**
     * 是否需要轮换
     */
    private boolean needsRotation;

    /**
     * 过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime expiresAt;

    /**
     * 最后使用时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime lastUsedAt;

    /**
     * 总请求次数
     */
    private Long totalRequests;

    /**
     * 成功请求次数
     */
    private Long successfulRequests;

    /**
     * 失败请求次数
     */
    private Long failedRequests;

    // ===== 配额配置 =====

    /**
     * 每日请求上限（0 表示无限制）
     */
    private Long dailyRequestLimit;

    /**
     * 每日 Token 使用上限（0 表示无限制）
     */
    private Long dailyTokenLimit;

    /**
     * 每分钟请求速率限制（0 表示无限制）
     */
    private Integer rateLimitPerMinute;

    /**
     * 配额告警阈值（0.0-1.0）
     */
    private Double quotaAlertThreshold;

    // ===== 配额使用量 =====

    /**
     * 今日请求次数
     */
    private Long todayRequestCount;

    /**
     * 今日 Token 使用量
     */
    private Long todayTokenUsage;

    /**
     * 是否触发配额告警
     */
    private boolean quotaAlertTriggered;

    /**
     * 剩余有效天数（-1 表示已过期，null 表示永不过期）
     */
    private Integer remainingDays;

    /**
     * 计算剩余天数
     */
    public void calculateRemainingDays() {
        if (expiresAt == null) {
            remainingDays = null;
        } else {
            long diffDays = java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), expiresAt);
            remainingDays = diffDays < 0 ? -1 : (int) diffDays;
        }
    }
}