package org.unreal.modelrouter.monitor.tracing.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TracingConfiguration 内部类单元测试
 */
@DisplayName("TracingConfiguration 测试")
class TracingConfigurationTest {

    @Nested
    @DisplayName("主配置类测试")
    class MainConfigTest {

        @Test
        @DisplayName("测试默认值")
        void testDefaultValues() {
            TracingConfiguration config = new TracingConfiguration();

            assertTrue(config.isEnabled());
            assertEquals("jairouter", config.getServiceName());
            assertEquals("1.0.0", config.getServiceVersion());
            assertEquals("production", config.getServiceNamespace());
            assertNotNull(config.getOpenTelemetry());
            assertNotNull(config.getSampling());
            assertNotNull(config.getLogging());
            assertNotNull(config.getExporter());
            assertNotNull(config.getPerformance());
            assertNotNull(config.getComponents());
            assertNotNull(config.getSecurity());
            assertNotNull(config.getMonitoring());
        }

        @Test
        @DisplayName("测试Setter和Getter")
        void testSetterGetter() {
            TracingConfiguration config = new TracingConfiguration();

            config.setEnabled(false);
            config.setServiceName("test-service");
            config.setServiceVersion("2.0.0");
            config.setServiceNamespace("test-ns");

            assertFalse(config.isEnabled());
            assertEquals("test-service", config.getServiceName());
            assertEquals("2.0.0", config.getServiceVersion());
            assertEquals("test-ns", config.getServiceNamespace());
        }
    }

    @Nested
    @DisplayName("OpenTelemetry配置测试")
    class OpenTelemetryConfigTest {

        @Test
        @DisplayName("测试默认值")
        void testDefaultValues() {
            OpenTelemetryConfig config = new OpenTelemetryConfig();

            assertTrue(config.isEnabled());
            assertNotNull(config.getResource());
            assertNotNull(config.getSdk());
        }

        @Test
        @DisplayName("测试SDK配置")
        void testSdkConfig() {
            OpenTelemetryConfig.SdkConfig sdkConfig = new OpenTelemetryConfig.SdkConfig();
            assertFalse(sdkConfig.isDisabled());
            assertNotNull(sdkConfig.getTrace());

            OpenTelemetryConfig.SdkConfig.TraceConfig traceConfig = sdkConfig.getTrace();
            assertNotNull(traceConfig.getProcessors());

            OpenTelemetryConfig.SdkConfig.TraceConfig.ProcessorsConfig processorsConfig = traceConfig.getProcessors();
            assertNotNull(processorsConfig.getBatch());

            OpenTelemetryConfig.SdkConfig.TraceConfig.ProcessorsConfig.BatchConfig batchConfig = processorsConfig.getBatch();
            assertEquals(Duration.ofSeconds(5), batchConfig.getScheduleDelay());
            assertEquals(2048, batchConfig.getMaxQueueSize());
            assertEquals(512, batchConfig.getMaxExportBatchSize());
            assertEquals(Duration.ofSeconds(30), batchConfig.getExportTimeout());
        }

        @Test
        @DisplayName("测试资源属性配置")
        void testResourceConfig() {
            OpenTelemetryConfig.ResourceConfig resourceConfig = new OpenTelemetryConfig.ResourceConfig();
            assertNotNull(resourceConfig.getAttributes());
            assertTrue(resourceConfig.getAttributes().isEmpty());

            resourceConfig.setAttributes(Map.of("service.name", "test"));
            assertEquals("test", resourceConfig.getAttributes().get("service.name"));
        }
    }

    @Nested
    @DisplayName("采样配置测试")
    class SamplingConfigTest {

        @Test
        @DisplayName("测试默认值")
        void testDefaultValues() {
            SamplingConfig config = new SamplingConfig();

            assertEquals(1.0, config.getRatio(), 0.01);
            assertNotNull(config.getServiceRatios());
            assertNotNull(config.getAlwaysSample());
            assertNotNull(config.getNeverSample());
            assertNotNull(config.getRules());
            assertNotNull(config.getAdaptive());
        }

        @Test
        @DisplayName("测试服务类型采样率")
        void testServiceRatios() {
            SamplingConfig config = new SamplingConfig();
            config.setServiceRatios(Map.of(
                    "chat", 0.5,
                    "embedding", 0.8
            ));

            assertEquals(0.5, config.getServiceRatios().get("chat"), 0.01);
            assertEquals(0.8, config.getServiceRatios().get("embedding"), 0.01);
        }

        @Test
        @DisplayName("测试采样规则")
        void testSamplingRules() {
            SamplingConfig.SamplingRule rule = new SamplingConfig.SamplingRule();
            rule.setCondition("duration >= 100");
            rule.setRatio(0.8);

            SamplingConfig config = new SamplingConfig();
            config.setRules(List.of(rule));

            assertEquals(1, config.getRules().size());
            assertEquals("duration >= 100", config.getRules().get(0).getCondition());
            assertEquals(0.8, config.getRules().get(0).getRatio(), 0.01);
        }

