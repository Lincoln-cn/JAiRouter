package org.unreal.modelrouter.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;

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
    private LocalDateTime timestamp;

    public RouterResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public RouterResponse(boolean success, String message, T data) {
        this();
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public RouterResponse(boolean success, String message, T data, String errorCode) {
        this(success, message, data);
        this.errorCode = errorCode;
    }

    // 成功响应
    public static <T> RouterResponse<T> success(T data, String message) {
        return new RouterResponse<>(true, message, data);
    }

    public static <T> RouterResponse<T> success(T data) {
        return success(data, "操作成功");
    }

    public static RouterResponse<Void> success() {
        return success(null, "操作成功");
    }

    public static RouterResponse<Void> success(String message) {
        return success(null, message);
    }

    // 错误响应
    public static <T> RouterResponse<T> error(String message, String errorCode) {
        return new RouterResponse<>(false, message, null, errorCode);
    }

    public static <T> RouterResponse<T> error(String message) {
        return error(message, null);
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}