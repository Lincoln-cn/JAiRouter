package org.unreal.modelrouter.monitoring.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.MonitoringProperties;

import java.time.Duration;
import java.util.Set;

/**
 * 监控配置验证器
 * 在应用启动时验证监控配置的有效性
 */
@Component
public class MonitoringConfigurationValidator {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringConfigurationValidator.class);
    
    private final MonitoringProperties monitoringProperties;

    public MonitoringConfigurationValidator(MonitoringProperties monitoringProperties) {
        this.monitoringProperties = monitoringProperties;
    }

    /**
     * 在应用启动完成后验证监控配置
     */
    @PostConstruct
    public void validateConfiguration() {
        logger.info("开始验证监控配置...");

        try {
            validateBasicConfiguration();
            validateSamplingConfiguration();
            validatePerformanceConfiguration();
            validateCustomTags();

            logger.info("监控配置验证通过");
        } catch (Exception e) {
            logger.error("监控配置验证失败: {}", e.getMessage());
            // 不抛出异常，允许应用继续启动，但记录错误
        }
    }

    /**
     * 验证基础配置
     */
    private void validateBasicConfiguration() {
        // 验证前缀
        String prefix = monitoringProperties.getPrefix();
        if (prefix == null || prefix.trim().isEmpty()) {
            logger.warn("监控指标前缀为空，将使用默认值");
        } else if (!prefix.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            logger.warn("监控指标前缀格式不正确: {}, 应该以字母开头，只包含字母、数字和下划线", prefix);
        }

        // 验证收集间隔
        Duration interval = monitoringProperties.getCollectionInterval();
        if (interval == null || interval.isNegative() || interval.isZero()) {
            logger.warn("监控收集间隔配置无效: {}, 将使用默认值10秒", interval);
        } else if (interval.toSeconds() < 1) {
            logger.warn("监控收集间隔过短: {}秒, 可能影响性能", interval.toSeconds());
        }

        // 验证启用的类别
        Set<String> categories = monitoringProperties.getEnabledCategories();
        if (categories == null || categories.isEmpty()) {
            logger.warn("未配置启用的监控类别，将使用默认类别");
        } else {
            Set<String> validCategories = Set.of("system", "business", "infrastructure");
            for (String category : categories) {
                if (!validCategories.contains(category)) {
                    logger.warn("未知的监控类别: {}, 有效类别: {}", category, validCategories);
                }
            }
        }
    }

    /**
     * 验证采样配置
     */
    private void validateSamplingConfiguration() {
        MonitoringProperties.Sampling sampling = monitoringProperties.getSampling();
        if (sampling == null) {
            logger.warn("采样配置为空，将使用默认值");
            return;
        }

        validateSamplingRate("request-metrics", sampling.getRequestMetrics());
        validateSamplingRate("backend-metrics", sampling.getBackendMetrics());
        validateSamplingRate("infrastructure-metrics", sampling.getInfrastructureMetrics());
        validateSamplingRate("trace-metrics", sampling.getTraceMetrics());
    }

    /**
     * 验证采样率
     */
    private void validateSamplingRate(String name, double rate) {
        if (rate < 0.0 || rate > 1.0) {
            logger.warn("{}采样率配置无效: {}, 应该在0.0-1.0之间", name, rate);
        } else if (rate < 0.1) {
            logger.info("{}采样率较低: {}, 可能会丢失重要指标", name, rate);
        }
    }

    /**
     * 验证性能配置
     */
    private void validatePerformanceConfiguration() {
        MonitoringProperties.Performance performance = monitoringProperties.getPerformance();
        if (performance == null) {
            logger.warn("性能配置为空，将使用默认值");
            return;
        }

        int batchSize = performance.getBatchSize();
        if (batchSize <= 0) {
            logger.warn("批量处理大小配置无效: {}, 应该大于0", batchSize);
        } else if (batchSize > 1000) {
            logger.warn("批量处理大小过大: {}, 可能影响内存使用", batchSize);
        }

        int bufferSize = performance.getBufferSize();
        if (bufferSize <= 0) {
            logger.warn("缓冲区大小配置无效: {}, 应该大于0", bufferSize);
        } else if (bufferSize < batchSize) {
            logger.warn("缓冲区大小({})小于批量处理大小({}), 可能影响性能", bufferSize, batchSize);
        }
    }

    /**
     * 验证自定义标签
     */
    private void validateCustomTags() {
        var customTags = monitoringProperties.getCustomTags();
        if (customTags == null || customTags.isEmpty()) {
            logger.info("未配置自定义标签");
            return;
        }

        for (var entry : customTags.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key == null || key.trim().isEmpty()) {
                logger.warn("自定义标签键为空");
                continue;
            }

            if (!key.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
                logger.warn("自定义标签键格式不正确: {}, 应该以字母开头，只包含字母、数字和下划线", key);
            }

            if (value == null || value.trim().isEmpty()) {
                logger.warn("自定义标签值为空: {}", key);
            }
        }

        logger.info("自定义标签配置: {}", customTags);
    }
}