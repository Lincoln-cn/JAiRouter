package org.unreal.modelrouter.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 监控配置属性类
 * 用于绑定application.yml中的监控相关配置
 */
@Component
@ConfigurationProperties(prefix = "monitoring.metrics")
public class MonitoringProperties {

    /**
     * 是否启用监控功能
     */
    private boolean enabled = true;

    /**
     * 指标前缀
     */
    private String prefix = "jairouter";

    /**
     * 启用的指标类别
     */
    private Set<String> enabledCategories = Set.of("system", "business", "infrastructure");

    /**
     * 指标收集间隔
     */
    private Duration collectionInterval = Duration.ofSeconds(10);

    /**
     * 自定义标签
     */
    private Map<String, String> customTags = new HashMap<>();

    /**
     * 指标采样配置
     */
    private SamplingConfig sampling = new SamplingConfig();

    /**
     * 性能优化配置
     */
    private PerformanceConfig performance = new PerformanceConfig();

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Set<String> getEnabledCategories() {
        return enabledCategories;
    }

    public void setEnabledCategories(Set<String> enabledCategories) {
        this.enabledCategories = enabledCategories;
    }

    public Duration getCollectionInterval() {
        return collectionInterval;
    }

    public void setCollectionInterval(Duration collectionInterval) {
        this.collectionInterval = collectionInterval;
    }

    public Map<String, String> getCustomTags() {
        return customTags;
    }

    public void setCustomTags(Map<String, String> customTags) {
        this.customTags = customTags;
    }

    public SamplingConfig getSampling() {
        return sampling;
    }

    public void setSampling(SamplingConfig sampling) {
        this.sampling = sampling;
    }

    public PerformanceConfig getPerformance() {
        return performance;
    }

    public void setPerformance(PerformanceConfig performance) {
        this.performance = performance;
    }

    /**
     * 指标采样配置
     */
    public static class SamplingConfig {
        /**
         * 请求指标采样率
         */
        private double requestMetrics = 1.0;

        /**
         * 后端调用指标采样率
         */
        private double backendMetrics = 1.0;

        /**
         * 基础设施指标采样率
         */
        private double infrastructureMetrics = 0.1;

        // Getters and Setters
        public double getRequestMetrics() {
            return requestMetrics;
        }

        public void setRequestMetrics(double requestMetrics) {
            this.requestMetrics = requestMetrics;
        }

        public double getBackendMetrics() {
            return backendMetrics;
        }

        public void setBackendMetrics(double backendMetrics) {
            this.backendMetrics = backendMetrics;
        }

        public double getInfrastructureMetrics() {
            return infrastructureMetrics;
        }

        public void setInfrastructureMetrics(double infrastructureMetrics) {
            this.infrastructureMetrics = infrastructureMetrics;
        }
    }

    /**
     * 性能优化配置
     */
    public static class PerformanceConfig {
        /**
         * 是否启用异步处理
         */
        private boolean asyncProcessing = true;

        /**
         * 批量处理大小
         */
        private int batchSize = 100;

        /**
         * 缓冲区大小
         */
        private int bufferSize = 1000;

        // Getters and Setters
        public boolean isAsyncProcessing() {
            return asyncProcessing;
        }

        public void setAsyncProcessing(boolean asyncProcessing) {
            this.asyncProcessing = asyncProcessing;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getBufferSize() {
            return bufferSize;
        }

        public void setBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
        }
    }
}