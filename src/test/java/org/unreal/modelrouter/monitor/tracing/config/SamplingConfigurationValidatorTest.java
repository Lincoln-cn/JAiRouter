package org.unreal.modelrouter.monitor.tracing.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitor.tracing.config.SamplingConfigurationValidator.ValidationResult;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration.SamplingConfig;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration.SamplingConfig.SamplingRule;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration.SamplingConfig.AdaptiveConfig;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SamplingConfigurationValidator 单元测试
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@DisplayName("SamplingConfigurationValidator 测试")
class SamplingConfigurationValidatorTest {

    private SamplingConfigurationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SamplingConfigurationValidator();
    }

    @Nested
    @DisplayName("基本验证测试")
    class BasicValidationTests {

        @Test
        @DisplayName("VAL-123: 基本验证 - null配置抛异常")
        void testValidateNullConfig() {
            ValidationResult result = validator.validateSamplingConfig(null);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("不能为null")));
        }

        @Test
        @DisplayName("VAL-124: 基本验证 - 默认配置成功")
        void testValidateDefaultConfig() {
            SamplingConfig config = new SamplingConfig();

            ValidationResult result = validator.validateSamplingConfig(config);

            assertTrue(result.isValid());
        }
    }

    @Nested
    @DisplayName("采样率验证测试")
    class RatioValidationTests {

        @Test
        @DisplayName("VAL-125: 采样率验证 - 有效采样率")
        void testValidateValidRatio() {
            SamplingConfig config = new SamplingConfig();
            config.setRatio(0.5);

            ValidationResult result = validator.validateSamplingConfig(config);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("VAL-126: 采样率验证 - 采样率为0")
        void testValidateZeroRatio() {
            SamplingConfig config = new SamplingConfig();
            config.setRatio(0.0);

            ValidationResult result = validator.validateSamplingConfig(config);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("VAL-127: 采样率验证 - 采样率为1")
        void testValidateMaxRatio() {
            SamplingConfig config = new SamplingConfig();
            config.setRatio(1.0);

            ValidationResult result = validator.validateSamplingConfig(config);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("VAL-128: 采样率验证 - 采样率超出范围")
        void testValidateInvalidRatio() {
            SamplingConfig config = new SamplingConfig();
            config.setRatio(1.5);

            ValidationResult result = validator.validateSamplingConfig(config);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("必须在0.0-1.0之间")));
        }

        @Test
        @DisplayName("VAL-129: 采样率验证 - 采样率为负数")
        void testValidateNegativeRatio() {
            SamplingConfig config = new SamplingConfig();
            config.setRatio(-0.5);

            ValidationResult result = validator.validateSamplingConfig(config);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("必须在0.0-1.0之间")));
        }
    }

    @Nested
    @DisplayName("服务类型采样率验证测试")
    class ServiceRatioValidationTests {

        @Test
        @DisplayName("VAL-130: 服务类型采样率 - 有效配置")
        void testValidateValidServiceRatios() {
            SamplingConfig config = new SamplingConfig();
            Map<String, Double> serviceRatios = new HashMap<>();
            serviceRatios.put("chat", 0.5);
            serviceRatios.put("embedding", 0.8);
            config.setServiceRatios(serviceRatios);

            ValidationResult result = validator.validateSamplingConfig(config);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("VAL-131: 服务类型采样率 - 空服务类型名称")
        void testValidateEmptyServiceType() {
            SamplingConfig config = new SamplingConfig();
            Map<String, Double> serviceRatios = new HashMap<>();
            serviceRatios.put("", 0.5);
            config.setServiceRatios(serviceRatios);

            ValidationResult result = validator.validateSamplingConfig(config);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("服务类型名称不能为空")));
        }
    }

    @Nested
    @DisplayName("采样规则验证测试")
    class SamplingRuleValidationTests {

        @Test
        @DisplayName("VAL-132: 采样规则 - 有效规则")
        void testValidateValidRule() {
            SamplingConfig config = new SamplingConfig();
            SamplingRule rule = new SamplingRule();
            rule.setCondition("duration >= 100");
            rule.setRatio(0.5);
            config.setRules(List.of(rule));

            ValidationResult result = validator.validateSamplingConfig(config);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("VAL-133: 采样规则 - 条件为空")
        void testValidateEmptyCondition() {
            SamplingConfig config = new SamplingConfig();
            SamplingRule rule = new SamplingRule();
            rule.setCondition("");
            rule.setRatio(0.5);
            config.setRules(List.of(rule));

            ValidationResult result = validator.validateSamplingConfig(config);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("条件不能为空")));
        }
    }

    @Nested
    @DisplayName("自适应采样配置验证测试")
    class AdaptiveConfigValidationTests {

        @Test
        @DisplayName("VAL-134: 自适应采样 - 有效配置")
        void testValidateValidAdaptiveConfig() {
            SamplingConfig config = new SamplingConfig();
            AdaptiveConfig adaptive = new AdaptiveConfig();
            adaptive.setEnabled(true);
            adaptive.setTargetSpansPerSecond(1000);
            adaptive.setMinRatio(0.1);
            adaptive.setMaxRatio(1.0);
            adaptive.setAdjustmentInterval(30);
            config.setAdaptive(adaptive);

            ValidationResult result = validator.validateSamplingConfig(config);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("VAL-135: 自适应采样 - 目标Span数量无效")
        void testValidateInvalidTargetSpans() {
            SamplingConfig config = new SamplingConfig();
            AdaptiveConfig adaptive = new AdaptiveConfig();
            adaptive.setEnabled(true);
            adaptive.setTargetSpansPerSecond(0);
            config.setAdaptive(adaptive);

            ValidationResult result = validator.validateSamplingConfig(config);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("目标Span数量必须大于0")));
        }

        @Test
        @DisplayName("VAL-136: 自适应采样 - 最小率大于最大率")
        void testValidateMinGreaterThanMax() {
            SamplingConfig config = new SamplingConfig();
            AdaptiveConfig adaptive = new AdaptiveConfig();
            adaptive.setEnabled(true);
            adaptive.setMinRatio(0.8);
            adaptive.setMaxRatio(0.5);
            config.setAdaptive(adaptive);

            ValidationResult result = validator.validateSamplingConfig(config);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("最小率必须小于最大率")));
        }

        @Test
        @DisplayName("VAL-137: 自适应采样 - 调整间隔过短产生警告")
        void testValidateShortAdjustmentInterval() {
            SamplingConfig config = new SamplingConfig();
            AdaptiveConfig adaptive = new AdaptiveConfig();
            adaptive.setEnabled(true);
            adaptive.setAdjustmentInterval(5);
            config.setAdaptive(adaptive);

            ValidationResult result = validator.validateSamplingConfig(config);

            assertTrue(result.isValid());
            assertTrue(result.hasWarnings());
        }
    }

    @Nested
    @DisplayName("采样列表验证测试")
    class SampleListValidationTests {

        @Test
        @DisplayName("VAL-138: 采样列表 - 冲突配置产生警告")
        void testValidateConflictingLists() {
            SamplingConfig config = new SamplingConfig();
            config.setAlwaysSample(List.of("operation1", "operation2"));
            config.setNeverSample(List.of("operation2", "operation3"));

            ValidationResult result = validator.validateSamplingConfig(config);

            assertTrue(result.isValid());
            assertTrue(result.hasWarnings());
            assertTrue(result.getWarnings().stream().anyMatch(e -> e.contains("同时在始终采样和从不采样列表中")));
        }

        @Test
        @DisplayName("VAL-139: 采样列表 - 无冲突配置")
        void testValidateNonConflictingLists() {
            SamplingConfig config = new SamplingConfig();
            config.setAlwaysSample(List.of("operation1"));
            config.setNeverSample(List.of("operation2"));

            ValidationResult result = validator.validateSamplingConfig(config);

            assertTrue(result.isValid());
            assertFalse(result.hasWarnings());
        }
    }

    @Nested
    @DisplayName("ValidationResult 测试")
    class ValidationResultTests {

        @Test
        @DisplayName("VAL-140: ValidationResult - 获取错误消息")
        void testValidationResultGetErrorMessage() {
            ValidationResult result = new ValidationResult(false, List.of("错误1", "错误2"), List.of());

            assertEquals("错误1; 错误2", result.getErrorMessage());
        }

        @Test
        @DisplayName("VAL-141: ValidationResult - 无错误时返回null")
        void testValidationResultNoErrorMessage() {
            ValidationResult result = new ValidationResult(true, List.of(), List.of());

            assertNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("VAL-142: ValidationResult - 获取警告消息")
        void testValidationResultGetWarningMessage() {
            ValidationResult result = new ValidationResult(true, List.of(), List.of("警告1"));

            assertEquals("警告1", result.getWarningMessage());
        }
    }
}
