package org.unreal.modelrouter.monitoring.registry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitoring.registry.DefaultCustomMeterRegistry;
import org.unreal.modelrouter.monitoring.registry.DefaultMetricRegistrationService;
import org.unreal.modelrouter.monitoring.registry.MetricRegistrationService;
import org.unreal.modelrouter.monitoring.registry.model.MetricRegistrationRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DefaultMetricRegistrationService单元测试
 */
class DefaultMetricRegistrationServiceTest {
    
    private DefaultMetricRegistrationService metricRegistrationService;
    private DefaultCustomMeterRegistry customMeterRegistry;
    
    @BeforeEach
    void setUp() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        customMeterRegistry = new DefaultCustomMeterRegistry(meterRegistry);
        metricRegistrationService = new DefaultMetricRegistrationService(customMeterRegistry);
    }
    
    @Test
    void testRegisterBusinessMetric() {
        // 准备测试数据
        MetricRegistrationRequest request = MetricRegistrationRequest.builder("business.requests", Meter.Type.COUNTER)
                .description("Business requests counter")
                .unit("requests")
                .category("business")
                .build();
        
        // 执行测试
        MetricRegistrationService.MetricRegistrationResult result = 
                metricRegistrationService.registerBusinessMetric(request);
        
        // 验证结果
        assertTrue(result.isSuccess());
        assertEquals("business.requests", result.getMetricName());
        assertTrue(customMeterRegistry.meterExists("business.requests", Map.of()));
    }
    
    @Test
    void testRegisterGaugeMetric() {
        // 准备测试数据
        AtomicInteger value = new AtomicInteger(100);
        MetricRegistrationRequest request = MetricRegistrationRequest.builder("queue.size", Meter.Type.GAUGE)
                .description("Queue size gauge")
                .unit("items")
                .build();
        
        // 执行测试
        MetricRegistrationService.MetricRegistrationResult result = 
                metricRegistrationService.registerGaugeMetric(request, value::get);
        
        // 验证结果
        assertTrue(result.isSuccess());
        assertEquals("queue.size", result.getMetricName());
        assertTrue(customMeterRegistry.meterExists("queue.size", Map.of()));
    }
    
    @Test
    void testRegisterGaugeMetricWithNullSupplier() {
        // 准备测试数据
        MetricRegistrationRequest request = MetricRegistrationRequest.builder("invalid.gauge", Meter.Type.GAUGE)
                .description("Invalid gauge")
                .build();
        
        // 执行测试
        MetricRegistrationService.MetricRegistrationResult result = 
                metricRegistrationService.registerGaugeMetric(request, null);
        
        // 验证结果
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Value supplier cannot be null"));
    }
    
    @Test
    void testBatchRegisterMetrics() {
        // 准备测试数据
        List<MetricRegistrationRequest> requests = List.of(
                MetricRegistrationRequest.builder("batch.counter1", Meter.Type.COUNTER)
                        .description("Batch counter 1").build(),
                MetricRegistrationRequest.builder("batch.counter2", Meter.Type.COUNTER)
                        .description("Batch counter 2").build(),
                MetricRegistrationRequest.builder("batch.timer1", Meter.Type.TIMER)
                        .description("Batch timer 1").build()
        );
        
        // 执行测试
        MetricRegistrationService.BatchRegistrationResult result = 
                metricRegistrationService.batchRegisterMetrics(requests);
        
        // 验证结果
        assertEquals(3, result.getTotalRequests());
        assertEquals(3, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        assertTrue(result.isAllSuccess());
        
        // 验证指标已注册
        assertTrue(customMeterRegistry.meterExists("batch.counter1", Map.of()));
        assertTrue(customMeterRegistry.meterExists("batch.counter2", Map.of()));
        assertTrue(customMeterRegistry.meterExists("batch.timer1", Map.of()));
    }
    
    @Test
    void testBatchRegisterMetricsWithFailures() {
        // 准备测试数据（包含无效请求）
        List<MetricRegistrationRequest> requests = List.of(
                MetricRegistrationRequest.builder("valid.counter", Meter.Type.COUNTER)
                        .description("Valid counter").build(),
                MetricRegistrationRequest.builder("", Meter.Type.COUNTER)  // 无效名称
                        .description("Invalid counter").build()
        );
        
        // 执行测试
        MetricRegistrationService.BatchRegistrationResult result = 
                metricRegistrationService.batchRegisterMetrics(requests);
        
        // 验证结果
        assertEquals(2, result.getTotalRequests());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertFalse(result.isAllSuccess());
    }
    
    @Test
    void testUnregisterMetric() {
        // 先注册指标
        MetricRegistrationRequest request = MetricRegistrationRequest.builder("test.unregister", Meter.Type.COUNTER)
                .tag("env", "test")
                .build();
        
        metricRegistrationService.registerBusinessMetric(request);
        assertTrue(customMeterRegistry.meterExists("test.unregister", Map.of("env", "test")));
        
        // 执行注销
        boolean result = metricRegistrationService.unregisterMetric("test.unregister", Map.of("env", "test"));
        
        // 验证结果
        assertTrue(result);
        assertFalse(customMeterRegistry.meterExists("test.unregister", Map.of("env", "test")));
    }
    
    @Test
    void testBatchUnregisterMetrics() {
        // 先注册多个指标
        metricRegistrationService.registerBusinessMetric(
                MetricRegistrationRequest.builder("batch.unregister1", Meter.Type.COUNTER).build());
        metricRegistrationService.registerBusinessMetric(
                MetricRegistrationRequest.builder("batch.unregister2", Meter.Type.COUNTER).build());
        
        // 执行批量注销
        List<String> metricNames = List.of("batch.unregister1", "batch.unregister2", "non.existent");
        MetricRegistrationService.BatchUnregistrationResult result = 
                metricRegistrationService.batchUnregisterMetrics(metricNames);
        
        // 验证结果
        assertEquals(3, result.getTotalRequests());
        assertEquals(2, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(List.of("non.existent"), result.getFailedMetrics());
    }
    
    @Test
    void testUpdateMetricConfiguration() {
        // 先注册指标
        MetricRegistrationRequest request = MetricRegistrationRequest.builder("test.config", Meter.Type.COUNTER)
                .description("Test config")
                .enabled(true)
                .samplingRate(1.0)
                .build();
        
        metricRegistrationService.registerBusinessMetric(request);
        
        // 更新配置
        boolean result = metricRegistrationService.updateMetricConfiguration("test.config", false, 0.5);
        
        // 验证结果
        assertTrue(result);
        
        var metadata = customMeterRegistry.getMetricMetadata("test.config");
        assertTrue(metadata.isPresent());
        assertFalse(metadata.get().isEnabled());
        assertEquals(0.5, metadata.get().getSamplingRate());
    }
    
    @Test
    void testGetMetricStatistics() {
        // 注册不同类型的指标
        metricRegistrationService.registerBusinessMetric(
                MetricRegistrationRequest.builder("stats.counter1", Meter.Type.COUNTER)
                        .category("business").enabled(true).build());
        metricRegistrationService.registerBusinessMetric(
                MetricRegistrationRequest.builder("stats.counter2", Meter.Type.COUNTER)
                        .category("system").enabled(false).build());
        metricRegistrationService.registerBusinessMetric(
                MetricRegistrationRequest.builder("stats.timer1", Meter.Type.TIMER)
                        .category("business").enabled(true).build());
        
        // 获取统计信息
        MetricRegistrationService.MetricStatistics stats = metricRegistrationService.getMetricStatistics();
        
        // 验证结果
        assertEquals(3, stats.getTotalMetrics());
        assertEquals(2, stats.getEnabledMetrics());
        assertEquals(1, stats.getDisabledMetrics());
        assertEquals(2, stats.getMetricsByCategory().get("business"));
        assertEquals(1, stats.getMetricsByCategory().get("system"));
        assertEquals(2, stats.getMetricsByType().get("COUNTER"));
        assertEquals(1, stats.getMetricsByType().get("TIMER"));
    }
    
    @Test
    void testGetMetricsByCategory() {
        // 注册不同类别的指标
        metricRegistrationService.registerBusinessMetric(
                MetricRegistrationRequest.builder("business.metric1", Meter.Type.COUNTER)
                        .category("business").build());
        metricRegistrationService.registerBusinessMetric(
                MetricRegistrationRequest.builder("business.metric2", Meter.Type.TIMER)
                        .category("business").build());
        metricRegistrationService.registerBusinessMetric(
                MetricRegistrationRequest.builder("system.metric1", Meter.Type.COUNTER)
                        .category("system").build());
        
        // 获取业务类别的指标
        var businessMetrics = metricRegistrationService.getMetricsByCategory("business");
        assertEquals(2, businessMetrics.size());
        
        // 获取系统类别的指标
        var systemMetrics = metricRegistrationService.getMetricsByCategory("system");
        assertEquals(1, systemMetrics.size());
    }
    
    @Test
    void testSearchMetrics() {
        // 注册测试指标
        metricRegistrationService.registerBusinessMetric(
                MetricRegistrationRequest.builder("api.requests.total", Meter.Type.COUNTER)
                        .category("api").build());
        metricRegistrationService.registerBusinessMetric(
                MetricRegistrationRequest.builder("api.response.time", Meter.Type.TIMER)
                        .category("api").build());
        metricRegistrationService.registerGaugeMetric(
                MetricRegistrationRequest.builder("db.connections.active", Meter.Type.GAUGE)
                        .category("database").build(), () -> 10);
        
        // 搜索包含"api"的指标
        var apiMetrics = metricRegistrationService.searchMetrics("api", null);
        assertEquals(2, apiMetrics.size());
        
        // 搜索特定类别的指标
        var dbMetrics = metricRegistrationService.searchMetrics(".*", "database");
        assertEquals(1, dbMetrics.size());
        
        // 搜索特定模式的指标
        var requestMetrics = metricRegistrationService.searchMetrics(".*requests.*", null);
        assertEquals(1, requestMetrics.size());
    }
    
    @Test
    void testValidateMetricRequest() {
        // 测试有效请求
        MetricRegistrationRequest validRequest = MetricRegistrationRequest.builder("valid_metric", Meter.Type.COUNTER)
                .description("Valid metric")
                .samplingRate(0.8)
                .build();
        
        MetricRegistrationService.ValidationResult result = 
                metricRegistrationService.validateMetricRequest(validRequest);
        
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
        
        // 测试无效请求 - 空名称
        MetricRegistrationRequest invalidRequest = MetricRegistrationRequest.builder("", Meter.Type.COUNTER)
                .build();
        
        MetricRegistrationService.ValidationResult invalidResult = 
                metricRegistrationService.validateMetricRequest(invalidRequest);
        
        assertFalse(invalidResult.isValid());
        assertFalse(invalidResult.getErrors().isEmpty());
        
        // 测试无效采样率
        assertThrows(IllegalArgumentException.class, () -> 
                MetricRegistrationRequest.builder("test", Meter.Type.COUNTER)
                        .samplingRate(1.5)
                        .build());
    }
    
    @Test
    void testPerformMetricCleanup() {
        // 注册一些指标
        metricRegistrationService.registerBusinessMetric(
                MetricRegistrationRequest.builder("cleanup.test1", Meter.Type.COUNTER).build());
        metricRegistrationService.registerBusinessMetric(
                MetricRegistrationRequest.builder("cleanup.test2", Meter.Type.COUNTER).build());
        
        // 执行清理
        MetricRegistrationService.CleanupResult result = metricRegistrationService.performMetricCleanup();
        
        // 验证结果（由于指标刚创建，不应该被清理）
        assertEquals(0, result.getCleanedMetrics());
        assertEquals(2, result.getTotalMetrics());
        assertTrue(result.getCleanedMetricNames().isEmpty());
    }
}