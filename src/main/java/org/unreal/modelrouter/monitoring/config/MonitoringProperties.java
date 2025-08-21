package org.unreal.modelrouter.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "monitoring")
public class MonitoringProperties {

    private boolean enabled = true;
    private String prefix = "jairouter";
    private Duration collectionInterval = Duration.ofSeconds(10);
    private Set<String> enabledCategories = new HashSet<String>() {{
        add("system");
        add("business");
        add("infrastructure");
    }};
    private Map<String, String> customTags;

    private Sampling sampling = new Sampling();
    private Performance performance = new Performance();
    private Thresholds thresholds = new Thresholds();

    public static class Sampling {
        private double requestMetrics = 1.0;
        private double backendMetrics = 1.0;
        private double infrastructureMetrics = 0.1;
        private double traceMetrics = 1.0;
        private double traceProcessingMetrics = 1.0;
        private double traceAnalysisMetrics = 1.0;

        // Getters and setters
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

        public double getTraceMetrics() {
            return traceMetrics;
        }

        public void setTraceMetrics(double traceMetrics) {
            this.traceMetrics = traceMetrics;
        }
        
        public double getTraceProcessingMetrics() {
            return traceProcessingMetrics;
        }

        public void setTraceProcessingMetrics(double traceProcessingMetrics) {
            this.traceProcessingMetrics = traceProcessingMetrics;
        }

        public double getTraceAnalysisMetrics() {
            return traceAnalysisMetrics;
        }

        public void setTraceAnalysisMetrics(double traceAnalysisMetrics) {
            this.traceAnalysisMetrics = traceAnalysisMetrics;
        }
    }

    public static class Performance {
        private boolean asyncProcessing = true;
        private int batchSize = 100;
        private int bufferSize = 1000;

        // Getters and setters
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

    public static class Thresholds {
        private long defaultThreshold = 1000L;
        private long slowQueryThreshold = 5000L;
        private Map<String, Long> operationThresholds;
        private Map<String, Long> slowQueryThresholds;

        // Getters and setters
        public long getDefaultThreshold() {
            return defaultThreshold;
        }

        public void setDefaultThreshold(long defaultThreshold) {
            this.defaultThreshold = defaultThreshold;
        }

        public long getSlowQueryThreshold() {
            return slowQueryThreshold;
        }

        public void setSlowQueryThreshold(long slowQueryThreshold) {
            this.slowQueryThreshold = slowQueryThreshold;
        }

        public Map<String, Long> getOperationThresholds() {
            return operationThresholds;
        }

        public void setOperationThresholds(Map<String, Long> operationThresholds) {
            this.operationThresholds = operationThresholds;
        }

        public Map<String, Long> getSlowQueryThresholds() {
            return slowQueryThresholds;
        }

        public void setSlowQueryThresholds(Map<String, Long> slowQueryThresholds) {
            this.slowQueryThresholds = slowQueryThresholds;
        }
    }

    // Getters and setters for main properties
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

    public Duration getCollectionInterval() {
        return collectionInterval;
    }

    public void setCollectionInterval(Duration collectionInterval) {
        this.collectionInterval = collectionInterval;
    }

    public Set<String> getEnabledCategories() {
        return enabledCategories;
    }

    public void setEnabledCategories(Set<String> enabledCategories) {
        this.enabledCategories = enabledCategories;
    }

    public Map<String, String> getCustomTags() {
        return customTags;
    }

    public void setCustomTags(Map<String, String> customTags) {
        this.customTags = customTags;
    }

    public Sampling getSampling() {
        return sampling;
    }

    public void setSampling(Sampling sampling) {
        this.sampling = sampling;
    }

    public Performance getPerformance() {
        return performance;
    }

    public void setPerformance(Performance performance) {
        this.performance = performance;
    }

    public Thresholds getThresholds() {
        return thresholds;
    }

    public void setThresholds(Thresholds thresholds) {
        this.thresholds = thresholds;
    }
}