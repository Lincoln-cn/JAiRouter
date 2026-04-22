package org.unreal.modelrouter.adapter.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 重试策略
 *
 * 负责定义和管理重试策略，支持指数退避和最大延迟限制。
 *
 * @author JAiRouter Team
 * @since v2.3.0
 */
@Component
public class RetryPolicy {

    private static final Logger logger = LoggerFactory.getLogger(RetryPolicy.class);

    /**
     * 默认最大重试次数
     */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * 默认初始延迟 (毫秒)
     */
    public static final long DEFAULT_INITIAL_DELAY_MS = 100;

    /**
     * 默认最大延迟 (毫秒)
     */
    public static final long DEFAULT_MAX_DELAY_MS = 5000;

    /**
     * 默认退避乘数
     */
    public static final double DEFAULT_MULTIPLIER = 2.0;

    private final int maxRetries;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double multiplier;

    /**
     * 构造函数 - 使用默认配置
     */
    public RetryPolicy() {
        this(DEFAULT_MAX_RETRIES,
             Duration.ofMillis(DEFAULT_INITIAL_DELAY_MS),
             Duration.ofMillis(DEFAULT_MAX_DELAY_MS),
             DEFAULT_MULTIPLIER);
    }

    /**
     * 构造函数 - 自定义配置
     *
     * @param maxRetries 最大重试次数
     * @param initialDelay 初始延迟
     * @param maxDelay 最大延迟
     * @param multiplier 退避乘数
     */
    public RetryPolicy(int maxRetries, Duration initialDelay, Duration maxDelay, double multiplier) {
        this.maxRetries = maxRetries;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.multiplier = multiplier;
    }

    /**
     * 计算下次重试延迟（指数退避）
     *
     * @param retryCount 当前重试次数（从 0 开始）
     * @return 下次重试延迟
     */
    public Duration getNextDelay(int retryCount) {
        long delayMs = (long) (initialDelay.toMillis() * Math.pow(multiplier, retryCount));
        delayMs = Math.min(delayMs, maxDelay.toMillis());
        return Duration.ofMillis(delayMs);
    }

    /**
     * 判断是否可以重试
     *
     * @param retryCount 当前重试次数
     * @param error 异常
     * @return 是否可以重试
     */
    public boolean canRetry(int retryCount, Throwable error) {
        if (retryCount >= maxRetries) {
            logger.debug("达到最大重试次数：{}/{}", retryCount, maxRetries);
            return false;
        }

        // 默认所有错误都可以重试
        return true;
    }

    /**
     * 创建带重试的 Mono
     *
     * @param operation 操作
     * @param retryCondition 重试条件
     * @param <T> 结果类型
     * @return 带重试的 Mono
     */
    public <T> Mono<T> withRetry(Supplier<Mono<T>> operation, Predicate<Throwable> retryCondition) {
        Retry retry = Retry.backoff(maxRetries, initialDelay)
                .maxBackoff(maxDelay)
                .filter(retryCondition);

        logger.debug("创建带重试的操作：maxRetries={}, initialDelay={}, maxDelay={}",
                maxRetries, initialDelay, maxDelay);

        return Mono.fromSupplier(operation)
                .flatMap(Mono::from)
                .retryWhen(retry);
    }

    /**
     * 创建带重试的 Mono（使用默认重试条件）
     *
     * @param operation 操作
     * @param <T> 结果类型
     * @return 带重试的 Mono
     */
    public <T> Mono<T> withRetry(Supplier<Mono<T>> operation) {
        return withRetry(operation, this::isRetryable);
    }

    /**
     * 判断错误是否可重试
     *
     * @param error 异常
     * @return 是否可重试
     */
    public boolean isRetryable(Throwable error) {
        // 不可重试的错误类型
        if (error instanceof org.springframework.web.client.HttpClientErrorException) {
            logger.debug("客户端错误，不重试：{}", error.getMessage());
            return false;
        }

        // 可重试的错误类型
        if (error instanceof java.util.concurrent.TimeoutException ||
            error instanceof java.net.ConnectException ||
            error instanceof java.net.SocketTimeoutException ||
            error instanceof org.unreal.modelrouter.exception.DownstreamServiceException) {
            logger.debug("可重试错误：{}", error.getClass().getSimpleName());
            return true;
        }

        // 默认重试所有错误
        logger.debug("重试错误：{}", error.getClass().getSimpleName());
        return true;
    }

    /**
     * 获取最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * 获取初始延迟
     */
    public Duration getInitialDelay() {
        return initialDelay;
    }

    /**
     * 获取最大延迟
     */
    public Duration getMaxDelay() {
        return maxDelay;
    }

    /**
     * 获取退避乘数
     */
    public double getMultiplier() {
        return multiplier;
    }
}
