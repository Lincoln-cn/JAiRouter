package org.unreal.modelrouter.tracing.reactive;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingService;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.function.Function;

/**
 * 追踪操作符
 * 
 * 为Mono和Flux提供追踪增强功能，包括：
 * - 自动创建和管理Span
 * - 响应式流中的上下文传播
 * - 错误处理和Span状态管理
 * - 性能指标收集
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
public class TracingOperator {
    
    /**
     * 为Mono添加追踪功能
     * 
     * @param source 源Mono
     * @param operationName 操作名称
     * @param spanKind Span类型
     * @param tracingService 追踪服务
     * @param <T> 数据类型
     * @return 增强后的Mono
     */
    public static <T> Mono<T> trace(Mono<T> source, String operationName, SpanKind spanKind, TracingService tracingService) {
        return new TracingMono<>(source, operationName, spanKind, tracingService);
    }
    
    /**
     * 为Flux添加追踪功能
     * 
     * @param source 源Flux
     * @param operationName 操作名称
     * @param spanKind Span类型
     * @param tracingService 追踪服务
     * @param <T> 数据类型
     * @return 增强后的Flux
     */
    public static <T> Flux<T> trace(Flux<T> source, String operationName, SpanKind spanKind, TracingService tracingService) {
        return new TracingFlux<>(source, operationName, spanKind, tracingService);
    }
    
    /**
     * 为任意Publisher添加追踪功能
     * 
     * @param operationName 操作名称
     * @param spanKind Span类型
     * @param tracingService 追踪服务
     * @param <T> 数据类型
     * @return 追踪转换函数
     */
    public static <T> Function<Publisher<T>, Publisher<T>> trace(String operationName, SpanKind spanKind, TracingService tracingService) {
        return source -> {
            if (source instanceof Mono) {
                return trace((Mono<T>) source, operationName, spanKind, tracingService);
            } else if (source instanceof Flux) {
                return trace((Flux<T>) source, operationName, spanKind, tracingService);
            } else {
                return Flux.from(source).transform(flux -> trace(flux, operationName, spanKind, tracingService));
            }
        };
    }
    
    /**
     * 追踪增强的Mono实现
     */
    private static class TracingMono<T> extends Mono<T> {
        private final Mono<T> source;
        private final String operationName;
        private final SpanKind spanKind;
        private final TracingService tracingService;
        
        public TracingMono(Mono<T> source, String operationName, SpanKind spanKind, TracingService tracingService) {
            this.source = source;
            this.operationName = operationName;
            this.spanKind = spanKind;
            this.tracingService = tracingService;
        }
        
        @Override
        public void subscribe(CoreSubscriber<? super T> actual) {
            Context context = actual.currentContext();
            
            // 获取或创建追踪上下文
            TracingContext tracingContext = ReactiveTracingContextHolder.getCurrentContext(context)
                    .orElseGet(() -> tracingService.createOperationSpan(operationName, spanKind));
            
            // 创建操作Span
            Span operationSpan = tracingContext.createSpan(operationName, spanKind);
            long startTime = System.currentTimeMillis();
            
            // 更新上下文
            Context enhancedContext = ReactiveTracingContextHolder.withTracingContext(context, tracingContext);
            
            source.subscribe(new TracingSubscriber<>(actual, enhancedContext, tracingContext, operationSpan, startTime));
        }
    }
    
    /**
     * 追踪增强的Flux实现
     */
    private static class TracingFlux<T> extends Flux<T> {
        private final Flux<T> source;
        private final String operationName;
        private final SpanKind spanKind;
        private final TracingService tracingService;
        
        public TracingFlux(Flux<T> source, String operationName, SpanKind spanKind, TracingService tracingService) {
            this.source = source;
            this.operationName = operationName;
            this.spanKind = spanKind;
            this.tracingService = tracingService;
        }
        
        @Override
        public void subscribe(CoreSubscriber<? super T> actual) {
            Context context = actual.currentContext();
            
            // 获取或创建追踪上下文
            TracingContext tracingContext = ReactiveTracingContextHolder.getCurrentContext(context)
                    .orElseGet(() -> tracingService.createOperationSpan(operationName, spanKind));
            
            // 创建操作Span
            Span operationSpan = tracingContext.createSpan(operationName, spanKind);
            long startTime = System.currentTimeMillis();
            
            // 更新上下文
            Context enhancedContext = ReactiveTracingContextHolder.withTracingContext(context, tracingContext);
            
            source.subscribe(new TracingSubscriber<>(actual, enhancedContext, tracingContext, operationSpan, startTime));
        }
    }
    
    /**
     * 追踪订阅者实现
     */
    private static class TracingSubscriber<T> implements CoreSubscriber<T> {
        private final CoreSubscriber<? super T> actual;
        private final Context context;
        private final TracingContext tracingContext;
        private final Span operationSpan;
        private final long startTime;
        
        public TracingSubscriber(CoreSubscriber<? super T> actual, Context context, 
                               TracingContext tracingContext, Span operationSpan, long startTime) {
            this.actual = actual;
            this.context = context;
            this.tracingContext = tracingContext;
            this.operationSpan = operationSpan;
            this.startTime = startTime;
        }
        
        @Override
        public Context currentContext() {
            return context;
        }
        
        @Override
        public void onSubscribe(org.reactivestreams.Subscription s) {
            actual.onSubscribe(s);
        }
        
        @Override
        public void onNext(T t) {
            try {
                actual.onNext(t);
            } catch (Throwable error) {
                onError(error);
            }
        }
        
        @Override
        public void onError(Throwable t) {
            try {
                // 记录错误到Span
                recordError(t);
                actual.onError(t);
            } finally {
                finishSpan();
            }
        }
        
        @Override
        public void onComplete() {
            try {
                // 记录成功完成
                recordSuccess();
                actual.onComplete();
            } finally {
                finishSpan();
            }
        }
        
        private void recordError(Throwable error) {
            try {
                if (tracingContext != null && operationSpan != null) {
                    tracingContext.finishSpan(operationSpan, error);
                }
            } catch (Exception e) {
                log.debug("记录追踪错误失败", e);
            }
        }
        
        private void recordSuccess() {
            try {
                if (operationSpan != null) {
                    long duration = System.currentTimeMillis() - startTime;
                    operationSpan.setAttribute("operation.duration_ms", duration);
                    operationSpan.setAttribute("operation.success", true);
                }
            } catch (Exception e) {
                log.debug("记录追踪成功信息失败", e);
            }
        }
        
        private void finishSpan() {
            try {
                if (tracingContext != null && operationSpan != null) {
                    long duration = System.currentTimeMillis() - startTime;
                    operationSpan.setAttribute("operation.total_duration_ms", duration);
                    tracingContext.finishSpan(operationSpan);
                }
            } catch (Exception e) {
                log.debug("完成追踪Span失败", e);
            }
        }
    }
    
    /**
     * 创建追踪转换器
     * 
     * @param operationName 操作名称
     * @param tracingService 追踪服务
     * @param <T> 数据类型
     * @return 追踪转换器
     */
    public static <T> Function<Mono<T>, Mono<T>> monoTracer(String operationName, TracingService tracingService) {
        return mono -> trace(mono, operationName, SpanKind.INTERNAL, tracingService);
    }
    
    /**
     * 创建追踪转换器
     * 
     * @param operationName 操作名称
     * @param tracingService 追踪服务
     * @param <T> 数据类型
     * @return 追踪转换器
     */
    public static <T> Function<Flux<T>, Flux<T>> fluxTracer(String operationName, TracingService tracingService) {
        return flux -> trace(flux, operationName, SpanKind.INTERNAL, tracingService);
    }
    
    /**
     * 为WebClient调用添加追踪
     * 
     * @param operationName 操作名称
     * @param tracingService 追踪服务
     * @param <T> 数据类型
     * @return 追踪转换器
     */
    public static <T> Function<Mono<T>, Mono<T>> webClientTracer(String operationName, TracingService tracingService) {
        return mono -> trace(mono, operationName, SpanKind.CLIENT, tracingService);
    }
    
    /**
     * 为数据库操作添加追踪
     * 
     * @param operationName 操作名称
     * @param tracingService 追踪服务
     * @param <T> 数据类型
     * @return 追踪转换器
     */
    public static <T> Function<Mono<T>, Mono<T>> databaseTracer(String operationName, TracingService tracingService) {
        return mono -> trace(mono, operationName, SpanKind.CLIENT, tracingService);
    }
    
    /**
     * 为消息处理添加追踪
     * 
     * @param operationName 操作名称
     * @param tracingService 追踪服务
     * @param <T> 数据类型
     * @return 追踪转换器
     */
    public static <T> Function<Mono<T>, Mono<T>> messageTracer(String operationName, TracingService tracingService) {
        return mono -> trace(mono, operationName, SpanKind.CONSUMER, tracingService);
    }
}