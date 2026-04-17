package org.unreal.modelrouter.adapter.support;

import lombok.extern.slf4j.Slf4j;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;

import java.lang.reflect.Method;

/**
 * 适配器指标和工具支持类
 * 提供请求大小计算、模型名称提取、指标记录等功能
 */
@Slf4j
public class MetricsSupport {

    private final MetricsCollector metricsCollector;
    private final String adapterType;

    public MetricsSupport(MetricsCollector metricsCollector, String adapterType) {
        this.metricsCollector = metricsCollector;
        this.adapterType = adapterType;
    }

    /**
     * 计算请求大小（字节）
     */
    public long calculateRequestSize(Object request) {
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
    public String getModelNameFromRequest(Object request) {
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
    public String getServiceTypeFromRequest(Object request) {
        if (request == null) {
            return "unknown";
        }

        if (request instanceof ChatDTO.Request) return "chat";
        if (request instanceof EmbeddingDTO.Request) return "embedding";
        if (request instanceof RerankDTO.Request) return "rerank";
        if (request instanceof TtsDTO.Request) return "tts";
        if (request instanceof SttDTO.Request) return "stt";
        if (request instanceof ImageGenerateDTO.Request) return "imgGen";
        if (request instanceof ImageEditDTO.Request) return "imgEdit";
        return "unknown";
    }

    /**
     * 记录请求指标
     */
    public void recordRequestMetrics(String serviceType, long requestSize, long responseSize) {
        if (metricsCollector != null) {
            metricsCollector.recordRequestSize(serviceType, requestSize, responseSize);
        }
    }

    /**
     * 记录响应时间指标
     */
    public void recordResponseTimeMetrics(String serviceType, String method, long responseTime, String status) {
        if (metricsCollector != null) {
            metricsCollector.recordRequest(serviceType, method, responseTime, status);
        }
    }

    /**
     * 记录后端调用指标
     */
    public void recordBackendCall(String instanceName, long duration, boolean success) {
        if (metricsCollector != null) {
            metricsCollector.recordBackendCall(adapterType, instanceName, duration, success);
        }
    }

    /**
     * 记录错误指标
     */
    public void recordErrorMetrics(String instanceName, long responseTime, String errorType) {
        if (metricsCollector != null) {
            metricsCollector.recordBackendCall(adapterType, instanceName, responseTime, false);
        }
    }
}
