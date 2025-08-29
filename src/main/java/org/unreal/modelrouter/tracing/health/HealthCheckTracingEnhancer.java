package org.unreal.modelrouter.tracing.health;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.tracing.DefaultTracingContext;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查追踪增强器
 * <p>
 * 为服务健康检查添加追踪功能，包括：
 * - 健康检查的响应时间和结果追踪
 * - 服务实例状态变化的事件追踪
 * - 服务发现和注册的追踪日志
 * - 健康检查失败的详细分析
 *
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Component
public class HealthCheckTracingEnhancer {

    private final StructuredLogger structuredLogger;
    private final Tracer tracer;

    // 健康检查相关的属性常量
    private static final String HEALTH_CHECK_TYPE = "health_check.type";
    private static final String HEALTH_CHECK_TARGET = "health_check.target";
    private static final String HEALTH_CHECK_RESULT = "health_check.result";
    private static final String HEALTH_CHECK_RESPONSE_TIME = "health_check.response_time_ms";
    private static final String SERVICE_TYPE = "service.type";
    private static final String INSTANCE_ID = "instance.id";
    private static final String INSTANCE_NAME = "instance.name";
    private static final String INSTANCE_URL = "instance.url";

    public HealthCheckTracingEnhancer(StructuredLogger structuredLogger,
                                      Tracer tracer) {
        this.structuredLogger = structuredLogger;
        this.tracer = tracer;
    }

    /**
     * 创建健康检查追踪上下文
     *
     * @param serviceType 服务类型
     * @param instance    服务实例
     * @return 追踪上下文
     */
    public TracingContext createHealthCheckContext(String serviceType, ModelRouterProperties.ModelInstance instance) {
        // 创建独立的追踪上下文用于健康检查
        TracingContext context = new DefaultTracingContext(tracer);

        String operationName = String.format("health_check_%s", serviceType);
        Span span = context.createSpan(operationName, SpanKind.CLIENT);

        // 设置健康检查相关属性
        span.setAttribute(HEALTH_CHECK_TYPE, "socket_connect");
        span.setAttribute(SERVICE_TYPE, serviceType);
        span.setAttribute(INSTANCE_ID, instance.getInstanceId());
        span.setAttribute(INSTANCE_NAME, instance.getName());
        span.setAttribute(INSTANCE_URL, instance.getBaseUrl());
        span.setAttribute(HEALTH_CHECK_TARGET, instance.getBaseUrl());

        return context;
    }

    /**
     * 记录健康检查开始事件
     *
     * @param serviceType 服务类型
     * @param instance    服务实例
     * @param context     追踪上下文
     */
    public void logHealthCheckStart(String serviceType, ModelRouterProperties.ModelInstance instance,
                                    TracingContext context) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("service_type", serviceType);
        eventData.put("instance_id", instance.getInstanceId());
        eventData.put("instance_name", instance.getName());
        eventData.put("instance_url", instance.getBaseUrl());
        eventData.put("check_type", "socket_connect");

