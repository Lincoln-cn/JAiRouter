package org.unreal.modelrouter.adapter.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AdapterTracingManager 单元测试
 * 
 * 测试适配器追踪管理器的功能，包括：
 * - 适配器调用 Span 的创建和结束
 * - 追踪属性记录
 * - 错误状态记录
 * - 追踪上下文管理
 * 
 * @author JAiRouter Team
 * @since v2.3.2
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdapterTracingManager 单元测试")
class AdapterTracingManagerTest {

    @Mock
    private Tracer tracer;

    @Mock
    private TracingContext tracingContext;

    @Mock
    private Span span;

    private AdapterTracingManager tracingManager;

    @BeforeEach
    void setUp() {
        tracingManager = new AdapterTracingManager(tracer);
        
        // 清理线程本地上下文
        TracingContextHolder.clearCurrentContext();
    }

    // ========================================
    // startAdapterCall 测试
    // ========================================

    @Test
    @DisplayName("开始适配器调用追踪 - 有活跃追踪上下文")
    void testStartAdapterCall_WithActiveContext() {
        // Arrange
        when(tracingContext.isActive()).thenReturn(true);
        when(tracingContext.createSpan(anyString(), eq(SpanKind.CLIENT))).thenReturn(span);
        TracingContextHolder.setCurrentContext(tracingContext);
        
        String adapterType = "gpustack";
        ModelRouterProperties.ModelInstance instance = createMockInstance("instance-1");
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;
        String modelName = "llama-3";

        // Act
        Span result = tracingManager.startAdapterCall(adapterType, instance, serviceType, modelName);

        // Assert
        assertNotNull(result);
        verify(tracingContext, times(1)).createSpan(anyString(), eq(SpanKind.CLIENT));
        verify(span, atLeastOnce()).setAttribute(anyString(), anyString());
        verify(tracingContext, times(1)).setCurrentSpan(span);
        
        // 清理
        TracingContextHolder.clearCurrentContext();
    }

    @Test
    @DisplayName("开始适配器调用追踪 - 无活跃追踪上下文返回 null")
    void testStartAdapterCall_NoActiveContext() {
        // Arrange
        when(tracingContext.isActive()).thenReturn(false);
        TracingContextHolder.setCurrentContext(tracingContext);
        
        String adapterType = "ollama";
        ModelRouterProperties.ModelInstance instance = createMockInstance("instance-2");
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.embedding;
        String modelName = "qwen-2";

        // Act
        Span result = tracingManager.startAdapterCall(adapterType, instance, serviceType, modelName);

        // Assert
        assertNull(result);
        verify(tracingContext, never()).createSpan(anyString(), any());
        
        // 清理
        TracingContextHolder.clearCurrentContext();
    }

    @Test
    @DisplayName("开始适配器调用追踪 - 追踪上下文为 null 返回 null")
    void testStartAdapterCall_NullContext() {
        // Arrange - 不设置任何上下文
        TracingContextHolder.clearCurrentContext();
        
        String adapterType = "vllm";
        ModelRouterProperties.ModelInstance instance = createMockInstance("instance-3");
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.rerank;
        String modelName = "chatglm-4";

        // Act
        Span result = tracingManager.startAdapterCall(adapterType, instance, serviceType, modelName);

        // Assert
        assertNull(result);
    }

    @Test
    @DisplayName("开始适配器调用追踪 - Span 名称构建正确")
    void testStartAdapterCall_SpanNameBuilding() {
        // Arrange
        when(tracingContext.isActive()).thenReturn(true);
        when(tracingContext.createSpan(anyString(), eq(SpanKind.CLIENT))).thenReturn(span);
        TracingContextHolder.setCurrentContext(tracingContext);
        
        String adapterType = "gpustack";
        ModelRouterProperties.ModelInstance instance = createMockInstance("instance-1");
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;
        String modelName = "llama-3-8b-instruct";

        // Act
        tracingManager.startAdapterCall(adapterType, instance, serviceType, modelName);

        // Assert
        ArgumentCaptor<String> spanNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(tracingContext).createSpan(spanNameCaptor.capture(), eq(SpanKind.CLIENT));
        
        String spanName = spanNameCaptor.getValue();
        assertTrue(spanName.contains("gpustack"));
        assertTrue(spanName.contains("chat"));
        assertTrue(spanName.contains("llama-3-8b-instruct"));
        
        // 清理
        TracingContextHolder.clearCurrentContext();
    }

