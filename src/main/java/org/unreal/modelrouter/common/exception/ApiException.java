package org.unreal.modelrouter.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 控制台面（{@code /api/**}）业务异常：携带对外错误码与 HTTP 状态码。
 *
 * <p><b>为什么需要它：</b>控制器此前普遍把失败「吞」成成功返回 ——
 * </p>
 * <pre>
 *   .onErrorResume(e -&gt; Mono.just(RouterResponse.error("API密钥不存在", "NOT_FOUND")))
 * </pre>
 * <p>{@code Mono.just(...)} 意味着这条链路正常完成，于是 HTTP 状态码恒为 200，
 * {@link org.unreal.modelrouter.common.exceptionhandler.ReactiveGlobalExceptionHandler}
 * 根本不会被触发（详见 issue #94）。调用方无法用状态码判别成败。
 * </p>
 *
 * <p><b>用法：</b>把上面的写法换成
 * </p>
 * <pre>
 *   .onErrorResume(e -&gt; Mono.error(ApiException.of("NOT_FOUND", "API密钥不存在")))
 * </pre>
 * <p>异常最终由 {@code GlobalControllerExceptionHandler} 处理，**响应体形状与原实现完全一致**
 * （仍是 {@code RouterResponse.success=false + message + errorCode}），只有 HTTP 状态码被修正。
 * </p>
 *
 * @author JAiRouter Team
 * @since 3.2.2
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 对外的业务错误码（保持与原 {@code RouterResponse.error(msg, code)} 的 code 一致）。 */
    private final String errorCode;

    /** 响应用的 HTTP 状态码。 */
    private final HttpStatus status;

    /**
     * 构造业务异常（状态码由 {@link #resolveStatus(String)} 依据错误码推导）。
     *
     * @param errorCode 业务错误码
     * @param message   对外错误信息
     */
    public ApiException(final String errorCode, final String message) {
        this(errorCode, message, resolveStatus(errorCode));
    }

    /**
     * 构造业务异常（显式指定状态码，用于错误码无法推导的场合）。
     *
     * @param errorCode 业务错误码
     * @param message   对外错误信息
     * @param status    HTTP 状态码
     */
    public ApiException(final String errorCode, final String message, final HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
    }

    /**
     * 便捷工厂。
     *
     * @param errorCode 业务错误码
     * @param message   对外错误信息
     * @return 业务异常
     */
    public static ApiException of(final String errorCode, final String message) {
        return new ApiException(errorCode, message);
    }

    /**
     * 便捷工厂（显式状态码）。
     *
     * @param errorCode 业务错误码
     * @param message   对外错误信息
     * @param status    HTTP 状态码
     * @return 业务异常
     */
    public static ApiException of(final String errorCode, final String message, final HttpStatus status) {
        return new ApiException(errorCode, message, status);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /**
     * 依据业务错误码推导 HTTP 状态码。
     *
     * <p>规则（按优先级）：</p>
     * <ol>
     *   <li>纯三位数字（如 {@code "400"}）→ 直接解析为该状态码</li>
     *   <li>精确匹配已知语义码</li>
     *   <li>后缀/前缀规则兜底（{@code *_NOT_FOUND} → 404、{@code INVALID_*} → 400 …）</li>
     *   <li>其余一律 500</li>
     * </ol>
     *
     * <p>之所以用规则而不是一张穷举表：仓库里现存 {@code *NOT_FOUND} / {@code INVALID_*} /
     * {@code *_FAILED} 三类命名占了绝大多数，规则能覆盖已有与后续新增的同类错误码。</p>
     *
     * @param errorCode 业务错误码（可为空）
     * @return 对应 HTTP 状态码，无法判定时返回 500
     */
    public static HttpStatus resolveStatus(final String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        final String code = errorCode.trim();

        // 1) 纯数字状态码
        if (code.matches("\\d{3}")) {
            final HttpStatus resolved = HttpStatus.resolve(Integer.parseInt(code));
            return resolved == null ? HttpStatus.INTERNAL_SERVER_ERROR : resolved;
        }

        // 2) 精确匹配
        switch (code) {
            case "NOT_FOUND":
                return HttpStatus.NOT_FOUND;
            case "INVALID_REQUEST":
                return HttpStatus.BAD_REQUEST;
            case "INTERNAL_ERROR":
                return HttpStatus.INTERNAL_SERVER_ERROR;
            case "BUILTIN_READONLY":
            case "RESTART_REQUIRED":
                return HttpStatus.FORBIDDEN;
            case "LOGIN_FAILED":
                return HttpStatus.UNAUTHORIZED;
            case "SERVICE_NOT_AVAILABLE":
                return HttpStatus.SERVICE_UNAVAILABLE;
            default:
                break;
        }

        // 3) 规则兜底
        if (code.endsWith("_NOT_FOUND")) {
            return HttpStatus.NOT_FOUND;
        }
        if (code.startsWith("INVALID_")) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code.startsWith("CONFLICT")) {
            return HttpStatus.CONFLICT;
        }
        if (code.startsWith("UNAUTHORIZED") || code.startsWith("UNAUTHENTICATED")) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code.startsWith("FORBIDDEN")) {
            return HttpStatus.FORBIDDEN;
        }
        if (code.contains("NOT_AVAILABLE") || code.startsWith("UNAVAILABLE")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (code.startsWith("RATE_LIMITED") || code.startsWith("TOO_MANY")) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
