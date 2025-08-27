package org.unreal.modelrouter.tracing.health;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * HealthCheckTracingEnhancer 单元测试
 */
@ExtendWith(MockitoExtension.class)
class HealthCheckTracingEnhancerTest {

    @Mock
    private StructuredLogger structuredLogger;
    
    @Mock
    private Tracer tracer;
    
    @Mock
    private TracingContext tracingContext;
    
    @Mock
    private Span span;
    
    @Mock
    private SpanBuilder spanBuilder;
    
    @Mock
    private SpanContext spanContext;

    private HealthCheckTracingEnhancer enhancer;
    private ModelRouterProperties.ModelInstance instance;

    @BeforeEach
    void setUp() {
        enhancer = new HealthCheckTracingEnhancer(structuredLogger, tracer);
        
        // 创建测试用的实例
        instance = new ModelRouterProperties.ModelInstance();
        instance.setName("test-instance");
        instance.setBaseUrl("http://localhost:8080");
        instance.setPath("/api/v1");
        
        // 使用lenient模式设置完整的SpanBuilder Mock链避免不必要的stubbing警告
        lenient().when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        lenient().when(spanBuilder.setSpanKind(any())).thenReturn(spanBuilder);
        lenient().when(spanBuilder.startSpan()).thenReturn(span);
        
        // 使用lenient模式设置 Span 和 SpanContext
        lenient().when(span.getSpanContext()).thenReturn(spanContext);
        lenient().when(spanContext.isValid()).thenReturn(true);
        lenient().when(spanContext.getTraceId()).thenReturn("test-trace-id");
        lenient().when(spanContext.getSpanId()).thenReturn("test-span-id");
    }

    @Test
    void testCreateHealthCheckContext() {
        // Given
        String serviceType = "chat";

        // When
        TracingContext context = enhancer.createHealthCheckContext(serviceType, instance);

        // Then
        assertNotNull(context);
        verify(span).setAttribute("health_check.type", "socket_connect");
        verify(span).setAttribute("service.type", serviceType);
        verify(span).setAttribute("instance.id", instance.getInstanceId());
        verify(span).setAttribute("instance.name", instance.getName());
        verify(span).setAttribute("instance.url", instance.getBaseUrl());
    }

    @Test
    void testLogHealthCheckStart() {
        // Given
        String serviceType = "embedding";

        // When
        enhancer.logHealthCheckStart(serviceType, instance, tracingContext);

        // Then
        verify(structuredLogger).logBusinessEvent(eq("health_check_start"), anyMap(), eq(tracingContext));
    }

    @Test
    void testLogHealthCheckComplete_Healthy() {
        // Given
        String serviceType = "chat";
        boolean healthy = true;
        long responseTime = 150L;
        String message = "Connection successful";
        
        when(tracingContext.getCurrentSpan()).thenReturn(span);
        when(span.isRecording()).thenReturn(true);

        // When
        enhancer.logHealthCheckComplete(serviceType, instance, healthy, responseTime, message, tracingContext);

        // Then
        verify(span).setAttribute("health_check.result", "healthy");
        verify(span).setAttribute("health_check.response_time_ms", responseTime);
        verify(span).setStatus(StatusCode.OK, "Health check passed");
        verify(structuredLogger).logBusinessEvent(eq("health_check_complete"), anyMap(), eq(tracingContext));
    }

    @Test
    void testLogHealthCheckComplete_Unhealthy() {
        // Given
        String serviceType = "chat";
        boolean healthy = false;
        long responseTime = 5000L;
        String message = "Connection failed";
        
        when(tracingContext.getCurrentSpan()).thenReturn(span);
        when(span.isRecording()).thenReturn(true);

        // When
        enhancer.logHealthCheckComplete(serviceType, instance, healthy, responseTime, message, tracingContext);

        // Then
        verify(span).setAttribute("health_check.result", "unhealthy");
        verify(span).setAttribute("health_check.response_time_ms", responseTime);
        verify(span).setStatus(StatusCode.ERROR, "Health check failed: " + message);
        verify(structuredLogger).logBusinessEvent(eq("health_check_complete"), anyMap(), eq(tracingContext));
    }

    @Test
    void testLogHealthCheckComplete_SlowButHealthy() {
        // Given
        String serviceType = "chat";
        boolean healthy = true;
        long responseTime = 1500L; // 超过1000ms阈值
        String message = "Connection successful but slow";
        
        when(tracingContext.getCurrentSpan()).thenReturn(span);
        when(span.isRecording()).thenReturn(true);

        // When
        enhancer.logHealthCheckComplete(serviceType, instance, healthy, responseTime, message, tracingContext);

        // Then
        verify(span).setAttribute("health_check.result", "healthy");
        verify(span).setStatus(StatusCode.OK, "Health check passed");
        verify(structuredLogger).logBusinessEvent(eq("health_check_complete"), anyMap(), eq(tracingContext));
        verify(structuredLogger).logSlowQuery(
            eq("health_check_" + serviceType), 
            eq(responseTime), 
            eq(1000L), 
            eq(tracingContext)
        );
    }

