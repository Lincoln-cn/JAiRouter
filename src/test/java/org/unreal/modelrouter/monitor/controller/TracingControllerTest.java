package org.unreal.modelrouter.monitor.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.actuate.health.Health;
import org.unreal.modelrouter.monitor.tracing.TracingService;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceMonitor;
import org.unreal.modelrouter.monitor.tracing.sampler.SamplingStrategyManager;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TracingController 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracingControllerTest {

    @Mock
    private TracingService tracingService;

    @Mock
    private TracingConfiguration tracingConfiguration;

    @Mock
    private TracingPerformanceMonitor performanceMonitor;

    @Mock
    private SamplingStrategyManager samplingStrategyManager;

    @InjectMocks
    private TracingController controller;

    private void setupDefaultMocks() {
        lenient().when(tracingConfiguration.isEnabled()).thenReturn(true);
        lenient().when(tracingConfiguration.getServiceName()).thenReturn("test-service");
        lenient().when(tracingConfiguration.getServiceVersion()).thenReturn("1.0.0");
        lenient().when(tracingConfiguration.getServiceNamespace()).thenReturn("default");

        TracingConfiguration.OpenTelemetryConfig otelConfig = mock(TracingConfiguration.OpenTelemetryConfig.class);
        TracingConfiguration.OpenTelemetryConfig.SdkConfig sdkConfig = mock(TracingConfiguration.OpenTelemetryConfig.SdkConfig.class);
        when(otelConfig.isEnabled()).thenReturn(true);
        when(sdkConfig.isDisabled()).thenReturn(false);
        lenient().when(tracingConfiguration.getOpenTelemetry()).thenReturn(otelConfig);
        lenient().when(otelConfig.getSdk()).thenReturn(sdkConfig);

        TracingConfiguration.SamplingConfig samplingConfig = mock(TracingConfiguration.SamplingConfig.class);
        TracingConfiguration.SamplingConfig.AdaptiveConfig adaptiveConfig = mock(TracingConfiguration.SamplingConfig.AdaptiveConfig.class);
        when(samplingConfig.getRatio()).thenReturn(1.0);
        when(adaptiveConfig.isEnabled()).thenReturn(false);
        when(samplingConfig.getAdaptive()).thenReturn(adaptiveConfig);
        when(samplingConfig.getServiceRatios()).thenReturn(new HashMap<>());
        when(samplingConfig.getAlwaysSample()).thenReturn(java.util.List.of());
        when(samplingConfig.getNeverSample()).thenReturn(java.util.List.of());
        lenient().when(tracingConfiguration.getSampling()).thenReturn(samplingConfig);

        TracingConfiguration.ExporterConfig exporterConfig = mock(TracingConfiguration.ExporterConfig.class);
        TracingConfiguration.ExporterConfig.LoggingExporterConfig loggingExporterConfig = mock(TracingConfiguration.ExporterConfig.LoggingExporterConfig.class);
        when(exporterConfig.getType()).thenReturn("otlp");
        when(loggingExporterConfig.isEnabled()).thenReturn(true);
        lenient().when(tracingConfiguration.getExporter()).thenReturn(exporterConfig);
        lenient().when(exporterConfig.getLogging()).thenReturn(loggingExporterConfig);

        TracingConfiguration.PerformanceConfig performanceConfig = mock(TracingConfiguration.PerformanceConfig.class);
        TracingConfiguration.PerformanceConfig.ThreadPoolConfig threadPoolConfig = mock(TracingConfiguration.PerformanceConfig.ThreadPoolConfig.class);
        TracingConfiguration.PerformanceConfig.BufferConfig bufferConfig = mock(TracingConfiguration.PerformanceConfig.BufferConfig.class);
        TracingConfiguration.PerformanceConfig.MemoryConfig memoryConfig = mock(TracingConfiguration.PerformanceConfig.MemoryConfig.class);
        when(performanceConfig.isAsyncProcessing()).thenReturn(true);
        when(threadPoolConfig.getCoreSize()).thenReturn(4);
        when(bufferConfig.getSize()).thenReturn(1000);
        when(memoryConfig.getMemoryLimitMb()).thenReturn(256);
        lenient().when(tracingConfiguration.getPerformance()).thenReturn(performanceConfig);
        lenient().when(performanceConfig.getThreadPool()).thenReturn(threadPoolConfig);
        lenient().when(performanceConfig.getBuffer()).thenReturn(bufferConfig);
        lenient().when(performanceConfig.getMemory()).thenReturn(memoryConfig);
    }

    @Nested
    @DisplayName("获取追踪系统状态测试")
    class GetTracingStatusTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            setupDefaultMocks();

            StepVerifier.create(controller.getTracingStatus())
                    .assertNext(response -> {
                        assert response.getStatusCode().value() == 200;
                        assert response.getBody() != null;
                        assert Boolean.TRUE.equals(response.getBody().get("enabled"));
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取追踪系统健康状态测试")
    class GetTracingHealthTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            Health health = Health.up().build();
            when(performanceMonitor.health()).thenReturn(health);

            StepVerifier.create(controller.getTracingHealth())
                    .assertNext(response -> {
                        assert response.getStatusCode().value() == 200;
                        assert response.getBody() != null;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取追踪配置测试")
    class GetTracingConfigurationTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            setupDefaultMocks();

            StepVerifier.create(controller.getTracingConfiguration())
                    .assertNext(response -> {
                        assert response.getStatusCode().value() == 200;
                        assert response.getBody() != null;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("更新追踪配置测试")
    class UpdateTracingConfigurationTests {

        @Test
        @DisplayName("更新成功")
        void updateSuccess() {
            setupDefaultMocks();
            TracingConfiguration newConfig = mock(TracingConfiguration.class);
            TracingConfiguration.SamplingConfig samplingConfig = mock(TracingConfiguration.SamplingConfig.class);
            when(newConfig.getSampling()).thenReturn(samplingConfig);
            when(newConfig.getLogging()).thenReturn(null);
            when(newConfig.getPerformance()).thenReturn(null);
            when(newConfig.getExporter()).thenReturn(null);
            when(newConfig.getMonitoring()).thenReturn(null);

            StepVerifier.create(controller.updateTracingConfiguration(newConfig))
                    .assertNext(response -> {
                        assert response.getStatusCode().value() == 200;
                        assert response.getBody() != null;
                        assert "追踪配置更新成功".equals(response.getBody().get("message"));
                    })
                    .verifyComplete();

            verify(samplingStrategyManager).updateSamplingConfiguration(any());
        }
    }

    @Nested
    @DisplayName("刷新采样策略测试")
    class RefreshSamplingStrategyTests {

        @Test
        @DisplayName("刷新成功")
        void refreshSuccess() {
            setupDefaultMocks();

            StepVerifier.create(controller.refreshSamplingStrategy())
                    .assertNext(response -> {
                        assert response.getStatusCode().value() == 200;
                        assert "采样策略已刷新".equals(response.getBody().get("message"));
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("获取追踪统计信息测试")
    class GetTracingStatsTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            setupDefaultMocks();
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalTraces", 100L);
            when(tracingService.getPerformanceStats()).thenReturn(Mono.just(stats));

            StepVerifier.create(controller.getTracingStats())
                    .assertNext(response -> {
                        assert response.getStatusCode().value() == 200;
                        assert response.getBody() != null;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("启用/禁用追踪测试")
    class EnableDisableTracingTests {

        @Test
        @DisplayName("启用追踪成功")
        void enableSuccess() {
            StepVerifier.create(controller.enableTracing())
                    .assertNext(response -> {
                        assert response.getStatusCode().value() == 200;
                        assert "enabled".equals(response.getBody().get("status"));
                    })
                    .verifyComplete();

            verify(tracingConfiguration).setEnabled(true);
        }

        @Test
        @DisplayName("禁用追踪成功")
        void disableSuccess() {
            StepVerifier.create(controller.disableTracing())
                    .assertNext(response -> {
                        assert response.getStatusCode().value() == 200;
                        assert "disabled".equals(response.getBody().get("status"));
                    })
                    .verifyComplete();

            verify(tracingConfiguration).setEnabled(false);
        }
    }

    @Nested
    @DisplayName("导出追踪数据测试")
    class ExportTracingDataTests {

        @Test
        @DisplayName("导出成功")
        void exportSuccess() {
            StepVerifier.create(controller.exportTracingData(null, null, "json"))
                    .assertNext(response -> {
                        assert response.getStatusCode().value() == 200;
                        assert response.getBody() != null;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("清理追踪缓存测试")
    class ClearTracingCacheTests {

        @Test
        @DisplayName("清理成功")
        void clearSuccess() {
            when(tracingService.triggerPerformanceOptimization()).thenReturn(Mono.empty());

            StepVerifier.create(controller.clearTracingCache())
                    .assertNext(response -> {
                        assert response.getStatusCode().value() == 200;
                        assert "追踪缓存清理已触发".equals(response.getBody().get("message"));
                    })
                    .verifyComplete();
        }
    }
}
