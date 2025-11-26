package org.unreal.modelrouter.tracing.interceptor;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
@RequiredArgsConstructor
public class ControllerTracingInterceptor {
    
    private final StructuredLogger structuredLogger;
    
    /**
     * 追踪Controller方法调用
     * 
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @param httpRequest HTTP请求
     * @param methodName Controller方法名
     * @param operation 实际业务操作
     * @return 包含追踪信息的响应
     */
    public Mono<ResponseEntity<?>> traceControllerCall(
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            ServerHttpRequest httpRequest,
            String methodName,
            Supplier<Mono<ResponseEntity<?>>> operation) {
        
        TracingContext tracingContext = TracingContextHolder.getCurrentContext();
        if (tracingContext == null || !tracingContext.isActive()) {
            // 如果没有追踪上下文，直接执行操作
            return operation.get();
        }
        
        Instant startTime = Instant.now();
        
        // 创建Controller层Span
        String operationName = String.format("Controller.%s", methodName);
        Span controllerSpan = tracingContext.createChildSpan(
            operationName, 
            SpanKind.INTERNAL, 
            tracingContext.getCurrentSpan()
        );
        
        // 设置Controller层属性
        setControllerAttributes(controllerSpan, serviceType, modelName, httpRequest, methodName);
        
        // 记录请求开始
        logControllerStart(serviceType, modelName, httpRequest, methodName, tracingContext);
        
        // 执行业务操作
        return operation.get()
            .doOnSuccess(response -> {
                long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
                handleControllerSuccess(controllerSpan, response, serviceType, modelName, 
                                      methodName, duration, tracingContext);
            })
            .doOnError(error -> {
                long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
                handleControllerError(controllerSpan, error, serviceType, modelName, 
                                    methodName, duration, tracingContext);
            })
            .doFinally(signalType -> {
                if (controllerSpan.isRecording()) {
                    controllerSpan.end();
                }
            });
    }
    
    /**
     * 追踪实例选择过程
     * 
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @param clientIp 客户端IP
     * @param selectedInstance 选中的实例
     */
    public void traceInstanceSelection(
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            String clientIp,
            ModelRouterProperties.ModelInstance selectedInstance) {
        
        TracingContext tracingContext = TracingContextHolder.getCurrentContext();
        if (tracingContext == null || !tracingContext.isActive()) {
            return;
        }
        
        Instant startTime = Instant.now();
        
        // 创建实例选择Span
        String operationName = "InstanceSelection";
        Span selectionSpan = tracingContext.createChildSpan(
            operationName, 
            SpanKind.INTERNAL, 
            tracingContext.getCurrentSpan()
        );
        
        try {
            // 设置实例选择属性
            selectionSpan.setAttribute("service.type", serviceType.name());
            selectionSpan.setAttribute("model.name", modelName);
            selectionSpan.setAttribute("client.ip", clientIp != null ? clientIp : "unknown");
            selectionSpan.setAttribute("instance.id", selectedInstance.getInstanceId());
            selectionSpan.setAttribute("instance.name", selectedInstance.getName());
            selectionSpan.setAttribute("instance.base_url", selectedInstance.getBaseUrl());
            selectionSpan.setAttribute("instance.adapter", 
                selectedInstance.getAdapter() != null ? selectedInstance.getAdapter() : "default");
            selectionSpan.setAttribute("instance.weight", selectedInstance.getWeight());
            
            // 记录实例选择事件
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("service_type", serviceType.name());
            eventData.put("model_name", modelName);
            eventData.put("selected_instance", selectedInstance.getInstanceId());
            eventData.put("instance_url", selectedInstance.getBaseUrl());
            eventData.put("adapter", selectedInstance.getAdapter() != null ? 
                selectedInstance.getAdapter() : "default");
            
            structuredLogger.logBusinessEvent("instance_selected", eventData, tracingContext);
            
            selectionSpan.setStatus(StatusCode.OK);
            
            long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            selectionSpan.setAttribute("duration_ms", duration);
            
            log.debug("实例选择完成: service={}, model={}, instance={}, duration={}ms",
                serviceType, modelName, selectedInstance.getInstanceId(), duration);
            
        } catch (Exception e) {
            selectionSpan.setStatus(StatusCode.ERROR, e.getMessage());
            selectionSpan.recordException(e);
            log.warn("记录实例选择追踪失败", e);
        } finally {
            selectionSpan.end();
        }
    }
    
