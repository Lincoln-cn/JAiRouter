package org.unreal.modelrouter.monitor.tracing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 追踪功能配置类
 * 
 * 提供OpenTelemetry追踪功能的完整配置选项，包括：
 * - 基础服务信息配置
 * - OpenTelemetry SDK配置
 * - 采样策略配置
 * - 结构化日志配置
 * - 导出器配置
 * - 性能优化配置
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "jairouter.tracing")
public class TracingConfiguration {
    
    /**
     * 追踪功能总开关
     */
    private boolean enabled = true;
    
    /**
     * 服务名称
     */
    private String serviceName = "jairouter";
    
    /**
     * 服务版本
     */
    private String serviceVersion = "1.0.0";
    
    /**
     * 服务命名空间
     */
    private String serviceNamespace = "production";
    
    /**
     * OpenTelemetry配置
     */
    private OpenTelemetryConfig openTelemetry = new OpenTelemetryConfig();
    
    /**
     * 采样配置
     */
    private SamplingConfig sampling = new SamplingConfig();
    
    /**
     * 日志配置
     */
    private LoggingConfig logging = new LoggingConfig();
    
    /**
     * 导出器配置
     */
    private ExporterConfig exporter = new ExporterConfig();
    
    /**
     * 性能配置
     */
    private TracingPerformanceConfig performance = new TracingPerformanceConfig();

    /**
     * 组件特定配置
     */
    private TracingComponentConfig components = new TracingComponentConfig();

    /**
     * 安全配置
     */
    private TracingSecurityConfig security = new TracingSecurityConfig();

    /**
     * 监控配置
     */
    private TracingMonitoringConfig monitoring = new TracingMonitoringConfig();
    
    /**
     * OpenTelemetry配置
     */
    @Data
    public static class OpenTelemetryConfig {
        private boolean enabled = true;
        private ResourceConfig resource = new ResourceConfig();
        private SdkConfig sdk = new SdkConfig();
        
        @Data
        public static class ResourceConfig {
            private Map<String, String> attributes = new HashMap<>();
        }
        
        @Data
        public static class SdkConfig {
            private boolean disabled = false;
            private TraceConfig trace = new TraceConfig();
            
            @Data
            public static class TraceConfig {
                private ProcessorsConfig processors = new ProcessorsConfig();
                
                @Data
                public static class ProcessorsConfig {
                    private BatchConfig batch = new BatchConfig();
                    
                    @Data
                    public static class BatchConfig {
                        private Duration scheduleDelay = Duration.ofSeconds(5);
                        private int maxQueueSize = 2048;
                        private int maxExportBatchSize = 512;
                        private Duration exportTimeout = Duration.ofSeconds(30);
                    }
                }
            }
        }
    }
    
    /**
     * 采样配置
     */
    @Data
    public static class SamplingConfig {
        /**
         * 全局采样率 (0.0-1.0)
         */
        private double ratio = 1.0;
        
        /**
         * 按服务类型的采样率
         */
        private Map<String, Double> serviceRatios = new HashMap<>();
        
        /**
         * 始终采样的操作
         */
        private List<String> alwaysSample = new ArrayList<>();
        
        /**
         * 从不采样的操作
         */
        private List<String> neverSample = new ArrayList<>();
        
        /**
         * 基于属性的采样规则
         */
        private List<SamplingRule> rules = new ArrayList<>();
        
        /**
         * 自适应采样配置
         */
        private AdaptiveConfig adaptive = new AdaptiveConfig();
        
        @Data
        public static class SamplingRule {
            private String condition;
            private double ratio;
        }
        
        @Data
        public static class AdaptiveConfig {
            /**
             * 是否启用自适应采样
             */
            private boolean enabled = false;
            
            /**
             * 目标每秒Span数量
             */
            private long targetSpansPerSecond = 1000;
            
            /**
             * 最小采样率
             */
            private double minRatio = 0.1;
            
            /**
             * 最大采样率
             */
            private double maxRatio = 1.0;
            
            /**
             * 调整间隔（秒）
             */
            private long adjustmentInterval = 30;
        }
    }
    
    /**
     * 结构化日志配置
     */
    @Data
    public static class LoggingConfig {
        /**
         * 启用结构化日志
         */
        private boolean structuredLogging = true;
        
        /**
         * 日志格式 (json, logfmt)
         */
        private String format = "json";
        
        /**
         * 包含追踪信息
         */
        private boolean includeTraceId = true;
        private boolean includeSpanId = true;
        
        /**
         * 敏感字段脱敏
         */
        private boolean sanitizeEnabled = true;
        private Set<String> sensitiveFields = new HashSet<>();
        
        /**
         * 日志级别映射
         */
        private Map<String, String> levelMapping = new HashMap<>();
        
        /**
         * 自定义字段
         */
        private Map<String, String> customFields = new HashMap<>();
        
        /**
         * 是否捕获HTTP头部
         */
        private boolean captureHeaders = true;
        
        /**
         * 是否在错误日志中包含堆栈跟踪信息
         */
        private boolean includeStackTrace = true;
    }
    
    /**
     * 导出器配置
     */
    @Data
    public static class ExporterConfig {
        /**
         * 导出器类型 (jaeger, zipkin, otlp, logging)
         */
        private String type = "jaeger";
        
        /**
         * Jaeger配置
         */
        private JaegerConfig jaeger = new JaegerConfig();
        
        /**
         * Zipkin配置
         */
        private ZipkinConfig zipkin = new ZipkinConfig();
        
        /**
         * OTLP配置
         */
        private OtlpConfig otlp = new OtlpConfig();
        
        /**
         * 日志导出器配置
         */
        private LoggingExporterConfig logging = new LoggingExporterConfig();
        
        @Data
        public static class JaegerConfig {
            private String endpoint = "http://localhost:14268/api/traces";
            private Duration timeout = Duration.ofSeconds(10);
            private Map<String, String> headers = new HashMap<>();
        }
        
        @Data
        public static class ZipkinConfig {
            private String endpoint = "http://localhost:9411/api/v2/spans";
            private Duration timeout = Duration.ofSeconds(10);
        }
        
        @Data
        public static class OtlpConfig {
            private String endpoint = "http://localhost:4318/v1/traces";
            private Duration timeout = Duration.ofSeconds(10);
            private Map<String, String> headers = new HashMap<>();
            private String compression = "gzip";
        }
        
        @Data
        public static class LoggingExporterConfig {
            private boolean enabled = false;
            private String level = "INFO";
        }
    }
    
}