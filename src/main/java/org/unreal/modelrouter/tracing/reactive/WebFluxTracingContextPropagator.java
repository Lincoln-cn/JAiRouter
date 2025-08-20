package org.unreal.modelrouter.tracing.reactive;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.tracing.TracingConstants;
import org.unreal.modelrouter.tracing.TracingContext;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.function.Function;

/**
 * WebFlux追踪上下文传播器
 * 
 * 专门处理WebFlux环境中的追踪上下文传播，包括：
 * - ServerWebExchange中的追踪上下文提取和注入
 * - WebFlux订阅者上下文的自动设置
 * - 请求处理链中的上下文传播
 * - 异步处理中的上下文保持
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebFluxTracingContextPropagator {
    
    /**
     * 从ServerWebExchange中提取追踪上下文
     * 
     * @param exchange Web交换对象
     * @return 追踪上下文，如果不存在则返回null
     */
    public TracingContext extractFromExchange(ServerWebExchange exchange) {
        try {
            Object contextObj = exchange.getAttribute(TracingConstants.ContextKeys.TRACING_CONTEXT);
            if (contextObj instanceof TracingContext) {
                return (TracingContext) contextObj;
            }
        } catch (Exception e) {
            log.debug("从ServerWebExchange提取追踪上下文失败", e);
        }
        return null;
    }
    
    /**
     * 将追踪上下文注入到ServerWebExchange中
     * 
     * @param exchange Web交换对象
     * @param tracingContext 追踪上下文
     */
    public void injectToExchange(ServerWebExchange exchange, TracingContext tracingContext) {
        if (tracingContext != null) {
            exchange.getAttributes().put(TracingConstants.ContextKeys.TRACING_CONTEXT, tracingContext);
            exchange.getAttributes().put(TracingConstants.ContextKeys.TRACE_ID, tracingContext.getTraceId());
            exchange.getAttributes().put(TracingConstants.ContextKeys.SPAN_ID, tracingContext.getSpanId());
        }
    }
    
    /**
     * 创建包含追踪上下文的Reactor上下文写入器
     * 
     * @param exchange Web交换对象
     * @return 上下文写入器函数
     */
    public Function<Context, Context> contextWriter(ServerWebExchange exchange) {
        TracingContext tracingContext = extractFromExchange(exchange);
        if (tracingContext != null) {
            return ReactiveTracingContextHolder.contextWriter(tracingContext);
        }
        return Function.identity();
    }
    
    /**
     * 为WebFlux处理链添加追踪上下文传播
     * 
     * @param exchange Web交换对象
     * @param operation 要执行的操作
     * @param <T> 操作返回类型
     * @return 包含追踪上下文的操作结果
     */
    public <T> Mono<T> withTracingContext(ServerWebExchange exchange, Mono<T> operation) {
        TracingContext tracingContext = extractFromExchange(exchange);
        if (tracingContext != null) {
            return ReactiveTracingContextHolder.withContext(tracingContext, operation);
        }
        return operation;
    }
    
    /**
     * 为WebFlux处理链添加追踪上下文传播（使用函数式接口）
     * 
     * @param exchange Web交换对象
     * @param operationFactory 操作工厂函数
     * @param <T> 操作返回类型
     * @return 包含追踪上下文的操作结果
     */
    public <T> Mono<T> withTracingContext(ServerWebExchange exchange, 
                                         java.util.function.Function<TracingContext, Mono<T>> operationFactory) {
        TracingContext tracingContext = extractFromExchange(exchange);
        return operationFactory.apply(tracingContext)
                .contextWrite(context -> {
                    if (tracingContext != null) {
                        return ReactiveTracingContextHolder.withTracingContext(context, tracingContext);
                    }
                    return context;
                });
    }
    
    /**
     * 检查ServerWebExchange是否包含追踪上下文
     * 
     * @param exchange Web交换对象
     * @return 是否包含追踪上下文
     */
    public boolean hasTracingContext(ServerWebExchange exchange) {
        return exchange.getAttribute(TracingConstants.ContextKeys.TRACING_CONTEXT) != null;
    }
    
    /**
     * 从ServerWebExchange中获取TraceID
     * 
     * @param exchange Web交换对象
     * @return TraceID，如果不存在则返回空字符串
     */
    public String getTraceId(ServerWebExchange exchange) {
        TracingContext context = extractFromExchange(exchange);
        return context != null ? context.getTraceId() : "";
    }
    
    /**
     * 从ServerWebExchange中获取SpanID
     * 
     * @param exchange Web交换对象
     * @return SpanID，如果不存在则返回空字符串
     */
    public String getSpanId(ServerWebExchange exchange) {
        TracingContext context = extractFromExchange(exchange);
        return context != null ? context.getSpanId() : "";
    }
    
    /**
     * 清理ServerWebExchange中的追踪上下文
     * 
     * @param exchange Web交换对象
     */
    public void clearTracingContext(ServerWebExchange exchange) {
        exchange.getAttributes().remove(TracingConstants.ContextKeys.TRACING_CONTEXT);
        exchange.getAttributes().remove(TracingConstants.ContextKeys.TRACE_ID);
        exchange.getAttributes().remove(TracingConstants.ContextKeys.SPAN_ID);
    }
    
    /**
     * 复制追踪上下文到新的ServerWebExchange
     * 
     * @param sourceExchange 源交换对象
     * @param targetExchange 目标交换对象
     */
    public void copyTracingContext(ServerWebExchange sourceExchange, ServerWebExchange targetExchange) {
        TracingContext tracingContext = extractFromExchange(sourceExchange);
        if (tracingContext != null) {
            injectToExchange(targetExchange, tracingContext);
        }
    }
    
    /**
     * 创建WebFlux处理器的追踪装饰器
     * 
     * @param operationName 操作名称
     * @param <T> 处理器输入类型
     * @param <R> 处理器输出类型
     * @return 追踪装饰器函数
     */
    public <T, R> Function<Function<T, Mono<R>>, Function<T, Mono<R>>> tracingDecorator(String operationName) {
        return handler -> input -> {
            return ReactiveTracingContextHolder.withCurrentContext(tracingContext -> {
                if (tracingContext != null) {
                    // 添加操作事件
                    tracingContext.addEvent("operation.start", 
                        java.util.Map.of("operation", operationName, "input_type", input.getClass().getSimpleName()));
                }
                
                return handler.apply(input)
                        .doOnSuccess(result -> {
                            if (tracingContext != null) {
                                tracingContext.addEvent("operation.success", 
                                    java.util.Map.of("operation", operationName, "result_type", 
                                        result != null ? result.getClass().getSimpleName() : "null"));
                            }
                        })
                        .doOnError(error -> {
                            if (tracingContext != null) {
                                tracingContext.addEvent("operation.error", 
                                    java.util.Map.of("operation", operationName, "error", error.getMessage()));
                            }
                        });
            });
        };
    }
    
    /**
     * 为WebFlux路由处理器添加追踪支持
     * 
     * @param exchange Web交换对象
     * @param routeName 路由名称
     * @param handler 路由处理器
     * @param <T> 返回类型
     * @return 包含追踪的处理结果
     */
    public <T> Mono<T> traceRoute(ServerWebExchange exchange, String routeName, Mono<T> handler) {
        return withTracingContext(exchange, tracingContext -> {
            if (tracingContext != null) {
                // 添加路由信息到Span
                tracingContext.setTag("route.name", routeName);
                tracingContext.setTag("route.path", exchange.getRequest().getPath().value());
                tracingContext.setTag("route.method", 
                    exchange.getRequest().getMethod() != null ? 
                        exchange.getRequest().getMethod().name() : "UNKNOWN");
                
                tracingContext.addEvent("route.start", 
                    java.util.Map.of("route", routeName, "timestamp", java.time.Instant.now().toString()));
            }
            
            return handler
                    .doOnSuccess(result -> {
                        if (tracingContext != null) {
                            tracingContext.addEvent("route.success", 
                                java.util.Map.of("route", routeName, "timestamp", java.time.Instant.now().toString()));
                        }
                    })
                    .doOnError(error -> {
                        if (tracingContext != null) {
                            tracingContext.addEvent("route.error", 
                                java.util.Map.of("route", routeName, "error", error.getMessage(), 
                                    "timestamp", java.time.Instant.now().toString()));
                        }
                    });
        });
    }
}