package org.unreal.modelrouter.auth.security.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
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
public class ApiKeyCreateRequest {

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
     * 权限列表
     * 可选值：READ, WRITE, DELETE, ADMIN
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
     * 每日请求上限（可选，0 表示无限制）
     */
    @Builder.Default
    private Long dailyRequestLimit = 0L;

    /**
     * 密钥轮换周期（天数），0 表示不自动轮换
     */
    @Builder.Default
    private Integer rotationPeriodDays = 0;
}