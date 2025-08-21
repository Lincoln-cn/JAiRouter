package org.unreal.modelrouter.monitoring.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 监控配置类
 * 负责配置Micrometer和Prometheus集成
 */
@Configuration
@EnableConfigurationProperties(MonitoringProperties.class)
public class MonitoringConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringConfiguration.class);

    private final MonitoringProperties monitoringProperties;

    public MonitoringConfiguration(MonitoringProperties monitoringProperties) {
        this.monitoringProperties = monitoringProperties;
    }

    /**
     * 自定义MeterRegistry配置
     * 添加全局标签和指标前缀
     */
    @Bean
    @Conditional(MonitoringEnabledCondition.class)
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
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
            
            // 指标前缀在MetricsCollector中直接应用到指标名称
            String prefix = monitoringProperties.getPrefix();
            
            logger.info("监控配置已应用: 前缀={}, 标签={}", prefix, tags);
        };
    }

    /**
     * 配置指标过滤器
     * 根据启用的类别过滤指标
     */
    @Bean
    @Conditional(MonitoringEnabledCondition.class)
    public MeterFilter categoryFilter() {
        return MeterFilter.accept(id -> {
            String name = id.getName();
            
            // 检查是否在启用的类别中
            return monitoringProperties.getEnabledCategories().stream()
                    .anyMatch(category -> name.contains(category) || isSystemMetric(name));
        });
    }

    /**
     * 配置采样率过滤器
     * 根据配置的采样率对指标进行采样
     */
    @Bean
    @Conditional(MonitoringEnabledCondition.class)
    public MeterFilter samplingFilter() {
        MonitoringProperties.Sampling sampling = monitoringProperties.getSampling();
        
        return MeterFilter.accept(id -> {
            String name = id.getName();
            double samplingRate = 1.0;
            
            // 根据指标类型确定采样率
            if (name.contains("request") || name.contains("http")) {
                samplingRate = sampling.getRequestMetrics();
            } else if (name.contains("backend") || name.contains("adapter")) {
                samplingRate = sampling.getBackendMetrics();
            } else if (name.contains("circuit") || name.contains("rate") || name.contains("load")) {
                samplingRate = sampling.getInfrastructureMetrics();
            }
            
            // 简单的采样逻辑
            return Math.random() < samplingRate;
        });
    }

    /**
     * 判断是否为系统指标
     */
    private boolean isSystemMetric(String metricName) {
        return metricName.startsWith("jvm.") || 
               metricName.startsWith("system.") || 
               metricName.startsWith("process.") ||
               metricName.startsWith("http.server.requests");
    }
}