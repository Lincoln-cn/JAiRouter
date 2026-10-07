package org.unreal.modelrouter.monitor.tracing.config;

import lombok.Data;

import java.time.Duration;

/**
 * 监控配置。
 *
 * <p>子段与消费方（issue #224 逐项核对过，方法 = 按 lombok 生成的 getter 名在 {@code src/main/java} 全仓计消费点）：</p>
 *
 * <ul>
 *   <li>{@code metrics.traces.histogram-buckets} 由 {@code TracingPerformanceMonitor} 读，用于注册
 *       {@code tracing.processing.latency} 的直方图桶（单位是**秒**，该指标记录的是毫秒，装配时换算）；</li>
 *   <li>{@code health.enabled} / {@code health.check-interval} / {@code health.failure-threshold} /
 *       {@code health.recovery-threshold} 由 {@code ExporterHealthChecker} 读（去抖动语义见 issue #224 批 B2）；</li>
 *   <li>{@code alerts.thresholds.memory-usage} 与 {@code alerts.thresholds.export-latency-p99} 由
 *       {@code TracingPerformanceMonitor} 读，替换掉原先写死的 0.8 与 5000。</li>
 * </ul>
 *
 * <p>原先还有 {@code self-monitoring}、{@code metrics.enabled}、{@code metrics.prefix}、
 * {@code metrics.traces.enabled}、{@code metrics.exporter.*}（4 个）、{@code alerts.enabled}、
 * {@code alerts.thresholds.export-failure-rate}、{@code alerts.thresholds.queue-size}
 * —— 这些字段**全仓没有任何读者**（连 {@code /actuator/info} 都不回显 {@code monitoring} 段），
 * 键却照常绑定、文档也照常宣传，属于「绑定得上却永远不生效」的骗人配置，已随 issue #224 删除 ——
 * 需要真实能力时，**先实现消费方，再按字段实名加回来**。</p>
 */
@Data
public class TracingMonitoringConfig {
    private MetricsConfig metrics = new MetricsConfig();
    private HealthConfig health = new HealthConfig();
    private AlertsConfig alerts = new AlertsConfig();

    @Data
    public static class MetricsConfig {
        private TracesConfig traces = new TracesConfig();

        @Data
        public static class TracesConfig {
            /**
             * 处理延迟直方图的桶边界，单位**秒**（与 {@code TracingPerformanceMonitor} 默认的
             * 0.1s–30s 一致）。该指标本身以毫秒记录，装配时由消费方换算。
             */
            private double[] histogramBuckets = {0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0};
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
        private ThresholdsConfig thresholds = new ThresholdsConfig();

        @Data
        public static class ThresholdsConfig {
            /** 堆使用率超过该比例即判为内存瓶颈（{@code TracingPerformanceMonitor#detectBottlenecks}） */
            private double memoryUsage = 0.8;

            /** {@code trace.export} 操作的 P99 危险阈值，单位毫秒（{@code TracingPerformanceMonitor} 默认阈值表） */
            private long exportLatencyP99 = 5000;
        }
    }
}
