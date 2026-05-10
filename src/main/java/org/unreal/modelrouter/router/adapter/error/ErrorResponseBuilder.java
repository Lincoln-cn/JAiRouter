package org.unreal.modelrouter.router.adapter.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;
import reactor.core.publisher.Mono;

/**
 * 错误响应构建器
 *
 * 负责将各种异常类型统一转换为 ResponseStatusException 并封装为 Mono.error()。
 *
 * 设计原则:
 * - 单一职责：专注于错误响应构建，不涉及分类或重试逻辑
 * - 与 AdapterErrorHandler 协作：AdapterErrorHandler 负责分类，本组件负责构建
 *
 * @author JAiRouter Team
 * @since v2.28.0
 */
@Component
public class ErrorResponseBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ErrorResponseBuilder.class);

    // ============ 核心方法 ============

    /**
     * 构建错误响应 (统一入口)
     *
     * 自动识别异常类型并转换为适当的 ResponseStatusException。
     *
     * @param throwable 异常对象
     * @return Mono.error() 封装的 ResponseStatusException
     */
    public Mono<ResponseStatusException> buildErrorResponse(final Throwable throwable) {
        if (throwable == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "未知错误"));
        }

        ResponseStatusException responseEx = convertToResponseStatusException(throwable);
        logError(responseEx, throwable);
        return Mono.error(responseEx);
    }

    /**
     * 构建带自定义消息的错误响应
     *
     * @param throwable 异常对象
     * @param customMessage 自定义错误消息
     * @return Mono.error() 封装的 ResponseStatusException
     */
    public Mono<ResponseStatusException> buildErrorResponse(
            final Throwable throwable, final String customMessage) {
        if (throwable == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, customMessage));
        }

        HttpStatus status = extractHttpStatus(throwable);
        ResponseStatusException responseEx = new ResponseStatusException(
                status, customMessage, throwable);
        logError(responseEx, throwable);
        return Mono.error(responseEx);
    }

    /**
     * 构建基于 HTTP 状态码的错误响应
     *
     * @param statusCode HTTP 状态码
     * @param message 错误消息
     * @return Mono.error() 封装的 ResponseStatusException
     */
    public Mono<ResponseStatusException> buildErrorResponse(
            final HttpStatus statusCode, final String message) {
        ResponseStatusException responseEx = new ResponseStatusException(statusCode, message);
        logger.error("错误响应构建: status={}, message={}", statusCode, message);
        return Mono.error(responseEx);
    }

    /**
     * 构建下游服务 4xx/5xx 错误响应
     *
     * @param statusCode HTTP 状态码
     * @param instanceName 实例名称（用于日志）
     * @param path 请求路径（用于日志）
     * @return Mono.error() 封装的 DownstreamServiceException
     */
    public Mono<DownstreamServiceException> buildDownstreamError(
            final HttpStatus statusCode, final String instanceName, final String path) {

        String errorMessage = getDownstreamErrorMessage(statusCode);
        logger.error("下游服务错误 ({}): instance={}, path={}", statusCode.value(), instanceName, path);

        return Mono.error(new DownstreamServiceException(errorMessage, statusCode));
    }

    // ============ 异常类型判断方法 ============

    /**
     * 判断是否为 WebClient 响应异常
     */
    public boolean isWebClientResponseException(final Throwable throwable) {
        return throwable instanceof WebClientResponseException;
    }

    /**
     * 判断是否为下游服务异常
     */
    public boolean isDownstreamServiceException(final Throwable throwable) {
        return throwable instanceof DownstreamServiceException;
    }

    /**
     * 判断是否为响应状态异常
     */
    public boolean isResponseStatusException(final Throwable throwable) {
        return throwable instanceof ResponseStatusException;
    }

    // ============ 类型转换方法 ============

    /**
     * 将异常转换为 ResponseStatusException
     *
     * 处理以下类型:
     * - WebClientResponseException: 保持原始状态码和消息
     * - DownstreamServiceException: 保持原始状态码和消息
     * - ResponseStatusException: 直接返回
     * - 其他: 转换为 500 INTERNAL_SERVER_ERROR
     */
    private ResponseStatusException convertToResponseStatusException(final Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException webEx = (WebClientResponseException) throwable;
            return new ResponseStatusException(
                    webEx.getStatusCode(), webEx.getMessage(), webEx);
        }

        if (throwable instanceof DownstreamServiceException) {
            DownstreamServiceException downEx = (DownstreamServiceException) throwable;
            return new ResponseStatusException(
                    downEx.getStatusCode(), downEx.getMessage(), downEx);
        }

        if (throwable instanceof ResponseStatusException) {
            return (ResponseStatusException) throwable;
        }

        // 默认转换为 500
        return new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, throwable.getMessage(), throwable);
    }

    /**
     * 从异常中提取 HTTP 状态码
     */
    private HttpStatus extractHttpStatus(final Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            HttpStatusCode statusCode = ((WebClientResponseException) throwable).getStatusCode();
            return HttpStatus.valueOf(statusCode.value());
        }
        if (throwable instanceof DownstreamServiceException) {
            return ((DownstreamServiceException) throwable).getStatusCode();
        }
        if (throwable instanceof ResponseStatusException) {
            HttpStatusCode statusCode = ((ResponseStatusException) throwable).getStatusCode();
            return HttpStatus.valueOf(statusCode.value());
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 获取下游服务错误消息
     */
    private String getDownstreamErrorMessage(final HttpStatus statusCode) {
        switch (statusCode.value()) {
            case 401:
                return "下游服务认证失败，请检查下游服务的认证配置";
            case 400:
                return "下游服务请求参数错误，请检查请求内容";
            case 503:
                return "下游服务暂时不可用，请稍后重试";
            default:
                return "下游服务异常";
        }
    }

    // ============ 日志记录方法 ============

    /**
     * 记录错误日志
     */
    private void logError(final ResponseStatusException responseEx, final Throwable originalEx) {
        String exceptionType = originalEx.getClass().getSimpleName();
        logger.error("{} 处理失败: {}", exceptionType, responseEx.getMessage(), responseEx);
    }
}