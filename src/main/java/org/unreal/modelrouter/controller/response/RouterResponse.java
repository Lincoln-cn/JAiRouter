package org.unreal.modelrouter.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 统一API响应格式
 * @param <T> 响应数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RouterResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private String errorCode;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    public RouterResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public RouterResponse(final boolean success, final String message, final T data) {
        this();
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public RouterResponse(final boolean success, final String message, final T data,final String errorCode) {
        this(success, message, data);
        this.errorCode = errorCode;
    }

    // 成功响应
    public static <T> RouterResponse<T> success(final T data, final String message) {
        return new RouterResponse<>(true, message, data);
    }

    public static <T> RouterResponse<T> success(final T data) {
        return success(data, "操作成功");
    }

    // 将无参 success 和 带 message 的 success 改为泛型静态方法，避免与 success(T) 发生重载歧义
    public static <T> RouterResponse<T> success() {
        return success(null, "操作成功");
    }

    public static <T> RouterResponse<T> success(final String message) {
        return success(null, message);
    }

    // 错误响应
    public static <T> RouterResponse<T> error(final String message, final String errorCode) {
        return new RouterResponse<>(false, message, null, errorCode);
    }

    public static <T> RouterResponse<T> error(final String message) {
        return error(message, null);
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(final boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(final T data) {
        this.data = data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(final String errorCode) {
        this.errorCode = errorCode;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}