    // ========================================
    // endAdapterCall 测试
    // ========================================

    @Test
    @DisplayName("结束适配器调用追踪 - 成功场景")
    void testEndAdapterCall_Success() {
        // Arrange
        when(tracingContext.isActive()).thenReturn(true);
        when(tracingContext.createSpan(anyString(), eq(SpanKind.CLIENT))).thenReturn(span);
        TracingContextHolder.setCurrentContext(tracingContext);
        
        Span span = tracingManager.startAdapterCall("gpustack", 
                createMockInstance("instance-1"), 
                ModelServiceRegistry.ServiceType.chat, "llama-3");

        // Act
        tracingManager.endAdapterCall(span, true, null);

        // Assert
        verify(span, times(1)).setStatus(StatusCode.OK);
        verify(span, times(1)).end();
        verify(tracingContext, times(1)).finishSpan(span);
        verify(span, never()).recordException(any());
        
        // 清理
        TracingContextHolder.clearCurrentContext();
    }

    @Test
    @DisplayName("结束适配器调用追踪 - 失败场景记录错误")
    void testEndAdapterCall_Failure() {
        // Arrange
        when(tracingContext.isActive()).thenReturn(true);
        when(tracingContext.createSpan(anyString(), eq(SpanKind.CLIENT))).thenReturn(span);
        TracingContextHolder.setCurrentContext(tracingContext);
        
        Span span = tracingManager.startAdapterCall("gpustack", 
                createMockInstance("instance-1"), 
                ModelServiceRegistry.ServiceType.chat, "llama-3");
        
        Throwable error = new RuntimeException("Connection timeout");

        // Act
        tracingManager.endAdapterCall(span, false, error);

        // Assert
        verify(span, times(1)).setStatus(StatusCode.ERROR);
        verify(span, times(1)).recordException(error);
        verify(span, atLeastOnce()).setAttribute(eq("error.message"), anyString());
        verify(span, atLeastOnce()).setAttribute(eq("error.type"), anyString());
        verify(span, times(1)).end();
        verify(tracingContext, times(1)).finishSpan(span);
        
        // 清理
        TracingContextHolder.clearCurrentContext();
    }

    @Test
    @DisplayName("结束适配器调用追踪 - span 为 null 时不抛异常")
    void testEndAdapterCall_NullSpan() {
        // Act & Assert
        assertDoesNotThrow(() -> 
            tracingManager.endAdapterCall(null, false, new RuntimeException("error"))
        );
    }

    @Test
    @DisplayName("结束适配器调用追踪 - error 为 null 时不抛异常")
    void testEndAdapterCall_NullError() {
        // Arrange
        when(tracingContext.isActive()).thenReturn(true);
        when(tracingContext.createSpan(anyString(), eq(SpanKind.CLIENT))).thenReturn(span);
        TracingContextHolder.setCurrentContext(tracingContext);
        
        Span span = tracingManager.startAdapterCall("gpustack", 
                createMockInstance("instance-1"), 
                ModelServiceRegistry.ServiceType.chat, "llama-3");

        // Act & Assert
        assertDoesNotThrow(() -> 
            tracingManager.endAdapterCall(span, false, null)
        );
        
        verify(span, times(1)).setStatus(StatusCode.ERROR);
        verify(span, never()).recordException(any());
        
        // 清理
        TracingContextHolder.clearCurrentContext();
    }

    // ========================================
    // recordAdapterAttributes 测试
    // ========================================

