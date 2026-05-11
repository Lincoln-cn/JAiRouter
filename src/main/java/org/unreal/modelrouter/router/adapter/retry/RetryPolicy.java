package org.unreal.modelrouter.router.adapter.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import org.unreal.modelrouter.router.model.ModelServiceRegistry;

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

    /**
     * 最大延迟上限 (毫秒) - v2.26.5
     */
    public static final long MAX_DELAY_CAP_MS = 10000;

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
    public RetryPolicy(final int maxRetries, final Duration initialDelay, final Duration maxDelay, final double multiplier) {
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
    public Duration getNextDelay(final int retryCount) {
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
    public boolean canRetry(final int retryCount, final Throwable error) {
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
    public <T> Mono<T> withRetry(final Supplier<Mono<T>> operation, final Predicate<Throwable> retryCondition) {
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
    public <T> Mono<T> withRetry(final Supplier<Mono<T>> operation) {
        return withRetry(operation, this::isRetryable);
    }

    /**
     * 判断错误是否可重试
     *
     * @param error 异常
     * @return 是否可重试
     */
    public boolean isRetryable(final Throwable error) {
        // 不可重试的错误类型
        if (error instanceof org.springframework.web.client.HttpClientErrorException) {
            logger.debug("客户端错误，不重试：{}", error.getMessage());
            return false;
        }

        // 可重试的错误类型
        if (error instanceof java.util.concurrent.TimeoutException
            || error instanceof java.net.ConnectException
            || error instanceof java.net.SocketTimeoutException
            || error instanceof org.unreal.modelrouter.common.exception.DownstreamServiceException) {
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

    // ============ v2.26.5: 从 BaseAdapter 迁移的方法 ============

    /**
     * 根据服务类型获取最大重试次数
     *
     * @param serviceType 服务类型
     * @return 最大重试次数
     */
    public int getMaxRetriesByServiceType(final ModelServiceRegistry.ServiceType serviceType) {
        if (serviceType == null) {
            return 1;
        }
        switch (serviceType) {
            case chat:
                return 2; // 聊天服务重试2次
            case embedding:
                return 2; // 嵌入服务重试2次
            case rerank:
                return 1; // 重排序服务重试1次（避免body重读问题）
            case tts:
                return 1; // TTS服务重试1次（文件较大）
            case stt:
                return 1; // STT服务重试1次（文件较大）
            case imgGen:
                return 1; // 图像生成重试1次（耗时较长）
            case imgEdit:
                return 1; // 图像编辑重试1次（耗时较长）
            default:
                return 1; // 默认重试1次
        }
    }

    /**
     * 计算重试延迟（指数退避）
     *
     * @param retryCount 当前重试次数
     * @return 延迟毫秒数
     */
    public long calculateRetryDelay(final int retryCount) {
        // 指数退避：基础延迟 * 2^重试次数，最大不超过10秒
        long baseDelay = 1000; // 1秒基础延迟
        long delay = baseDelay * (long) Math.pow(2, retryCount);
        return Math.min(delay, MAX_DELAY_CAP_MS);
    }
}
