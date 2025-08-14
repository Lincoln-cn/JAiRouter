package org.unreal.modelrouter.monitoring.registry;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.unreal.modelrouter.monitoring.config.MonitoringEnabledCondition;

/**
 * 自定义指标注册配置类
 * 配置自定义指标注册相关的Bean和功能
 */
@Configuration
@EnableScheduling
@Conditional(MonitoringEnabledCondition.class)
public class CustomMetricsConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomMetricsConfiguration.class);
    
    @Bean
    @ConditionalOnProperty(name = "monitoring.metrics.custom-registry.enabled", havingValue = "true", matchIfMissing = true)
    public CustomMeterRegistry customMeterRegistry(MeterRegistry meterRegistry) {
        logger.info("Creating CustomMeterRegistry bean");
        return new DefaultCustomMeterRegistry(meterRegistry);
    }
    
    @Bean
    @ConditionalOnProperty(name = "monitoring.metrics.custom-registry.enabled", havingValue = "true", matchIfMissing = true)
    public MetricRegistrationService metricRegistrationService(CustomMeterRegistry customMeterRegistry) {
        logger.info("Creating MetricRegistrationService bean");
        return new DefaultMetricRegistrationService(customMeterRegistry);
    }
    
    @Bean
    @ConditionalOnProperty(name = "monitoring.metrics.lifecycle-management.enabled", havingValue = "true", matchIfMissing = true)
    public MetricLifecycleManager metricLifecycleManager(MetricRegistrationService metricRegistrationService,
                                                        CustomMeterRegistry customMeterRegistry) {
        logger.info("Creating MetricLifecycleManager bean");
        return new MetricLifecycleManager(metricRegistrationService, customMeterRegistry);
    }
}