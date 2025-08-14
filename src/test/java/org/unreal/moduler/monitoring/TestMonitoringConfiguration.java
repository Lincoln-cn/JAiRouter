package org.unreal.moduler.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.unreal.modelrouter.monitoring.circuitbreaker.MetricsCircuitBreaker;
import org.unreal.modelrouter.monitoring.collector.DefaultMetricsCollector;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitoring.config.MonitoringProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 测试用监控配置
 * 提供简化的监控组件配置用于单元测试
 */
@SpringBootConfiguration
@EnableConfigurationProperties(MonitoringProperties.class)
public class TestMonitoringConfiguration {

    @Bean
    @Primary
    public MeterRegistry testMeterRegistry(MonitoringProperties monitoringProperties) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        
        // Apply common tags manually
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("application", "jairouter"));
        
        Map<String, String> customTags = monitoringProperties.getCustomTags();
        if (customTags != null && !customTags.isEmpty()) {
            customTags.forEach((key, value) -> tags.add(Tag.of(key, value)));
        }
        
        registry.config().commonTags(tags);
        return registry;
    }

    @Bean
    @Primary
    public MonitoringProperties testMonitoringProperties() {
        MonitoringProperties properties = new MonitoringProperties();
        properties.setEnabled(true);
        properties.setPrefix("test");
        // Custom tags will be set by @TestPropertySource
        return properties;
    }

    @Bean
    @Primary
    public MetricsCollector testMetricsCollector(MeterRegistry meterRegistry, MonitoringProperties properties) {
        return new DefaultMetricsCollector(meterRegistry, properties);
    }

    @Bean
    @Primary
    public MetricsCircuitBreaker testMetricsCircuitBreaker() {
        return new MetricsCircuitBreaker();
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(MonitoringProperties monitoringProperties) {
        return registry -> {
            // 添加自定义标签
            List<Tag> tags = new ArrayList<>();
            
            // 添加应用程序标签
            tags.add(Tag.of("application", "jairouter"));
            
            // 添加自定义标签
            Map<String, String> customTags = monitoringProperties.getCustomTags();
            if (customTags != null && !customTags.isEmpty()) {
                customTags.forEach((key, value) -> tags.add(Tag.of(key, value)));
            }
            
            registry.config().commonTags(tags);
        };
    }
}