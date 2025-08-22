package org.unreal.modelrouter.tracing.logger;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.unreal.modelrouter.tracing.TracingContextHolder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 追踪信息Logback Appender
 * 
 * 为日志添加追踪上下文信息，包括traceId和spanId，支持JSON格式输出
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
public class TracingLogbackAppender extends AppenderBase<ILoggingEvent> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    
    private boolean jsonFormat = true;
    private boolean includeTraceContext = true;
    
    @Override
    protected void append(ILoggingEvent event) {
        try {
            if (jsonFormat) {
                String jsonLog = formatAsJson(event);
                System.out.println(jsonLog);
            } else {
                String textLog = formatAsText(event);
                System.out.println(textLog);
            }
        } catch (Exception e) {
            // 降级到标准输出，防止日志系统异常影响主流程
            System.err.println("TracingLogbackAppender error: " + e.getMessage());
            System.out.println(event.getFormattedMessage());
        }
    }
    
    /**
     * 将日志事件格式化为JSON格式
     * 
     * @param event 日志事件
     * @return JSON格式的日志字符串
     */
    private String formatAsJson(ILoggingEvent event) throws JsonProcessingException {
        Map<String, Object> logData = new HashMap<>();
        
        // 基本日志信息
        logData.put("timestamp", DATE_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp())));
        logData.put("level", event.getLevel().toString());
        logData.put("logger", event.getLoggerName());
        logData.put("message", event.getFormattedMessage());
        logData.put("thread", event.getThreadName());
        
        // 添加追踪上下文信息
        if (includeTraceContext) {
            addTraceContext(logData);
        }
        
        // 添加异常信息
        if (event.getThrowableProxy() != null) {
            Map<String, Object> throwableData = new HashMap<>();
            throwableData.put("class", event.getThrowableProxy().getClassName());
            throwableData.put("message", event.getThrowableProxy().getMessage());
            logData.put("throwable", throwableData);
        }
        
        return objectMapper.writeValueAsString(logData);
    }
    
    /**
     * 将日志事件格式化为文本格式
     * 
     * @param event 日志事件
     * @return 文本格式的日志字符串
     */
    private String formatAsText(ILoggingEvent event) {
        StringBuilder sb = new StringBuilder();
        
        // 时间戳
        sb.append(DATE_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp())));
        
        // 线程名
        sb.append(" [").append(event.getThreadName()).append("]");
        
        // 日志级别
        sb.append(" ").append(event.getLevel().toString());
        
        // 追踪上下文
        if (includeTraceContext) {
            String traceId = TracingContextHolder.getCurrentTraceId();
            String spanId = TracingContextHolder.getCurrentSpanId();
            
            if (!traceId.isEmpty() || !spanId.isEmpty()) {
                sb.append(" [");
                if (!traceId.isEmpty()) {
                    sb.append("traceId=").append(traceId);
                    if (!spanId.isEmpty()) {
                        sb.append(", ");
                    }
                }
                if (!spanId.isEmpty()) {
                    sb.append("spanId=").append(spanId);
                }
                sb.append("]");
            }
        }
        
        // Logger名称和消息
        sb.append(" ").append(event.getLoggerName()).append(" - ").append(event.getFormattedMessage());
        
        return sb.toString();
    }
    
    /**
     * 添加追踪上下文信息到日志数据中
     * 
     * @param logData 日志数据Map
     */
    private void addTraceContext(Map<String, Object> logData) {
        String traceId = TracingContextHolder.getCurrentTraceId();
        String spanId = TracingContextHolder.getCurrentSpanId();
        
        if (!traceId.isEmpty()) {
            logData.put("traceId", traceId);
        }
        
        if (!spanId.isEmpty()) {
            logData.put("spanId", spanId);
        }
    }
    
    // Getters and Setters
    
    public boolean isJsonFormat() {
        return jsonFormat;
    }
    
    public void setJsonFormat(boolean jsonFormat) {
        this.jsonFormat = jsonFormat;
    }
    
    public boolean isIncludeTraceContext() {
        return includeTraceContext;
    }
    
    public void setIncludeTraceContext(boolean includeTraceContext) {
        this.includeTraceContext = includeTraceContext;
    }
}