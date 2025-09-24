package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 安全错误响应DTO
 * 提供统一的安全异常响应格式
 */
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

    public SecurityErrorResponse(LocalDateTime timestamp, int status, String error, String message, String errorCode, String path, String requestId) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.errorCode = errorCode;
        this.path = path;
        this.requestId = requestId;
    }

    public SecurityErrorResponse() {
    }

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

    public static SecurityErrorResponseBuilder builder() {
        return new SecurityErrorResponseBuilder();
    }

    public LocalDateTime getTimestamp() {
        return this.timestamp;
    }

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return this.status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return this.error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorCode() {
        return this.errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getPath() {
        return this.path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRequestId() {
        return this.requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String toString() {
        return "SecurityErrorResponse(timestamp=" + this.getTimestamp() + ", status=" + this.getStatus() + ", error=" + this.getError() + ", message=" + this.getMessage() + ", errorCode=" + this.getErrorCode() + ", path=" + this.getPath() + ", requestId=" + this.getRequestId() + ")";
    }

    public static class SecurityErrorResponseBuilder {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private String errorCode;
        private String path;
        private String requestId;

        SecurityErrorResponseBuilder() {
        }

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        public SecurityErrorResponseBuilder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public SecurityErrorResponseBuilder status(int status) {
            this.status = status;
            return this;
        }

        public SecurityErrorResponseBuilder error(String error) {
            this.error = error;
            return this;
        }

        public SecurityErrorResponseBuilder message(String message) {
            this.message = message;
            return this;
        }

        public SecurityErrorResponseBuilder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public SecurityErrorResponseBuilder path(String path) {
            this.path = path;
            return this;
        }

        public SecurityErrorResponseBuilder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public SecurityErrorResponse build() {
            return new SecurityErrorResponse(this.timestamp, this.status, this.error, this.message, this.errorCode, this.path, this.requestId);
        }

        public String toString() {
            return "SecurityErrorResponse.SecurityErrorResponseBuilder(timestamp=" + this.timestamp + ", status=" + this.status + ", error=" + this.error + ", message=" + this.message + ", errorCode=" + this.errorCode + ", path=" + this.path + ", requestId=" + this.requestId + ")";
        }
    }
}