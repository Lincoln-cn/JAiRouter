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
    public RetryPolicy(
            final int maxRetries, final Duration initialDelay,
            final Duration maxDelay, final double multiplier) {
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
        // #718：4xx 是调用方问题 —— 重试只会把同一个坏请求再打给上游两次，
        // 既救不了这次调用，又把「1 次 400」放大成「3 次 400」。
        // 此前只有阻塞式 HttpClientErrorException 在不可重试名单里，Reactor 侧的
        // WebClientResponseException / ResponseStatusException 与 DownstreamServiceException(4xx)
        // 全都落到末尾的「默认重试所有错误」。
        final Integer status = statusOf(error);
        if (status != null && status >= 400 && status < 500 && status != 408) {
            logger.debug("调用方错误（{}），不重试：{}", status, status == 429
                    ? "上游要求退避，重试会立刻再打一次" : error.getMessage());
            return false;
        }

        // 可重试的错误类型：超时 / 建连失败 / 下游 5xx 侧异常
        if (error instanceof java.util.concurrent.TimeoutException
            || error instanceof java.net.ConnectException
            || error instanceof java.net.SocketTimeoutException
            || error instanceof org.unreal.modelrouter.common.exception.DownstreamServiceException) {
            logger.debug("可重试错误：{}", error.getClass().getSimpleName());
            return true;
        }

        // 其余仍按「默认重试」处理（HTTP 状态未知的传输层异常，如 WebClientRequestException）
        logger.debug("重试错误：{}", error.getClass().getSimpleName());
        return true;
    }

    /**
     * #718：从异常里取下游 HTTP 状态码。
     *
     * <p>覆盖本仓链路上真实会出现的三类：{@code DownstreamServiceException}（适配器显式抛出）、
     * {@code WebClientResponseException}（Reactor 侧）、{@code ResponseStatusException}（状态保真转译）。
     * 取不到状态码（连接失败、TLS 失败、超时等）返回 {@code null}，由调用方按「未知 = 可重试」处理。</p>
     *
     * @param error 异常
     * @return HTTP 状态码；无法判定时为 {@code null}
     */
    private static Integer statusOf(final Throwable error) {
        if (error instanceof org.unreal.modelrouter.common.exception.DownstreamServiceException downstreamError) {
            return downstreamError.getStatusCode() == null ? null : downstreamError.getStatusCode().value();
        }
        if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException
                webClientError) {
            return webClientError.getStatusCode().value();
        }
        if (error instanceof org.springframework.web.server.ResponseStatusException statusError) {
            return statusError.getStatusCode().value();
        }
        if (error instanceof org.springframework.web.client.HttpClientErrorException clientError) {
            return clientError.getStatusCode().value();
        }
        return null;
    }

    /**
     * #827：按服务类型判断是否重试 —— 不幂等的服务只在「请求根本没发出去」时重试。
     *
     * <p>TTS / STT / 生图 / 图像编辑的请求不可重复下发：上游可能已经生成内容并计费，
     * 只是响应没回到网关。对这类服务重试 5xx 或读超时，会把「1 次失败」放大成
     * 「2 次计费」，用户拿到的是其中一次的结果，另一次白付钱。</p>
     *
     * <p>唯一安全的例外是「连接建立失败」——请求连上游的 TCP 连接都没建起来，
     * 可以确定上游没有收到请求（见 {@link #neverReachedUpstream(Throwable)}）。</p>
     *
     * @param error       异常
     * @param serviceType 服务类型
     * @return 是否允许重试
     */
    public boolean isRetryableForService(final Throwable error,
            final ModelServiceRegistry.ServiceType serviceType) {
        if (!isRetryable(error)) {
            return false;
        }
        if (!isNonIdempotent(serviceType)) {
            return true;
        }
        if (neverReachedUpstream(error)) {
            logger.debug("媒体类服务（{}）仅在建连失败时重试：{}", serviceType, error.getClass().getSimpleName());
            return true;
        }
        logger.warn("媒体类服务（{}）不重试非建连失败，避免重复下发与重复计费：{}",
                serviceType, error.getClass().getSimpleName());
        return false;
    }

    /**
     * 该服务类型的请求是否不幂等（重复下发会产生副作用）。
     *
     * @param serviceType 服务类型
     * @return true 表示不幂等
     */
    static boolean isNonIdempotent(final ModelServiceRegistry.ServiceType serviceType) {
        return serviceType == ModelServiceRegistry.ServiceType.tts
                || serviceType == ModelServiceRegistry.ServiceType.stt
                || serviceType == ModelServiceRegistry.ServiceType.imgGen
                || serviceType == ModelServiceRegistry.ServiceType.imgEdit;
    }

    /**
     * 请求是否根本没到达上游：连接被拒 / 域名解析失败 / 无路由。
     *
     * <p>沿着 cause 链找，因为 Reactor 侧会把真实原因包在
     * {@code WebClientRequestException} 或 {@code RetryExhaustedException} 里面。
     * 读超时（{@code SocketTimeoutException} / {@code TimeoutException}）<b>不</b>属于这一类：
     * 请求已经发出去了，上游可能已经处理完。</p>
     *
     * @param error 异常
     * @return true 表示可以确定上游没有收到请求
     */
    static boolean neverReachedUpstream(final Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            if (current instanceof java.net.ConnectException
                    || current instanceof java.net.UnknownHostException
                    || current instanceof java.net.NoRouteToHostException) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
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
