package org.unreal.modelrouter.tracing.logger;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.unreal.modelrouter.tracing.TracingContextHolder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TraceIdConverterTest {

    @Test
    void testConvertWithTraceId() {
        // 准备
        TraceIdConverter converter = new TraceIdConverter();
        ILoggingEvent event = Mockito.mock(ILoggingEvent.class);
        
        // 设置追踪上下文
        TracingContextHolder.setCurrentContext(new TestTracingContext("test-trace-id", "test-span-id"));
        
        try {
            // 执行
            String result = converter.convert(event);
            
            // 验证
            assertEquals("test-trace-id", result);
        } finally {
            TracingContextHolder.clearCurrentContext();
        }
    }
    
    @Test
    void testConvertWithoutTraceId() {
        // 准备
        TraceIdConverter converter = new TraceIdConverter();
        ILoggingEvent event = Mockito.mock(ILoggingEvent.class);
        
        // 确保没有追踪上下文
        TracingContextHolder.clearCurrentContext();
        
        // 执行
        String result = converter.convert(event);
        
        // 验证
        assertEquals("", result);
    }
    
    /**
     * 简单的测试用TracingContext实现
     */
    private static class TestTracingContext implements org.unreal.modelrouter.tracing.TracingContext {
        private final String traceId;
        private final String spanId;
        
        public TestTracingContext(String traceId, String spanId) {
            this.traceId = traceId;
            this.spanId = spanId;
        }
        
        @Override
        public String getTraceId() {
            return traceId;
        }
        
        @Override
        public String getSpanId() {
            return spanId;
        }
        
        // 实现其他必需的方法，返回默认值
        @Override
        public io.opentelemetry.api.trace.Span createSpan(String operationName, io.opentelemetry.api.trace.SpanKind kind) {
            return io.opentelemetry.api.trace.Span.getInvalid();
        }
        
        @Override
        public io.opentelemetry.api.trace.Span createChildSpan(String operationName, io.opentelemetry.api.trace.SpanKind kind, io.opentelemetry.api.trace.Span parentSpan) {
            return io.opentelemetry.api.trace.Span.getInvalid();
        }
        
        @Override
        public io.opentelemetry.api.trace.Span getCurrentSpan() {
            return io.opentelemetry.api.trace.Span.getInvalid();
        }
        
        @Override
        public void setCurrentSpan(io.opentelemetry.api.trace.Span span) {
        }
        
        @Override
        public void finishSpan(io.opentelemetry.api.trace.Span span) {
        }
        
        @Override
        public void finishSpan(io.opentelemetry.api.trace.Span span, Throwable error) {
        }
        
        @Override
        public void injectContext(Map<String, String> headers) {
        }
        
        @Override
        public org.unreal.modelrouter.tracing.TracingContext extractContext(Map<String, String> headers) {
            return this;
        }
        
        @Override
        public org.unreal.modelrouter.tracing.TracingContext copy() {
            return this;
        }
        
        @Override
        public void setTag(String key, String value) {
        }
        
        @Override
        public void setTag(String key, Number value) {
        }
        
        @Override
        public void setTag(String key, Boolean value) {
        }
        
        @Override
        public void addEvent(String name, Map<String, Object> attributes) {
        }
        
        @Override
        public void addEvent(String name) {
        }
        
        @Override
        public Map<String, String> getLogContext() {
            return Map.of("traceId", traceId, "spanId", spanId);
        }
        
        @Override
        public boolean isActive() {
            return true;
        }
        
        @Override
        public boolean isSampled() {
            return true;
        }
        
        @Override
        public void clear() {
        }
    }
}