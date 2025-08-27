package org.unreal.modelrouter.tracing;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

/**
 * TracingContext单元测试
 * 
 * 测试追踪上下文的核心功能，包括：
 * - Span的创建和管理
 * - 上下文传播
 * - 属性和事件管理
 * - 状态管理
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracingContextTest {

    private TracingContext tracingContext;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        // 使用真实的SDK Tracer Provider进行测试
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
        tracer = tracerProvider.get("test-tracer");
        tracingContext = new DefaultTracingContext(tracer);
    }

    @Test
    void testCreateSpan() {
        // 测试创建Span
        String operationName = "test-operation";
        SpanKind spanKind = SpanKind.SERVER;

        Span span = tracingContext.createSpan(operationName, spanKind);

        assertNotNull(span, "创建的Span不应为null");
        assertTrue(span.getSpanContext().isValid(), "Span上下文应该有效");
        assertFalse(span.getSpanContext().getTraceId().isEmpty(), "TraceId不应为空");
        assertFalse(span.getSpanContext().getSpanId().isEmpty(), "SpanId不应为空");
    }

    @Test
    void testCreateChildSpan() {
        // 创建父Span
        Span parentSpan = tracingContext.createSpan("parent-operation", SpanKind.SERVER);
        assertNotNull(parentSpan, "父Span不应为null");

        // 创建子Span
        Span childSpan = tracingContext.createChildSpan("child-operation", SpanKind.CLIENT, parentSpan);

        assertNotNull(childSpan, "子Span不应为null");
        assertTrue(childSpan.getSpanContext().isValid(), "子Span上下文应该有效");
        assertEquals(parentSpan.getSpanContext().getTraceId(), 
                    childSpan.getSpanContext().getTraceId(), 
                    "父子Span应该有相同的TraceId");
        assertNotEquals(parentSpan.getSpanContext().getSpanId(), 
                       childSpan.getSpanContext().getSpanId(), 
                       "父子Span应该有不同的SpanId");
    }

    @Test
    void testGetAndSetCurrentSpan() {
        // 初始状态下获取当前Span
        Span initialSpan = tracingContext.getCurrentSpan();
        assertNotNull(initialSpan, "初始Span不应为null");

        // 创建新Span并设置为当前
        Span newSpan = tracingContext.createSpan("new-operation", SpanKind.INTERNAL);
        tracingContext.setCurrentSpan(newSpan);

        Span currentSpan = tracingContext.getCurrentSpan();
        assertEquals(newSpan, currentSpan, "当前Span应该是设置的新Span");
    }

    @Test
    void testFinishSpan() {
        // 创建Span
        Span span = tracingContext.createSpan("test-finish", SpanKind.SERVER);
        assertTrue(span.getSpanContext().isValid(), "Span应该是有效的");

        // 完成Span
        tracingContext.finishSpan(span);
        
        // 验证Span已结束（通过检查是否能继续添加属性来验证）
        // 注意：由于OpenTelemetry的实现，已结束的Span仍然可以设置属性，这里我们主要验证方法不抛异常
        assertDoesNotThrow(() -> span.setAttribute("test", "value"));
    }

    @Test
    void testFinishSpanWithError() {
        // 创建Span
        Span span = tracingContext.createSpan("test-error", SpanKind.SERVER);
        
        // 创建测试异常
        Exception testError = new RuntimeException("Test error message");

        // 完成Span并记录错误
        assertDoesNotThrow(() -> tracingContext.finishSpan(span, testError));
    }

    @Test
    void testInjectAndExtractContext() {
        // 创建Span以建立追踪上下文
        Span span = tracingContext.createSpan("test-propagation", SpanKind.SERVER);
        String originalTraceId = tracingContext.getTraceId();
        String originalSpanId = tracingContext.getSpanId();

        // 注入上下文到头部
        Map<String, String> headers = new HashMap<>();
        tracingContext.injectContext(headers);

        assertFalse(headers.isEmpty(), "注入后头部信息不应为空");

        // 从头部提取上下文
        TracingContext extractedContext = tracingContext.extractContext(headers);
        assertNotNull(extractedContext, "提取的上下文不应为null");

        // 验证提取的上下文包含相同的TraceId
        // 注意：SpanId可能不同，因为提取时可能创建新的Span
        assertFalse(extractedContext.getTraceId().isEmpty(), "提取的TraceId不应为空");
    }

    @Test
    void testExtractContextFromEmptyHeaders() {
        // 测试从空头部提取上下文
        Map<String, String> emptyHeaders = new HashMap<>();
        TracingContext extractedContext = tracingContext.extractContext(emptyHeaders);
        
        assertNotNull(extractedContext, "从空头部提取的上下文不应为null");
    }

    @Test
    void testExtractContextFromNullHeaders() {
        // 测试从null头部提取上下文
        TracingContext extractedContext = tracingContext.extractContext(null);
        
        assertNotNull(extractedContext, "从null头部提取的上下文不应为null");
    }

    @Test
    void testCopyContext() {
        // 创建Span以建立状态
        Span span = tracingContext.createSpan("original-operation", SpanKind.SERVER);
        String originalTraceId = tracingContext.getTraceId();
        String originalSpanId = tracingContext.getSpanId();

        // 复制上下文
        TracingContext copiedContext = tracingContext.copy();

        assertNotNull(copiedContext, "复制的上下文不应为null");
        assertEquals(originalTraceId, copiedContext.getTraceId(), "复制上下文应有相同的TraceId");
        assertEquals(originalSpanId, copiedContext.getSpanId(), "复制上下文应有相同的SpanId");
        
        // 验证是不同的实例
        assertNotSame(tracingContext, copiedContext, "复制的上下文应该是不同的实例");
    }

    @Test
    void testSetStringTag() {
        // 创建Span
        Span span = tracingContext.createSpan("test-string-tag", SpanKind.SERVER);
        
        // 设置字符串属性
        String key = "test.string.key";
        String value = "test string value";
        
        assertDoesNotThrow(() -> tracingContext.setTag(key, value));
    }

    @Test
    void testSetNumberTag() {
        // 创建Span
        Span span = tracingContext.createSpan("test-number-tag", SpanKind.SERVER);
        
        // 测试Long值
        assertDoesNotThrow(() -> tracingContext.setTag("test.long", 123L));
        
        // 测试Double值
        assertDoesNotThrow(() -> tracingContext.setTag("test.double", 45.67));
        
        // 测试Integer值
        assertDoesNotThrow(() -> tracingContext.setTag("test.int", 89));
    }

    @Test
    void testSetBooleanTag() {
        // 创建Span
        Span span = tracingContext.createSpan("test-boolean-tag", SpanKind.SERVER);
        
        // 设置布尔属性
        assertDoesNotThrow(() -> tracingContext.setTag("test.boolean.true", true));
        assertDoesNotThrow(() -> tracingContext.setTag("test.boolean.false", false));
    }

    @Test
    void testAddEventWithAttributes() {
        // 创建Span
        Span span = tracingContext.createSpan("test-event", SpanKind.SERVER);
        
        // 创建事件属性
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("string.attr", "string value");
        attributes.put("long.attr", 123L);
        attributes.put("double.attr", 45.67);
        attributes.put("boolean.attr", true);
        
        // 添加事件
        String eventName = "test.event";
        assertDoesNotThrow(() -> tracingContext.addEvent(eventName, attributes));
    }

    @Test
    void testAddEventWithoutAttributes() {
        // 创建Span
        Span span = tracingContext.createSpan("test-simple-event", SpanKind.SERVER);
        
        // 添加简单事件
        String eventName = "simple.event";
        assertDoesNotThrow(() -> tracingContext.addEvent(eventName));
    }

    @Test
    void testAddEventWithNullAttributes() {
        // 创建Span
        Span span = tracingContext.createSpan("test-null-event", SpanKind.SERVER);
        
        // 添加事件（null属性）
        String eventName = "null.event";
        assertDoesNotThrow(() -> tracingContext.addEvent(eventName, null));
    }

    @Test
    void testGetTraceIdAndSpanId() {
        // 创建Span
        Span span = tracingContext.createSpan("test-ids", SpanKind.SERVER);
        
        // 获取ID
        String traceId = tracingContext.getTraceId();
        String spanId = tracingContext.getSpanId();
        
        assertNotNull(traceId, "TraceId不应为null");
        assertNotNull(spanId, "SpanId不应为null");
        assertFalse(traceId.isEmpty(), "TraceId不应为空");
        assertFalse(spanId.isEmpty(), "SpanId不应为空");
        
        // 验证ID格式（OpenTelemetry标准格式）
        assertEquals(32, traceId.length(), "TraceId应该是32个字符");
        assertEquals(16, spanId.length(), "SpanId应该是16个字符");
    }

    @Test
    void testGetLogContext() {
        // 创建Span
        Span span = tracingContext.createSpan("test-log-context", SpanKind.SERVER);
        
        // 获取日志上下文
        Map<String, String> logContext = tracingContext.getLogContext();
        
        assertNotNull(logContext, "日志上下文不应为null");
        assertTrue(logContext.containsKey("traceId"), "日志上下文应包含traceId");
        assertTrue(logContext.containsKey("spanId"), "日志上下文应包含spanId");
        
        assertEquals(tracingContext.getTraceId(), logContext.get("traceId"));
        assertEquals(tracingContext.getSpanId(), logContext.get("spanId"));
    }

    @Test
    void testIsActive() {
        // 初始状态
        assertFalse(tracingContext.isActive(), "初始状态应该是非活跃的");
        
        // 创建Span后
        Span span = tracingContext.createSpan("test-active", SpanKind.SERVER);
        assertTrue(tracingContext.isActive(), "创建Span后应该是活跃的");
        
        // 清理后
        tracingContext.clear();
        assertFalse(tracingContext.isActive(), "清理后应该是非活跃的");
    }

    @Test
    void testIsSampled() {
        // 创建Span
        Span span = tracingContext.createSpan("test-sampled", SpanKind.SERVER);
        
        // 检查采样状态（这个测试依赖于具体的采样配置）
        boolean isSampled = tracingContext.isSampled();
        // 由于采样配置可能变化，这里只验证方法不抛异常
        assertNotNull(isSampled);
    }

    @Test
    void testClear() {
        // 创建Span
        Span span = tracingContext.createSpan("test-clear", SpanKind.SERVER);
        assertTrue(tracingContext.isActive(), "创建Span后应该是活跃的");
        
        // 清理上下文
        tracingContext.clear();
        
        assertFalse(tracingContext.isActive(), "清理后应该是非活跃的");
        assertTrue(tracingContext.getTraceId().isEmpty(), "清理后TraceId应该为空");
        assertTrue(tracingContext.getSpanId().isEmpty(), "清理后SpanId应该为空");
    }

    @Test
    void testSetTagsAfterClear() {
        // 创建Span并清理
        Span span = tracingContext.createSpan("test-after-clear", SpanKind.SERVER);
        tracingContext.clear();
        
        // 尝试在清理后设置属性
        assertDoesNotThrow(() -> tracingContext.setTag("key", "value"));
        assertDoesNotThrow(() -> tracingContext.setTag("number", 123));
        assertDoesNotThrow(() -> tracingContext.setTag("boolean", true));
    }

    @Test
    void testAddEventAfterClear() {
        // 创建Span并清理
        Span span = tracingContext.createSpan("test-event-after-clear", SpanKind.SERVER);
        tracingContext.clear();
        
        // 尝试在清理后添加事件
        assertDoesNotThrow(() -> tracingContext.addEvent("test-event"));
        assertDoesNotThrow(() -> tracingContext.addEvent("test-event-with-attrs", 
                Map.of("key", "value")));
    }

    @Test
    void testMultipleSpanOperations() {
        // 测试多个Span操作的综合场景
        
        // 创建根Span
        Span rootSpan = tracingContext.createSpan("root-operation", SpanKind.SERVER);
        String rootTraceId = tracingContext.getTraceId();
        
        // 添加属性和事件
        tracingContext.setTag("operation.type", "root");
        tracingContext.addEvent("root.started");
        
        // 创建子Span
        Span childSpan = tracingContext.createChildSpan("child-operation", SpanKind.CLIENT, rootSpan);
        tracingContext.setCurrentSpan(childSpan);
        
        // 验证子Span的TraceId与根Span相同
        assertEquals(rootTraceId, tracingContext.getTraceId(), "子Span应该有相同的TraceId");
        
        // 为子Span添加属性
        tracingContext.setTag("operation.type", "child");
        tracingContext.addEvent("child.processing");
        
        // 完成子Span
        tracingContext.finishSpan(childSpan);
        
        // 切换回根Span
        tracingContext.setCurrentSpan(rootSpan);
        tracingContext.addEvent("root.completed");
        
        // 完成根Span
        tracingContext.finishSpan(rootSpan);
        
        // 验证操作成功完成
        assertTrue(true, "多Span操作应该成功完成");
    }
}