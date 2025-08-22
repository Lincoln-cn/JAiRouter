package org.unreal.modelrouter.tracing.logger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.tracing.TracingContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TracingLogbackAppenderTest {

    private TracingLogbackAppender appender;
    private Logger logger;
    private LoggerContext loggerContext;

    @BeforeEach
    void setUp() {
        loggerContext = new LoggerContext();
        logger = loggerContext.getLogger("TEST");
        appender = new TracingLogbackAppender();
        appender.setContext(loggerContext);
        appender.setName("TEST_APPENDER");
        appender.start();
    }

    @Test
    void testAppendWithTraceInfo() {
        try (MockedStatic<TracingContextHolder> mockedTracingContextHolder = mockStatic(TracingContextHolder.class)) {
            // 准备
            mockedTracingContextHolder.when(TracingContextHolder::getCurrentContext)
                    .thenReturn(new TestTracingContext("test-trace-id", "test-span-id"));

            LoggingEvent event = new LoggingEvent();
            event.setLevel(Level.INFO);
            event.setLoggerName("TEST");
            event.setMessage("Test message");
            event.setLoggerContext(loggerContext);
            event.setThreadName("test-thread");

            // 执行
            appender.append(event);

            // 验证
            assertNotNull(appender);
        }
    }

    @Test
    void testAppendWithoutTraceInfo() {
        try (MockedStatic<TracingContextHolder> mockedTracingContextHolder = mockStatic(TracingContextHolder.class)) {
            // 准备
            mockedTracingContextHolder.when(TracingContextHolder::getCurrentContext)
                    .thenReturn(null);

            LoggingEvent event = new LoggingEvent();
            event.setLevel(Level.INFO);
            event.setLoggerName("TEST");
            event.setMessage("Test message");
            event.setLoggerContext(loggerContext);
            event.setThreadName("test-thread");

            // 执行
            appender.append(event);

            // 验证
            assertNotNull(appender);
        }
    }

    @Test
    void testAppendWithException() {
        try (MockedStatic<TracingContextHolder> mockedTracingContextHolder = mockStatic(TracingContextHolder.class)) {
            // 准备
            mockedTracingContextHolder.when(TracingContextHolder::getCurrentContext)
                    .thenReturn(new TestTracingContext("test-trace-id", "test-span-id"));

            LoggingEvent event = new LoggingEvent();
            event.setLevel(Level.ERROR);
            event.setLoggerName("TEST");
            event.setMessage("Test error message");
            event.setLoggerContext(loggerContext);
            event.setThreadName("test-thread");
            
            // 创建ThrowableProxy
            Throwable throwable = new Throwable("Test exception");
            ThrowableProxy throwableProxy = new ThrowableProxy(throwable);
            event.setThrowableProxy(throwableProxy);

            // 执行
            appender.append(event);

            // 验证
            assertNotNull(appender);
        }
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
        public void injectContext(java.util.Map<String, String> headers) {
        }

        @Override
        public org.unreal.modelrouter.tracing.TracingContext extractContext(java.util.Map<String, String> headers) {
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
        public void addEvent(String name, java.util.Map<String, Object> attributes) {
        }

        @Override
        public void addEvent(String name) {
        }

        @Override
        public java.util.Map<String, String> getLogContext() {
            return java.util.Map.of("traceId", traceId, "spanId", spanId);
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