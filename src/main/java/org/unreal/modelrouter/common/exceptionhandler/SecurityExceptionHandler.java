package org.unreal.modelrouter.common.exceptionhandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.common.dto.SecurityErrorResponse;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;
import org.unreal.modelrouter.common.exception.AuthorizationException;
import org.unreal.modelrouter.common.exception.SanitizationException;
import org.unreal.modelrouter.common.exception.SecurityException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * 全局安全异常处理器
 * 处理所有安全相关的异常，提供统一的错误响应格式
 *
 * <p>v3.1 PR-4d.1：本 advice 会服务 {@code /v1/**}（OpenAI SDK / Claude Code 直连面）。此前它对
 * 所有路径都返回控制台面的 {@link SecurityErrorResponse}，成为 {@code /v1/**} 上区别于
 * {@code ReactiveGlobalExceptionHandler} 与 {@code GlobalControllerExceptionHandler} 的
 * <b>第三种错误体形状</b>，客户端无法解析。现在 5 个 {@code @ExceptionHandler} 均按请求路径分支，
 * 复用 {@link V1ErrorBodyMapper}（与另外两个处理器同一张 HTTP→type 映射表）：</p>
 * <ul>
 *   <li>{@code /v1/messages}（Anthropic 面）→ {@code {"type":"error","error":{"type","message"}}}；</li>
 *   <li>其余 {@code /v1/**}（OpenAI 面）→ {@code {"error":{"message","type","code"}}}；</li>
 *   <li>非 {@code /v1} 路径（含 {@code /api/**}）→ 逐字节保持既有 {@link SecurityErrorResponse} 形状。</li>
 * </ul>
 * <p>状态码与日志语义不变（状态码原样透传，仅错误体形状随路径切换）。</p>
 */
@RestControllerAdvice
@Order(1) // 确保安全异常处理器优先于通用异常处理器
public class SecurityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(SecurityExceptionHandler.class);

    /**
     * 数据脱敏异常对外的通用消息（不暴露具体的脱敏错误信息）.
     */
    private static final String SANITIZATION_ERROR_MESSAGE = "数据处理失败";

    /**
     * 下游服务异常的错误码（既有契约，原样保留）.
     */
    private static final String DOWNSTREAM_SERVICE_ERROR_CODE = "DOWNSTREAM_SERVICE_ERROR";

    /**
     * 处理认证异常
     *
     * @param ex       认证异常
     * @param exchange 当前交换对象（仅用于取请求路径，决定错误体形状）
     * @return {@code /v1/**}：客户端协议形状错误体（状态码原样）；其余路径：{@link SecurityErrorResponse}
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> handleAuthenticationException(final AuthenticationException ex,
                                                           final ServerWebExchange exchange) {
        logger.warn("认证失败: {} - {}", ex.getErrorCode(), ex.getMessage());

        final ResponseEntity<?> clientResponse = V1ErrorBodyMapper.toClientErrorResponse(
                exchange, ex.getHttpStatus().value(), ex.getMessage(), ex.getErrorCode());
        if (clientResponse != null) {
            return clientResponse;
        }

        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .path(getCurrentPath())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    /**
     * 处理授权异常
     *
     * @param ex       授权异常
     * @param exchange 当前交换对象（仅用于取请求路径，决定错误体形状）
     * @return {@code /v1/**}：客户端协议形状错误体（状态码原样）；其余路径：{@link SecurityErrorResponse}
     */
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<?> handleAuthorizationException(final AuthorizationException ex,
                                                          final ServerWebExchange exchange) {
        logger.warn("授权失败: {} - {}", ex.getErrorCode(), ex.getMessage());

        final ResponseEntity<?> clientResponse = V1ErrorBodyMapper.toClientErrorResponse(
                exchange, ex.getHttpStatus().value(), ex.getMessage(), ex.getErrorCode());
        if (clientResponse != null) {
            return clientResponse;
        }

        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .path(getCurrentPath())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    /**
     * 处理数据脱敏异常
     *
     * @param ex       脱敏异常
     * @param exchange 当前交换对象（仅用于取请求路径，决定错误体形状）
     * @return {@code /v1/**}：客户端协议形状错误体（消息仍为「数据处理失败」，不暴露细节）；
     *         其余路径：{@link SecurityErrorResponse}
     */
    @ExceptionHandler(SanitizationException.class)
    public ResponseEntity<?> handleSanitizationException(final SanitizationException ex,
                                                         final ServerWebExchange exchange) {
        logger.error("数据脱敏异常: {} - {}", ex.getErrorCode(), ex.getMessage(), ex);

        final ResponseEntity<?> clientResponse = V1ErrorBodyMapper.toClientErrorResponse(
                exchange, ex.getHttpStatus().value(), SANITIZATION_ERROR_MESSAGE, ex.getErrorCode());
        if (clientResponse != null) {
            return clientResponse;
        }

        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .message(SANITIZATION_ERROR_MESSAGE)  // 不暴露具体的脱敏错误信息
                .errorCode(ex.getErrorCode())
                .path(getCurrentPath())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    /**
     * 处理通用安全异常
     *
     * @param ex       安全异常
     * @param exchange 当前交换对象（仅用于取请求路径，决定错误体形状）
     * @return {@code /v1/**}：客户端协议形状错误体（状态码原样）；其余路径：{@link SecurityErrorResponse}
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<?> handleSecurityException(final SecurityException ex,
                                                     final ServerWebExchange exchange) {
        logger.error("安全异常: {} - {}", ex.getErrorCode(), ex.getMessage(), ex);

        final ResponseEntity<?> clientResponse = V1ErrorBodyMapper.toClientErrorResponse(
                exchange, ex.getHttpStatus().value(), ex.getMessage(), ex.getErrorCode());
        if (clientResponse != null) {
            return clientResponse;
        }

        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .path(getCurrentPath())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    /**
     * 处理下游服务异常
     *
     * <p>既有的 401 归一见原样保留：HTTP 状态码取「401 → {@code UNAUTHORIZED}」覆盖后的结果，
     * 协议面的 type 也随之按该最终状态码映射（401 → {@code authentication_error}）。</p>
     *
     * @param ex       下游服务异常
     * @param exchange 当前交换对象（仅用于取请求路径，决定错误体形状）
     * @return {@code /v1/**}：客户端协议形状错误体（状态码原样）；其余路径：{@link SecurityErrorResponse}
     */
    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<?> handleDownstreamServiceException(final DownstreamServiceException ex,
                                                              final ServerWebExchange exchange) {
        logger.warn("下游服务异常: {}", ex.getMessage(), ex);

        // 对于认证相关的下游错误，使用401状态码
        HttpStatus status = ex.getStatusCode().value() == 401
            ? HttpStatus.UNAUTHORIZED : HttpStatus.valueOf(ex.getStatusCode().value());

        final ResponseEntity<?> clientResponse = V1ErrorBodyMapper.toClientErrorResponse(
                exchange, status.value(), ex.getMessage(), DOWNSTREAM_SERVICE_ERROR_CODE);
        if (clientResponse != null) {
            return clientResponse;
        }

        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatusCode().value())
                .error(ex.getStatusCode().getReasonPhrase())
                .message(ex.getMessage())
                .errorCode(DOWNSTREAM_SERVICE_ERROR_CODE)
                .build();

        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * 获取当前请求路径
     * 在WebFlux环境中，可以通过ServerRequest获取路径信息
     *
     * @return 路径（既有实现返回固定值，非 /v1 错误体形状保持不变）
     */
    private String getCurrentPath() {
        // 在实际的WebFlux环境中，可以通过ServerRequest获取路径
        // 这里先返回一个默认值，后续可以通过RequestContextHolder或其他方式获取
        return "/api/security";
    }
}
