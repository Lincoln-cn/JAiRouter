package org.unreal.moduler.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.unreal.modelrouter.ModelRouterApplication;
import org.unreal.modelrouter.monitoring.MonitoringProperties;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 监控配置测试类
 */
@SpringBootTest(classes = ModelRouterApplication.class)
@TestPropertySource(properties = {
    "monitoring.metrics.enabled=true",
    "monitoring.metrics.prefix=test",
    "monitoring.metrics.collection-interval=5s",
    "monitoring.metrics.custom-tags.environment=test",
    "monitoring.metrics.custom-tags.version=1.0.0",
    "monitoring.metrics.sampling.request-metrics=0.8",
    "monitoring.metrics.sampling.backend-metrics=0.9",
    "monitoring.metrics.sampling.infrastructure-metrics=0.1",
    "monitoring.metrics.performance.async-processing=true",
    "monitoring.metrics.performance.batch-size=50",
    "monitoring.metrics.performance.buffer-size=500"
})
public class MonitoringConfigurationTest {

    @Autowired
    private MonitoringProperties monitoringProperties;

    @Test
    public void testBasicConfiguration() {
        assertTrue(monitoringProperties.isEnabled());
        assertEquals("test", monitoringProperties.getPrefix());
        assertEquals(5, monitoringProperties.getCollectionInterval().getSeconds());
    }

    @Test
    public void testCustomTags() {
        var customTags = monitoringProperties.getCustomTags();
        assertNotNull(customTags);
        assertEquals("test", customTags.get("environment"));
        assertEquals("1.0.0", customTags.get("version"));
    }

    @Test
    public void testSamplingConfiguration() {
        var sampling = monitoringProperties.getSampling();
        assertNotNull(sampling);
        assertEquals(0.8, sampling.getRequestMetrics(), 0.001);
        assertEquals(0.9, sampling.getBackendMetrics(), 0.001);
        assertEquals(0.1, sampling.getInfrastructureMetrics(), 0.001);
    }

    @Test
    public void testPerformanceConfiguration() {
        var performance = monitoringProperties.getPerformance();
        assertNotNull(performance);
        assertTrue(performance.isAsyncProcessing());
        assertEquals(50, performance.getBatchSize());
        assertEquals(500, performance.getBufferSize());
    }

    @Test
    public void testEnabledCategories() {
        var categories = monitoringProperties.getEnabledCategories();
        assertNotNull(categories);
        assertTrue(categories.contains("system"));
        assertTrue(categories.contains("business"));
        assertTrue(categories.contains("infrastructure"));
    }
}