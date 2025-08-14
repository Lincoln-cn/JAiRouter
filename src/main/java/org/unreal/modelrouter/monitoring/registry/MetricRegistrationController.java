package org.unreal.modelrouter.monitoring.registry;

import io.micrometer.core.instrument.Meter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.monitoring.config.MonitoringEnabledCondition;
import org.unreal.modelrouter.monitoring.registry.model.MetricMetadata;
import org.unreal.modelrouter.monitoring.registry.model.MetricRegistrationRequest;

import java.util.List;
import java.util.Map;

/**
 * 指标注册管理控制器
 * 提供动态指标注册、注销和管理的REST API
 */
@RestController
@RequestMapping("/api/monitoring/metrics")
@Tag(name = "Metric Registration", description = "动态指标注册和管理API")
@Conditional(MonitoringEnabledCondition.class)
public class MetricRegistrationController {
    
    private final MetricRegistrationService metricRegistrationService;
    private final CustomMeterRegistry customMeterRegistry;
    
    public MetricRegistrationController(MetricRegistrationService metricRegistrationService,
                                      CustomMeterRegistry customMeterRegistry) {
        this.metricRegistrationService = metricRegistrationService;
        this.customMeterRegistry = customMeterRegistry;
    }
    
    @PostMapping("/register")
    @Operation(summary = "注册新指标", description = "动态注册一个新的业务指标")
    public ResponseEntity<MetricRegistrationService.MetricRegistrationResult> registerMetric(
            @RequestBody MetricRegistrationRequest request) {
        
        MetricRegistrationService.MetricRegistrationResult result = 
                metricRegistrationService.registerBusinessMetric(request);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @PostMapping("/register/batch")
    @Operation(summary = "批量注册指标", description = "批量注册多个业务指标")
    public ResponseEntity<MetricRegistrationService.BatchRegistrationResult> batchRegisterMetrics(
            @RequestBody List<MetricRegistrationRequest> requests) {
        
        MetricRegistrationService.BatchRegistrationResult result = 
                metricRegistrationService.batchRegisterMetrics(requests);
        
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{metricName}")
    @Operation(summary = "注销指标", description = "注销指定的指标")
    public ResponseEntity<Map<String, Object>> unregisterMetric(
            @PathVariable String metricName,
            @RequestParam(required = false) Map<String, String> tags) {
        
        boolean result = metricRegistrationService.unregisterMetric(metricName, tags != null ? tags : Map.of());
        
        Map<String, Object> response = Map.of(
                "success", result,
                "metricName", metricName,
                "message", result ? "Metric unregistered successfully" : "Failed to unregister metric"
        );
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/batch")
    @Operation(summary = "批量注销指标", description = "批量注销多个指标")
    public ResponseEntity<MetricRegistrationService.BatchUnregistrationResult> batchUnregisterMetrics(
            @RequestBody List<String> metricNames) {
        
        MetricRegistrationService.BatchUnregistrationResult result = 
                metricRegistrationService.batchUnregisterMetrics(metricNames);
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/metadata")
    @Operation(summary = "获取所有指标元数据", description = "获取所有已注册指标的元数据信息")
    public ResponseEntity<List<MetricMetadata>> getAllMetricMetadata() {
        List<MetricMetadata> metadata = customMeterRegistry.getAllMetricMetadata();
        return ResponseEntity.ok(metadata);
    }
    
    @GetMapping("/metadata/{metricName}")
    @Operation(summary = "获取指标元数据", description = "获取指定指标的元数据信息")
    public ResponseEntity<MetricMetadata> getMetricMetadata(@PathVariable String metricName) {
        return customMeterRegistry.getMetricMetadata(metricName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PutMapping("/metadata/{metricName}")
    @Operation(summary = "更新指标元数据", description = "更新指定指标的元数据信息")
    public ResponseEntity<Map<String, Object>> updateMetricMetadata(
            @PathVariable String metricName,
            @RequestBody MetricMetadata metadata) {
        
        boolean result = customMeterRegistry.updateMetricMetadata(metricName, metadata);
        
        Map<String, Object> response = Map.of(
                "success", result,
                "metricName", metricName,
                "message", result ? "Metadata updated successfully" : "Failed to update metadata"
        );
        
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{metricName}/configuration")
    @Operation(summary = "更新指标配置", description = "更新指标的启用状态和采样率")
    public ResponseEntity<Map<String, Object>> updateMetricConfiguration(
            @PathVariable String metricName,
            @RequestParam boolean enabled,
            @RequestParam(defaultValue = "1.0") double samplingRate) {
        
        boolean result = metricRegistrationService.updateMetricConfiguration(metricName, enabled, samplingRate);
        
        Map<String, Object> response = Map.of(
                "success", result,
                "metricName", metricName,
                "enabled", enabled,
                "samplingRate", samplingRate,
                "message", result ? "Configuration updated successfully" : "Failed to update configuration"
        );
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/statistics")
    @Operation(summary = "获取指标统计信息", description = "获取指标注册的统计信息")
    public ResponseEntity<MetricRegistrationService.MetricStatistics> getMetricStatistics() {
        MetricRegistrationService.MetricStatistics statistics = metricRegistrationService.getMetricStatistics();
        return ResponseEntity.ok(statistics);
    }
    
    @GetMapping("/category/{category}")
    @Operation(summary = "按类别获取指标", description = "获取指定类别的所有指标")
    public ResponseEntity<List<MetricMetadata>> getMetricsByCategory(@PathVariable String category) {
        List<MetricMetadata> metrics = metricRegistrationService.getMetricsByCategory(category);
        return ResponseEntity.ok(metrics);
    }
    
    @GetMapping("/search")
    @Operation(summary = "搜索指标", description = "根据名称模式和类别搜索指标")
    public ResponseEntity<List<MetricMetadata>> searchMetrics(
            @Parameter(description = "指标名称模式（支持正则表达式）") @RequestParam String namePattern,
            @Parameter(description = "指标类别（可选）") @RequestParam(required = false) String category) {
        
        List<MetricMetadata> metrics = metricRegistrationService.searchMetrics(namePattern, category);
        return ResponseEntity.ok(metrics);
    }
    
    @PostMapping("/validate")
    @Operation(summary = "验证指标请求", description = "验证指标注册请求的有效性")
    public ResponseEntity<MetricRegistrationService.ValidationResult> validateMetricRequest(
            @RequestBody MetricRegistrationRequest request) {
        
        MetricRegistrationService.ValidationResult result = 
                metricRegistrationService.validateMetricRequest(request);
        
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/cleanup")
    @Operation(summary = "清理过期指标", description = "清理过期或无效的指标")
    public ResponseEntity<MetricRegistrationService.CleanupResult> performMetricCleanup() {
        MetricRegistrationService.CleanupResult result = metricRegistrationService.performMetricCleanup();
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/exists/{metricName}")
    @Operation(summary = "检查指标是否存在", description = "检查指定指标是否已注册")
    public ResponseEntity<Map<String, Object>> checkMetricExists(
            @PathVariable String metricName,
            @RequestParam(required = false) Map<String, String> tags) {
        
        boolean exists = customMeterRegistry.meterExists(metricName, tags != null ? tags : Map.of());
        
        Map<String, Object> response = Map.of(
                "metricName", metricName,
                "exists", exists,
                "tags", tags != null ? tags : Map.of()
        );
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/list")
    @Operation(summary = "获取所有指标", description = "获取所有已注册的指标实例")
    public ResponseEntity<List<Map<String, Object>>> getAllMeters() {
        List<Map<String, Object>> meters = customMeterRegistry.getAllMeters().stream()
                .map(meter -> Map.of(
                        "name", meter.getId().getName(),
                        "type", meter.getId().getType().name(),
                        "tags", meter.getId().getTags().stream()
                                .collect(java.util.stream.Collectors.toMap(
                                        tag -> tag.getKey(),
                                        tag -> tag.getValue()
                                )),
                        "description", meter.getId().getDescription() != null ? meter.getId().getDescription() : "",
                        "baseUnit", meter.getId().getBaseUnit() != null ? meter.getId().getBaseUnit() : ""
                ))
                .collect(java.util.stream.Collectors.toList());
        
        return ResponseEntity.ok(meters);
    }
}