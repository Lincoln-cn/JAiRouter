package org.unreal.modelrouter.monitoring.registry;

import org.unreal.modelrouter.monitoring.registry.model.MetricMetadata;
import org.unreal.modelrouter.monitoring.registry.model.MetricRegistrationRequest;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 指标注册服务接口
 * 提供高级的指标管理和生命周期控制功能
 */
public interface MetricRegistrationService {
    
    /**
     * 注册业务指标
     * 
     * @param request 指标注册请求
     * @return 注册结果
     */
    MetricRegistrationResult registerBusinessMetric(MetricRegistrationRequest request);
    
    /**
     * 注册Gauge指标
     * 
     * @param request 指标注册请求
     * @param valueSupplier 值提供器
     * @return 注册结果
     */
    MetricRegistrationResult registerGaugeMetric(MetricRegistrationRequest request, Supplier<Number> valueSupplier);
    
    /**
     * 批量注册指标
     * 
     * @param requests 指标注册请求列表
     * @return 批量注册结果
     */
    BatchRegistrationResult batchRegisterMetrics(List<MetricRegistrationRequest> requests);
    
    /**
     * 注销指标
     * 
     * @param metricName 指标名称
     * @param tags 指标标签
     * @return 是否成功注销
     */
    boolean unregisterMetric(String metricName, Map<String, String> tags);
    
    /**
     * 批量注销指标
     * 
     * @param metricNames 指标名称列表
     * @return 批量注销结果
     */
    BatchUnregistrationResult batchUnregisterMetrics(List<String> metricNames);
    
    /**
     * 更新指标配置
     * 
     * @param metricName 指标名称
     * @param enabled 是否启用
     * @param samplingRate 采样率
     * @return 是否成功更新
     */
    boolean updateMetricConfiguration(String metricName, boolean enabled, double samplingRate);
    
    /**
     * 获取指标统计信息
     * 
     * @return 指标统计信息
     */
    MetricStatistics getMetricStatistics();
    
    /**
     * 按类别获取指标
     * 
     * @param category 指标类别
     * @return 指标元数据列表
     */
    List<MetricMetadata> getMetricsByCategory(String category);
    
    /**
     * 搜索指标
     * 
     * @param namePattern 名称模式
     * @param category 类别（可选）
     * @return 匹配的指标元数据列表
     */
    List<MetricMetadata> searchMetrics(String namePattern, String category);
    
    /**
     * 验证指标配置
     * 
     * @param request 指标注册请求
     * @return 验证结果
     */
    ValidationResult validateMetricRequest(MetricRegistrationRequest request);
    
    /**
     * 执行指标清理
     * 
     * @return 清理结果
     */
    CleanupResult performMetricCleanup();
    
    /**
     * 指标注册结果
     */
    class MetricRegistrationResult {
        private final boolean success;
        private final String message;
        private final String metricName;
        private final Exception error;
        
        public MetricRegistrationResult(boolean success, String message, String metricName, Exception error) {
            this.success = success;
            this.message = message;
            this.metricName = metricName;
            this.error = error;
        }
        
        public static MetricRegistrationResult success(String metricName) {
            return new MetricRegistrationResult(true, "Metric registered successfully", metricName, null);
        }
        
        public static MetricRegistrationResult failure(String metricName, String message, Exception error) {
            return new MetricRegistrationResult(false, message, metricName, error);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getMetricName() { return metricName; }
        public Exception getError() { return error; }
    }
    
    /**
     * 批量注册结果
     */
    class BatchRegistrationResult {
        private final int totalRequests;
        private final int successCount;
        private final int failureCount;
        private final List<MetricRegistrationResult> results;
        
        public BatchRegistrationResult(int totalRequests, int successCount, int failureCount, 
                                     List<MetricRegistrationResult> results) {
            this.totalRequests = totalRequests;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.results = results;
        }
        
        public int getTotalRequests() { return totalRequests; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public List<MetricRegistrationResult> getResults() { return results; }
        public boolean isAllSuccess() { return failureCount == 0; }
    }
    
    /**
     * 批量注销结果
     */
    class BatchUnregistrationResult {
        private final int totalRequests;
        private final int successCount;
        private final int failureCount;
        private final List<String> failedMetrics;
        
        public BatchUnregistrationResult(int totalRequests, int successCount, int failureCount, 
                                       List<String> failedMetrics) {
            this.totalRequests = totalRequests;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.failedMetrics = failedMetrics;
        }
        
        public int getTotalRequests() { return totalRequests; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public List<String> getFailedMetrics() { return failedMetrics; }
        public boolean isAllSuccess() { return failureCount == 0; }
    }
    
    /**
     * 指标统计信息
     */
    class MetricStatistics {
        private final int totalMetrics;
        private final int enabledMetrics;
        private final int disabledMetrics;
        private final Map<String, Integer> metricsByCategory;
        private final Map<String, Integer> metricsByType;
        
        public MetricStatistics(int totalMetrics, int enabledMetrics, int disabledMetrics,
                              Map<String, Integer> metricsByCategory, Map<String, Integer> metricsByType) {
            this.totalMetrics = totalMetrics;
            this.enabledMetrics = enabledMetrics;
            this.disabledMetrics = disabledMetrics;
            this.metricsByCategory = metricsByCategory;
            this.metricsByType = metricsByType;
        }
        
        public int getTotalMetrics() { return totalMetrics; }
        public int getEnabledMetrics() { return enabledMetrics; }
        public int getDisabledMetrics() { return disabledMetrics; }
        public Map<String, Integer> getMetricsByCategory() { return metricsByCategory; }
        public Map<String, Integer> getMetricsByType() { return metricsByType; }
    }
    
    /**
     * 验证结果
     */
    class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, List.of(), List.of());
        }
        
        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors, List.of());
        }
        
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
    }
    
    /**
     * 清理结果
     */
    class CleanupResult {
        private final int cleanedMetrics;
        private final int totalMetrics;
        private final List<String> cleanedMetricNames;
        
        public CleanupResult(int cleanedMetrics, int totalMetrics, List<String> cleanedMetricNames) {
            this.cleanedMetrics = cleanedMetrics;
            this.totalMetrics = totalMetrics;
            this.cleanedMetricNames = cleanedMetricNames;
        }
        
        public int getCleanedMetrics() { return cleanedMetrics; }
        public int getTotalMetrics() { return totalMetrics; }
        public List<String> getCleanedMetricNames() { return cleanedMetricNames; }
    }
}