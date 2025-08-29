package org.unreal.modelrouter.monitoring.error;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.config.ErrorTrackerProperties;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

/**
 * 错误追踪自动配置
 * 
 * 根据配置条件自动装配错误追踪相关的组件。
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(ErrorTrackerProperties.class)
public class ErrorTrackerAutoConfiguration {
    
    /**
     * 创建堆栈脱敏配置Bean
     *
     * @param properties 错误追踪配置属性
     * @return 堆栈脱敏配置
     */
    @Bean
    public ErrorTrackerProperties.SanitizationConfig sanitizationConfig(ErrorTrackerProperties properties) {
        return properties.getSanitization();
    }
    
    /**
     * 创建错误追踪器
     * 
     * @param structuredLogger 结构化日志记录器
     * @return 错误追踪器
     */
    @Bean
    @ConditionalOnProperty(name = "jairouter.monitoring.enabled", havingValue = "true", matchIfMissing = true)
    public ErrorTracker errorTracker(StructuredLogger structuredLogger) {
        return new ErrorTracker(structuredLogger);
    }
    
    /**
     * 创建错误指标收集器
     * 
     * @param meterRegistry 指标注册表
     * @param errorTracker 错误追踪器
     * @param properties 错误追踪配置属性
     * @return 错误指标收集器
     */
    @Bean
    @ConditionalOnProperty(name = "jairouter.monitoring.error-tracking.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "jairouter.monitoring.metrics.enabled", havingValue = "true", matchIfMissing = true)
    public ErrorMetricsCollector errorMetricsCollector(
            MeterRegistry meterRegistry,
            ErrorTracker errorTracker,
            ErrorTrackerProperties properties) {
        
        return new ErrorMetricsCollector(meterRegistry, errorTracker, properties);
    }
    
    /**
     * 创建异常堆栈脱敏器
     * 
     * @param properties 错误追踪配置属性
     * @return 异常堆栈脱敏器
     */
    @Bean
    @ConditionalOnProperty(name = "jairouter.monitoring.error-tracking.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "jairouter.monitoring.error-tracking.sanitization.enabled", havingValue = "true", matchIfMissing = true)
    public StackTraceSanitizer stackTraceSanitizer(ErrorTrackerProperties.SanitizationConfig sanitizationConfig) {
        return new StackTraceSanitizer(sanitizationConfig);
    }
}