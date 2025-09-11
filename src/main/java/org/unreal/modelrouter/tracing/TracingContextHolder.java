package org.unreal.modelrouter.tracing;

import lombok.extern.slf4j.Slf4j;

/**
 * 追踪上下文持有者
 * 
 * 提供线程本地的追踪上下文存储，支持：
 * - 线程安全的上下文存储和获取
 * - 上下文的继承和传播
 * - 内存泄漏防护
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
public class TracingContextHolder {
    
    /**
     * 线程本地存储
     */
    private static final ThreadLocal<TracingContext> CONTEXT_HOLDER = new ThreadLocal<>();
    
    /**
     * 获取当前线程的追踪上下文
     * 
     * @return 追踪上下文，如果没有则返回null
     */
    public static TracingContext getCurrentContext() {
        return CONTEXT_HOLDER.get();
    }
    
    /**
     * 设置当前线程的追踪上下文
     * 
     * @param context 要设置的追踪上下文
     */
    public static void setCurrentContext(TracingContext context) {
        if (context != null) {
            CONTEXT_HOLDER.set(context);
            log.debug("设置追踪上下文到当前线程，traceId: {}", context.getTraceId());
        } else {
            CONTEXT_HOLDER.remove();
            log.debug("移除当前线程的追踪上下文");
        }
    }
    
    /**
     * 清理当前线程的追踪上下文
     * 
     * 重要：必须在请求处理完成后调用，防止内存泄漏
     */
    public static void clearCurrentContext() {
        TracingContext context = CONTEXT_HOLDER.get();
        if (context != null) {
            log.debug("清理当前线程的追踪上下文，traceId: {}", context.getTraceId());
            context.clear();
        }
        CONTEXT_HOLDER.remove();
    }
    
    /**
     * 检查当前线程是否有追踪上下文
     * 
     * @return 如果有追踪上下文返回true
     */
    public static boolean hasCurrentContext() {
        TracingContext context = CONTEXT_HOLDER.get();
        return context != null && context.isActive();
    }
    
    /**
     * 获取当前追踪ID
     * 
     * @return 追踪ID，如果没有追踪上下文则返回空字符串
     */
    public static String getCurrentTraceId() {
        TracingContext context = CONTEXT_HOLDER.get();
        return context != null ? context.getTraceId() : "";
    }
    
    /**
     * 获取当前SpanID
     * 
     * @return SpanID，如果没有追踪上下文则返回空字符串
     */
    public static String getCurrentSpanId() {
        TracingContext context = CONTEXT_HOLDER.get();
        return context != null ? context.getSpanId() : "";
    }
    
    /**
     * 执行带有追踪上下文的操作
     * 
     * @param context 追踪上下文
     * @param operation 要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     * @throws Exception 操作异常
     */
    public static <T> T executeWithContext(TracingContext context, ContextualOperation<T> operation) throws Exception {
        TracingContext previousContext = getCurrentContext();
        try {
            setCurrentContext(context);
            return operation.execute();
        } finally {
            setCurrentContext(previousContext);
        }
    }
    
    /**
     * 执行带有追踪上下文的操作（无返回值）
     * 
     * @param context 追踪上下文
     * @param operation 要执行的操作
     * @throws Exception 操作异常
     */
    public static void executeWithContext(TracingContext context, ContextualVoidOperation operation) throws Exception {
        TracingContext previousContext = getCurrentContext();
        try {
            setCurrentContext(context);
            operation.execute();
        } finally {
            setCurrentContext(previousContext);
        }
    }
    
    /**
     * 带有追踪上下文的操作接口
     * 
     * @param <T> 返回值类型
     */
    @FunctionalInterface
    public interface ContextualOperation<T> {
        T execute() throws Exception;
    }
    
    /**
     * 带有追踪上下文的无返回值操作接口
     */
    @FunctionalInterface
    public interface ContextualVoidOperation {
        void execute() throws Exception;
    }
}