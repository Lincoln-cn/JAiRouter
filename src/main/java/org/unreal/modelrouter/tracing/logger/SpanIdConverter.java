package org.unreal.modelrouter.tracing.logger;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.unreal.modelrouter.tracing.TracingContextHolder;

/**
 * SpanId转换器
 * 
 * 在日志模式中使用，输出当前追踪上下文的spanId
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
public class SpanIdConverter extends ClassicConverter {
    
    @Override
    public String convert(ILoggingEvent event) {
        String spanId = TracingContextHolder.getCurrentSpanId();
        return spanId != null ? spanId : "";
    }
}