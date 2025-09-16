package org.unreal.modelrouter.tracing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.*;

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
    private PerformanceConfig performance = new PerformanceConfig();
    
    /**
     * 组件特定配置
     */
    private ComponentsConfig components = new ComponentsConfig();
    
    /**
     * 安全配置
     */
    private SecurityConfig security = new SecurityConfig();
    
    /**
     * 监控配置
     */
    private MonitoringConfig monitoring = new MonitoringConfig();
    
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
    
    /**
     * 性能优化配置
     */
    @Data
    public static class PerformanceConfig {
        /**
         * 异步处理
         */
        private boolean asyncProcessing = true;
        
        /**
         * 线程池配置
         */
        private ThreadPoolConfig threadPool = new ThreadPoolConfig();
        
        /**
         * 缓冲区配置
         */
        private BufferConfig buffer = new BufferConfig();
        
        /**
         * 内存管理
         */
        private MemoryConfig memory = new MemoryConfig();
        
        /**
         * 批处理配置
         */
        private BatchConfig batch = new BatchConfig();
        
        @Data
        public static class ThreadPoolConfig {
            private int coreSize = 2;
            private int maxSize = 8;
            private int queueCapacity = 1000;
            private Duration keepAlive = Duration.ofSeconds(60);
            private String threadNamePrefix = "tracing-";
        }
        
        @Data
        public static class BufferConfig {
            private int size = 1024;
            private Duration flushInterval = Duration.ofSeconds(5);
            private Duration maxWaitTime = Duration.ofSeconds(30);
        }
        
        @Data
        public static class MemoryConfig {
            private int maxSpansInMemory = 10000;
            private int memoryLimitMb = 100;
            private Duration gcInterval = Duration.ofSeconds(60);
        }
        
        @Data
        public static class BatchConfig {
            private int size = 100;
            private Duration timeout = Duration.ofSeconds(5);
            private int maxConcurrentBatches = 3;
        }
    }
    
    /**
     * 组件特定配置
     */
    @Data
    public static class ComponentsConfig {
        private HttpConfig http = new HttpConfig();
        private DatabaseConfig database = new DatabaseConfig();
        private CacheConfig cache = new CacheConfig();
        private MessagingConfig messaging = new MessagingConfig();
        private LoadBalancerConfig loadBalancer = new LoadBalancerConfig();
        private RateLimiterConfig rateLimiter = new RateLimiterConfig();
        private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
        
        @Data
        public static class HttpConfig {
            private boolean enabled = true;
            private boolean captureHeaders = true;
            private boolean captureBody = false;
            private int maxBodySize = 1024;
            private List<String> excludedPaths = new ArrayList<>();
        }
        
        @Data
        public static class DatabaseConfig {
            private boolean enabled = false;
            private boolean captureSql = false;
            private int maxSqlLength = 1000;
        }
        
        @Data
        public static class CacheConfig {
            private boolean enabled = true;
            private boolean captureKeys = false;
            private boolean captureValues = false;
        }
        
        @Data
        public static class MessagingConfig {
            private boolean enabled = false;
            private boolean captureHeaders = true;
            private boolean captureBody = false;
        }
        
        @Data
        public static class LoadBalancerConfig {
            private boolean enabled = true;
            private boolean captureStrategy = true;
            private boolean captureCandidates = true;
            private boolean captureSelection = true;
            private boolean captureStatistics = true;
        }
        
        @Data
        public static class RateLimiterConfig {
            private boolean enabled = true;
            private boolean captureAlgorithm = true;
            private boolean captureQuota = true;
            private boolean captureDecision = true;
            private boolean captureStatistics = true;
        }
        
        @Data
        public static class CircuitBreakerConfig {
            private boolean enabled = true;
            private boolean captureState = true;
            private boolean captureStateChanges = true;
            private boolean captureStatistics = true;
            private boolean captureFailureRate = true;
        }
    }
    
    /**
     * 安全配置
     */
    @Data
    public static class SecurityConfig {
        private SanitizationConfig sanitization = new SanitizationConfig();
        private AccessControlConfig accessControl = new AccessControlConfig();
        private EncryptionConfig encryption = new EncryptionConfig();
        private AuditConfig audit = new AuditConfig();
        
        @Data
        public static class SanitizationConfig {
            private boolean enabled = true;
            private boolean inheritGlobalRules = true;
            private List<String> additionalPatterns = new ArrayList<>();
            private List<String> sensitiveAttributes = new ArrayList<>();
            private boolean encryptSensitiveData = false;
            
            /**
             * 追踪特定的脱敏规则
             */
            private TracingSanitizationRules tracingRules = new TracingSanitizationRules();
            
            @Data
            public static class TracingSanitizationRules {
                private boolean sanitizeSpanAttributes = true;
                private boolean sanitizeEventAttributes = true;
                private boolean sanitizeLogData = true;
                private List<String> exemptedAttributes = new ArrayList<>();
                private String defaultMaskCharacter = "*";
            }
        }
        
        @Data
        public static class AccessControlConfig {
            private boolean restrictTraceAccess = true;
            private List<String> allowedRoles = new ArrayList<>();
            private boolean enableRoleBasedFiltering = true;
            private boolean auditAccessAttempts = true;
            private int maxAccessHistoryPerUser = 100;
            
            /**
             * 字段级别访问控制
             */
            private FieldAccessControl fieldAccess = new FieldAccessControl();
            
            @Data
            public static class FieldAccessControl {
                private boolean enabled = true;
                private Map<String, List<String>> fieldRoleMapping = new HashMap<>();
                private List<String> adminOnlyFields = new ArrayList<>();
            }
        }
        
        @Data
        public static class EncryptionConfig {
            private boolean enabled = false;
            private String algorithm = "AES";
            private int keySize = 256;
            private boolean encryptSensitiveSpans = true;
            private boolean encryptSensitiveLogs = true;
            
            /**
             * 密钥管理配置
             */
            private KeyManagement keyManagement = new KeyManagement();
            
            /**
             * 数据保留策略
             */
            private DataRetention dataRetention = new DataRetention();
            
            @Data
            public static class KeyManagement {
                private boolean autoRotation = true;
                private Duration rotationInterval = Duration.ofDays(1);
                private String keyStorePath = "./keys";
                private boolean useHardwareSecurityModule = false;
            }
            
            @Data
            public static class DataRetention {
                private Duration defaultRetention = Duration.ofDays(30);
                private Duration sensitiveDataRetention = Duration.ofDays(7);
                private Duration errorDataRetention = Duration.ofDays(90);
                private Duration performanceDataRetention = Duration.ofDays(60);
                private boolean autoCleanup = true;
                private Duration cleanupInterval = Duration.ofHours(1);
            }
        }
        
        @Data
        public static class AuditConfig {
            private boolean enabled = true;
            private boolean auditDataAccess = true;
            private boolean auditSanitization = true;
            private boolean auditEncryption = true;
            private boolean auditConfigChanges = true;
            private String auditLogLevel = "INFO";
            
            /**
             * 审计日志存储配置
             */
            private AuditStorage storage = new AuditStorage();
            
            @Data
            public static class AuditStorage {
                private boolean separateAuditLog = true;
                private String auditLogFile = "audit.log";
                private boolean encryptAuditLog = false;
                private Duration auditLogRetention = Duration.ofDays(365);
            }
        }
    }
    
    /**
     * 监控配置
     */
    @Data
    public static class MonitoringConfig {
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
}