    @Test
    void testLogInstanceStateChange_Recovery() {
        // Given
        String serviceType = "embedding";
        boolean previousState = false;
        boolean currentState = true;
        String reason = "Connection restored";

        // When
        enhancer.logInstanceStateChange(serviceType, instance, previousState, currentState, reason);

        // Then
        verify(span).setAttribute("service.type", serviceType);
        verify(span).setAttribute("state.previous", "unhealthy");
        verify(span).setAttribute("state.current", "healthy");
        verify(span).setAttribute("state.change_reason", reason);
        verify(span).setStatus(StatusCode.OK, "Instance recovered");
        verify(structuredLogger).logBusinessEvent(eq("instance_recovered"), anyMap(), any());
    }

    @Test
    void testLogInstanceStateChange_Failure() {
        // Given
        String serviceType = "chat";
        boolean previousState = true;
        boolean currentState = false;
        String reason = "Connection timeout";

        // When
        enhancer.logInstanceStateChange(serviceType, instance, previousState, currentState, reason);

        // Then
        verify(span).setAttribute("state.previous", "healthy");
        verify(span).setAttribute("state.current", "unhealthy");
        verify(span).setStatus(StatusCode.ERROR, "Instance failed");
        verify(structuredLogger).logBusinessEvent(eq("instance_failed"), anyMap(), any());
    }

    @Test
    void testLogServiceStateChange() {
        // Given
        String serviceType = "rerank";
        boolean hasHealthyInstance = true;
        int totalInstances = 3;
        int healthyInstances = 2;

        // When
        enhancer.logServiceStateChange(serviceType, hasHealthyInstance, totalInstances, healthyInstances);

        // Then
        verify(span).setAttribute("service.type", serviceType);
        verify(span).setAttribute("service.has_healthy_instance", hasHealthyInstance);
        verify(span).setAttribute("service.total_instances", totalInstances);
        verify(span).setAttribute("service.healthy_instances", healthyInstances);
        verify(span).setAttribute("service.unhealthy_instances", 1);
        verify(span).setAttribute("service.health_ratio", 2.0/3.0);
        verify(span).setStatus(StatusCode.OK, "Some instances are healthy");
        verify(structuredLogger).logBusinessEvent(eq("service_health_status"), anyMap(), any());
    }

    @Test
    void testLogHealthCheckBatchComplete() {
        // Given
        int totalServices = 4;
        int healthyServices = 3;
        int totalInstances = 10;
        int healthyInstances = 7;
        long checkDuration = 2500L;

        // When
        enhancer.logHealthCheckBatchComplete(totalServices, healthyServices, totalInstances, healthyInstances, checkDuration);

        // Then
        verify(span).setAttribute("batch.total_services", totalServices);
        verify(span).setAttribute("batch.healthy_services", healthyServices);
        verify(span).setAttribute("batch.total_instances", totalInstances);
        verify(span).setAttribute("batch.healthy_instances", healthyInstances);
        verify(span).setAttribute("batch.duration_ms", checkDuration);
        verify(span).setAttribute("batch.service_health_ratio", 3.0/4.0);
        verify(span).setAttribute("batch.instance_health_ratio", 7.0/10.0);
        // 3/4 = 75%, 小于80%阈值，所以应该是 "Some services are unhealthy"
        verify(span).setStatus(StatusCode.OK, "Some services are unhealthy");
        verify(structuredLogger).logBusinessEvent(eq("health_check_batch_complete"), anyMap(), any());
    }

    @Test
    void testLogServiceInstanceRegistered() {
        // Given
        String serviceType = "tts";

        // When
        enhancer.logServiceInstanceRegistered(serviceType, instance);

        // Then
        verify(span).setAttribute("service.type", serviceType);
        verify(span).setAttribute("instance.id", instance.getInstanceId());
        verify(span).setAttribute("instance.name", instance.getName());
        verify(span).setAttribute("instance.url", instance.getBaseUrl());
        verify(structuredLogger).logBusinessEvent(eq("service_instance_registered"), anyMap(), any());
    }

    @Test
    void testLogServiceInstanceDiscovered() {
        // Given
        String serviceType = "stt";

        // When
        enhancer.logServiceInstanceDiscovered(serviceType, instance);

        // Then
        verify(span).setAttribute("service.type", serviceType);
        verify(span).setAttribute("instance.id", instance.getInstanceId());
        verify(span).setAttribute("instance.name", instance.getName());
        verify(span).setAttribute("instance.url", instance.getBaseUrl());
        verify(structuredLogger).logBusinessEvent(eq("service_instance_discovered"), anyMap(), any());
    }
}