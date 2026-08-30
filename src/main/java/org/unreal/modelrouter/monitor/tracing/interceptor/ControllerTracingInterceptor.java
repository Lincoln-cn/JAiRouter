package org.unreal.modelrouter.monitor.tracing.interceptor;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitor.tracing.TracingConstants;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.monitor.tracing.TracingContextHolder;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * Controller层追踪拦截器
 *
 * 提供Controller层的完整链路追踪，包括：
 * - 请求接收和参数解析
 * - 服务类型和模型选择
 * - 实例选择和负载均衡
 * - 适配器调用
 * - 响应处理和返回
 *
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ControllerTracingInterceptor {

    private final TracingSpanHelper tracingSpanHelper;

    public ControllerTracingInterceptor(final TracingSpanHelper tracingSpanHelper) {
        this.tracingSpanHelper = tracingSpanHelper;
    }

    /**
     * 从 ServerWebExchange 获取追踪上下文
     */
    private TracingContext getTracingContextFromExchange(final ServerWebExchange exchange) {
        if (exchange != null) {
            TracingContext context = exchange.getAttribute(TracingConstants.ContextKeys.TRACING_CONTEXT);
            if (context != null && context.isActive()) {
                return context;
            }
        }
        return TracingContextHolder.getCurrentContext();
    }

    /**
     * 追踪Controller方法调用（使用 ServerWebExchange 获取上下文）
     */
    public Mono<ResponseEntity<?>> traceControllerCall(
            final ServerWebExchange exchange,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final ServerHttpRequest httpRequest,
            final String methodName,
            final Supplier<Mono<ResponseEntity<?>>> operation) {

        TracingContext tracingContext = getTracingContextFromExchange(exchange);
        if (tracingContext == null || !tracingContext.isActive()) {
            return operation.get();
        }

        return doTraceControllerCall(tracingContext, serviceType, modelName, httpRequest, methodName, operation);
    }

    /**
     * 追踪Controller方法调用（从 ThreadLocal 获取上下文）
     */
    public Mono<ResponseEntity<?>> traceControllerCall(
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final ServerHttpRequest httpRequest,
            final String methodName,
            final Supplier<Mono<ResponseEntity<?>>> operation) {

        TracingContext tracingContext = TracingContextHolder.getCurrentContext();
        if (tracingContext == null || !tracingContext.isActive()) {
            return operation.get();
        }

        return doTraceControllerCall(tracingContext, serviceType, modelName, httpRequest, methodName, operation);
    }

    /**
     * 实际执行Controller追踪逻辑
     */
    private Mono<ResponseEntity<?>> doTraceControllerCall(
            final TracingContext tracingContext,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final ServerHttpRequest httpRequest,
            final String methodName,
            final Supplier<Mono<ResponseEntity<?>>> operation) {

        Instant startTime = Instant.now();

        // 创建Controller层Span
        String operationName = String.format("Controller.%s", methodName);
        Span controllerSpan = tracingContext.createChildSpan(
            operationName,
            SpanKind.INTERNAL,
            tracingContext.getCurrentSpan()
        );

        // 设置Controller层属性
        tracingSpanHelper.setControllerAttributes(controllerSpan, serviceType, modelName, httpRequest, methodName);

        // 记录请求开始
        tracingSpanHelper.logControllerStart(serviceType, modelName, httpRequest, methodName, tracingContext);

        // 执行业务操作
        return operation.get()
            .doOnSuccess(response -> {
                long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
                tracingSpanHelper.handleControllerSuccess(controllerSpan, response, serviceType, modelName,
                                      methodName, duration, tracingContext);
            })
            .doOnError(error -> {
                long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
                tracingSpanHelper.handleControllerError(controllerSpan, error, serviceType, modelName,
                                    methodName, duration, tracingContext);
            })
            .doFinally(signalType -> {
                if (controllerSpan.isRecording()) {
                    controllerSpan.end();
                }
                tracingSpanHelper.recordChildSpan(controllerSpan, tracingContext, startTime, operationName);
            });
    }

    /**
     * 追踪实例选择过程（使用传入的 TracingContext）
     */
    public void traceInstanceSelection(
            final TracingContext tracingContext,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final String clientIp,
            final ModelRouterProperties.ModelInstance selectedInstance) {

        if (tracingContext == null || !tracingContext.isActive()) {
            return;
        }

        tracingSpanHelper.doTraceInstanceSelection(tracingContext, serviceType, modelName, clientIp, selectedInstance, null);
    }

    /**
     * 追踪实例选择过程（从 ThreadLocal 获取上下文）
     */
    public void traceInstanceSelection(
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final String clientIp,
            final ModelRouterProperties.ModelInstance selectedInstance) {

        TracingContext tracingContext = TracingContextHolder.getCurrentContext();
        if (tracingContext == null || !tracingContext.isActive()) {
            return;
        }

        tracingSpanHelper.doTraceInstanceSelection(tracingContext, serviceType, modelName, clientIp, selectedInstance, null);
    }

    /**
     * 追踪实例选择失败（使用传入的 TracingContext）
     */
    public void traceInstanceSelectionFailure(
            final TracingContext tracingContext,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final String clientIp,
            final Throwable error) {

        if (tracingContext == null || !tracingContext.isActive()) {
            return;
        }

        tracingSpanHelper.doTraceInstanceSelection(tracingContext, serviceType, modelName, clientIp, null, error);
    }

    /**
     * 追踪实例选择失败（从 ThreadLocal 获取上下文）- 兼容旧方法
     */
    public void traceInstanceSelectionFailure(
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final String clientIp,
            final Throwable error) {

        TracingContext tracingContext = TracingContextHolder.getCurrentContext();
        traceInstanceSelectionFailure(tracingContext, serviceType, modelName, clientIp, error);
    }

    /**
     * 追踪适配器调用（使用传入的 TracingContext）
     */
    public <T> Mono<T> traceAdapterCall(
            final TracingContext tracingContext,
            final String adapterName,
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance,
            final Supplier<Mono<T>> operation) {

        if (tracingContext == null || !tracingContext.isActive()) {
            return operation.get();
        }

        return tracingSpanHelper.doTraceAdapterCall(tracingContext, adapterName, serviceType, instance, operation);
    }

    /**
     * 追踪适配器调用（从 ThreadLocal 获取上下文）
     */
    public <T> Mono<T> traceAdapterCall(
            final String adapterName,
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance,
            final Supplier<Mono<T>> operation) {

        TracingContext tracingContext = TracingContextHolder.getCurrentContext();
        if (tracingContext == null || !tracingContext.isActive()) {
            return operation.get();
        }

        return tracingSpanHelper.doTraceAdapterCall(tracingContext, adapterName, serviceType, instance, operation);
    }
}
