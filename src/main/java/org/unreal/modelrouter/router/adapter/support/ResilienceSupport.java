package org.unreal.modelrouter.router.adapter.support;

import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.adapter.checker.CapabilityChecker;
import org.unreal.modelrouter.router.adapter.error.AdapterErrorHandler;
import org.unreal.modelrouter.router.adapter.error.ErrorResponseBuilder;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.adapter.retry.RetryPolicy;
import org.unreal.modelrouter.router.adapter.tracing.AdapterTracingManager;

/**
 * ResilienceSupport - 弹性支持组件
 * 聚合容错、重试、监控相关的依赖，减少 BaseAdapter 构造函数参数。
 *
 * @since v2.28.0
 */
@Component
public class ResilienceSupport {

    private final CapabilityChecker capabilityChecker;
    private final AdapterErrorHandler errorHandler;
    private final RetryPolicy retryPolicy;
    private final AdapterMetricsRecorder metricsRecorder;
    private final AdapterTracingManager tracingManager;
    private final ErrorResponseBuilder errorResponseBuilder;

    public ResilienceSupport(final CapabilityChecker capabilityChecker,
                             final AdapterErrorHandler errorHandler,
                             final RetryPolicy retryPolicy,
                             final AdapterMetricsRecorder metricsRecorder,
                             final AdapterTracingManager tracingManager,
                             final ErrorResponseBuilder errorResponseBuilder) {
        this.capabilityChecker = capabilityChecker;
        this.errorHandler = errorHandler;
        this.retryPolicy = retryPolicy;
        this.metricsRecorder = metricsRecorder;
        this.tracingManager = tracingManager;
        this.errorResponseBuilder = errorResponseBuilder;
    }

    public CapabilityChecker getCapabilityChecker() {
        return capabilityChecker;
    }

    public AdapterErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public AdapterMetricsRecorder getMetricsRecorder() {
        return metricsRecorder;
    }

    public AdapterTracingManager getTracingManager() {
        return tracingManager;
    }

    public ErrorResponseBuilder getErrorResponseBuilder() {
        return errorResponseBuilder;
    }
}