    @Test
    @DisplayName("记录适配器属性 - 正常记录")
    void testRecordAdapterAttributes_Success() {
        // Arrange
        ModelRouterProperties.ModelInstance instance = createMockInstance("instance-1");
        instance.setBaseUrl("http://localhost:8080");
        instance.setStatus("active");
        instance.setWeight(10);
        
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;

        // Act
        tracingManager.recordAdapterAttributes(span, "gpustack", instance, serviceType, "llama-3");

        // Assert
        verify(span, atLeastOnce()).setAttribute("adapter.type", "gpustack");
        verify(span, atLeastOnce()).setAttribute("adapter.instance.name", "instance-1");
        verify(span, atLeastOnce()).setAttribute("adapter.instance.url", "http://localhost:8080");
        verify(span, atLeastOnce()).setAttribute("adapter.service.type", "CHAT");
        verify(span, atLeastOnce()).setAttribute("adapter.model.name", "llama-3");
        verify(span, atLeastOnce()).setAttribute("adapter.instance.healthy", true);
        verify(span, atLeastOnce()).setAttribute("adapter.instance.weight", 10L);
    }

    @Test
    @DisplayName("记录适配器属性 - span 为 null 时不抛异常")
    void testRecordAdapterAttributes_NullSpan() {
        // Arrange
        ModelRouterProperties.ModelInstance instance = createMockInstance("instance-1");
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;

        // Act & Assert
        assertDoesNotThrow(() -> 
            tracingManager.recordAdapterAttributes(null, "gpustack", instance, serviceType, "llama-3")
        );
    }

    // ========================================
    // recordRequestAttributes 测试
    // ========================================

    @Test
    @DisplayName("记录请求属性 - 正常记录")
    void testRecordRequestAttributes_Success() {
        // Arrange
        long requestSize = 2048L;
        boolean hasStream = true;

        // Act
        tracingManager.recordRequestAttributes(span, requestSize, hasStream);

        // Assert
        verify(span, times(1)).setAttribute("adapter.request.size", requestSize);
        verify(span, times(1)).setAttribute("adapter.request.streaming", hasStream);
    }

    @Test
    @DisplayName("记录请求属性 - span 为 null 时不抛异常")
    void testRecordRequestAttributes_NullSpan() {
        // Act & Assert
        assertDoesNotThrow(() -> 
            tracingManager.recordRequestAttributes(null, 1024L, false)
        );
    }

    // ========================================
    // recordResponseAttributes 测试
    // ========================================

    @Test
    @DisplayName("记录响应属性 - 正常记录")
    void testRecordResponseAttributes_Success() {
        // Arrange
        long responseSize = 4096L;
        long durationMs = 250L;
        int statusCode = 200;

        // Act
        tracingManager.recordResponseAttributes(span, responseSize, durationMs, statusCode);

        // Assert
        verify(span, times(1)).setAttribute("adapter.response.size", responseSize);
        verify(span, times(1)).setAttribute("adapter.response.duration.ms", durationMs);
        verify(span, times(1)).setAttribute("adapter.response.status.code", statusCode);
    }

    @Test
    @DisplayName("记录响应属性 - span 为 null 时不抛异常")
    void testRecordResponseAttributes_NullSpan() {
        // Act & Assert
        assertDoesNotThrow(() -> 
            tracingManager.recordResponseAttributes(null, 2048L, 100L, 200)
        );
    }

    // ========================================
    // buildSpanName 测试（通过 startAdapterCall 间接测试）
    // ========================================

    @Test
    @DisplayName("Span 名称构建 - 包含所有信息")
    void testSpanNameBuilding_Complete() {
        // Arrange
        when(tracingContext.isActive()).thenReturn(true);
        when(tracingContext.createSpan(anyString(), eq(SpanKind.CLIENT))).thenReturn(span);
        TracingContextHolder.setCurrentContext(tracingContext);

        // Act
        tracingManager.startAdapterCall("gpustack", 
                createMockInstance("instance-1"), 
                ModelServiceRegistry.ServiceType.chat, "llama-3-8b");

        // Assert
        ArgumentCaptor<String> spanNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(tracingContext).createSpan(spanNameCaptor.capture(), eq(SpanKind.CLIENT));
        
        String spanName = spanNameCaptor.getValue();
        assertEquals("adapter.call.gpustack.chat.llama-3-8b", spanName);
        
        // 清理
        TracingContextHolder.clearCurrentContext();
    }

