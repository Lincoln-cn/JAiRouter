package org.unreal.modelrouter.tracing.adapter;

import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AdapterTracingEnhancer 单元测试
 */
@ExtendWith(MockitoExtension.class)
class AdapterTracingEnhancerTest {

    @Mock
    private StructuredLogger structuredLogger;
    
    @Mock
    private TracingContext tracingContext;
    
    @Mock
    private Span span;

    private AdapterTracingEnhancer enhancer;
    private ModelRouterProperties.ModelInstance instance;

    @BeforeEach
    void setUp() {
        enhancer = new AdapterTracingEnhancer(structuredLogger);
        
        // 创建测试用的实例
        instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-instance");
        instance.setBaseUrl("http://api.openai.com");
        instance.setPath("/v1/chat/completions");
    }

    @Test
    void testEnhanceAdapterSpan_OpenAI() {
        // Given
        String adapterType = "openai";
        String serviceType = "chat";
        String modelName = "gpt-3.5-turbo";
        
        when(span.isRecording()).thenReturn(true);

        // When
        enhancer.enhanceAdapterSpan(span, adapterType, instance, serviceType, modelName);

        // Then
        verify(span).setAttribute("adapter.type", adapterType);
        verify(span).setAttribute("service.type", serviceType);
        verify(span).setAttribute("model.name", modelName);
        verify(span).setAttribute("instance.id", instance.getInstanceId());
        verify(span).setAttribute("instance.name", instance.getName());
        verify(span).setAttribute("instance.base_url", instance.getBaseUrl());
        verify(span).setAttribute("instance.path", instance.getPath());
        verify(span).setAttribute("model.type", "llm");
        verify(span).setAttribute("adapter.version", "v1");
        verify(span).setAttribute("adapter.capabilities", "chat,embedding,tts,stt,image");
        verify(span).setAttribute("api.provider", "openai");
        verify(span).setAttribute("api.version", "v1");
    }

    @Test
    void testEnhanceAdapterSpan_Ollama() {
        // Given
        String adapterType = "ollama";
        String serviceType = "embedding";
        String modelName = "bge-large-zh";
        
        when(span.isRecording()).thenReturn(true);

        // When
        enhancer.enhanceAdapterSpan(span, adapterType, instance, serviceType, modelName);

        // Then
        verify(span).setAttribute("adapter.type", adapterType);
        verify(span).setAttribute("service.type", serviceType);
        verify(span).setAttribute("model.name", modelName);
        verify(span).setAttribute("model.type", "embedding");
        verify(span).setAttribute("adapter.version", "v1");
        verify(span).setAttribute("adapter.capabilities", "chat,embedding");
        verify(span).setAttribute("api.provider", "ollama");
        verify(span).setAttribute("deployment.type", "local");
    }

    @Test
    void testEnhanceAdapterSpan_VLLM() {
        // Given
        String adapterType = "vllm";
        String serviceType = "chat";
        String modelName = "llama-2-7b";
        
        when(span.isRecording()).thenReturn(true);

        // When
        enhancer.enhanceAdapterSpan(span, adapterType, instance, serviceType, modelName);

        // Then
        verify(span).setAttribute("adapter.type", adapterType);
        verify(span).setAttribute("model.type", "llm");
        verify(span).setAttribute("adapter.version", "v1");
        verify(span).setAttribute("adapter.capabilities", "chat");
        verify(span).setAttribute("api.provider", "vllm");
        verify(span).setAttribute("deployment.type", "distributed");
    }

    @Test
    void testEnhanceAdapterSpan_NullSpan() {
        // Given
        String adapterType = "openai";
        String serviceType = "chat";
        String modelName = "gpt-3.5-turbo";

        // When & Then
        assertDoesNotThrow(() -> {
            enhancer.enhanceAdapterSpan(null, adapterType, instance, serviceType, modelName);
        });
    }

    @Test
    void testEnhanceAdapterSpan_NotRecording() {
        // Given
        String adapterType = "openai";
        String serviceType = "chat";
        String modelName = "gpt-3.5-turbo";
        
        when(span.isRecording()).thenReturn(false);

        // When
        enhancer.enhanceAdapterSpan(span, adapterType, instance, serviceType, modelName);

        // Then
        verify(span, never()).setAttribute(anyString(), anyString());
    }

    @Test
    void testLogAdapterCallStart() {
        // Given
        String adapterType = "gpustack";
        String serviceType = "chat";
        String modelName = "qwen-7b";

        // When
        enhancer.logAdapterCallStart(adapterType, instance, serviceType, modelName, tracingContext);

        // Then
        verify(structuredLogger).logBusinessEvent(eq("adapter_call_start"), argThat(data -> {
            Map<String, Object> eventData = (Map<String, Object>) data;
            return adapterType.equals(eventData.get("adapter_type")) &&
                   serviceType.equals(eventData.get("service_type")) &&
                   modelName.equals(eventData.get("model_name")) &&
                   instance.getInstanceId().equals(eventData.get("instance_id"));
        }), eq(tracingContext));
    }