    /**
     * 追踪适配器调用
     * 
     * @param adapterName 适配器名称
     * @param serviceType 服务类型
     * @param instance 实例信息
     * @param operation 适配器操作
     * @return 包含追踪的操作结果
     */
    public <T> Mono<T> traceAdapterCall(
            String adapterName,
            ModelServiceRegistry.ServiceType serviceType,
            ModelRouterProperties.ModelInstance instance,
            Supplier<Mono<T>> operation) {
        
        TracingContext tracingContext = TracingContextHolder.getCurrentContext();
        if (tracingContext == null || !tracingContext.isActive()) {
            return operation.get();
        }
        
        Instant startTime = Instant.now();
        
        // 创建适配器调用Span
        String operationName = String.format("Adapter.%s.%s", adapterName, serviceType.name());
        Span adapterSpan = tracingContext.createChildSpan(
            operationName, 
            SpanKind.INTERNAL, 
            tracingContext.getCurrentSpan()
        );
        
        // 设置适配器属性
        adapterSpan.setAttribute("adapter.name", adapterName);
        adapterSpan.setAttribute("adapter.service_type", serviceType.name());
        adapterSpan.setAttribute("adapter.instance_id", instance.getInstanceId());
        adapterSpan.setAttribute("adapter.instance_url", instance.getBaseUrl());
        
        // 记录适配器调用开始
        Map<String, Object> startData = new HashMap<>();
        startData.put("adapter", adapterName);
        startData.put("service_type", serviceType.name());
        startData.put("instance_id", instance.getInstanceId());
        startData.put("instance_url", instance.getBaseUrl());
        
        structuredLogger.logBusinessEvent("adapter_call_start", startData, tracingContext);
        
        return operation.get()
            .doOnSuccess(result -> {
                long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
                adapterSpan.setStatus(StatusCode.OK);
                adapterSpan.setAttribute("duration_ms", duration);
                
                // 记录适配器调用成功
                Map<String, Object> successData = new HashMap<>();
                successData.put("adapter", adapterName);
                successData.put("service_type", serviceType.name());
                successData.put("instance_id", instance.getInstanceId());
                successData.put("duration_ms", duration);
                successData.put("success", true);
                
                structuredLogger.logBusinessEvent("adapter_call_complete", successData, tracingContext);
                
                log.debug("适配器调用成功: adapter={}, service={}, instance={}, duration={}ms",
                    adapterName, serviceType, instance.getInstanceId(), duration);
            })
            .doOnError(error -> {
                long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
                adapterSpan.setStatus(StatusCode.ERROR, error.getMessage());
                adapterSpan.recordException(error);
                adapterSpan.setAttribute("duration_ms", duration);
                
                // 记录适配器调用失败
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("adapter", adapterName);
                errorData.put("service_type", serviceType.name());
                errorData.put("instance_id", instance.getInstanceId());
                errorData.put("duration_ms", duration);
                errorData.put("success", false);
                errorData.put("error_type", error.getClass().getSimpleName());
                errorData.put("error_message", error.getMessage());
                
                structuredLogger.logError(error, tracingContext, errorData);
                
                log.warn("适配器调用失败: adapter={}, service={}, instance={}, error={}",
                    adapterName, serviceType, instance.getInstanceId(), error.getMessage());
            })
            .doFinally(signalType -> {
                if (adapterSpan.isRecording()) {
                    adapterSpan.end();
                }
            });
    }
    
