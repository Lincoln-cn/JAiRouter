package org.unreal.modelrouter.router.adapter.support;

import org.unreal.modelrouter.common.constants.ServiceTypeConstants;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ResponseStatusException;

/**
 * 适配器重试支持工具类
 *
 * <p>提供重试策略、延迟计算等功能。
 * <p><b>Deprecated / zero consumers:</b> nothing outside this file references it &mdash; the main + test
 * reference count is 0 (scan on 2026-10-20).
 *
 * <p><b>What it used to do:</b> it was the adapter-side retry helper &mdash;
 * {@code getMaxRetries(serviceType)} hard-coded per-service limits (chat 2, embedding 2, rerank 1, ...),
 * and it decided whether an error was retryable, computed the backoff delay and recorded retry metrics.
 * Retries now run through {@link org.unreal.modelrouter.router.adapter.retry.RetryPolicy} (Reactor
 * {@code Retry} with exponential backoff and a max-delay cap), reached via
 * {@code ResilienceSupport.getRetryPolicy()}, with adapter metrics recorded by
 * {@code AdapterMetricsRecorder}.
 *
 * <p><b>Before deleting:</b> a plain helper &mdash; no bean, no configuration binding and no serialized
 * shape; a full compile plus the adapter suites is enough.
 *
 * @deprecated No caller remains in main or test sources (scan 2026-10-20); kept only until the dead-code
 *             sweep deletes it.
 */
@Slf4j
@Deprecated(forRemoval = true)
public class RetrySupport {

    private final MetricsCollector metricsCollector;
    private final String adapterType;

    public RetrySupport(final MetricsCollector metricsCollector, final String adapterType) {
        this.metricsCollector = metricsCollector;
        this.adapterType = adapterType;
    }

    /**
     * 获取最大重试次数
     */
    public int getMaxRetries(final String serviceType) {
        if (serviceType == null) {
            return 1;
        }
        
        // 使用常量类判断
        if (ServiceTypeConstants.CHAT.equals(serviceType)) {
            return 2;
        }
        if (ServiceTypeConstants.EMBEDDING.equals(serviceType)) {
            return 2;
        }
        if (ServiceTypeConstants.RERANK.equals(serviceType)) {
            return 1;
        }
        if (ServiceTypeConstants.TTS.equals(serviceType)) {
            return 1;
        }
        if (ServiceTypeConstants.STT.equals(serviceType)) {
            return 1;
        }
        if (ServiceTypeConstants.IMG_GEN.equals(serviceType)) {
            return 1;
        }
        if (ServiceTypeConstants.IMG_EDIT.equals(serviceType)) {
            return 1;
        }
        return 1;
    }

    /**
     * 判断是否应该重试
     */
    public boolean shouldRetry(final Throwable throwable, final int currentRetryCount, final int maxRetries) {
        if (currentRetryCount >= maxRetries) {
            log.debug("达到最大重试次数，不再重试：currentRetryCount={}, maxRetries={}", currentRetryCount, maxRetries);
            return false;
        }

        if (throwable instanceof org.springframework.web.server.ServerWebInputException) {
            log.warn("ServerWebInputException 错误，不重试：{}", throwable.getMessage());
            return false;
        }

        if (throwable instanceof ResponseStatusException statusException) {
            int statusCode = statusException.getStatusCode().value();

            if (statusCode == 400) {
                log.warn("400 Bad Request 错误，不重试：{}", statusException.getMessage());
                return false;
            }

            if (statusCode == 401) {
                log.warn("401 Unauthorized 错误，不重试");
                return false;
            }

            if (statusException.getStatusCode().is5xxServerError()) {
                log.debug("5xx 服务器错误，可以重试：status={}", statusException.getStatusCode());
                return true;
            }

            if (statusCode == 429) {
                log.debug("429 Too Many Requests，可以重试");
                return true;
            }

            if (statusCode == 408) {
                log.debug("408 Request Timeout，可以重试");
                return true;
            }

            log.debug("其他 4xx 客户端错误，不重试：status={}", statusException.getStatusCode());
            return false;
        }

        if (throwable instanceof java.net.ConnectException
                || throwable instanceof java.net.SocketTimeoutException
                || throwable instanceof java.io.IOException) {
            log.debug("网络相关异常，可以重试：exception={}", throwable.getClass().getSimpleName());
            return true;
        }

        log.debug("其他异常，不重试：exception={}", throwable.getClass().getSimpleName());
        return false;
    }

    /**
     * 计算重试延迟（指数退避）
     */
    public long calculateRetryDelay(final int retryCount) {
        long baseDelay = 1000;
        long delay = baseDelay * (1L << retryCount);
        return Math.min(delay, 10000);
    }

    /**
     * 记录重试指标
     */
    public void recordRetryMetrics(final String instanceName) {
        if (metricsCollector != null) {
            metricsCollector.recordBackendCall(adapterType, instanceName, 0, false);
        }
    }
}