        structuredLogger.logBusinessEvent("health_check_start", eventData, context);
    }

    /**
     * 记录健康检查完成事件
     *
     * @param serviceType  服务类型
     * @param instance     服务实例
     * @param healthy      是否健康
     * @param responseTime 响应时间
     * @param message      检查结果消息
     * @param context      追踪上下文
     */
    public void logHealthCheckComplete(String serviceType, ModelRouterProperties.ModelInstance instance,
                                       boolean healthy, long responseTime, String message, TracingContext context) {
        // 更新Span状态
        Span currentSpan = context.getCurrentSpan();
        if (currentSpan != null && currentSpan.isRecording()) {
            currentSpan.setAttribute(HEALTH_CHECK_RESULT, healthy ? "healthy" : "unhealthy");
            currentSpan.setAttribute(HEALTH_CHECK_RESPONSE_TIME, responseTime);

            if (healthy) {
                currentSpan.setStatus(StatusCode.OK, "Health check passed");
            } else {
                currentSpan.setStatus(StatusCode.ERROR, "Health check failed: " + message);
            }
        }

        // 记录结构化日志
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("service_type", serviceType);
        eventData.put("instance_id", instance.getInstanceId());
        eventData.put("instance_name", instance.getName());
        eventData.put("instance_url", instance.getBaseUrl());
        eventData.put("healthy", healthy);
        eventData.put("response_time_ms", responseTime);
        eventData.put("message", message);
        eventData.put("check_type", "socket_connect");

        // 添加健康状态分类
        if (healthy) {
            if (responseTime < 100) {
                eventData.put("health_category", "excellent");
            } else if (responseTime < 500) {
                eventData.put("health_category", "good");
            } else {
                eventData.put("health_category", "slow_but_healthy");
            }
        } else {
            eventData.put("health_category", "unhealthy");
        }

        structuredLogger.logBusinessEvent("health_check_complete", eventData, context);

        // 如果是慢响应，记录慢查询日志
        if (healthy && responseTime > 1000) {
            structuredLogger.logSlowQuery(
                    String.format("health_check_%s", serviceType),
                    responseTime,
                    1000,
                    context
            );
        }
    }

    /**
     * 记录服务实例状态变化事件
     *
     * @param serviceType   服务类型
     * @param instance      服务实例
     * @param previousState 之前状态
     * @param currentState  当前状态
     * @param reason        状态变化原因
     */
    public void logInstanceStateChange(String serviceType, ModelRouterProperties.ModelInstance instance,
                                       boolean previousState, boolean currentState, String reason) {
        // 创建独立的追踪上下文用于状态变化事件
        TracingContext context = new DefaultTracingContext(tracer);

        String operationName = String.format("instance_state_change_%s", serviceType);
        Span span = context.createSpan(operationName, SpanKind.INTERNAL);

        // 设置Span属性
        span.setAttribute(SERVICE_TYPE, serviceType);
        span.setAttribute(INSTANCE_ID, instance.getInstanceId());
        span.setAttribute(INSTANCE_NAME, instance.getName());
        span.setAttribute(INSTANCE_URL, instance.getBaseUrl());
        span.setAttribute("state.previous", previousState ? "healthy" : "unhealthy");
        span.setAttribute("state.current", currentState ? "healthy" : "unhealthy");
        span.setAttribute("state.change_reason", reason);

        // 记录结构化日志
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("service_type", serviceType);
        eventData.put("instance_id", instance.getInstanceId());
        eventData.put("instance_name", instance.getName());
        eventData.put("instance_url", instance.getBaseUrl());
        eventData.put("previous_state", previousState ? "healthy" : "unhealthy");
        eventData.put("current_state", currentState ? "healthy" : "unhealthy");
        eventData.put("state_changed", previousState != currentState);
        eventData.put("reason", reason);

        // 确定事件类型
        String eventType;
        if (!previousState && currentState) {
            eventType = "instance_recovered";
            span.setStatus(StatusCode.OK, "Instance recovered");
        } else if (previousState && !currentState) {
            eventType = "instance_failed";
            span.setStatus(StatusCode.ERROR, "Instance failed");
        } else {
            eventType = "instance_state_confirmed";
            span.setStatus(StatusCode.OK, "Instance state confirmed");
        }

        structuredLogger.logBusinessEvent(eventType, eventData, context);

        // 完成Span
        context.finishSpan(span);
    }

    /**
     * 记录服务级别状态变化事件
     *
     * @param serviceType        服务类型
     * @param hasHealthyInstance 是否有健康实例
     * @param totalInstances     总实例数
     * @param healthyInstances   健康实例数
     */
    public void logServiceStateChange(String serviceType, boolean hasHealthyInstance,
                                      int totalInstances, int healthyInstances) {
        // 创建独立的追踪上下文用于服务状态变化
        TracingContext context = new DefaultTracingContext(tracer);

        String operationName = String.format("service_state_change_%s", serviceType);
        Span span = context.createSpan(operationName, SpanKind.INTERNAL);

        // 设置Span属性
        span.setAttribute(SERVICE_TYPE, serviceType);
        span.setAttribute("service.has_healthy_instance", hasHealthyInstance);
        span.setAttribute("service.total_instances", totalInstances);
        span.setAttribute("service.healthy_instances", healthyInstances);
        span.setAttribute("service.unhealthy_instances", totalInstances - healthyInstances);

        // 计算健康率
        double healthRatio = totalInstances > 0 ? (double) healthyInstances / totalInstances : 0.0;
        span.setAttribute("service.health_ratio", healthRatio);

        // 记录结构化日志
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("service_type", serviceType);
        eventData.put("has_healthy_instance", hasHealthyInstance);
        eventData.put("total_instances", totalInstances);
        eventData.put("healthy_instances", healthyInstances);
        eventData.put("unhealthy_instances", totalInstances - healthyInstances);
        eventData.put("health_ratio", healthRatio);

        // 确定服务健康状态
        String healthStatus;
        if (healthyInstances == 0) {
            healthStatus = "all_down";
            span.setStatus(StatusCode.ERROR, "All instances are down");
        } else if (healthyInstances == totalInstances) {
            healthStatus = "all_healthy";
            span.setStatus(StatusCode.OK, "All instances are healthy");
        } else {
            healthStatus = "partially_healthy";
            span.setStatus(StatusCode.OK, "Some instances are healthy");
        }

        eventData.put("health_status", healthStatus);

        structuredLogger.logBusinessEvent("service_health_status", eventData, context);

        // 完成Span
        context.finishSpan(span);
    }

    /**
     * 记录健康检查批次完成事件
     *
     * @param totalServices    总服务数
     * @param healthyServices  健康服务数
     * @param totalInstances   总实例数
     * @param healthyInstances 健康实例数
     * @param checkDuration    检查总耗时
     */
    public void logHealthCheckBatchComplete(int totalServices, int healthyServices,
                                            int totalInstances, int healthyInstances, long checkDuration) {
        // 创建独立的追踪上下文用于批次完成事件
        TracingContext context = new DefaultTracingContext(tracer);

        String operationName = "health_check_batch_complete";
        Span span = context.createSpan(operationName, SpanKind.INTERNAL);

        // 设置Span属性
        span.setAttribute("batch.total_services", totalServices);
        span.setAttribute("batch.healthy_services", healthyServices);
        span.setAttribute("batch.total_instances", totalInstances);
        span.setAttribute("batch.healthy_instances", healthyInstances);
        span.setAttribute("batch.duration_ms", checkDuration);

        // 计算健康率
        double serviceHealthRatio = totalServices > 0 ? (double) healthyServices / totalServices : 0.0;
        double instanceHealthRatio = totalInstances > 0 ? (double) healthyInstances / totalInstances : 0.0;

        span.setAttribute("batch.service_health_ratio", serviceHealthRatio);
        span.setAttribute("batch.instance_health_ratio", instanceHealthRatio);

        // 记录结构化日志
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("total_services", totalServices);
        eventData.put("healthy_services", healthyServices);
        eventData.put("unhealthy_services", totalServices - healthyServices);
        eventData.put("total_instances", totalInstances);
        eventData.put("healthy_instances", healthyInstances);
        eventData.put("unhealthy_instances", totalInstances - healthyInstances);
        eventData.put("service_health_ratio", serviceHealthRatio);
        eventData.put("instance_health_ratio", instanceHealthRatio);
        eventData.put("check_duration_ms", checkDuration);

        // 确定整体健康状态
        String overallHealth;
        if (healthyServices == 0) {
            overallHealth = "critical";
            span.setStatus(StatusCode.ERROR, "No healthy services");
        } else if (serviceHealthRatio >= 0.8) {
            overallHealth = "good";
            span.setStatus(StatusCode.OK, "Most services are healthy");
        } else if (serviceHealthRatio >= 0.5) {
            overallHealth = "degraded";
            span.setStatus(StatusCode.OK, "Some services are unhealthy");
        } else {
            overallHealth = "poor";
            span.setStatus(StatusCode.ERROR, "Many services are unhealthy");
        }

        eventData.put("overall_health", overallHealth);

        structuredLogger.logBusinessEvent("health_check_batch_complete", eventData, context);

        // 完成Span
        context.finishSpan(span);
    }
    
    /**
     * 记录服务实例注册事件
     * 
     * @param serviceType 服务类型
     * @param instance 服务实例
     */
    public void logServiceInstanceRegistered(String serviceType, ModelRouterProperties.ModelInstance instance) {
        // 创建独立的追踪上下文用于服务注册事件
        TracingContext context = new DefaultTracingContext(tracer);
        
        String operationName = String.format("service_instance_registered_%s", serviceType);
        Span span = context.createSpan(operationName, SpanKind.INTERNAL);
        
        // 设置Span属性
        span.setAttribute(SERVICE_TYPE, serviceType);
        span.setAttribute(INSTANCE_ID, instance.getInstanceId());
        span.setAttribute(INSTANCE_NAME, instance.getName());
        span.setAttribute(INSTANCE_URL, instance.getBaseUrl());
        
        // 记录结构化日志
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("service_type", serviceType);
        eventData.put("instance_id", instance.getInstanceId());
        eventData.put("instance_name", instance.getName());
        eventData.put("instance_url", instance.getBaseUrl());
        eventData.put("event_type", "registration");
        
        structuredLogger.logBusinessEvent("service_instance_registered", eventData, context);
        
        // 完成Span
        context.finishSpan(span);
    }
    
    /**
     * 记录服务实例发现事件
     * 
     * @param serviceType 服务类型
     * @param instance 服务实例
     */
    public void logServiceInstanceDiscovered(String serviceType, ModelRouterProperties.ModelInstance instance) {
        // 创建独立的追踪上下文用于服务发现事件
        TracingContext context = new DefaultTracingContext(tracer);
        
        String operationName = String.format("service_instance_discovered_%s", serviceType);
        Span span = context.createSpan(operationName, SpanKind.INTERNAL);
        
        // 设置Span属性
        span.setAttribute(SERVICE_TYPE, serviceType);
        span.setAttribute(INSTANCE_ID, instance.getInstanceId());
        span.setAttribute(INSTANCE_NAME, instance.getName());
        span.setAttribute(INSTANCE_URL, instance.getBaseUrl());
        
        // 记录结构化日志
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("service_type", serviceType);
        eventData.put("instance_id", instance.getInstanceId());
        eventData.put("instance_name", instance.getName());
        eventData.put("instance_url", instance.getBaseUrl());
        eventData.put("event_type", "discovery");
        
        structuredLogger.logBusinessEvent("service_instance_discovered", eventData, context);
        
        // 完成Span
        context.finishSpan(span);
    }
}