package org.unreal.modelrouter.router.adapter.support;

import org.unreal.modelrouter.constants.ServiceTypeConstants;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * 适配器指标和工具支持类
 * 提供请求大小计算、模型名称提取、指标记录等功能
 */
@Slf4j
public class MetricsSupport {

    private final MetricsCollector metricsCollector;
    private final String adapterType;

    public MetricsSupport(final MetricsCollector metricsCollector, final String adapterType) {
        this.metricsCollector = metricsCollector;
        this.adapterType = adapterType;
    }

    /**
     * 计算请求大小（字节）
     */
    public long calculateRequestSize(final Object request) {
        if (request == null) {
            return 0;
        }
        try {
            String requestStr = request.toString();
            return requestStr.getBytes().length;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 从请求对象中提取模型名称
     */
    public String getModelNameFromRequest(final Object request) {
        if (request == null) {
            return "unknown";
        }

        try {
            Method modelMethod = request.getClass().getMethod("model");
            Object modelName = modelMethod.invoke(request);
            return modelName != null ? modelName.toString() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 从请求对象中提取服务类型
     */
    public String getServiceTypeFromRequest(final Object request) {
        if (request == null) {
            return "unknown";
        }

        if (request instanceof ChatDTO.Request) {
            return ServiceTypeConstants.CHAT;
        }
        if (request instanceof EmbeddingDTO.Request) {
            return ServiceTypeConstants.EMBEDDING;
        }
        if (request instanceof RerankDTO.Request) {
            return ServiceTypeConstants.RERANK;
        }
        if (request instanceof TtsDTO.Request) {
            return ServiceTypeConstants.TTS;
        }
        if (request instanceof SttDTO.Request) {
            return ServiceTypeConstants.STT;
        }
        if (request instanceof ImageGenerateDTO.Request) {
            return ServiceTypeConstants.IMG_GEN;
        }
        if (request instanceof ImageEditDTO.Request) {
            return ServiceTypeConstants.IMG_EDIT;
        }
        return "unknown";
    }

    /**
     * 记录请求指标
     */
    public void recordRequestMetrics(final String serviceType, final long requestSize, final long responseSize) {
        if (metricsCollector != null) {
            metricsCollector.recordRequestSize(serviceType, requestSize, responseSize);
        }
    }

    /**
     * 记录响应时间指标
     */
    public void recordResponseTimeMetrics(final String serviceType, final String method, final long responseTime,final String status) {
        if (metricsCollector != null) {
            metricsCollector.recordRequest(serviceType, method, responseTime, status);
        }
    }

    /**
     * 记录后端调用指标
     */
    public void recordBackendCall(final String instanceName, final long duration, final boolean success) {
        if (metricsCollector != null) {
            metricsCollector.recordBackendCall(adapterType, instanceName, duration, success);
        }
    }

    /**
     * 记录错误指标
     */
    public void recordErrorMetrics(final String instanceName, final long responseTime, final String errorType) {
        if (metricsCollector != null) {
            metricsCollector.recordBackendCall(adapterType, instanceName, responseTime, false);
        }
    }
}
