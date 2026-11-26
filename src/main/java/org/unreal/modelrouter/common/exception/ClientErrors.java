package org.unreal.modelrouter.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;

/**
 * 构造「客户端可见错误」的唯一入口（issue #1197 / #1198）。
 *
 * <p><b>要解决的问题：</b>控制器里散落着约 75 处
 * {@code RouterResponse.error("xxx失败: " + ex.getMessage(), code)} 与
 * {@code ApiException.of(code, "xxx失败: " + ex.getMessage())}。{@code ex} 的静态类型通常是
 * {@code Exception}，于是数据库连接串、文件路径、JSON 解析位置、JWT 内部细节都会被拼进响应体
 * （CodeQL {@code java/error-message-exposure}；#92 / #93 是其中最容易触发的那两处）。</p>
 *
 * <p><b>口径：</b>对外消息 = {@code operation}（固定、面向人的短语）+ 仅当异常显式声明自己
 * 适合外显时才追加的 detail。判定见 {@link #clientSafeDetail(Throwable)}：</p>
 * <ul>
 *   <li>{@link ClientFacingException} —— 业务校验语，外显</li>
 *   <li>{@link ApiException} —— 本就是对外业务错误，外显</li>
 *   <li>{@link ResponseStatusException} —— 只取 {@code getReason()}（不用 {@code getMessage()}：
 *       后者会带 {@code "404 NOT_FOUND"} 之类前缀）</li>
 *   <li>其余（{@code SQLException} / {@code IOException} / {@code NamingException} / 反射异常…）
 *       —— <b>一律不外显</b>，原文只进日志</li>
 * </ul>
 *
 * <p><b>用法：</b></p>
 * <pre>
 *   // Reactor 链路：需要让状态码生效，用 ApiException
 *   .onErrorResume(e -&gt; Mono.error(ClientErrors.api(e, ErrorCode.LOGIN_FAILED, "登录失败")))
 *
 *   // 同步链路：直接要响应体
 *   return ResponseEntity.badRequest().body(ClientErrors.response(e, ErrorCode.ADD_FAILED, "添加失败"));
 * </pre>
 *
 * <p>日志里会带上 {@code operation} / {@code errorCode} / 原始异常（含堆栈），并已由
 * {@link org.unreal.modelrouter.common.filter.RequestIdWebFilter} 在响应头回写 {@code X-Request-Id}
 * —— 排障链路不因「响应体不再回显原文」而断。</p>
 *
 * @author JAiRouter Team
 * @since 3.9.0
 */
public final class ClientErrors {

    private static final Logger logger = LoggerFactory.getLogger(ClientErrors.class);

    /**
     * 对外消息里 operation 与 detail 之间的分隔符。
     *
     * <p>必须是半角 {@code ": "}：{@code V1ErrorBodyMapper.sanitizeMessage} 的 legacy 前缀模式
     * （如 {@code ^请求处理失败: (.+)$}）按半角匹配，换成全角会让 {@code /v1/**} 的 SDK 侧
     * 重新看到内部前缀。</p>
     */
    private static final String SEPARATOR = ": ";

    private ClientErrors() {
    }

    /**
     * 按错误码构造业务异常（供 {@code Mono.error(...)} 使用）。
     *
     * @param cause     原始异常
     * @param code      错误码（决定对外错误码与 HTTP 状态）
     * @param operation 固定短语，如「登录失败」
     * @return 业务异常
     */
    public static ApiException api(final Throwable cause, final ErrorCode code, final String operation) {
        return api(cause, code.code(), operation, code.status());
    }

    /**
     * 按错误码字符串构造业务异常（供 {@code Mono.error(...)} 使用）。
     *
     * @param cause     原始异常
     * @param errorCode 对外错误码字符串
     * @param operation 固定短语，如「登录失败」
     * @return 业务异常
     */
    public static ApiException api(final Throwable cause, final String errorCode, final String operation) {
        return api(cause, errorCode, operation, null);
    }

    private static ApiException api(final Throwable cause, final String errorCode, final String operation,
                                    final org.springframework.http.HttpStatus status) {
        if (cause instanceof ApiException apiException) {
            // 已是业务异常：保留其错误码与消息，避免二次包装丢语义
            return apiException;
        }
        logSuppressed(cause, errorCode, operation);
        final HttpStatus effectiveStatus = status == null
                ? ApiException.resolveStatus(errorCode) : status;
        return ApiException.of(errorCode, message(cause, operation), effectiveStatus);
    }

    /**
     * 按错误码构造错误响应体（供直接返回 {@code ResponseEntity} 的链路使用）。
     *
     * @param cause     原始异常
     * @param code      错误码
     * @param operation 固定短语，如「添加失败」
     * @param <T>       响应体泛型
     * @return 错误响应体
     */
    public static <T> RouterResponse<T> response(final Throwable cause, final ErrorCode code,
                                                 final String operation) {
        return response(cause, code.code(), operation);
    }

    /**
     * 按错误码字符串构造错误响应体。
     *
     * @param cause     原始异常
     * @param errorCode 对外错误码字符串
     * @param operation 固定短语，如「添加失败」
     * @param <T>       响应体泛型
     * @return 错误响应体
     */
    public static <T> RouterResponse<T> response(final Throwable cause, final String errorCode,
                                                 final String operation) {
        logSuppressed(cause, errorCode, operation);
        return RouterResponse.error(message(cause, operation), errorCode);
    }

    /**
     * 组装对外消息：固定短语 +（client-safe 时的）异常 detail。
     *
     * @param cause     原始异常，可为 {@code null}
     * @param operation 固定短语，可为空
     * @return 对外消息
     */
    public static String message(final Throwable cause, final String operation) {
        final String detail = clientSafeDetail(cause);
        if (detail == null) {
            return operation;
        }
        if (operation == null || operation.isBlank()) {
            return detail;
        }
        return operation + SEPARATOR + detail;
    }

    /**
     * 只有显式声明可外显的异常才返回 detail，其余返回 {@code null}。
     *
     * <p>这是整套口径的唯一判定点：{@link ApiExceptions#wrap} 也复用它，所以「哪里拼了
     * getMessage()」不再决定文案，异常类型才决定。</p>
     *
     * @param cause 原始异常，可为 {@code null}
     * @return 可外显的 detail，或 {@code null}
     */
    public static String clientSafeDetail(final Throwable cause) {
        if (cause == null) {
            return null;
        }
        if (cause instanceof ClientFacingException clientFacing) {
            return blankToNull(clientFacing.getMessage());
        }
        if (cause instanceof ApiException apiException) {
            return blankToNull(apiException.getMessage());
        }
        if (cause instanceof ResponseStatusException responseStatusException) {
            return blankToNull(responseStatusException.getReason());
        }
        return null;
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 被压掉的详情记一条 warn：响应体已不再回显原文，日志是唯一还留有细节的地方。
     */
    private static void logSuppressed(final Throwable cause, final String errorCode, final String operation) {
        if (cause == null || clientSafeDetail(cause) != null) {
            return;
        }
        // 占位符少于参数个数、末尾是 Throwable ⇒ SLF4J 会把它作为异常打印（含堆栈）
        logger.warn("对外返回固定文案，异常原文仅记录在日志：operation={}, errorCode={}",
                operation, errorCode, cause);
    }
}