        @Test
        @DisplayName("测试自适应采样配置")
        void testAdaptiveConfig() {
            SamplingConfig.AdaptiveConfig adaptive = new SamplingConfig.AdaptiveConfig();

            assertFalse(adaptive.isEnabled());
            assertEquals(1000L, adaptive.getTargetSpansPerSecond());
            assertEquals(0.1, adaptive.getMinRatio(), 0.01);
            assertEquals(1.0, adaptive.getMaxRatio(), 0.01);
            assertEquals(30L, adaptive.getAdjustmentInterval());

            adaptive.setEnabled(true);
            adaptive.setTargetSpansPerSecond(500L);
            adaptive.setMinRatio(0.2);
            adaptive.setMaxRatio(0.9);
            adaptive.setAdjustmentInterval(60L);

            assertTrue(adaptive.isEnabled());
            assertEquals(500L, adaptive.getTargetSpansPerSecond());
            assertEquals(0.2, adaptive.getMinRatio(), 0.01);
            assertEquals(0.9, adaptive.getMaxRatio(), 0.01);
            assertEquals(60L, adaptive.getAdjustmentInterval());
        }
    }

    @Nested
    @DisplayName("日志配置测试")
    class LoggingConfigTest {

        @Test
        @DisplayName("测试默认值")
        void testDefaultValues() {
            LoggingConfig config = new LoggingConfig();

            assertTrue(config.isStructuredLogging());
            assertEquals("json", config.getFormat());
            assertTrue(config.isIncludeTraceId());
            assertTrue(config.isIncludeSpanId());
            assertTrue(config.isSanitizeEnabled());
            assertTrue(config.isCaptureHeaders());
            assertTrue(config.isIncludeStackTrace());
        }

        @Test
        @DisplayName("测试敏感字段配置")
        void testSensitiveFields() {
            LoggingConfig config = new LoggingConfig();
            config.setSensitiveFields(Set.of("password", "token", "secret"));

            assertEquals(3, config.getSensitiveFields().size());
            assertTrue(config.getSensitiveFields().contains("password"));
        }

        @Test
        @DisplayName("测试自定义字段")
        void testCustomFields() {
            LoggingConfig config = new LoggingConfig();
            config.setCustomFields(Map.of("app", "jairouter", "version", "1.0"));

            assertEquals("jairouter", config.getCustomFields().get("app"));
            assertEquals("1.0", config.getCustomFields().get("version"));
        }
    }

    @Nested
    @DisplayName("导出器配置测试")
    class ExporterConfigTest {

        @Test
        @DisplayName("测试默认值")
        void testDefaultValues() {
            ExporterConfig config = new ExporterConfig();

            assertEquals("jaeger", config.getType());
            assertNotNull(config.getJaeger());
            assertNotNull(config.getZipkin());
            assertNotNull(config.getOtlp());
            assertNotNull(config.getLogging());
        }

        @Test
        @DisplayName("测试Jaeger配置")
        void testJaegerConfig() {
            ExporterConfig.JaegerConfig jaeger = new ExporterConfig.JaegerConfig();

            assertEquals("http://localhost:14268/api/traces", jaeger.getEndpoint());
            assertEquals(Duration.ofSeconds(10), jaeger.getTimeout());
            assertNotNull(jaeger.getHeaders());

            jaeger.setEndpoint("http://jaeger:14268/api/traces");
            jaeger.setHeaders(Map.of("X-Custom", "value"));

            assertEquals("http://jaeger:14268/api/traces", jaeger.getEndpoint());
            assertEquals("value", jaeger.getHeaders().get("X-Custom"));
        }

        @Test
        @DisplayName("测试Zipkin配置")
        void testZipkinConfig() {
            ExporterConfig.ZipkinConfig zipkin = new ExporterConfig.ZipkinConfig();

            assertEquals("http://localhost:9411/api/v2/spans", zipkin.getEndpoint());
            assertEquals(Duration.ofSeconds(10), zipkin.getTimeout());

            zipkin.setEndpoint("http://zipkin:9411/api/v2/spans");
            assertEquals("http://zipkin:9411/api/v2/spans", zipkin.getEndpoint());
        }

        @Test
        @DisplayName("测试OTLP配置")
        void testOtlpConfig() {
            ExporterConfig.OtlpConfig otlp = new ExporterConfig.OtlpConfig();

            assertEquals("http://localhost:4318/v1/traces", otlp.getEndpoint());
            assertEquals(Duration.ofSeconds(10), otlp.getTimeout());
            assertEquals("gzip", otlp.getCompression());
            assertNotNull(otlp.getHeaders());
        }
    }

    @Nested
    @DisplayName("性能配置测试")
    class PerformanceConfigTest {

        @Test
        @DisplayName("测试默认值")
        void testDefaultValues() {
            PerformanceConfig config = new PerformanceConfig();

            assertTrue(config.isAsyncProcessing());
            assertNotNull(config.getThreadPool());
            assertNotNull(config.getBuffer());
            assertNotNull(config.getMemory());
            assertNotNull(config.getBatch());
        }

