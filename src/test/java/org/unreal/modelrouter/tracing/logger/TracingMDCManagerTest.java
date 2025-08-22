package org.unreal.modelrouter.tracing.logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;
import org.unreal.modelrouter.tracing.DefaultTracingContext;
import org.unreal.modelrouter.tracing.TracingContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TracingMDCManagerTest {

    private TracingMDCManager tracingMDCManager;
    
    @Mock
    private Tracer tracer;
    
    @Mock
    private Span span;
    
    @Mock
    private SpanContext spanContext;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tracingMDCManager = new TracingMDCManager();
        
        // 设置Mock行为
        when(span.getSpanContext()).thenReturn(spanContext);
        when(spanContext.getTraceId()).thenReturn("test-trace-id");
        when(spanContext.getSpanId()).thenReturn("test-span-id");
        when(spanContext.isValid()).thenReturn(true);
    }
    
    @Test
    void testSetMDC() {
        // 准备
        DefaultTracingContext context = new DefaultTracingContext(tracer);
        context.setCurrentSpan(span);
        
        // 执行
        tracingMDCManager.setMDC(context);
        
        // 验证
        assertEquals("test-trace-id", MDC.get(TracingMDCManager.MDC_TRACE_ID_KEY));
        assertEquals("test-span-id", MDC.get(TracingMDCManager.MDC_SPAN_ID_KEY));
    }
    
    @Test
    void testClearMDC() {
        // 准备
        MDC.put(TracingMDCManager.MDC_TRACE_ID_KEY, "test-trace-id");
        MDC.put(TracingMDCManager.MDC_SPAN_ID_KEY, "test-span-id");
        
        // 执行
        tracingMDCManager.clearMDC();
        
        // 验证
        assertNull(MDC.get(TracingMDCManager.MDC_TRACE_ID_KEY));
        assertNull(MDC.get(TracingMDCManager.MDC_SPAN_ID_KEY));
    }
    
    @Test
    void testGetMDCContext() {
        // 准备
        MDC.put(TracingMDCManager.MDC_TRACE_ID_KEY, "test-trace-id");
        MDC.put(TracingMDCManager.MDC_SPAN_ID_KEY, "test-span-id");
        
        // 执行
        Map<String, String> mdcContext = tracingMDCManager.getMDCContext();
        
        // 验证
        assertNotNull(mdcContext);
        assertEquals("test-trace-id", mdcContext.get(TracingMDCManager.MDC_TRACE_ID_KEY));
        assertEquals("test-span-id", mdcContext.get(TracingMDCManager.MDC_SPAN_ID_KEY));
    }
    
    @Test
    void testHasMDCContext() {
        // 初始状态应该没有MDC上下文
        assertFalse(tracingMDCManager.hasMDCContext());
        
        // 设置MDC上下文
        MDC.put(TracingMDCManager.MDC_TRACE_ID_KEY, "test-trace-id");
        assertTrue(tracingMDCManager.hasMDCContext());
        
        // 清理MDC上下文
        tracingMDCManager.clearMDC();
        assertFalse(tracingMDCManager.hasMDCContext());
    }
}