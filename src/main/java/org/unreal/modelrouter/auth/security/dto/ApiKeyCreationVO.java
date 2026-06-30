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
 * API Key 创建响应 VO（包含 keyValue，仅创建时返回一次）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyCreationVO {

    /**
     * API Key 唯一标识符
     */
    private String keyId;

    /**
     * API Key 值（仅在创建时返回，请妥善保存）
     */
    private String keyValue;

    /**
     * API Key 描述信息
     */
    private String description;

    /**
     * 权限列表
     */
    private List<String> permissions;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createdAt;

    /**
     * 过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime expiresAt;

    /**
     * 上次轮换时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime lastRotatedAt;

    /**
     * 每日请求上限
     */
    private Long dailyRequestLimit;

    /**
     * 每日 Token 使用上限
     */
    private Long dailyTokenLimit;

    /**
     * 每分钟请求速率限制
     */
    private Integer rateLimitPerMinute;

    /**
     * 配额告警阈值
     */
    private Double quotaAlertThreshold;

    /**
     * 警告信息：密钥值只会显示一次
     */
    @Builder.Default
    private String warning = "密钥值只会显示一次，请妥善保存！";
}