        @Test
        @DisplayName("测试线程池配置")
        void testThreadPoolConfig() {
            PerformanceConfig.ThreadPoolConfig threadPool = new PerformanceConfig.ThreadPoolConfig();

            assertEquals(2, threadPool.getCoreSize());
            assertEquals(8, threadPool.getMaxSize());
            assertEquals(1000, threadPool.getQueueCapacity());
            assertEquals(Duration.ofSeconds(60), threadPool.getKeepAlive());
            assertEquals("tracing-", threadPool.getThreadNamePrefix());
        }

        @Test
        @DisplayName("测试内存配置")
        void testMemoryConfig() {
            PerformanceConfig.MemoryConfig memory = new PerformanceConfig.MemoryConfig();

            assertEquals(10000, memory.getMaxSpansInMemory());
            assertEquals(100, memory.getMemoryLimitMb());
            assertEquals(Duration.ofSeconds(60), memory.getGcInterval());
        }
    }

    @Nested
    @DisplayName("组件配置测试")
    class ComponentsConfigTest {

        @Test
        @DisplayName("测试默认值")
        void testDefaultValues() {
            ComponentsConfig config = new ComponentsConfig();

            assertNotNull(config.getHttp());
            assertNotNull(config.getDatabase());
            assertNotNull(config.getCache());
            assertNotNull(config.getMessaging());
            assertNotNull(config.getLoadBalancer());
            assertNotNull(config.getRateLimiter());
            assertNotNull(config.getCircuitBreaker());
        }

        @Test
        @DisplayName("测试HTTP组件配置")
        void testHttpConfig() {
            ComponentsConfig.HttpConfig http = new ComponentsConfig.HttpConfig();

            assertTrue(http.isEnabled());
            assertTrue(http.isCaptureHeaders());
            assertFalse(http.isCaptureBody());
            assertEquals(1024, http.getMaxBodySize());
        }
    }

    @Nested
    @DisplayName("安全配置测试")
    class SecurityConfigTest {

        @Test
        @DisplayName("测试默认值")
        void testDefaultValues() {
            SecurityConfig config = new SecurityConfig();

            assertNotNull(config.getSanitization());
            assertNotNull(config.getAccessControl());
            assertNotNull(config.getEncryption());
            assertNotNull(config.getAudit());
        }

        @Test
        @DisplayName("测试脱敏配置")
        void testSanitizationConfig() {
            SecurityConfig.SanitizationConfig sanitization = new SecurityConfig.SanitizationConfig();

            assertTrue(sanitization.isEnabled());
            assertTrue(sanitization.isInheritGlobalRules());
            assertNotNull(sanitization.getTracingRules());
        }

        @Test
        @DisplayName("测试访问控制配置")
        void testAccessControlConfig() {
            SecurityConfig.AccessControlConfig accessControl = new SecurityConfig.AccessControlConfig();

            assertTrue(accessControl.isRestrictTraceAccess());
            assertTrue(accessControl.isEnableRoleBasedFiltering());
            assertTrue(accessControl.isAuditAccessAttempts());

            accessControl.setAllowedRoles(List.of("ADMIN", "OPERATOR"));
            assertEquals(2, accessControl.getAllowedRoles().size());
        }

        @Test
        @DisplayName("测试加密配置")
        void testEncryptionConfig() {
            SecurityConfig.EncryptionConfig encryption = new SecurityConfig.EncryptionConfig();

            assertFalse(encryption.isEnabled());
            assertEquals("AES", encryption.getAlgorithm());
            assertEquals(256, encryption.getKeySize());
        }
    }

    @Nested
    @DisplayName("监控配置测试")
    class MonitoringConfigTest {

        @Test
        @DisplayName("测试默认值")
        void testDefaultValues() {
            MonitoringConfig config = new MonitoringConfig();

            assertTrue(config.isSelfMonitoring());
            assertNotNull(config.getMetrics());
            assertNotNull(config.getHealth());
            assertNotNull(config.getAlerts());
        }

        @Test
        @DisplayName("测试指标配置")
        void testMetricsConfig() {
            MonitoringConfig.MetricsConfig metrics = new MonitoringConfig.MetricsConfig();

            assertTrue(metrics.isEnabled());
            assertEquals("jairouter.tracing", metrics.getPrefix());
            assertNotNull(metrics.getTraces());
            assertNotNull(metrics.getExporter());
        }

        @Test
        @DisplayName("测试健康检查配置")
        void testHealthConfig() {
            MonitoringConfig.HealthConfig health = new MonitoringConfig.HealthConfig();

            assertTrue(health.isEnabled());
            assertEquals(Duration.ofSeconds(30), health.getCheckInterval());
            assertEquals(3, health.getFailureThreshold());
            assertEquals(2, health.getRecoveryThreshold());
        }

        @Test
        @DisplayName("测试告警配置")
        void testAlertsConfig() {
            MonitoringConfig.AlertsConfig alerts = new MonitoringConfig.AlertsConfig();

            assertTrue(alerts.isEnabled());
            assertNotNull(alerts.getThresholds());

            MonitoringConfig.AlertsConfig.ThresholdsConfig thresholds = alerts.getThresholds();
            assertEquals(0.1, thresholds.getExportFailureRate(), 0.01);
            assertEquals(5000L, thresholds.getExportLatencyP99());
        }
    }
}
