package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 安全错误响应DTO
 * 提供统一的安全异常响应格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityErrorResponse {

    /**
     * 错误发生时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * HTTP状态码
     */
    private int status;

    /**
     * HTTP状态描述
     */
    private String error;

    /**
     * 错误消息
     */
    private String message;

    /**
     * 安全错误代码
     */
    private String errorCode;

    /**
     * 请求路径
     */
    private String path;

    /**
     * 请求ID（可选，用于追踪）
     */
    private String requestId;

    /**
     * 创建认证错误响应
     */
    public static SecurityErrorResponse authenticationError(String message, String errorCode) {
        return SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(401)
                .error("Unauthorized")
                .message(message)
                .errorCode(errorCode)
                .build();
    }

    /**
     * 创建授权错误响应
     */
    public static SecurityErrorResponse authorizationError(String message, String errorCode) {
        return SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(403)
                .error("Forbidden")
                .message(message)
                .errorCode(errorCode)
                .build();
    }

    /**
     * 创建数据脱敏错误响应
     */
    public static SecurityErrorResponse sanitizationError(String message, String errorCode) {
        return SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(500)
                .error("Internal Server Error")
                .message(message)
                .errorCode(errorCode)
                .build();
    }
}