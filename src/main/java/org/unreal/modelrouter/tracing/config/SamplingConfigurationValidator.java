package org.unreal.modelrouter.tracing.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 采样配置验证器
 * 
 * 提供采样配置的完整验证功能，确保配置参数的有效性
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SamplingConfigurationValidator {
    
    /**
     * 验证采样配置
     * 
     * @param config 采样配置
     * @return 验证结果
     */
    public ValidationResult validateSamplingConfig(final TracingConfiguration.SamplingConfig config) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (config == null) {
            errors.add("采样配置不能为null");
            return new ValidationResult(false, errors, warnings);
        }
        
        // 验证全局采样率
        validateRatio("global ratio", config.getRatio(), errors);
        
        // 验证服务类型采样率
        if (config.getServiceRatios() != null) {
            for (Map.Entry<String, Double> entry : config.getServiceRatios().entrySet()) {
                String serviceType = entry.getKey();
                Double ratio = entry.getValue();
                if (serviceType == null || serviceType.trim().isEmpty()) {
                    errors.add("服务类型名称不能为空");
                } else {
                    validateRatio("service ratio for " + serviceType, ratio, errors);
                }
            }
        }
        
        // 验证采样规则
        if (config.getRules() != null) {
            for (int i = 0; i < config.getRules().size(); i++) {
                TracingConfiguration.SamplingConfig.SamplingRule rule = config.getRules().get(i);
                validateSamplingRule(rule, i, errors, warnings);
            }
        }
        
        // 验证自适应配置
        if (config.getAdaptive() != null) {
            validateAdaptiveConfig(config.getAdaptive(), errors, warnings);
        }
        
        // 验证列表配置
        validateSampleLists(config, warnings);
        
        boolean isValid = errors.isEmpty();
        if (isValid) {
            log.debug("采样配置验证通过");
        } else {
            log.warn("采样配置验证失败: {}", errors);
        }
        
        return new ValidationResult(isValid, errors, warnings);
    }
    
    /**
     * 验证采样率
     */
    private void validateRatio(final String name, final Double ratio, final List<String> errors) {
        if (ratio == null) {
            errors.add(name + " 不能为null");
        } else if (ratio < 0.0 || ratio > 1.0) {
            errors.add(name + " 必须在0.0-1.0之间，当前值: " + ratio);
        }
    }
    
    /**
     * 验证采样规则
     */
    private void validateSamplingRule(final TracingConfiguration.SamplingConfig.SamplingRule rule, 
                                    final int index, final List<String> errors, final List<String> warnings) {
        if (rule == null) {
            errors.add("采样规则[" + index + "] 不能为null");
            return;
        }
        
        // 验证条件
        if (rule.getCondition() == null || rule.getCondition().trim().isEmpty()) {
            errors.add("采样规则[" + index + "] 条件不能为空");
        } else {
            validateRuleCondition(rule.getCondition(), index, warnings);
        }
        
        // 验证采样率
        validateRatio("rule[" + index + "] ratio", rule.getRatio(), errors);
    }
    
    /**
     * 验证规则条件语法
     */
    private void validateRuleCondition(final String condition, final int index, final List<String> warnings) {
        // 简单的条件语法验证
        if (!condition.contains(">=") && !condition.contains("==") && 
            !condition.contains("<=") && !condition.contains("!=")) {
            warnings.add("采样规则[" + index + "] 条件可能无效，支持的操作符: >=, <=, ==, !=");
        }
    }
    
    /**
     * 验证自适应配置
     */
    private void validateAdaptiveConfig(final TracingConfiguration.SamplingConfig.AdaptiveConfig adaptive, 
                                      final List<String> errors, final List<String> warnings) {
        if (!adaptive.isEnabled()) {
            return; // 未启用则不验证
        }
        
        // 验证目标Span数量
        if (adaptive.getTargetSpansPerSecond() <= 0) {
            errors.add("自适应采样目标Span数量必须大于0，当前值: " + adaptive.getTargetSpansPerSecond());
        }
        
        // 验证最小最大采样率
        validateRatio("adaptive min ratio", adaptive.getMinRatio(), errors);
        validateRatio("adaptive max ratio", adaptive.getMaxRatio(), errors);
        
        if (adaptive.getMinRatio() >= adaptive.getMaxRatio()) {
            errors.add("自适应采样最小率必须小于最大率，min: " + 
                      adaptive.getMinRatio() + ", max: " + adaptive.getMaxRatio());
        }
        
        // 验证调整间隔
        if (adaptive.getAdjustmentInterval() <= 0) {
            errors.add("自适应采样调整间隔必须大于0秒，当前值: " + adaptive.getAdjustmentInterval());
        } else if (adaptive.getAdjustmentInterval() < 10) {
            warnings.add("自适应采样调整间隔过短可能导致频繁调整，建议至少10秒");
        }
    }
    
    /**
     * 验证采样列表
     */
    private void validateSampleLists(final TracingConfiguration.SamplingConfig config, final List<String> warnings) {
        if (config.getAlwaysSample() != null && config.getNeverSample() != null) {
            // 检查是否有冲突的配置
            for (String alwaysSpan : config.getAlwaysSample()) {
                if (config.getNeverSample().contains(alwaysSpan)) {
                    warnings.add("Span操作 '" + alwaysSpan + "' 同时在始终采样和从不采样列表中");
                }
            }
        }
    }
    
    /**
     * 验证结果类
     */
    /**
     * 验证结果内部类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        /**
         * 构造验证结果
         *
         * @param valid 是否验证通过
         * @param errors 错误消息列表
         * @param warnings 警告消息列表
         */
        public ValidationResult(final boolean valid, final List<String> errors, final List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        /**
         * 获取验证是否通过
         *
         * @return 验证结果
         */
        public boolean isValid() {
            return valid;
        }
        
        /**
         * 获取错误消息列表
         *
         * @return 错误消息列表
         */
        public List<String> getErrors() {
            return errors;
        }
        
        /**
         * 获取警告消息列表
         *
         * @return 警告消息列表
         */
        public List<String> getWarnings() {
            return warnings;
        }
        
        /**
         * 判断是否有警告
         *
         * @return 是否存在警告
         */
        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }
        
        /**
         * 获取错误消息字符串
         *
         * @return 错误消息字符串，无错误时返回 null
         */
        public String getErrorMessage() {
            if (errors.isEmpty()) {
                return null;
            }
            return String.join("; ", errors);
        }
        
        /**
         * 获取警告消息字符串
         *
         * @return 警告消息字符串，无警告时返回 null
         */
        public String getWarningMessage() {
            if (warnings.isEmpty()) {
                return null;
            }
            return String.join("; ", warnings);
        }
    }
}