package org.unreal.modelrouter.common.exceptionhandler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.common.controller.response.RouterResponse; // 确保引入您项目中的Response类
import org.unreal.modelrouter.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 控制台面（{@code /api/**}）控制器异常处理器.
 *
 * <p>v3.1 PR-4d：本 advice 对<b>所有</b>路径生效，且在 WebFlux 中先于
 * {@link ReactiveGlobalExceptionHandler} 拿到注解控制器内抛出的异常（advice 由
 * {@code RequestMappingHandlerAdapter.handleError} 解析，早于 {@code WebExceptionHandler} 链）。
 * 因此 {@code /v1/**}（OpenAI SDK / Claude Code 直连面）的错误体必须在此一并分形状输出：
 * {@code /v1/messages}（Anthropic 面）→ {@code {"type":"error","error":{"type","message"}}}；
 * 其余 {@code /v1/**} → {@code {"error":{"message","type","code"}}}；映射见 {@link V1ErrorBodyMapper}。
 * 状态码与日志语义不变，{@code /api/**} 及其它路径仍返回 {@link RouterResponse}。</p>
 */
@RestControllerAdvice
public class GlobalControllerExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalControllerExceptionHandler.class);

    /**
     * 处理响应状态异常.
     *
     * @param ex       状态异常（含 {@code ServerWebInputException} 等子类）
     * @param exchange 当前交换对象（仅用于取请求路径，决定错误体形状）
     * @return {@code /v1/**}：客户端协议形状错误体（状态码原样）；其余路径：{@link RouterResponse}
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatusException(final ResponseStatusException ex,
                                                           final ServerWebExchange exchange) {

        // 在这里，您可以确定地捕获到异常
        logger.error("通过 @RestControllerAdvice 捕获到响应状态异常: status={}, reason={}",
                ex.getStatusCode(), ex.getReason(), ex);

        RouterResponse<Void> errorResponse = RouterResponse.error(
                "请求处理失败: " + ex.getMessage(),
                String.valueOf(ex.getStatusCode().value())
        );

        final ResponseEntity<?> clientResponse = V1ErrorBodyMapper.toClientErrorResponse(
                exchange, ex.getStatusCode().value(),
                errorResponse.getMessage(), errorResponse.getErrorCode());
        if (clientResponse != null) {
            return clientResponse;
        }

        return new ResponseEntity<>(errorResponse, ex.getStatusCode());
    }

    /**
     * 处理业务异常（issue #94）.
     *
     * <p>控制器此前把失败写成 {@code Mono.just(RouterResponse.error(msg, code))}，导致状态码恒为 200、
     * 全局处理器失效。迁移为 {@code Mono.error(ApiException.of(code, msg))} 后由此处统一处理：
     * <b>响应体与旧行为完全一致</b>（{@code RouterResponse} 的 {@code message}/{@code errorCode} 原样保留，
     * 不加任何前缀），仅把 HTTP 状态码修正为错误码对应的语义（见 {@link ApiException#resolveStatus}）。</p>
     *
     * @param ex       业务异常
     * @param exchange 当前交换对象（仅用于取请求路径，决定 {@code /v1/**} 的错误体形状）
     * @return 带正确状态码的响应
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleApiException(final ApiException ex,
                                                final ServerWebExchange exchange) {

        logger.warn("通过 @RestControllerAdvice 捕获到业务异常: code={}, status={}, message={}",
                ex.getErrorCode(), ex.getStatus().value(), ex.getMessage());

        final ResponseEntity<?> clientResponse = V1ErrorBodyMapper.toClientErrorResponse(
                exchange, ex.getStatus().value(), ex.getMessage(), ex.getErrorCode());
        if (clientResponse != null) {
            return clientResponse;
        }

        return new ResponseEntity<>(
                RouterResponse.error(ex.getMessage(), ex.getErrorCode()), ex.getStatus());
    }
}
