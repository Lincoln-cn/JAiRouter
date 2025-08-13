package org.unreal.moduler.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.unreal.modelrouter.ModelRouterApplication;
import org.unreal.modelrouter.monitoring.DefaultMetricsCollector;
import org.unreal.modelrouter.monitoring.MonitoringProperties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 监控集成测试
 * 验证监控组件在Spring Boot环境中的集成
 */
@SpringBootTest(classes = ModelRouterApplication.class)
@TestPropertySource(properties = {
    "monitoring.metrics.enabled=true",
    "monitoring.metrics.prefix=integration_test",
    "monitoring.metrics.custom-tags.test=true"
})
public class MonitoringIntegrationTest {

    @Autowired
    private MonitoringProperties monitoringProperties;

    @Autowired
    private DefaultMetricsCollector metricsCollector;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    public void testMonitoringComponentsAreLoaded() {
        assertNotNull(monitoringProperties);
        assertNotNull(metricsCollector);
        assertNotNull(meterRegistry);
    }

    @Test
    public void testMonitoringConfiguration() {
        assertTrue(monitoringProperties.isEnabled());
        assertEquals("integration_test", monitoringProperties.getPrefix());
        assertEquals("true", monitoringProperties.getCustomTags().get("test"));
    }

    @Test
    public void testMetricsCollectionWorks() {
        // 记录一个测试指标
        metricsCollector.recordRequest("test_service", "GET", 100, "200");
        
        // Debug: Print all available meters
        System.out.println("Available meters:");
        meterRegistry.getMeters().forEach(meter -> {
            System.out.println("  " + meter.getId().getName() + " - " + meter.getId().getTags());
        });
        
        // 验证指标是否被正确记录
        var counter = meterRegistry.find("integration_test_requests_total")
            .tag("service", "test_service")
            .tag("method", "GET")
            .tag("status", "200")
            .counter();
        
        assertNotNull(counter, "Counter should not be null. Available meters: " + 
            meterRegistry.getMeters().stream().map(m -> m.getId().getName()).toList());
        assertEquals(1.0, counter.count(), 0.001);
    }

    @Test
    public void testCustomTagsAreApplied() {
        // 记录一个测试指标，确保有指标被创建
        metricsCollector.recordRequest("test_service_tag", "GET", 100, "200");

        // 验证自定义标签是否被应用到MeterRegistry
        var meters = meterRegistry.getMeters();
        
        // 测试指标是否正确包含自定义标签
        boolean hasCustomTag = false;
        for (var meter : meters) {
            var tags = meter.getId().getTags();
            for (var tag : tags) {
                if ("test".equals(tag.getKey()) && "true".equals(tag.getValue())) {
                    hasCustomTag = true;
                    break;
                }
            }
            if (hasCustomTag) break;
        }
        
        assertTrue(hasCustomTag, "Custom tags should be applied to metrics");
    }
}