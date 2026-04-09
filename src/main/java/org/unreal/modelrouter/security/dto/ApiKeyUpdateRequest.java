package org.unreal.modelrouter.security.dto;

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
 * 更新 API Key 请求 DTO
 * 注意：keyValue 不可更新
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyUpdateRequest {

    /**
     * API Key 描述信息
     */
    @Size(max = 128, message = "描述长度不能超过128字符")
    private String description;

    /**
     * 权限列表
     */
    private List<String> permissions;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime expiresAt;

    /**
     * 允许使用的 IP 白名单
     */
    private List<String> allowedIpAddresses;

    /**
     * 每日请求上限（0 表示无限制）
     */
    private Long dailyRequestLimit;
}