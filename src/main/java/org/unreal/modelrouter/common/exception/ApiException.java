package org.unreal.modelrouter.common.exception;

import org.springframework.http.HttpStatus;
import org.unreal.modelrouter.common.constants.ErrorCode;

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
        // issue #370：先查错误码注册表（单一事实来源）；未登记、或该码的状态不由规则链决定时，
        // 落回原有命名规则 —— 保证「引入注册表零行为变化」（见 ErrorCode.isResolvedByRuleChain）。
        if (ErrorCode.isResolvedByRuleChain(errorCode)) {
            return ErrorCode.of(errorCode).orElseThrow().status();
        }
        return legacyResolveStatus(errorCode);
    }

    /**
     * 历史兜底规则链（issue #370 之前的唯一实现）—— 用于两类码：**未登记**在 {@link ErrorCode} 里的码，
     * 以及**已登记但状态不由规则链决定**的码（渲染路径非 {@link ErrorCode.RenderPath#API_EXCEPTION}）。
     *
     * <p>保留原样是为了**零行为变化**：未登记的码（上游透传、临时新增字面量等）仍按原命名规则推导。
     * {@code ErrorCodeRegistryTest} 会用不变量断言钉住「登记码走注册表、未登记码走本规则链」。</p>
     *
     * <p>⚠️ issue #405：本表里的精确匹配项也要与**端点实测状态**保持一致（它决定「响应体路径」的码
     * 经 {@code resolveStatus} 推导时的值）——{@code BUILTIN_READONLY} 403、{@code RESTART_REQUIRED} 400。</p>
     */
    static HttpStatus legacyResolveStatus(final String errorCode) {
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
                // issue #405：内置项只读是授权语义 ⇒ 与端点一致，两端都是 403
                return HttpStatus.FORBIDDEN;
            case "RESTART_REQUIRED":
                // issue #405：需重启 = 请求校验语义 ⇒ 与端点一致，两端都是 400
                return HttpStatus.BAD_REQUEST;
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
