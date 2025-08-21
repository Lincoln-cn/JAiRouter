package org.unreal.modelrouter.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.unreal.modelrouter.monitoring.collector.DefaultMetricsCollector;
import org.unreal.modelrouter.monitoring.config.MonitoringProperties;

/**
 * 集成测试专用配置
 * 为集成测试提供优化的监控组件配置
 */
@TestConfiguration
@Profile("integration-test")
public class IntegrationTestConfiguration {

    @Bean
    @Primary
    public MeterRegistry testMeterRegistry() {
        // 使用SimpleMeterRegistry进行测试，避免复杂的Prometheus配置
        return new SimpleMeterRegistry();
    }

    @Bean
    public MonitoringProperties testMonitoringProperties() {
        MonitoringProperties properties = new MonitoringProperties();
        properties.setEnabled(true);
        properties.setPrefix("integration_test");
        properties.getCustomTags().put("test", "true");
        properties.getCustomTags().put("environment", "test");
        
        // 性能优化配置
        MonitoringProperties.Performance performance = new MonitoringProperties.Performance();
        performance.setAsyncProcessing(true);
        performance.setBatchSize(10);
        performance.setBufferSize(100);
        properties.setPerformance(performance);
        
        // 采样配置
        MonitoringProperties.Sampling sampling = new MonitoringProperties.Sampling();
        sampling.setRequestMetrics(1.0);
        sampling.setBackendMetrics(1.0);
        sampling.setInfrastructureMetrics(1.0);
        properties.setSampling(sampling);
        
        return properties;
    }

    @Bean
    public DefaultMetricsCollector testMetricsCollector(MeterRegistry meterRegistry, 
                                                       MonitoringProperties properties) {
        return new DefaultMetricsCollector(meterRegistry, properties);
    }
}