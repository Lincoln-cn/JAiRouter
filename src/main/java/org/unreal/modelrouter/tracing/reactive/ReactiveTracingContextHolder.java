package org.unreal.modelrouter.tracing.reactive;

import lombok.extern.slf4j.Slf4j;
import org.unreal.modelrouter.tracing.TracingConstants;
import org.unreal.modelrouter.tracing.TracingContext;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Optional;

/**
 * 响应式追踪上下文持有者
 * 
 * 在Reactor响应式流中管理追踪上下文的传播，提供：
 * - Reactor Context中的追踪上下文存储和获取
 * - 响应式流中的上下文自动传播
 * - 线程安全的上下文访问
 * - 上下文生命周期管理
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
public class ReactiveTracingContextHolder {
    
    /**
     * 从当前Reactor上下文中获取追踪上下文
     * 
     * @return 包含追踪上下文的Mono，如果不存在则返回空Mono
     */
    public static Mono<TracingContext> getCurrentContext() {
        return Mono.deferContextual(contextView -> {
            try {
                if (contextView.hasKey(TracingConstants.ContextKeys.TRACING_CONTEXT)) {
                    TracingContext context = contextView.get(TracingConstants.ContextKeys.TRACING_CONTEXT);
                    return Mono.just(context);
                } else {
                    // 只在调试级别记录，避免生产环境日志过多
                    if (log.isDebugEnabled()) {
                        log.debug("Reactor上下文中不包含追踪上下文键: {}", TracingConstants.ContextKeys.TRACING_CONTEXT);
                    }
                    return Mono.empty();
                }
            } catch (Exception e) {
                log.debug("从Reactor上下文中获取追踪上下文失败", e);
                return Mono.empty();
            }
        });
    }
    
    /**
     * 从当前Reactor上下文中获取追踪上下文（同步方式）
     * 
     * @param context Reactor上下文
     * @return 追踪上下文的Optional包装
     */
    public static Optional<TracingContext> getCurrentContext(Context context) {
        try {
            if (context.hasKey(TracingConstants.ContextKeys.TRACING_CONTEXT)) {
                TracingContext tracingContext = context.get(TracingConstants.ContextKeys.TRACING_CONTEXT);
                return Optional.of(tracingContext);
            }
        } catch (Exception e) {
            log.debug("从Reactor上下文中获取追踪上下文失败", e);
        }
        return Optional.empty();
    }
    
    /**
     * 从当前Reactor上下文中获取TraceID
     * 
     * @return 包含TraceID的Mono，如果不存在则返回空字符串
     */
    public static Mono<String> getCurrentTraceId() {
        return getCurrentContext()
                .map(TracingContext::getTraceId)
                .defaultIfEmpty("");
    }
    
    /**
     * 从当前Reactor上下文中获取SpanID
     * 
     * @return 包含SpanID的Mono，如果不存在则返回空字符串
     */
    public static Mono<String> getCurrentSpanId() {
        return getCurrentContext()
                .map(TracingContext::getSpanId)
                .defaultIfEmpty("");
    }
    
    /**
     * 将追踪上下文设置到Reactor上下文中
     * 
     * @param tracingContext 追踪上下文
     * @return 包含追踪信息的Reactor上下文
     */
    public static Context withTracingContext(TracingContext tracingContext) {
        if (tracingContext == null) {
            return Context.empty();
        }
        
        return Context.of(
                TracingConstants.ContextKeys.TRACING_CONTEXT, tracingContext,
                TracingConstants.ContextKeys.TRACE_ID, tracingContext.getTraceId(),
                TracingConstants.ContextKeys.SPAN_ID, tracingContext.getSpanId()
        );
    }
    
    /**
     * 将追踪上下文添加到现有的Reactor上下文中
     * 
     * @param existingContext 现有的Reactor上下文
     * @param tracingContext 追踪上下文
     * @return 合并后的Reactor上下文
     */
    public static Context withTracingContext(Context existingContext, TracingContext tracingContext) {
        if (tracingContext == null) {
            return existingContext;
        }
        
        return existingContext.put(TracingConstants.ContextKeys.TRACING_CONTEXT, tracingContext)
                .put(TracingConstants.ContextKeys.TRACE_ID, tracingContext.getTraceId())
                .put(TracingConstants.ContextKeys.SPAN_ID, tracingContext.getSpanId());
    }
    
    /**
     * 检查当前Reactor上下文是否包含追踪信息
     * 
     * @return 包含检查结果的Mono
     */
    public static Mono<Boolean> hasTracingContext() {
        return Mono.deferContextual(contextView -> {
            boolean hasContext = contextView.hasKey(TracingConstants.ContextKeys.TRACING_CONTEXT);
            return Mono.just(hasContext);
        });
    }
    
    /**
     * 检查指定的Reactor上下文是否包含追踪信息
     * 
     * @param context Reactor上下文
     * @return 是否包含追踪信息
     */
    public static boolean hasTracingContext(Context context) {
        return context.hasKey(TracingConstants.ContextKeys.TRACING_CONTEXT);
    }
    
    /**
     * 从Reactor上下文中移除追踪信息
     * 
     * @param context Reactor上下文
     * @return 移除追踪信息后的上下文
     */
    public static Context clearTracingContext(Context context) {
        return context.delete(TracingConstants.ContextKeys.TRACING_CONTEXT)
                .delete(TracingConstants.ContextKeys.TRACE_ID)
                .delete(TracingConstants.ContextKeys.SPAN_ID);
    }
    
    /**
     * 复制追踪上下文到新的Reactor上下文
     * 
     * @param sourceContext 源上下文
     * @param targetContext 目标上下文
     * @return 包含追踪信息的目标上下文
     */
    public static Context copyTracingContext(Context sourceContext, Context targetContext) {
        Optional<TracingContext> tracingContext = getCurrentContext(sourceContext);
        if (tracingContext.isPresent()) {
            return withTracingContext(targetContext, tracingContext.get());
        }
        return targetContext;
    }
    
    /**
     * 创建包含追踪信息的上下文写入器
     * 
     * @param tracingContext 追踪上下文
     * @return 上下文写入器函数
     */
    public static java.util.function.Function<Context, Context> contextWriter(TracingContext tracingContext) {
        return context -> withTracingContext(context, tracingContext);
    }
    
    /**
     * 执行带有追踪上下文的操作
     * 
     * @param tracingContext 追踪上下文
     * @param operation 要执行的操作
     * @param <T> 操作返回类型
     * @return 包含操作结果的Mono
     */
    public static <T> Mono<T> withContext(TracingContext tracingContext, Mono<T> operation) {
        if (tracingContext == null) {
            return operation;
        }
        
        return operation.contextWrite(withTracingContext(tracingContext));
    }
    
    /**
     * 执行带有追踪上下文的操作（使用现有上下文）
     * 
     * @param operation 要执行的操作
     * @param <T> 操作返回类型
     * @return 包含操作结果的Mono
     */
    public static <T> Mono<T> withCurrentContext(java.util.function.Function<TracingContext, Mono<T>> operation) {
        return getCurrentContext()
                .flatMap(operation)
                .switchIfEmpty(Mono.defer(() -> {
                    // 当没有追踪上下文时，仍然执行操作但传入null
                    if (log.isDebugEnabled()) {
                        log.debug("在没有追踪上下文的情况下执行操作");
                    }
                    return operation.apply(null);
                }));
    }
}