    @Test
    @DisplayName("Span 名称构建 - 模型名称过长时截断")
    void testSpanNameBuilding_LongModelName() {
        // Arrange
        when(tracingContext.isActive()).thenReturn(true);
        when(tracingContext.createSpan(anyString(), eq(SpanKind.CLIENT))).thenReturn(span);
        TracingContextHolder.setCurrentContext(tracingContext);
        
        String longModelName = "this-is-a-very-long-model-name-that-should-be-truncated";

        // Act
        tracingManager.startAdapterCall("ollama", 
                createMockInstance("instance-1"), 
                ModelServiceRegistry.ServiceType.embedding, longModelName);

        // Assert
        ArgumentCaptor<String> spanNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(tracingContext).createSpan(spanNameCaptor.capture(), eq(SpanKind.CLIENT));
        
        String spanName = spanNameCaptor.getValue();
        assertTrue(spanName.contains("ollama"));
        assertTrue(spanName.contains("embedding"));
        // 验证模型名称被截断
        assertTrue(spanName.length() < 60);
        
        // 清理
        TracingContextHolder.clearCurrentContext();
    }

    // ========================================
    // 集成场景测试
    // ========================================

    @Test
    @DisplayName("完整追踪流程 - 成功场景")
    void testFullTracingFlow_Success() {
        // Arrange
        when(tracingContext.isActive()).thenReturn(true);
        when(tracingContext.createSpan(anyString(), eq(SpanKind.CLIENT))).thenReturn(span);
        TracingContextHolder.setCurrentContext(tracingContext);

        String adapterType = "gpustack";
        ModelRouterProperties.ModelInstance instance = createMockInstance("instance-1");
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.chat;
        String modelName = "llama-3";

        // Act - 模拟完整追踪流程
        Span startedSpan = tracingManager.startAdapterCall(adapterType, instance, serviceType, modelName);
        tracingManager.recordRequestAttributes(startedSpan, 2048L, true);
        tracingManager.recordResponseAttributes(startedSpan, 4096L, 250L, 200);
        tracingManager.endAdapterCall(startedSpan, true, null);

        // Assert
        assertNotNull(startedSpan);
        verify(span, atLeastOnce()).setAttribute(anyString(), anyString());
        verify(span, times(1)).setStatus(StatusCode.OK);
        verify(span, times(1)).end();
        
        // 清理
        TracingContextHolder.clearCurrentContext();
    }

    @Test
    @DisplayName("完整追踪流程 - 失败场景")
    void testFullTracingFlow_Failure() {
        // Arrange
        when(tracingContext.isActive()).thenReturn(true);
        when(tracingContext.createSpan(anyString(), eq(SpanKind.CLIENT))).thenReturn(span);
        TracingContextHolder.setCurrentContext(tracingContext);
        
        Throwable error = new RuntimeException("Downstream service error");

        String adapterType = "vllm";
        ModelRouterProperties.ModelInstance instance = createMockInstance("instance-2");
        ModelServiceRegistry.ServiceType serviceType = ModelServiceRegistry.ServiceType.rerank;
        String modelName = "qwen-2";

        // Act - 模拟完整追踪流程（失败）
        Span startedSpan = tracingManager.startAdapterCall(adapterType, instance, serviceType, modelName);
        tracingManager.recordRequestAttributes(startedSpan, 1024L, false);
        tracingManager.endAdapterCall(startedSpan, false, error);

        // Assert
        assertNotNull(startedSpan);
        verify(span, times(1)).setStatus(StatusCode.ERROR);
        verify(span, times(1)).recordException(error);
        verify(span, times(1)).end();
        
        // 清理
        TracingContextHolder.clearCurrentContext();
    }

    // ========================================
    // 辅助方法
    // ========================================

    /**
     * 创建模拟的 ModelInstance 对象
     */
    private ModelRouterProperties.ModelInstance createMockInstance(String name) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName(name);
        instance.setBaseUrl("http://localhost:8080");
        instance.setWeight(5);
        instance.setStatus("active");
        return instance;
    }
}
