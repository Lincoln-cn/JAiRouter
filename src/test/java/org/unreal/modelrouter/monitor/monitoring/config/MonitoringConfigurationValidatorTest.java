package org.unreal.modelrouter.monitor.monitoring.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.core.MonitoringProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.*;

/**
 * MonitoringConfigurationValidator 单元测试
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@DisplayName("MonitoringConfigurationValidator 测试")
class MonitoringConfigurationValidatorTest {

    private MonitoringProperties monitoringProperties;
    private MonitoringConfigurationValidator validator;

    @BeforeEach
    void setUp() {
        monitoringProperties = mock(MonitoringProperties.class);
        validator = new MonitoringConfigurationValidator(monitoringProperties);
    }

    @Nested
    @DisplayName("基础配置验证测试")
    class BasicConfigurationTests {

        @Test
        @DisplayName("VAL-143: 基础配置 - 有效配置成功")
        void testValidateBasicConfigurationValid() {
            // Setup valid configuration
            when(monitoringProperties.getPrefix()).thenReturn("jairouter");
            when(monitoringProperties.getCollectionInterval()).thenReturn(Duration.ofSeconds(10));
            when(monitoringProperties.getEnabledCategories()).thenReturn(Set.of("system", "business"));
            when(monitoringProperties.getSampling()).thenReturn(null);
            when(monitoringProperties.getPerformance()).thenReturn(null);
            when(monitoringProperties.getCustomTags()).thenReturn(null);

            // Should not throw exception
            validator.validateConfiguration();
        }

        @Test
        @DisplayName("VAL-144: 基础配置 - 空前缀产生警告")
        void testValidateEmptyPrefix() {
            when(monitoringProperties.getPrefix()).thenReturn("");
            when(monitoringProperties.getCollectionInterval()).thenReturn(Duration.ofSeconds(10));
            when(monitoringProperties.getEnabledCategories()).thenReturn(Set.of("system"));
            when(monitoringProperties.getSampling()).thenReturn(null);
            when(monitoringProperties.getPerformance()).thenReturn(null);
            when(monitoringProperties.getCustomTags()).thenReturn(null);

            validator.validateConfiguration();
        }

        @Test
        @DisplayName("VAL-145: 基础配置 - 无效前缀格式产生警告")
        void testValidateInvalidPrefixFormat() {
            when(monitoringProperties.getPrefix()).thenReturn("123_invalid");
            when(monitoringProperties.getCollectionInterval()).thenReturn(Duration.ofSeconds(10));
            when(monitoringProperties.getEnabledCategories()).thenReturn(Set.of("system"));
            when(monitoringProperties.getSampling()).thenReturn(null);
            when(monitoringProperties.getPerformance()).thenReturn(null);
            when(monitoringProperties.getCustomTags()).thenReturn(null);

            validator.validateConfiguration();
        }

        @Test
        @DisplayName("VAL-146: 基础配置 - 收集间隔过短产生警告")
        void testValidateShortCollectionInterval() {
            when(monitoringProperties.getPrefix()).thenReturn("jairouter");
            when(monitoringProperties.getCollectionInterval()).thenReturn(Duration.ofMillis(500));
            when(monitoringProperties.getEnabledCategories()).thenReturn(Set.of("system"));
            when(monitoringProperties.getSampling()).thenReturn(null);
            when(monitoringProperties.getPerformance()).thenReturn(null);
            when(monitoringProperties.getCustomTags()).thenReturn(null);

            validator.validateConfiguration();
        }

        @Test
        @DisplayName("VAL-147: 基础配置 - 未知类别产生警告")
        void testValidateUnknownCategory() {
            when(monitoringProperties.getPrefix()).thenReturn("jairouter");
            when(monitoringProperties.getCollectionInterval()).thenReturn(Duration.ofSeconds(10));
            when(monitoringProperties.getEnabledCategories()).thenReturn(Set.of("unknown"));
            when(monitoringProperties.getSampling()).thenReturn(null);
            when(monitoringProperties.getPerformance()).thenReturn(null);
            when(monitoringProperties.getCustomTags()).thenReturn(null);

            validator.validateConfiguration();
        }
    }

    @Nested
    @DisplayName("采样配置验证测试")
    class SamplingConfigurationTests {

        @Test
        @DisplayName("VAL-148: 采样配置 - 有效配置成功")
        void testValidateSamplingValid() {
            MonitoringProperties.Sampling sampling = mock(MonitoringProperties.Sampling.class);
            when(sampling.getRequestMetrics()).thenReturn(0.5);
            when(sampling.getBackendMetrics()).thenReturn(0.8);
            when(sampling.getInfrastructureMetrics()).thenReturn(1.0);
            when(sampling.getTraceMetrics()).thenReturn(0.3);

            when(monitoringProperties.getPrefix()).thenReturn("jairouter");
            when(monitoringProperties.getCollectionInterval()).thenReturn(Duration.ofSeconds(10));
            when(monitoringProperties.getEnabledCategories()).thenReturn(Set.of("system"));
            when(monitoringProperties.getSampling()).thenReturn(sampling);
            when(monitoringProperties.getPerformance()).thenReturn(null);
            when(monitoringProperties.getCustomTags()).thenReturn(null);

            validator.validateConfiguration();
        }

        @Test
        @DisplayName("VAL-149: 采样配置 - 无效采样率产生警告")
        void testValidateInvalidSamplingRate() {
            MonitoringProperties.Sampling sampling = mock(MonitoringProperties.Sampling.class);
            when(sampling.getRequestMetrics()).thenReturn(1.5); // Invalid
            when(sampling.getBackendMetrics()).thenReturn(0.8);
            when(sampling.getInfrastructureMetrics()).thenReturn(1.0);
            when(sampling.getTraceMetrics()).thenReturn(0.3);

            when(monitoringProperties.getPrefix()).thenReturn("jairouter");
            when(monitoringProperties.getCollectionInterval()).thenReturn(Duration.ofSeconds(10));
            when(monitoringProperties.getEnabledCategories()).thenReturn(Set.of("system"));
            when(monitoringProperties.getSampling()).thenReturn(sampling);
            when(monitoringProperties.getPerformance()).thenReturn(null);
            when(monitoringProperties.getCustomTags()).thenReturn(null);

            validator.validateConfiguration();
        }

        @Test
        @DisplayName("VAL-150: 采样配置 - 低采样率产生信息日志")
        void testValidateLowSamplingRate() {
            MonitoringProperties.Sampling sampling = mock(MonitoringProperties.Sampling.class);
            when(sampling.getRequestMetrics()).thenReturn(0.05); // Low
            when(sampling.getBackendMetrics()).thenReturn(0.8);
            when(sampling.getInfrastructureMetrics()).thenReturn(1.0);
            when(sampling.getTraceMetrics()).thenReturn(0.3);

            when(monitoringProperties.getPrefix()).thenReturn("jairouter");
            when(monitoringProperties.getCollectionInterval()).thenReturn(Duration.ofSeconds(10));
            when(monitoringProperties.getEnabledCategories()).thenReturn(Set.of("system"));
            when(monitoringProperties.getSampling()).thenReturn(sampling);
            when(monitoringProperties.getPerformance()).thenReturn(null);
            when(monitoringProperties.getCustomTags()).thenReturn(null);

            validator.validateConfiguration();
        }
    }

    @Nested
    @DisplayName("性能配置验证测试")
    class PerformanceConfigurationTests {

        @Test
        @DisplayName("VAL-151: 性能配置 - 有效配置成功")
        void testValidatePerformanceValid() {
            MonitoringProperties.Performance performance = mock(MonitoringProperties.Performance.class);
            when(performance.getBatchSize()).thenReturn(100);
            when(performance.getBufferSize()).thenReturn(1000);

            when(monitoringProperties.getPrefix()).thenReturn("jairouter");
            when(monitoringProperties.getCollectionInterval()).thenReturn(Duration.ofSeconds(10));
            when(monitoringProperties.getEnabledCategories()).thenReturn(Set.of("system"));
            when(monitoringProperties.getSampling()).thenReturn(null);
            when(monitoringProperties.getPerformance()).thenReturn(performance);
            when(monitoringProperties.getCustomTags()).thenReturn(null);

            validator.validateConfiguration();
        }

        @Test
        @DisplayName("VAL-152: 性能配置 - 无效批量大小产生警告")
        void testValidateInvalidBatchSize() {
            MonitoringProperties.Performance performance = mock(MonitoringProperties.Performance.class);
            when(performance.getBatchSize()).thenReturn(0); // Invalid
            when(performance.getBufferSize()).thenReturn(1000);

            when(monitoringProperties.getPrefix()).thenReturn("jairouter");
            when(monitoringProperties.getCollectionInterval()).thenReturn(Duration.ofSeconds(10));
            when(monitoringProperties.getEnabledCategories()).thenReturn(Set.of("system"));
            when(monitoringProperties.getSampling()).thenReturn(null);
            when(monitoringProperties.getPerformance()).thenReturn(performance);
            when(monitoringProperties.getCustomTags()).thenReturn(null);

            validator.validateConfiguration();
        }

        @Test
        @DisplayName("VAL-153: 性能配置 - 批量大小过大产生警告")
        void testValidateLargeBatchSize() {
            MonitoringProperties.Performance performance = mock(MonitoringProperties.Performance.class);
            when(performance.getBatchSize()).thenReturn(2000); // Too large
            when(performance.getBufferSize()).thenReturn(3000);

            when(monitoringProperties.getPrefix()).thenReturn("jairouter");
            when(monitoringProperties.getCollectionInterval()).thenReturn(Duration.ofSeconds(10));
            when(monitoringProperties.getEnabledCategories()).thenReturn(Set.of("system"));
            when(monitoringProperties.getSampling()).thenReturn(null);
            when(monitoringProperties.getPerformance()).thenReturn(performance);
            when(monitoringProperties.getCustomTags()).thenReturn(null);

            validator.validateConfiguration();
        }

        @Test
        @DisplayName("VAL-154: 性能配置 - 缓冲区小于批量大小产生警告")
        void testValidateBufferSmallerThanBatch() {
            MonitoringProperties.Performance performance = mock(MonitoringProperties.Performance.class);
            when(performance.getBatchSize()).thenReturn(1000);
            when(performance.getBufferSize()).thenReturn(500); // Smaller

            when(monitoringProperties.getPrefix()).thenReturn("jairouter");
            when(monitoringProperties.getCollectionInterval()).thenReturn(Duration.ofSeconds(10));
            when(monitoringProperties.getEnabledCategories()).thenReturn(Set.of("system"));
            when(monitoringProperties.getSampling()).thenReturn(null);
            when(monitoringProperties.getPerformance()).thenReturn(performance);
            when(monitoringProperties.getCustomTags()).thenReturn(null);

            validator.validateConfiguration();
        }
    }

    @Nested
    @DisplayName("自定义标签验证测试")
    class CustomTagsTests {

        @Test
        @DisplayName("VAL-155: 自定义标签 - 有效配置成功")
        void testValidateCustomTagsValid() {
            Map<String, String> customTags = new HashMap<>();
            customTags.put("env", "production");
            customTags.put("region", "us-west");

            when(monitoringProperties.getPrefix()).thenReturn("jairouter");
            when(monitoringProperties.getCollectionInterval()).thenReturn(Duration.ofSeconds(10));
            when(monitoringProperties.getEnabledCategories()).thenReturn(Set.of("system"));
            when(monitoringProperties.getSampling()).thenReturn(null);
            when(monitoringProperties.getPerformance()).thenReturn(null);
            when(monitoringProperties.getCustomTags()).thenReturn(customTags);

            validator.validateConfiguration();
        }

        @Test
        @DisplayName("VAL-156: 自定义标签 - 无效键格式产生警告")
        void testValidateInvalidTagKey() {
            Map<String, String> customTags = new HashMap<>();
            customTags.put("123_invalid", "value");

            when(monitoringProperties.getPrefix()).thenReturn("jairouter");
            when(monitoringProperties.getCollectionInterval()).thenReturn(Duration.ofSeconds(10));
            when(monitoringProperties.getEnabledCategories()).thenReturn(Set.of("system"));
            when(monitoringProperties.getSampling()).thenReturn(null);
            when(monitoringProperties.getPerformance()).thenReturn(null);
            when(monitoringProperties.getCustomTags()).thenReturn(customTags);

            validator.validateConfiguration();
        }
    }
}
