package org.unreal.modelrouter.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitoring.collector.DefaultMetricsCollector;
import org.unreal.modelrouter.monitoring.config.MonitoringProperties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 默认指标收集器测试类
 */
public class DefaultMetricsCollectorTest {

    private DefaultMetricsCollector metricsCollector;
    private MeterRegistry meterRegistry;
    private MonitoringProperties monitoringProperties;

    @BeforeEach
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        monitoringProperties = new MonitoringProperties();
        monitoringProperties.setPrefix("test");
        
        metricsCollector = new DefaultMetricsCollector(meterRegistry, monitoringProperties);
    }

    @Test
    public void testRecordRequest() {
        // 记录请求指标
        metricsCollector.recordRequest("chat", "POST", 100, "200");
        
        // 验证计数器
        var counter = meterRegistry.find("test_requests_total")
            .tag("service", "chat")
            .tag("method", "POST")
            .tag("status", "200")
            .counter();
        
        assertNotNull(counter);
        assertEquals(1.0, counter.count(), 0.001);
        
        // 验证计时器
        var timer = meterRegistry.find("test_request_duration_seconds")
            .tag("service", "chat")
            .tag("method", "POST")
            .timer();
        
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    public void testRecordBackendCall() {
        // 记录后端调用指标
        metricsCollector.recordBackendCall("gpustack", "instance1", 200, true);
        
        // 验证计数器
        var counter = meterRegistry.find("test_backend_calls_total")
            .tag("adapter", "gpustack")
            .tag("instance", "instance1")
            .tag("status", "success")
            .counter();
        
        assertNotNull(counter);
        assertEquals(1.0, counter.count(), 0.001);
        
        // 验证计时器
        var timer = meterRegistry.find("test_backend_call_duration_seconds")
            .tag("adapter", "gpustack")
            .tag("instance", "instance1")
            .timer();
        
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    public void testRecordRateLimit() {
        // 记录限流指标
        metricsCollector.recordRateLimit("chat", "token-bucket", true);
        
        // 验证计数器
        var counter = meterRegistry.find("test_rate_limit_events_total")
            .tag("service", "chat")
            .tag("algorithm", "token-bucket")
            .tag("result", "allowed")
            .counter();
        
        assertNotNull(counter);
        assertEquals(1.0, counter.count(), 0.001);
    }

    @Test
    public void testRecordCircuitBreaker() {
        // 记录熔断器指标
        metricsCollector.recordCircuitBreaker("chat", "CLOSED", "success");
        
        // 验证事件计数器
        var counter = meterRegistry.find("test_circuit_breaker_events_total")
            .tag("service", "chat")
            .tag("event", "success")
            .counter();
        
        assertNotNull(counter);
        assertEquals(1.0, counter.count(), 0.001);
        
        // 验证状态Gauge
        var gauge = meterRegistry.find("test_circuit_breaker_state")
            .tag("service", "chat")
            .gauge();
        
        assertNotNull(gauge);
        assertEquals(0.0, gauge.value(), 0.001); // CLOSED = 0
    }

    @Test
    public void testRecordLoadBalancer() {
        // 记录负载均衡指标
        metricsCollector.recordLoadBalancer("chat", "round-robin", "instance1");
        
        // 验证计数器
        var counter = meterRegistry.find("test_loadbalancer_selections_total")
            .tag("service", "chat")
            .tag("strategy", "round-robin")
            .tag("instance", "instance1")
            .counter();
        
        assertNotNull(counter);
        assertEquals(1.0, counter.count(), 0.001);
    }

    @Test
    public void testRecordHealthCheck() {
        // 记录健康检查指标
        metricsCollector.recordHealthCheck("gpustack", "instance1", true, 50);
        
        // 验证健康状态Gauge
        var gauge = meterRegistry.find("test_backend_health")
            .tag("adapter", "gpustack")
            .tag("instance", "instance1")
            .gauge();
        
        assertNotNull(gauge);
        assertEquals(1.0, gauge.value(), 0.001); // healthy = 1
        
        // 验证响应时间计时器
        var timer = meterRegistry.find("test_health_check_duration_seconds")
            .tag("adapter", "gpustack")
            .tag("instance", "instance1")
            .timer();
        
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    public void testRecordRequestSize() {
        // 记录请求大小指标
        metricsCollector.recordRequestSize("chat", 1024, 2048);
        
        // 验证请求大小分布摘要
        var requestSummary = meterRegistry.find("test_request_size_bytes")
            .tag("service", "chat")
            .summary();
        
        assertNotNull(requestSummary);
        assertEquals(1, requestSummary.count());
        assertEquals(1024.0, requestSummary.totalAmount(), 0.001);
        
        // 验证响应大小分布摘要
        var responseSummary = meterRegistry.find("test_response_size_bytes")
            .tag("service", "chat")
            .summary();
        
        assertNotNull(responseSummary);
        assertEquals(1, responseSummary.count());
        assertEquals(2048.0, responseSummary.totalAmount(), 0.001);
    }
}