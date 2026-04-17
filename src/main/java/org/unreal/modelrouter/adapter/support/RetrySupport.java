package org.unreal.modelrouter.adapter.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;

/**
 * 适配器重试支持工具类
 * 提供重试策略、延迟计算等功能
 */
@Slf4j
public class RetrySupport {

    private final MetricsCollector metricsCollector;
    private final String adapterType;

    public RetrySupport(MetricsCollector metricsCollector, String adapterType) {
        this.metricsCollector = metricsCollector;
        this.adapterType = adapterType;
    }

    /**
     * 获取最大重试次数
     */
    public int getMaxRetries(String serviceType) {
        return switch (serviceType) {
            case "chat" -> 2;
            case "embedding" -> 2;
            case "rerank" -> 1;
            case "tts" -> 1;
            case "stt" -> 1;
            case "imgGen" -> 1;
            case "imgEdit" -> 1;
            default -> 1;
        };
    }

    /**
     * 判断是否应该重试
     */
    public boolean shouldRetry(Throwable throwable, int currentRetryCount, int maxRetries) {
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

        if (throwable instanceof java.net.ConnectException ||
                throwable instanceof java.net.SocketTimeoutException ||
                throwable instanceof java.io.IOException) {
            log.debug("网络相关异常，可以重试：exception={}", throwable.getClass().getSimpleName());
            return true;
        }

        log.debug("其他异常，不重试：exception={}", throwable.getClass().getSimpleName());
        return false;
    }

    /**
     * 计算重试延迟（指数退避）
     */
    public long calculateRetryDelay(int retryCount) {
        long baseDelay = 1000;
        long delay = baseDelay * (1L << retryCount);
        return Math.min(delay, 10000);
    }

    /**
     * 记录重试指标
     */
    public void recordRetryMetrics(String instanceName) {
        if (metricsCollector != null) {
            metricsCollector.recordBackendCall(adapterType, instanceName, 0, false);
        }
    }
}
