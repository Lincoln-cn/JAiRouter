package org.unreal.modelrouter.tracing.logger;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;

import java.util.Map;

/**
 * 追踪MDC管理器
 * 
 * 自动管理MDC中的追踪上下文信息，包括：
 * - 在请求开始时自动设置traceId和spanId到MDC
 * - 在响应式上下文中的MDC传播
 * - MDC清理和内存泄漏防护
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class TracingMDCManager {
    
    public static final String MDC_TRACE_ID_KEY = "traceId";
    public static final String MDC_SPAN_ID_KEY = "spanId";
    
    /**
     * 设置MDC中的追踪上下文
     * 
     * @param context 追踪上下文
     */
    public void setMDC(TracingContext context) {
        if (context != null) {
            String traceId = context.getTraceId();
            String spanId = context.getSpanId();
            
            if (traceId != null && !traceId.isEmpty()) {
                MDC.put(MDC_TRACE_ID_KEY, traceId);
            }
            
            if (spanId != null && !spanId.isEmpty()) {
                MDC.put(MDC_SPAN_ID_KEY, spanId);
            }
            
            log.debug("设置MDC追踪上下文: traceId={}, spanId={}", traceId, spanId);
        }
    }
    
    /**
     * 从当前线程的追踪上下文中设置MDC
     */
    public void setMDCFromCurrentContext() {
        TracingContext context = TracingContextHolder.getCurrentContext();
        if (context != null && context.isActive()) {
            setMDC(context);
        }
    }
    
    /**
     * 清理MDC中的追踪上下文
     */
    public void clearMDC() {
        MDC.remove(MDC_TRACE_ID_KEY);
        MDC.remove(MDC_SPAN_ID_KEY);
        log.debug("清理MDC追踪上下文");
    }
    
    /**
     * 获取MDC中的追踪上下文信息
     * 
     * @return 包含traceId和spanId的Map
     */
    public Map<String, String> getMDCContext() {
        return MDC.getCopyOfContextMap();
    }
    
    /**
     * 检查MDC中是否包含追踪上下文
     * 
     * @return 如果MDC中包含追踪上下文返回true
     */
    public boolean hasMDCContext() {
        return MDC.get(MDC_TRACE_ID_KEY) != null || MDC.get(MDC_SPAN_ID_KEY) != null;
    }
}