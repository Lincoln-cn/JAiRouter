package org.unreal.modelrouter.tracing.adapter;

import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

import java.util.HashMap;
import java.util.Map;

/**
 * 适配器追踪增强器
 * 
 * 为不同类型的适配器添加特定的追踪信息，包括：
 * - 适配器特定的Span属性
 * - 适配器性能指标收集
 * - 适配器调用失败时的重试追踪
 * - 适配器特定的结构化日志
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Component
public class AdapterTracingEnhancer {
    
    private final StructuredLogger structuredLogger;
    
    // 适配器特定的属性常量
    private static final String ADAPTER_TYPE = "adapter.type";
    private static final String ADAPTER_VERSION = "adapter.version";
    private static final String ADAPTER_CAPABILITIES = "adapter.capabilities";
    private static final String MODEL_NAME = "model.name";
    private static final String MODEL_TYPE = "model.type";
    private static final String SERVICE_TYPE = "service.type";
    
    public AdapterTracingEnhancer(StructuredLogger structuredLogger) {
        this.structuredLogger = structuredLogger;
    }
    
    /**
     * 增强适配器调用的Span
     * 
     * @param span 要增强的Span
     * @param adapterType 适配器类型
     * @param instance 服务实例
     * @param serviceType 服务类型
     * @param modelName 模型名称
     */
    public void enhanceAdapterSpan(Span span, String adapterType, 
                                  ModelRouterProperties.ModelInstance instance,
                                  String serviceType, String modelName) {
        if (span == null || !span.isRecording()) {
            return;
        }
        
        // 设置适配器基本信息
        span.setAttribute(ADAPTER_TYPE, adapterType);
        span.setAttribute(SERVICE_TYPE, serviceType);
        span.setAttribute(MODEL_NAME, modelName);
        
        // 设置实例信息
        if (instance != null) {
            span.setAttribute("instance.id", instance.getInstanceId());
            span.setAttribute("instance.name", instance.getName());
            span.setAttribute("instance.base_url", instance.getBaseUrl());
            span.setAttribute("instance.path", instance.getPath());
            
            // 设置模型类型（从实例名称推断）
            String modelType = inferModelType(modelName);
            if (modelType != null) {
                span.setAttribute(MODEL_TYPE, modelType);
            }
        }
        
        // 根据适配器类型设置特定属性
        enhanceByAdapterType(span, adapterType, instance);
    }
    
    /**
     * 记录适配器调用开始事件
     * 
     * @param adapterType 适配器类型
     * @param instance 服务实例
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @param context 追踪上下文
     */
    public void logAdapterCallStart(String adapterType, ModelRouterProperties.ModelInstance instance,
                                   String serviceType, String modelName, TracingContext context) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("adapter_type", adapterType);
        eventData.put("service_type", serviceType);
        eventData.put("model_name", modelName);
        
        if (instance != null) {
            eventData.put("instance_id", instance.getInstanceId());
            eventData.put("instance_name", instance.getName());
            eventData.put("base_url", instance.getBaseUrl());
            eventData.put("path", instance.getPath());
        }
        
        structuredLogger.logBusinessEvent("adapter_call_start", eventData, context);
    }
    
    /**
     * 记录适配器调用完成事件
     * 
     * @param adapterType 适配器类型
     * @param instance 服务实例
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @param duration 调用时长
     * @param success 是否成功
     * @param context 追踪上下文
     */
    public void logAdapterCallComplete(String adapterType, ModelRouterProperties.ModelInstance instance,
                                      String serviceType, String modelName, long duration, 
                                      boolean success, TracingContext context) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("adapter_type", adapterType);
        eventData.put("service_type", serviceType);
        eventData.put("model_name", modelName);
        eventData.put("duration_ms", duration);
        eventData.put("success", success);
        
        if (instance != null) {
            eventData.put("instance_id", instance.getInstanceId());
            eventData.put("instance_name", instance.getName());
            eventData.put("base_url", instance.getBaseUrl());
        }
        
        // 记录适配器特定的性能指标
        Map<String, Object> performanceMetrics = collectAdapterPerformanceMetrics(adapterType, duration, success);
        eventData.putAll(performanceMetrics);
        
        structuredLogger.logBusinessEvent("adapter_call_complete", eventData, context);
        
        // 如果是慢调用，记录慢查询日志
        long slowThreshold = getSlowCallThreshold(adapterType);
        if (duration > slowThreshold) {
            structuredLogger.logSlowQuery(
                String.format("%s_adapter_call", adapterType), 
                duration, 
                slowThreshold, 
                context
            );
        }
    }
    
    /**
     * 记录适配器重试事件
     * 
     * @param adapterType 适配器类型
     * @param instance 服务实例
     * @param retryCount 重试次数
     * @param maxRetries 最大重试次数
     * @param lastError 上次错误
     * @param context 追踪上下文
     */
    public void logAdapterRetry(String adapterType, ModelRouterProperties.ModelInstance instance,
                               int retryCount, int maxRetries, Throwable lastError, TracingContext context) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("adapter_type", adapterType);
        eventData.put("retry_count", retryCount);
        eventData.put("max_retries", maxRetries);
        eventData.put("remaining_retries", maxRetries - retryCount);
        
        if (instance != null) {
            eventData.put("instance_id", instance.getInstanceId());
            eventData.put("base_url", instance.getBaseUrl());
        }
        
        if (lastError != null) {
            eventData.put("last_error_type", lastError.getClass().getSimpleName());
            eventData.put("last_error_message", lastError.getMessage());
        }
        
        structuredLogger.logBusinessEvent("adapter_retry", eventData, context);
    }
    
    /**
     * 根据适配器类型设置特定属性
     */
    private void enhanceByAdapterType(Span span, String adapterType, ModelRouterProperties.ModelInstance instance) {
        switch (adapterType.toLowerCase()) {
            case "openai":
                enhanceOpenAIAdapter(span, instance);
                break;
            case "ollama":
                enhanceOllamaAdapter(span, instance);
                break;
            case "vllm":
                enhanceVLLMAdapter(span, instance);
                break;
            case "gpustack":
                enhanceGPUStackAdapter(span, instance);
                break;
            case "xinference":
                enhanceXinferenceAdapter(span, instance);
                break;
            case "localai":
                enhanceLocalAIAdapter(span, instance);
                break;
            default:
                enhanceGenericAdapter(span, instance);
                break;
        }
    }
    
    /**
     * 增强OpenAI适配器
     */
    private void enhanceOpenAIAdapter(Span span, ModelRouterProperties.ModelInstance instance) {
        span.setAttribute(ADAPTER_VERSION, "v1");
        span.setAttribute(ADAPTER_CAPABILITIES, "chat,embedding,tts,stt,image");
        span.setAttribute("api.provider", "openai");
        span.setAttribute("api.version", "v1");
    }
    
    /**
     * 增强Ollama适配器
     */
    private void enhanceOllamaAdapter(Span span, ModelRouterProperties.ModelInstance instance) {
        span.setAttribute(ADAPTER_VERSION, "v1");
        span.setAttribute(ADAPTER_CAPABILITIES, "chat,embedding");
        span.setAttribute("api.provider", "ollama");
        span.setAttribute("deployment.type", "local");
    }
    
    /**
     * 增强VLLM适配器
     */
    private void enhanceVLLMAdapter(Span span, ModelRouterProperties.ModelInstance instance) {
        span.setAttribute(ADAPTER_VERSION, "v1");
        span.setAttribute(ADAPTER_CAPABILITIES, "chat");
        span.setAttribute("api.provider", "vllm");
        span.setAttribute("deployment.type", "distributed");
    }
    
    /**
     * 增强GPUStack适配器
     */
    private void enhanceGPUStackAdapter(Span span, ModelRouterProperties.ModelInstance instance) {
        span.setAttribute(ADAPTER_VERSION, "v1");
        span.setAttribute(ADAPTER_CAPABILITIES, "chat,embedding");
        span.setAttribute("api.provider", "gpustack");
        span.setAttribute("deployment.type", "gpu_cluster");
    }
    
    /**
     * 增强Xinference适配器
     */
    private void enhanceXinferenceAdapter(Span span, ModelRouterProperties.ModelInstance instance) {
        span.setAttribute(ADAPTER_VERSION, "v1");
        span.setAttribute(ADAPTER_CAPABILITIES, "chat,embedding,rerank");
        span.setAttribute("api.provider", "xinference");
        span.setAttribute("deployment.type", "distributed");
    }
    
    /**
     * 增强LocalAI适配器
     */
    private void enhanceLocalAIAdapter(Span span, ModelRouterProperties.ModelInstance instance) {
        span.setAttribute(ADAPTER_VERSION, "v1");
        span.setAttribute(ADAPTER_CAPABILITIES, "chat,embedding,tts,stt");
        span.setAttribute("api.provider", "localai");
        span.setAttribute("deployment.type", "local");
    }
    
    /**
     * 增强通用适配器
     */
    private void enhanceGenericAdapter(Span span, ModelRouterProperties.ModelInstance instance) {
        span.setAttribute(ADAPTER_VERSION, "unknown");
        span.setAttribute("api.provider", "generic");
    }
    
    /**
     * 推断模型类型
     */
    private String inferModelType(String modelName) {
        if (modelName == null) {
            return null;
        }
        
        String lowerName = modelName.toLowerCase();
        
        // 大语言模型
        if (lowerName.contains("gpt") || lowerName.contains("llama") || 
            lowerName.contains("qwen") || lowerName.contains("chatglm") ||
            lowerName.contains("baichuan") || lowerName.contains("claude")) {
            return "llm";
        }
        
        // 嵌入模型
        if (lowerName.contains("embedding") || lowerName.contains("embed") ||
            lowerName.contains("bge") || lowerName.contains("sentence")) {
            return "embedding";
        }
        
        // 重排序模型
        if (lowerName.contains("rerank") || lowerName.contains("reranker")) {
            return "rerank";
        }
        
        // TTS模型
        if (lowerName.contains("tts") || lowerName.contains("speech")) {
            return "tts";
        }
        
        // STT模型
        if (lowerName.contains("stt") || lowerName.contains("whisper")) {
            return "stt";
        }
        
        // 图像模型
        if (lowerName.contains("dall") || lowerName.contains("stable") ||
            lowerName.contains("midjourney") || lowerName.contains("image")) {
            return "image";
        }
        
        return "unknown";
    }
    
    /**
     * 收集适配器性能指标
     */
    private Map<String, Object> collectAdapterPerformanceMetrics(String adapterType, long duration, boolean success) {
        Map<String, Object> metrics = new HashMap<>();
        
        // 基础性能指标
        metrics.put("response_time_ms", duration);
        metrics.put("success_rate", success ? 1.0 : 0.0);
        
        // 适配器特定的性能分类
        if (duration < 100) {
            metrics.put("performance_category", "excellent");
        } else if (duration < 500) {
            metrics.put("performance_category", "good");
        } else if (duration < 2000) {
            metrics.put("performance_category", "acceptable");
        } else {
            metrics.put("performance_category", "slow");
        }
        
        // 适配器类型特定的指标
        switch (adapterType.toLowerCase()) {
            case "openai":
                metrics.put("api_quota_consumed", 1);
                break;
            case "ollama":
                metrics.put("local_gpu_usage", "estimated");
                break;
            case "vllm":
                metrics.put("distributed_call", true);
                break;
        }
        
        return metrics;
    }
    
    /**
     * 获取慢调用阈值
     */
    private long getSlowCallThreshold(String adapterType) {
        // 根据适配器类型返回不同的慢调用阈值
        switch (adapterType.toLowerCase()) {
            case "openai":
                return 5000; // OpenAI API通常较慢
            case "ollama":
                return 2000; // 本地部署相对较快
            case "vllm":
                return 3000; // 分布式部署中等
            case "gpustack":
                return 2500; // GPU集群较快
            default:
                return 3000; // 默认阈值
        }
    }
}