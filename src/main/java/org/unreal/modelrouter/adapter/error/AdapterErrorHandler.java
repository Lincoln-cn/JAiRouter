package org.unreal.modelrouter.adapter.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.exception.DownstreamServiceException;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

/**
 * 适配器错误处理器
 *
 * 负责处理适配器调用中的错误分类、错误响应和降级策略。
 *
 * @author JAiRouter Team
 * @since v2.3.0
 */
@Component
public class AdapterErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(AdapterErrorHandler.class);

    /**
     * 分类错误代码
     *
     * @param throwable 异常
     * @return 错误代码
     */
    public String classifyError(Throwable throwable) {
        if (throwable == null) {
            return "500";
        }

        if (throwable instanceof org.springframework.web.server.ResponseStatusException) {
            return String.valueOf(((org.springframework.web.server.ResponseStatusException) throwable).getStatusCode().value());
        } else if (throwable instanceof DownstreamServiceException) {
            return "503";
        } else if (throwable instanceof TimeoutException) {
            return "504";
        } else if (throwable instanceof java.net.ConnectException) {
            return "503";
        } else if (throwable instanceof java.net.SocketTimeoutException) {
            return "504";
        } else {
            return "500";
        }
    }

    /**
     * 创建错误响应
     *
     * @param error 异常
     * @param responseType 响应类型
     * @return 错误响应实体
     */
    @SuppressWarnings("unchecked")
    public <T> Mono<ResponseEntity<T>> createErrorResponse(Throwable error, Class<T> responseType) {
        String errorCode = classifyError(error);
        HttpStatus status = getHttpStatus(errorCode);

        logger.warn("适配器调用失败：errorCode={}, message={}", errorCode, error.getMessage());

        T errorBody = createErrorBody(errorCode, error.getMessage(), responseType);
        return Mono.just(ResponseEntity.status(status).body(errorBody));
    }

    /**
     * 判断是否应该重试
     *
     * @param error 异常
     * @param retryCount 当前重试次数
     * @param maxRetries 最大重试次数
     * @return 是否应该重试
     */
    public boolean shouldRetry(Throwable error, int retryCount, int maxRetries) {
        if (retryCount >= maxRetries) {
            logger.debug("达到最大重试次数：{}", maxRetries);
            return false;
        }

        // 不可重试的错误类型
        if (error instanceof org.springframework.web.client.HttpClientErrorException) {
            logger.debug("客户端错误，不重试：{}", error.getMessage());
            return false;
        }

        // 可重试的错误类型
        if (error instanceof TimeoutException ||
            error instanceof java.net.ConnectException ||
            error instanceof java.net.SocketTimeoutException ||
            error instanceof DownstreamServiceException) {
            logger.debug("可重试错误：{}", error.getClass().getSimpleName());
            return true;
        }

        // 5xx 服务器错误可以重试
        String errorCode = classifyError(error);
        if (errorCode.startsWith("5")) {
            logger.debug("服务器错误，可重试：{}", errorCode);
            return true;
        }

        logger.debug("未知错误类型，不重试：{}", error.getClass().getSimpleName());
        return false;
    }

    /**
     * 执行降级策略
     *
     * @param fallbackResponse 降级响应
     * @param error 原始错误
     * @param <T> 响应类型
     * @return 降级响应实体
     */
    public <T> Mono<ResponseEntity<T>> executeFallback(T fallbackResponse, Throwable error) {
        if (fallbackResponse != null) {
            logger.info("执行降级策略：返回降级响应");
            return Mono.just(ResponseEntity.ok(fallbackResponse));
        }

        logger.warn("无降级响应，返回错误响应：{}", error.getMessage());
        return createErrorResponse(error, (Class<T>) fallbackResponse.getClass());
    }

    /**
     * 获取 HTTP 状态
     */
    private HttpStatus getHttpStatus(String errorCode) {
        try {
            int code = Integer.parseInt(errorCode);
            return HttpStatus.valueOf(code);
        } catch (Exception e) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * 创建错误响应体
     */
    @SuppressWarnings("unchecked")
    private <T> T createErrorBody(String errorCode, String message, Class<T> responseType) {
        // 如果是 String 类型，返回 JSON 格式错误信息
        if (responseType == String.class) {
            return (T) String.format("{\"error\": {\"code\": \"%s\", \"message\": \"%s\"}}", errorCode, message);
        }

        // 其他类型返回 null，由调用方处理
        return null;
    }
}
