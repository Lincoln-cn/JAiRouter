package org.unreal.modelrouter.monitoring.registry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitoring.registry.DefaultCustomMeterRegistry;
import org.unreal.modelrouter.monitoring.registry.model.MetricMetadata;
import org.unreal.modelrouter.monitoring.registry.model.MetricRegistrationRequest;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DefaultCustomMeterRegistry单元测试
 */
class DefaultCustomMeterRegistryTest {
    
    private MeterRegistry meterRegistry;
    private DefaultCustomMeterRegistry customMeterRegistry;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        customMeterRegistry = new DefaultCustomMeterRegistry(meterRegistry);
    }
    
    @Test
    void testRegisterCounter() {
        // 准备测试数据
        MetricRegistrationRequest request = MetricRegistrationRequest.builder("test.counter", Meter.Type.COUNTER)
                .description("Test counter metric")
                .unit("requests")
                .tag("service", "test")
                .category("business")
                .build();
        
        // 执行测试
        Counter counter = customMeterRegistry.registerCounter(request);
        
        // 验证结果
        assertNotNull(counter);
        assertEquals("test.counter", counter.getId().getName());
        assertEquals("Test counter metric", counter.getId().getDescription());
        assertEquals("requests", counter.getId().getBaseUnit());
        
        // 验证元数据
        Optional<MetricMetadata> metadata = customMeterRegistry.getMetricMetadata("test.counter");
        assertTrue(metadata.isPresent());
        assertEquals("Test counter metric", metadata.get().getDescription());
        assertEquals("business", metadata.get().getCategory());
    }
    
    @Test
    void testRegisterGauge() {
        // 准备测试数据
        AtomicInteger value = new AtomicInteger(42);
        MetricRegistrationRequest request = MetricRegistrationRequest.builder("test.gauge", Meter.Type.GAUGE)
                .description("Test gauge metric")
                .unit("items")
                .tag("type", "queue")
                .build();
        
        // 执行测试
        Gauge gauge = customMeterRegistry.registerGauge(request, value::get);
        
        // 验证结果
        assertNotNull(gauge);
        assertEquals("test.gauge", gauge.getId().getName());
        assertEquals(42.0, gauge.value());
        
        // 更新值并验证
        value.set(100);
        assertEquals(100.0, gauge.value());
    }
    
    @Test
    void testRegisterTimer() {
        // 准备测试数据
        MetricRegistrationRequest request = MetricRegistrationRequest.builder("test.timer", Meter.Type.TIMER)
                .description("Test timer metric")
                .tag("operation", "process")
                .build();
        
        // 执行测试
        Timer timer = customMeterRegistry.registerTimer(request);
        
        // 验证结果
        assertNotNull(timer);
        assertEquals("test.timer", timer.getId().getName());
        assertEquals("Test timer metric", timer.getId().getDescription());
    }
    
    @Test
    void testRegisterDuplicateMetric() {
        // 准备测试数据
        MetricRegistrationRequest request = MetricRegistrationRequest.builder("duplicate.counter", Meter.Type.COUNTER)
                .description("Duplicate counter")
                .build();
        
        // 第一次注册
        Counter counter1 = customMeterRegistry.registerCounter(request);
        assertNotNull(counter1);
        
        // 第二次注册相同指标
        Counter counter2 = customMeterRegistry.registerCounter(request);
        assertNotNull(counter2);
        
        // 应该返回相同的实例
        assertEquals(counter1, counter2);
    }
    
    @Test
    void testUnregisterMeter() {
        // 注册指标
        MetricRegistrationRequest request = MetricRegistrationRequest.builder("test.unregister", Meter.Type.COUNTER)
                .description("Test unregister")
                .tag("env", "test")
                .build();
        
        Counter counter = customMeterRegistry.registerCounter(request);
        assertNotNull(counter);
        
        // 验证指标存在
        assertTrue(customMeterRegistry.meterExists("test.unregister", Map.of("env", "test")));
        
        // 注销指标
        boolean result = customMeterRegistry.unregisterMeter("test.unregister", Map.of("env", "test"));
        assertTrue(result);
        
        // 验证指标已被注销
        assertFalse(customMeterRegistry.meterExists("test.unregister", Map.of("env", "test")));
        assertFalse(customMeterRegistry.getMetricMetadata("test.unregister").isPresent());
    }
    
    @Test
    void testUnregisterNonExistentMeter() {
        // 尝试注销不存在的指标
        boolean result = customMeterRegistry.unregisterMeter("non.existent", Map.of());
        assertFalse(result);
    }
    
    @Test
    void testGetMetricMetadata() {
        // 注册指标
        MetricRegistrationRequest request = MetricRegistrationRequest.builder("test.metadata", Meter.Type.COUNTER)
                .description("Test metadata")
                .unit("events")
                .category("system")
                .samplingRate(0.5)
                .build();
        
        customMeterRegistry.registerCounter(request);
        
        // 获取元数据
        Optional<MetricMetadata> metadata = customMeterRegistry.getMetricMetadata("test.metadata");
        assertTrue(metadata.isPresent());
        
        MetricMetadata meta = metadata.get();
        assertEquals("test.metadata", meta.getName());
        assertEquals("Test metadata", meta.getDescription());
        assertEquals("events", meta.getUnit());
        assertEquals("system", meta.getCategory());
        assertEquals(0.5, meta.getSamplingRate());
        assertTrue(meta.isEnabled());
    }
    
    @Test
    void testUpdateMetricMetadata() {
        // 注册指标
        MetricRegistrationRequest request = MetricRegistrationRequest.builder("test.update", Meter.Type.COUNTER)
                .description("Original description")
                .build();
        
        customMeterRegistry.registerCounter(request);
        
        // 更新元数据
        MetricMetadata updatedMetadata = MetricMetadata.builder("test.update", Meter.Type.COUNTER)
                .description("Updated description")
                .enabled(false)
                .samplingRate(0.8)
                .build();
        
        boolean result = customMeterRegistry.updateMetricMetadata("test.update", updatedMetadata);
        assertTrue(result);
        
        // 验证更新
        Optional<MetricMetadata> metadata = customMeterRegistry.getMetricMetadata("test.update");
        assertTrue(metadata.isPresent());
        assertEquals("Updated description", metadata.get().getDescription());
        assertFalse(metadata.get().isEnabled());
        assertEquals(0.8, metadata.get().getSamplingRate());
    }
    
    @Test
    void testGetAllMetricMetadata() {
        // 注册多个指标
        customMeterRegistry.registerCounter(
                MetricRegistrationRequest.builder("counter1", Meter.Type.COUNTER).build());
        customMeterRegistry.registerCounter(
                MetricRegistrationRequest.builder("counter2", Meter.Type.COUNTER).build());
        customMeterRegistry.registerTimer(
                MetricRegistrationRequest.builder("timer1", Meter.Type.TIMER).build());
        
        // 获取所有元数据
        var allMetadata = customMeterRegistry.getAllMetricMetadata();
        assertEquals(3, allMetadata.size());
    }
    
    @Test
    void testMeterExists() {
        // 注册指标
        MetricRegistrationRequest request = MetricRegistrationRequest.builder("test.exists", Meter.Type.COUNTER)
                .tag("env", "prod")
                .build();
        
        customMeterRegistry.registerCounter(request);
        
        // 测试存在性检查
        assertTrue(customMeterRegistry.meterExists("test.exists", Map.of("env", "prod")));
        assertFalse(customMeterRegistry.meterExists("test.exists", Map.of("env", "dev")));
        assertFalse(customMeterRegistry.meterExists("non.existent", Map.of()));
    }
    
    @Test
    void testGetMeter() {
        // 注册指标
        MetricRegistrationRequest request = MetricRegistrationRequest.builder("test.get", Meter.Type.COUNTER)
                .tag("service", "api")
                .build();
        
        Counter counter = customMeterRegistry.registerCounter(request);
        
        // 获取指标
        Optional<Meter> meter = customMeterRegistry.getMeter("test.get", Map.of("service", "api"));
        assertTrue(meter.isPresent());
        assertEquals(counter, meter.get());
        
        // 获取不存在的指标
        Optional<Meter> nonExistent = customMeterRegistry.getMeter("non.existent", Map.of());
        assertFalse(nonExistent.isPresent());
    }
    
    @Test
    void testGetAllMeters() {
        // 注册多个指标
        customMeterRegistry.registerCounter(
                MetricRegistrationRequest.builder("counter1", Meter.Type.COUNTER).build());
        customMeterRegistry.registerTimer(
                MetricRegistrationRequest.builder("timer1", Meter.Type.TIMER).build());
        
        // 获取所有指标
        var allMeters = customMeterRegistry.getAllMeters();
        assertEquals(2, allMeters.size());
    }
    
    @Test
    void testInvalidRequests() {
        // 测试null请求
        assertThrows(IllegalArgumentException.class, () -> 
                customMeterRegistry.registerCounter(null));
        
        // 测试空名称
        MetricRegistrationRequest emptyNameRequest = MetricRegistrationRequest.builder("", Meter.Type.COUNTER).build();
        assertThrows(IllegalArgumentException.class, () -> 
                customMeterRegistry.registerCounter(emptyNameRequest));
        
        // 测试错误的指标类型
        MetricRegistrationRequest wrongTypeRequest = MetricRegistrationRequest.builder("test", Meter.Type.TIMER).build();
        assertThrows(IllegalArgumentException.class, () -> 
                customMeterRegistry.registerCounter(wrongTypeRequest));
        
        // 测试无效的采样率
        assertThrows(IllegalArgumentException.class, () -> 
                MetricRegistrationRequest.builder("test", Meter.Type.COUNTER)
                        .samplingRate(1.5)
                        .build());
    }
    
    @Test
    void testCleanupExpiredMeters() {
        // 注册一些指标
        customMeterRegistry.registerCounter(
                MetricRegistrationRequest.builder("counter1", Meter.Type.COUNTER).build());
        customMeterRegistry.registerCounter(
                MetricRegistrationRequest.builder("counter2", Meter.Type.COUNTER).build());
        
        // 执行清理（由于指标刚创建，应该不会被清理）
        int cleanedCount = customMeterRegistry.cleanupExpiredMeters();
        assertEquals(0, cleanedCount);
        
        // 验证指标仍然存在
        assertEquals(2, customMeterRegistry.getAllMeters().size());
    }
}