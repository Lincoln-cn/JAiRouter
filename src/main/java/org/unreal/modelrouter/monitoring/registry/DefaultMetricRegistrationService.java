package org.unreal.modelrouter.monitoring.registry;

import io.micrometer.core.instrument.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.monitoring.config.MonitoringEnabledCondition;
import org.unreal.modelrouter.monitoring.registry.model.MetricMetadata;
import org.unreal.modelrouter.monitoring.registry.model.MetricRegistrationRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 默认指标注册服务实现
 * 提供完整的指标生命周期管理功能
 */
@Service
@Conditional(MonitoringEnabledCondition.class)
public class DefaultMetricRegistrationService implements MetricRegistrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultMetricRegistrationService.class);
    
    private final CustomMeterRegistry customMeterRegistry;
    
    // 指标名称验证模式 (允许字母、数字、下划线和点)
    private static final Pattern METRIC_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$");
    
    public DefaultMetricRegistrationService(CustomMeterRegistry customMeterRegistry) {
        this.customMeterRegistry = customMeterRegistry;
        logger.info("DefaultMetricRegistrationService initialized");
    }
    
    @Override
    public MetricRegistrationResult registerBusinessMetric(MetricRegistrationRequest request) {
        try {
            ValidationResult validation = validateMetricRequest(request);
            if (!validation.isValid()) {
                String errorMessage = "Validation failed: " + String.join(", ", validation.getErrors());
                return MetricRegistrationResult.failure(request.getName(), errorMessage, null);
            }
            
            switch (request.getType()) {
                case COUNTER:
                    customMeterRegistry.registerCounter(request);
                    break;
                case TIMER:
                    customMeterRegistry.registerTimer(request);
                    break;
                default:
                    return MetricRegistrationResult.failure(request.getName(), 
                            "Unsupported metric type for business metric: " + request.getType(), null);
            }
            
            logger.info("Successfully registered business metric: {}", request.getName());
            return MetricRegistrationResult.success(request.getName());
            
        } catch (Exception e) {
            logger.error("Failed to register business metric: {}", request.getName(), e);
            return MetricRegistrationResult.failure(request.getName(), e.getMessage(), e);
        }
    }
    
    @Override
    public MetricRegistrationResult registerGaugeMetric(MetricRegistrationRequest request, Supplier<Number> valueSupplier) {
        try {
            ValidationResult validation = validateMetricRequest(request);
            if (!validation.isValid()) {
                String errorMessage = "Validation failed: " + String.join(", ", validation.getErrors());
                return MetricRegistrationResult.failure(request.getName(), errorMessage, null);
            }
            
            if (request.getType() != Meter.Type.GAUGE) {
                return MetricRegistrationResult.failure(request.getName(), 
                        "Expected GAUGE type but got: " + request.getType(), null);
            }
            
            if (valueSupplier == null) {
                return MetricRegistrationResult.failure(request.getName(), 
                        "Value supplier cannot be null for gauge metric", null);
            }
            
            customMeterRegistry.registerGauge(request, valueSupplier);
            
            logger.info("Successfully registered gauge metric: {}", request.getName());
            return MetricRegistrationResult.success(request.getName());
            
        } catch (Exception e) {
            logger.error("Failed to register gauge metric: {}", request.getName(), e);
            return MetricRegistrationResult.failure(request.getName(), e.getMessage(), e);
        }
    }
    
    @Override
    public BatchRegistrationResult batchRegisterMetrics(List<MetricRegistrationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new BatchRegistrationResult(0, 0, 0, List.of());
        }
        
        List<MetricRegistrationResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        
        for (MetricRegistrationRequest request : requests) {
            MetricRegistrationResult result = registerBusinessMetric(request);
            results.add(result);
            
            if (result.isSuccess()) {
                successCount++;
            } else {
                failureCount++;
            }
        }
        
        logger.info("Batch registration completed: {} total, {} success, {} failure", 
                   requests.size(), successCount, failureCount);
        
        return new BatchRegistrationResult(requests.size(), successCount, failureCount, results);
    }
    
    @Override
    public boolean unregisterMetric(String metricName, Map<String, String> tags) {
        try {
            boolean result = customMeterRegistry.unregisterMeter(metricName, tags);
            if (result) {
                logger.info("Successfully unregistered metric: {} with tags: {}", metricName, tags);
            } else {
                logger.warn("Failed to unregister metric: {} with tags: {}", metricName, tags);
            }
            return result;
        } catch (Exception e) {
            logger.error("Error unregistering metric: {}", metricName, e);
            return false;
        }
    }
    
    @Override
    public BatchUnregistrationResult batchUnregisterMetrics(List<String> metricNames) {
        if (metricNames == null || metricNames.isEmpty()) {
            return new BatchUnregistrationResult(0, 0, 0, List.of());
        }
        
        int successCount = 0;
        int failureCount = 0;
        List<String> failedMetrics = new ArrayList<>();
        
        for (String metricName : metricNames) {
            boolean result = unregisterMetric(metricName, Map.of());
            
            if (result) {
                successCount++;
            } else {
                failureCount++;
                failedMetrics.add(metricName);
            }
        }
        
        logger.info("Batch unregistration completed: {} total, {} success, {} failure", 
                   metricNames.size(), successCount, failureCount);
        
        return new BatchUnregistrationResult(metricNames.size(), successCount, failureCount, failedMetrics);
    }
    
    @Override
    public boolean updateMetricConfiguration(String metricName, boolean enabled, double samplingRate) {
        try {
            var metadataOpt = customMeterRegistry.getMetricMetadata(metricName);
            if (metadataOpt.isEmpty()) {
                logger.warn("Metric {} not found for configuration update", metricName);
                return false;
            }
            
            MetricMetadata currentMetadata = metadataOpt.get();
            MetricMetadata updatedMetadata = currentMetadata.toBuilder()
                    .enabled(enabled)
                    .samplingRate(samplingRate)
                    .build();
            
            boolean result = customMeterRegistry.updateMetricMetadata(metricName, updatedMetadata);
            if (result) {
                logger.info("Updated configuration for metric: {} (enabled: {}, samplingRate: {})", 
                           metricName, enabled, samplingRate);
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Failed to update configuration for metric: {}", metricName, e);
            return false;
        }
    }
    
    @Override
    public MetricStatistics getMetricStatistics() {
        try {
            List<MetricMetadata> allMetrics = customMeterRegistry.getAllMetricMetadata();
            
            int totalMetrics = allMetrics.size();
            int enabledMetrics = (int) allMetrics.stream().filter(MetricMetadata::isEnabled).count();
            int disabledMetrics = totalMetrics - enabledMetrics;
            
            Map<String, Integer> metricsByCategory = allMetrics.stream()
                    .collect(Collectors.groupingBy(
                            MetricMetadata::getCategory,
                            Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                    ));
            
            Map<String, Integer> metricsByType = allMetrics.stream()
                    .collect(Collectors.groupingBy(
                            metadata -> metadata.getType().name(),
                            Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                    ));
            
            return new MetricStatistics(totalMetrics, enabledMetrics, disabledMetrics, 
                                      metricsByCategory, metricsByType);
        } catch (Exception e) {
            logger.error("Failed to get metric statistics", e);
            return new MetricStatistics(0, 0, 0, Map.of(), Map.of());
        }
    }
    
    @Override
    public List<MetricMetadata> getMetricsByCategory(String category) {
        try {
            return customMeterRegistry.getAllMetricMetadata().stream()
                    .filter(metadata -> category.equals(metadata.getCategory()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to get metrics by category: {}", category, e);
            return List.of();
        }
    }
    
    @Override
    public List<MetricMetadata> searchMetrics(String namePattern, String category) {
        try {
            Pattern pattern = Pattern.compile(namePattern, Pattern.CASE_INSENSITIVE);
            
            return customMeterRegistry.getAllMetricMetadata().stream()
                    .filter(metadata -> pattern.matcher(metadata.getName()).find())
                    .filter(metadata -> category == null || category.equals(metadata.getCategory()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to search metrics with pattern: {} and category: {}", namePattern, category, e);
            return List.of();
        }
    }
    
    @Override
    public ValidationResult validateMetricRequest(MetricRegistrationRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // 验证指标名称
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            errors.add("Metric name cannot be null or empty");
        } else if (!METRIC_NAME_PATTERN.matcher(request.getName()).matches()) {
            errors.add("Metric name must match pattern: " + METRIC_NAME_PATTERN.pattern());
        }
        
        // 验证指标类型
        if (request.getType() == null) {
            errors.add("Metric type cannot be null");
        }
        
        // 验证采样率
        if (request.getSamplingRate() < 0.0 || request.getSamplingRate() > 1.0) {
            errors.add("Sampling rate must be between 0.0 and 1.0");
        }
        
        // 检查指标是否已存在
        if (customMeterRegistry.meterExists(request.getName(), request.getTags())) {
            warnings.add("Metric with same name and tags already exists");
        }
        
        // 验证标签
        if (request.getTags() != null) {
            for (Map.Entry<String, String> tag : request.getTags().entrySet()) {
                if (tag.getKey() == null || tag.getKey().trim().isEmpty()) {
                    errors.add("Tag key cannot be null or empty");
                }
                if (tag.getValue() == null) {
                    errors.add("Tag value cannot be null");
                }
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public CleanupResult performMetricCleanup() {
        try {
            List<MetricMetadata> beforeCleanup = customMeterRegistry.getAllMetricMetadata();
            int totalBefore = beforeCleanup.size();
            
            int cleanedCount = customMeterRegistry.cleanupExpiredMeters();
            
            List<MetricMetadata> afterCleanup = customMeterRegistry.getAllMetricMetadata();
            List<String> cleanedMetricNames = beforeCleanup.stream()
                    .map(MetricMetadata::getName)
                    .filter(name -> afterCleanup.stream().noneMatch(m -> m.getName().equals(name)))
                    .collect(Collectors.toList());
            
            logger.info("Metric cleanup completed: {} metrics cleaned out of {} total", 
                       cleanedCount, totalBefore);
            
            return new CleanupResult(cleanedCount, totalBefore, cleanedMetricNames);
        } catch (Exception e) {
            logger.error("Failed to perform metric cleanup", e);
            return new CleanupResult(0, 0, List.of());
        }
    }
}