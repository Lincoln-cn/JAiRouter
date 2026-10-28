package org.unreal.modelrouter.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 下游服务异常
 * 用于区分本地服务异常和下游服务异常
 */
public class DownstreamServiceException extends RuntimeException {

    private final HttpStatus statusCode;

    /** #717：下游错误体里的错误码（{@code error.code}/{@code error.type}），无则为状态码串。 */
    private final String upstreamCode;

    /** #717：下游 {@code Retry-After}（秒），无则 {@code null}。 */
    private final Long retryAfterSeconds;

    public DownstreamServiceException(final String message, final HttpStatus statusCode) {
        this(message, statusCode, null, null);
    }

    public DownstreamServiceException(final String message, final HttpStatus statusCode, final Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.upstreamCode = null;
        this.retryAfterSeconds = null;
    }

    /**
     * #717：带下游错误码与 {@code Retry-After} 的构造。
     *
     * @param message           面向调用方的消息（已含下游语义）
     * @param statusCode        下游状态码
     * @param upstreamCode      下游错误码；{@code null} 表示未提供
     * @param retryAfterSeconds 下游要求的退避秒数；{@code null} 表示未提供
     */
    public DownstreamServiceException(final String message, final HttpStatus statusCode,
                                      final String upstreamCode, final Long retryAfterSeconds) {
        super(message);
        this.statusCode = statusCode;
        this.upstreamCode = upstreamCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus getStatusCode() {
        return statusCode;
    }

    public String getUpstreamCode() {
        return upstreamCode;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
