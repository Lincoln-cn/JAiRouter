package org.unreal.modelrouter.monitoring.collector;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.unreal.modelrouter.config.MonitoringProperties;
import org.unreal.modelrouter.monitoring.collector.DefaultMetricsCollector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DefaultMetricsCollector 单元测试
 */
class DefaultMetricsCollectorTest {

    private MeterRegistry meterRegistry;
    private DefaultMetricsCollector metricsCollector;
    
    @Mock
    private MonitoringProperties monitoringProperties;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        
        // 设置默认的监控属性
        when(monitoringProperties.getPrefix()).thenReturn("jairouter");
        
        metricsCollector = new DefaultMetricsCollector(meterRegistry, monitoringProperties);
    }

    @Test
    void testRecordRequest() {
        String service = "chat";
        String method = "POST";
        long duration = 100;
        String status = "200";

        metricsCollector.recordRequest(service, method, duration, status);

        // 验证指标是否被记录
        assertNotNull(meterRegistry.find("jairouter_requests_total").counter());
        assertNotNull(meterRegistry.find("jairouter_request_duration_seconds").timer());
        
        assertEquals(1.0, meterRegistry.find("jairouter_requests_total").counter().count());
    }

    @Test
    void testRecordBackendCall() {
        String adapter = "ollama";
        String instance = "instance1";
        long duration = 200;
        boolean success = true;

        metricsCollector.recordBackendCall(adapter, instance, duration, success);

        // 验证指标是否被记录
        assertNotNull(meterRegistry.find("jairouter_backend_calls_total").counter());
        assertNotNull(meterRegistry.find("jairouter_backend_call_duration_seconds").timer());
        
        assertEquals(1.0, meterRegistry.find("jairouter_backend_calls_total").counter().count());
    }

    @Test
    void testRecordRateLimit() {
        String service = "embedding";
        String algorithm = "token_bucket";
        boolean allowed = true;

        metricsCollector.recordRateLimit(service, algorithm, allowed);

        // 验证指标是否被记录
        assertNotNull(meterRegistry.find("jairouter_rate_limit_events_total").counter());
        assertEquals(1.0, meterRegistry.find("jairouter_rate_limit_events_total").counter().count());
    }

    @Test
    void testRecordCircuitBreaker() {
        String service = "chat";
        String state = "CLOSED";
        String event = "success";

        metricsCollector.recordCircuitBreaker(service, state, event);

        // 验证指标是否被记录
        assertNotNull(meterRegistry.find("jairouter_circuit_breaker_events_total").counter());
        assertNotNull(meterRegistry.find("jairouter_circuit_breaker_state").gauge());
        
        assertEquals(1.0, meterRegistry.find("jairouter_circuit_breaker_events_total").counter().count());
    }

    @Test
    void testRecordLoadBalancer() {
        String service = "chat";
        String strategy = "round_robin";
        String selectedInstance = "instance1";

        metricsCollector.recordLoadBalancer(service, strategy, selectedInstance);

        // 验证指标是否被记录
        assertNotNull(meterRegistry.find("jairouter_loadbalancer_selections_total").counter());
        assertEquals(1.0, meterRegistry.find("jairouter_loadbalancer_selections_total").counter().count());
    }

    @Test
    void testRecordHealthCheck() {
        String adapter = "ollama";
        String instance = "instance1";
        boolean healthy = true;
        long responseTime = 50;

        metricsCollector.recordHealthCheck(adapter, instance, healthy, responseTime);

        // 验证指标是否被记录
        assertNotNull(meterRegistry.find("jairouter_backend_health").gauge());
        assertNotNull(meterRegistry.find("jairouter_health_check_duration_seconds").timer());
        
        assertEquals(1.0, meterRegistry.find("jairouter_backend_health").gauge().value());
    }

    @Test
    void testRecordRequestSize() {
        String service = "chat";
        long requestSize = 1024;
        long responseSize = 2048;

        metricsCollector.recordRequestSize(service, requestSize, responseSize);

        // 验证指标是否被记录
        assertNotNull(meterRegistry.find("jairouter_request_size_bytes").summary());
        assertNotNull(meterRegistry.find("jairouter_response_size_bytes").summary());
        
        assertEquals(1, meterRegistry.find("jairouter_request_size_bytes").summary().count());
        assertEquals(1, meterRegistry.find("jairouter_response_size_bytes").summary().count());
    }

    @Test
    void testMetricPrefixHandling() {
        // 测试空前缀
        when(monitoringProperties.getPrefix()).thenReturn("");
        DefaultMetricsCollector collectorWithEmptyPrefix = new DefaultMetricsCollector(meterRegistry, monitoringProperties);
        
        collectorWithEmptyPrefix.recordRequest("test", "GET", 100, "200");
        
        // 应该使用默认前缀（无前缀）
        assertNotNull(meterRegistry.find("requests_total").counter());
    }

    @Test
    void testCircuitBreakerStateMapping() {
        metricsCollector.recordCircuitBreaker("test", "CLOSED", "success");
        assertEquals(0.0, meterRegistry.find("jairouter_circuit_breaker_state").gauge().value());
        
        metricsCollector.recordCircuitBreaker("test", "OPEN", "failure");
        assertEquals(1.0, meterRegistry.find("jairouter_circuit_breaker_state").gauge().value());
        
        metricsCollector.recordCircuitBreaker("test", "HALF_OPEN", "attempt");
        assertEquals(2.0, meterRegistry.find("jairouter_circuit_breaker_state").gauge().value());
    }

    @Test
    void testHealthCheckStateMapping() {
        // 测试健康状态
        metricsCollector.recordHealthCheck("ollama", "instance1", true, 50);
        assertEquals(1.0, meterRegistry.find("jairouter_backend_health").gauge().value());
        
        // 测试不健康状态
        metricsCollector.recordHealthCheck("ollama", "instance1", false, 50);
        assertEquals(0.0, meterRegistry.find("jairouter_backend_health").gauge().value());
    }

    @Test
    void testExceptionHandling() {
        // 模拟异常情况 - 这里主要测试不会抛出异常
        assertDoesNotThrow(() -> {
            metricsCollector.recordRequest(null, null, -1, null);
            metricsCollector.recordBackendCall(null, null, -1, true);
            metricsCollector.recordRateLimit(null, null, true);
        });
    }
}