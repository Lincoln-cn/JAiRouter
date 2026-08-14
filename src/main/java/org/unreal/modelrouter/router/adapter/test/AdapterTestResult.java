package org.unreal.modelrouter.router.adapter.test;

/**
 * 适配器测试结果
 */
public class AdapterTestResult {

    private boolean success;
    private String status;
    private long latencyMs;
    private String message;
    private Integer httpStatusCode;
    private java.util.Map<String, Object> details;

    public AdapterTestResult() {
    }

    public AdapterTestResult(final boolean success, final String status,
                             final long latencyMs, final String message) {
        this.success = success;
        this.status = status;
        this.latencyMs = latencyMs;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(final boolean success) {
        this.success = success;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(final long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(final Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public java.util.Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(final java.util.Map<String, Object> details) {
        this.details = details;
    }

    /**
     * 测试状态常量
     */
    public static final String STATUS_CONNECTED = "CONNECTED";
    public static final String STATUS_AUTH_FAILED = "AUTH_FAILED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    public static final String STATUS_ERROR = "ERROR";

    /**
     * 创建成功结果
     */
    public static AdapterTestResult connected(final long latencyMs, final String message) {
        return new AdapterTestResult(true, STATUS_CONNECTED, latencyMs, message);
    }

    /**
     * 创建认证失败结果
     */
    public static AdapterTestResult authFailed(final long latencyMs, final String message) {
        return new AdapterTestResult(false, STATUS_AUTH_FAILED, latencyMs, message);
    }

    /**
     * 创建超时结果
     */
    public static AdapterTestResult timeout(final String message) {
        return new AdapterTestResult(false, STATUS_TIMEOUT, 0, message);
    }

    /**
     * 创建错误结果
     */
    public static AdapterTestResult error(final String message) {
        return new AdapterTestResult(false, STATUS_ERROR, 0, message);
    }
}