    /**
     * 设置Controller层属性
     */
    private void setControllerAttributes(
            Span span,
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            ServerHttpRequest httpRequest,
            String methodName) {
        
        try {
            span.setAttribute("controller.method", methodName);
            span.setAttribute("service.type", serviceType.name());
            span.setAttribute("model.name", modelName);
            span.setAttribute("http.method", httpRequest.getMethod().name());
            span.setAttribute("http.path", httpRequest.getPath().value());
            
            // 添加请求头信息（脱敏）
            String contentType = httpRequest.getHeaders().getFirst("Content-Type");
            if (contentType != null) {
                span.setAttribute("http.content_type", contentType);
            }
            
            String contentLength = httpRequest.getHeaders().getFirst("Content-Length");
            if (contentLength != null) {
                try {
                    span.setAttribute("http.request_size", Long.parseLong(contentLength));
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
        } catch (Exception e) {
            log.debug("设置Controller属性失败", e);
        }
    }
    
    /**
     * 记录Controller调用开始
     */
    private void logControllerStart(
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            ServerHttpRequest httpRequest,
            String methodName,
            TracingContext tracingContext) {
        
        try {
            Map<String, Object> startData = new HashMap<>();
            startData.put("controller_method", methodName);
            startData.put("service_type", serviceType.name());
            startData.put("model_name", modelName);
            startData.put("http_method", httpRequest.getMethod().name());
            startData.put("http_path", httpRequest.getPath().value());
            
            structuredLogger.logBusinessEvent("controller_call_start", startData, tracingContext);
        } catch (Exception e) {
            log.debug("记录Controller开始日志失败", e);
        }
    }
    
    /**
     * 处理Controller成功响应
     */
    private void handleControllerSuccess(
            Span span,
            ResponseEntity<?> response,
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            String methodName,
            long duration,
            TracingContext tracingContext) {
        
        try {
            span.setStatus(StatusCode.OK);
            span.setAttribute("duration_ms", duration);
            span.setAttribute("http.status_code", response.getStatusCode().value());
            
            // 记录响应大小
            if (response.getBody() != null) {
                String bodyStr = response.getBody().toString();
                span.setAttribute("http.response_size", bodyStr.length());
            }
            
            // 记录Controller调用成功
            Map<String, Object> successData = new HashMap<>();
            successData.put("controller_method", methodName);
            successData.put("service_type", serviceType.name());
            successData.put("model_name", modelName);
            successData.put("duration_ms", duration);
            successData.put("status_code", response.getStatusCode().value());
            successData.put("success", true);
            
            structuredLogger.logBusinessEvent("controller_call_complete", successData, tracingContext);
            
            log.debug("Controller调用成功: method={}, service={}, model={}, duration={}ms, status={}",
                methodName, serviceType, modelName, duration, response.getStatusCode().value());
            
        } catch (Exception e) {
            log.debug("处理Controller成功响应失败", e);
        }
    }
    
    /**
     * 处理Controller错误响应
     */
    private void handleControllerError(
            Span span,
            Throwable error,
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            String methodName,
            long duration,
            TracingContext tracingContext) {
        
        try {
            span.setStatus(StatusCode.ERROR, error.getMessage());
            span.recordException(error);
            span.setAttribute("duration_ms", duration);
            
            // 记录Controller调用失败
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("controller_method", methodName);
            errorData.put("service_type", serviceType.name());
            errorData.put("model_name", modelName);
            errorData.put("duration_ms", duration);
            errorData.put("success", false);
            errorData.put("error_type", error.getClass().getSimpleName());
            errorData.put("error_message", error.getMessage());
            
            structuredLogger.logError(error, tracingContext, errorData);
            
            log.warn("Controller调用失败: method={}, service={}, model={}, error={}",
                methodName, serviceType, modelName, error.getMessage());
            
        } catch (Exception e) {
            log.debug("处理Controller错误响应失败", e);
        }
    }
}
