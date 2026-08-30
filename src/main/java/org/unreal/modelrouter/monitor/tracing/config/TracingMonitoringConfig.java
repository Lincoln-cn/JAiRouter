package org.unreal.modelrouter.monitor.tracing.config;

import lombok.Data;

import java.time.Duration;

/**
 * 监控配置
 */
@Data
public class TracingMonitoringConfig {
    private boolean selfMonitoring = true;
    private MetricsConfig metrics = new MetricsConfig();
    private HealthConfig health = new HealthConfig();
    private AlertsConfig alerts = new AlertsConfig();

    @Data
    public static class MetricsConfig {
        private boolean enabled = true;
        private String prefix = "jairouter.tracing";
        private TracesConfig traces = new TracesConfig();
        private ExporterMetricsConfig exporter = new ExporterMetricsConfig();

        @Data
        public static class TracesConfig {
            private boolean enabled = true;
            private double[] histogramBuckets = {0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0};
        }

        @Data
        public static class ExporterMetricsConfig {
            private boolean enabled = true;
            private boolean successRate = true;
            private boolean latency = true;
            private boolean queueSize = true;
        }
    }

    @Data
    public static class HealthConfig {
        private boolean enabled = true;
        private Duration checkInterval = Duration.ofSeconds(30);
        private int failureThreshold = 3;
        private int recoveryThreshold = 2;
    }

    @Data
    public static class AlertsConfig {
        private boolean enabled = true;
        private ThresholdsConfig thresholds = new ThresholdsConfig();

        @Data
        public static class ThresholdsConfig {
            private double exportFailureRate = 0.1;
            private long exportLatencyP99 = 5000;
            private double memoryUsage = 0.8;
            private double queueSize = 0.9;
        }
    }
}
