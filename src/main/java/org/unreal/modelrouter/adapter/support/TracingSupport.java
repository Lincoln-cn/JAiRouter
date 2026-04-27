package org.unreal.modelrouter.adapter.support;

import lombok.extern.slf4j.Slf4j;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;
import org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer;
import org.unreal.modelrouter.util.ApplicationContextProvider;

/**
 * 适配器追踪支持工具类
 * 处理适配器调用、重试、转换错误的追踪记录
 */
@Slf4j
public class TracingSupport {

    private final MetricsCollector metricsCollector;
    private final String adapterType;

    public TracingSupport(final MetricsCollector metricsCollector, final String adapterType) {
        this.metricsCollector = metricsCollector;
        this.adapterType = adapterType;
    }

    /**
     * 记录适配器调用开始
     */
    public void logAdapterCallStart(final ModelRouterProperties.ModelInstance instance, 
                                     final String serviceType, final String modelName) {
        TracingContext tracingContext = TracingContextHolder.getCurrentContext();
        if (tracingContext != null && tracingContext.isActive()) {
            try {
                AdapterTracingEnhancer enhancer = ApplicationContextProvider.getBean(AdapterTracingEnhancer.class);
                enhancer.logAdapterCallStart(adapterType, instance, serviceType, modelName, tracingContext);

                io.opentelemetry.api.trace.Span currentSpan = tracingContext.getCurrentSpan();
                if (currentSpan != null) {
                    enhancer.enhanceAdapterSpan(currentSpan, adapterType, instance, serviceType, modelName);
                }
            } catch (Exception e) {
                log.debug("记录适配器调用追踪失败：{}", e.getMessage());
            }
        }
    }

    /**
     * 记录适配器调用完成
     */
    public void logAdapterCallComplete(final ModelRouterProperties.ModelInstance instance, 
                                        final String serviceType, final String modelName, 
                                        final long duration, final boolean success) {
        TracingContext tracingContext = TracingContextHolder.getCurrentContext();
        if (tracingContext != null && tracingContext.isActive()) {
            try {
                AdapterTracingEnhancer enhancer = ApplicationContextProvider.getBean(AdapterTracingEnhancer.class);
                enhancer.logAdapterCallComplete(adapterType, instance, serviceType, modelName, duration, success, tracingContext);
            } catch (Exception e) {
                log.debug("记录适配器调用完成追踪失败：{}", e.getMessage());
            }
        }
    }

    /**
     * 记录适配器重试
     */
    public void logAdapterRetry(final ModelRouterProperties.ModelInstance instance, 
                                 final int retryCount, final int maxRetries, final Throwable error) {
        TracingContext tracingContext = TracingContextHolder.getCurrentContext();
        if (tracingContext != null && tracingContext.isActive()) {
            try {
                AdapterTracingEnhancer enhancer = ApplicationContextProvider.getBean(AdapterTracingEnhancer.class);
                enhancer.logAdapterRetry(adapterType, instance, retryCount, maxRetries, error, tracingContext);
            } catch (Exception e) {
                log.debug("记录适配器重试追踪失败：{}", e.getMessage());
            }
        }

        if (metricsCollector != null) {
            metricsCollector.recordBackendCall(adapterType, instance != null ? instance.getName() : "unknown", 0, false);
        }
    }

    /**
     * 记录适配器转换错误
     */
    public void logAdapterTransformError(final Throwable error) {
        TracingContext tracingContext = TracingContextHolder.getCurrentContext();
        if (tracingContext != null && tracingContext.isActive()) {
            try {
                AdapterTracingEnhancer enhancer = ApplicationContextProvider.getBean(AdapterTracingEnhancer.class);
                enhancer.logAdapterRetry(adapterType, null, 0, 0, error, tracingContext);
            } catch (Exception e) {
                log.debug("记录适配器转换错误追踪失败：{}", e.getMessage());
            }
        }

        if (metricsCollector != null) {
            metricsCollector.recordBackendCall(adapterType, "unknown", 0, false);
        }
    }
}
