package org.unreal.modelrouter.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Marker;

/**
 * 日志过滤器配置类
 * 用于屏蔽特定的异常日志记录
 */
@Configuration
public class LoggingFilterConfiguration {

    /**
     * 自定义过滤器，用于屏蔽ReadOnlyHttpHeaders.set相关的UnsupportedOperationException异常
     */
    public static class ReadOnlyHttpHeadersExceptionFilter extends TurboFilter {
        
        @Override
        public FilterReply decide(Marker marker, ch.qos.logback.classic.Logger logger, Level level, String format, Object[] params, Throwable t) {
            // 检查日志信息是否包含ReadOnlyHttpHeaders.set相关的异常
            if (format != null && 
                (format.contains("ReadOnlyHttpHeaders.set") ||
                 format.contains("UnsupportedOperationException: null"))) {
                // 屏蔽这个特定的异常日志
                return FilterReply.DENY;
            }
            
            // 检查异常堆栈信息是否包含ReadOnlyHttpHeaders.set或UnsupportedOperationException
            if (t != null) {
                String message = t.getMessage();
                if (message != null && 
                    (message.contains("ReadOnlyHttpHeaders.set") || 
                     message.contains("UnsupportedOperationException"))) {
                    return FilterReply.DENY;
                }
                
                // 检查堆栈跟踪信息
                StackTraceElement[] stackTrace = t.getStackTrace();
                if (stackTrace != null) {
                    for (StackTraceElement element : stackTrace) {
                        if (element.toString().contains("ReadOnlyHttpHeaders.set") || 
                            element.toString().contains("UnsupportedOperationException")) {
                            return FilterReply.DENY;
                        }
                    }
                }
            }
            
            // 其他日志正常处理
            return FilterReply.NEUTRAL;
        }
    }
}