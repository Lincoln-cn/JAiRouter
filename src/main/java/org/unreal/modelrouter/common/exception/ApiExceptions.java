package org.unreal.modelrouter.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 异常 → {@link ApiException} 的统一映射工具（issue #94 配套）。
 *
 * <p><b>背景：</b>控制器里大量 {@code onErrorResume(e -> ... ApiException.of("INTERNAL_ERROR", "xxx失败: " + e.getMessage()))}
 * 把**任何**异常都压成 500。例如「Key 已存在」抛的是 {@code IllegalArgumentException}，
 * 本应 400，却被压成 500；而若调用链里已经有 {@link ApiException}，再包一层会**丢掉原始错误码**。</p>
 *
 * <p>本工具按异常类型还原语义，catch-all 一律改为调用它：</p>
 * <pre>
 *   .onErrorResume(e -&gt; Mono.error(ApiExceptions.wrap(e, "创建API密钥失败")))
 * </pre>
 *
 * <p>映射规则：</p>
 * <ul>
 *   <li>{@link ApiException} → 原样返回（保留原始错误码与状态码，不二次包装）</li>
 *   <li>{@link IllegalArgumentException} → {@code INVALID_REQUEST}（400），消息用异常自身消息</li>
 *   <li>{@link IllegalStateException} → {@code CONFLICT_STATE}（409），消息用异常自身消息</li>
 *   <li>{@link ResponseStatusException} → 保留其状态码，错误码用状态码数字</li>
 *   <li>其它 → {@code INTERNAL_ERROR}（500），消息为 {@code fallbackMessage + ": " + e.getMessage()}</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 3.2.2
 */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    /**
     * 按异常类型映射为语义正确的 {@link ApiException}。
     *
     * @param e               原始异常
     * @param fallbackMessage 未知异常时的消息前缀（保持与迁移前一致的对外措辞）
     * @return 语义化的业务异常
     */
    public static ApiException wrap(final Throwable e, final String fallbackMessage) {
        if (e instanceof ApiException apiException) {
            // 已是业务异常：保留原始错误码/状态码，避免被压成 500
            return apiException;
        }
        if (e instanceof IllegalArgumentException) {
            return ApiException.of("INVALID_REQUEST", messageOf(e, fallbackMessage));
        }
        if (e instanceof IllegalStateException) {
            return ApiException.of("CONFLICT_STATE", messageOf(e, fallbackMessage));
        }
        if (e instanceof ResponseStatusException rse) {
            final int status = rse.getStatusCode().value();
            return ApiException.of(String.valueOf(status), messageOf(e, fallbackMessage),
                    HttpStatus.resolve(status) == null ? HttpStatus.INTERNAL_SERVER_ERROR
                            : HttpStatus.resolve(status));
        }
        return ApiException.of("INTERNAL_ERROR", messageOf(e, fallbackMessage));
    }

    /**
     * 组装对外消息：优先保留原始异常消息，无消息时退回兜底文案。
     *
     * @param e               原始异常
     * @param fallbackMessage 兜底文案前缀
     * @return 对外消息
     */
    private static String messageOf(final Throwable e, final String fallbackMessage) {
        final String detail = e == null ? null : e.getMessage();
        if (detail == null || detail.isBlank()) {
            return fallbackMessage;
        }
        return fallbackMessage == null || fallbackMessage.isBlank()
                ? detail
                : fallbackMessage + ": " + detail;
    }
}