    @Test
    void testLogAdapterCallComplete_Success() {
        // Given
        String adapterType = "xinference";
        String serviceType = "rerank";
        String modelName = "bge-reranker";
        long duration = 250L;
        boolean success = true;

        // When
        enhancer.logAdapterCallComplete(adapterType, instance, serviceType, modelName, duration, success, tracingContext);

        // Then
        verify(structuredLogger).logBusinessEvent(eq("adapter_call_complete"), argThat(data -> {
            Map<String, Object> eventData = (Map<String, Object>) data;
            return adapterType.equals(eventData.get("adapter_type")) &&
                   serviceType.equals(eventData.get("service_type")) &&
                   modelName.equals(eventData.get("model_name")) &&
                   duration == (Long) eventData.get("duration_ms") &&
                   success == (Boolean) eventData.get("success");
        }), eq(tracingContext));
    }

    @Test
    void testLogAdapterCallComplete_SlowCall() {
        // Given
        String adapterType = "openai";
        String serviceType = "chat";
        String modelName = "gpt-4";
        long duration = 6000L; // 超过OpenAI的5000ms阈值
        boolean success = true;

        // When
        enhancer.logAdapterCallComplete(adapterType, instance, serviceType, modelName, duration, success, tracingContext);

        // Then
        verify(structuredLogger).logBusinessEvent(eq("adapter_call_complete"), anyMap(), eq(tracingContext));
        verify(structuredLogger).logSlowQuery(
            eq("openai_adapter_call"), 
            eq(duration), 
            eq(5000L), 
            eq(tracingContext)
        );
    }

    @Test
    void testLogAdapterRetry() {
        // Given
        String adapterType = "localai";
        int retryCount = 2;
        int maxRetries = 3;
        Throwable lastError = new RuntimeException("Connection timeout");

        // When
        enhancer.logAdapterRetry(adapterType, instance, retryCount, maxRetries, lastError, tracingContext);

        // Then
        verify(structuredLogger).logBusinessEvent(eq("adapter_retry"), argThat(data -> {
            Map<String, Object> eventData = (Map<String, Object>) data;
            return adapterType.equals(eventData.get("adapter_type")) &&
                   retryCount == (Integer) eventData.get("retry_count") &&
                   maxRetries == (Integer) eventData.get("max_retries") &&
                   (maxRetries - retryCount) == (Integer) eventData.get("remaining_retries") &&
                   lastError.getClass().getSimpleName().equals(eventData.get("last_error_type"));
        }), eq(tracingContext));
    }

    @Test
    void testModelTypeInference() {
        // Test different model types
        testModelTypeInference("gpt-3.5-turbo", "llm");
        testModelTypeInference("text-embedding-ada-002", "embedding");
        testModelTypeInference("bge-reranker-large", "rerank");
        testModelTypeInference("tts-1", "tts");
        testModelTypeInference("whisper-1", "stt");
        testModelTypeInference("dall-e-3", "image");
        testModelTypeInference("unknown-model", "unknown");
    }

    private void testModelTypeInference(String modelName, String expectedType) {
        // Given
        String adapterType = "openai";
        String serviceType = "chat";
        
        // Create a fresh span mock for each test
        Span testSpan = mock(Span.class);
        when(testSpan.isRecording()).thenReturn(true);

        // When
        enhancer.enhanceAdapterSpan(testSpan, adapterType, instance, serviceType, modelName);

        // Then
        verify(testSpan).setAttribute("model.type", expectedType);
    }

    @Test
    void testAdapterTypeEnhancement() {
        // Test different adapter types
        testAdapterTypeEnhancement("gpustack", "gpu_cluster");
        testAdapterTypeEnhancement("xinference", "distributed");
        testAdapterTypeEnhancement("localai", "local");
        testAdapterTypeEnhancement("unknown", null);
    }

    private void testAdapterTypeEnhancement(String adapterType, String expectedDeploymentType) {
        // Given
        String serviceType = "chat";
        String modelName = "test-model";
        
        // Create a fresh span mock for each test
        Span testSpan = mock(Span.class);
        when(testSpan.isRecording()).thenReturn(true);

        // When
        enhancer.enhanceAdapterSpan(testSpan, adapterType, instance, serviceType, modelName);

        // Then
        verify(testSpan).setAttribute("adapter.type", adapterType);
        if (expectedDeploymentType != null) {
            verify(testSpan).setAttribute("deployment.type", expectedDeploymentType);
        }
    }

    @Test
    void testLogAdapterCallStart_NullInstance() {
        // Given
        String adapterType = "test";
        String serviceType = "chat";
        String modelName = "test-model";

        // When
        enhancer.logAdapterCallStart(adapterType, null, serviceType, modelName, tracingContext);

        // Then
        verify(structuredLogger).logBusinessEvent(eq("adapter_call_start"), argThat(data -> {
            Map<String, Object> eventData = (Map<String, Object>) data;
            return adapterType.equals(eventData.get("adapter_type")) &&
                   serviceType.equals(eventData.get("service_type")) &&
                   modelName.equals(eventData.get("model_name")) &&
                   !eventData.containsKey("instance_id");
        }), eq(tracingContext));
    }

    @Test
    void testLogAdapterRetry_NullError() {
        // Given
        String adapterType = "test";
        int retryCount = 1;
        int maxRetries = 3;

        // When
        enhancer.logAdapterRetry(adapterType, instance, retryCount, maxRetries, null, tracingContext);

        // Then
        verify(structuredLogger).logBusinessEvent(eq("adapter_retry"), argThat(data -> {
            Map<String, Object> eventData = (Map<String, Object>) data;
            return adapterType.equals(eventData.get("adapter_type")) &&
                   retryCount == (Integer) eventData.get("retry_count") &&
                   !eventData.containsKey("last_error_type") &&
                   !eventData.containsKey("last_error_message");
        }), eq(tracingContext));
    }
}