package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.tracing.TracingService;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.tracing.performance.TracingPerformanceMonitor;
import org.unreal.modelrouter.tracing.sampler.SamplingStrategyManager;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 追踪管理控制器
 * 
 * 提供追踪系统的综合管理REST API接口，包括：
 * - 追踪状态查询和控制
 * - 追踪配置管理
 * - 追踪系统健康检查
 * - 追踪数据查询和导出
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/tracing/actuator")
@RequiredArgsConstructor
@Tag(name = "追踪管理", description = "追踪系统综合管理API")
public class TracingController {

    private final TracingService tracingService;
    private final TracingConfiguration tracingConfiguration;
    private final TracingPerformanceMonitor performanceMonitor;
    private final SamplingStrategyManager samplingStrategyManager;

    @GetMapping("/status")
    @Operation(summary = "获取追踪系统状态", description = "获取追踪系统的当前运行状态和基本信息")
    @ApiResponse(responseCode = "200", description = "成功返回追踪系统状态")
    public Mono<ResponseEntity<Map<String, Object>>> getTracingStatus() {
        return Mono.fromCallable(() -> {
            Map<String, Object> status = new HashMap<>();
            
            // 基本状态信息
            status.put("enabled", tracingConfiguration.isEnabled());
            status.put("serviceName", tracingConfiguration.getServiceName());
            status.put("serviceVersion", tracingConfiguration.getServiceVersion());
            status.put("serviceNamespace", tracingConfiguration.getServiceNamespace());
            status.put("timestamp", LocalDateTime.now());
            
            // OpenTelemetry状态
            Map<String, Object> otelStatus = new HashMap<>();
            otelStatus.put("enabled", tracingConfiguration.getOpenTelemetry().isEnabled());
            otelStatus.put("sdkDisabled", tracingConfiguration.getOpenTelemetry().getSdk().isDisabled());
            status.put("openTelemetry", otelStatus);
            
            // 采样状态
            Map<String, Object> samplingStatus = new HashMap<>();
            TracingConfiguration.SamplingConfig samplingConfig = tracingConfiguration.getSampling();
            samplingStatus.put("globalRatio", samplingConfig.getRatio());
            samplingStatus.put("adaptiveEnabled", samplingConfig.getAdaptive().isEnabled());
            samplingStatus.put("serviceRatios", samplingConfig.getServiceRatios());
            samplingStatus.put("alwaysSampleCount", samplingConfig.getAlwaysSample().size());
            samplingStatus.put("neverSampleCount", samplingConfig.getNeverSample().size());
            status.put("sampling", samplingStatus);
            
            // 导出器状态
            Map<String, Object> exporterStatus = new HashMap<>();
            TracingConfiguration.ExporterConfig exporterConfig = tracingConfiguration.getExporter();
            exporterStatus.put("type", exporterConfig.getType());
            exporterStatus.put("loggingEnabled", exporterConfig.getLogging().isEnabled());
            status.put("exporter", exporterStatus);
            
            // 性能配置状态
            Map<String, Object> performanceStatus = new HashMap<>();
            TracingConfiguration.PerformanceConfig performanceConfig = tracingConfiguration.getPerformance();
            performanceStatus.put("asyncProcessing", performanceConfig.isAsyncProcessing());
            performanceStatus.put("threadPoolCoreSize", performanceConfig.getThreadPool().getCoreSize());
            performanceStatus.put("bufferSize", performanceConfig.getBuffer().getSize());
            performanceStatus.put("memoryLimitMb", performanceConfig.getMemory().getMemoryLimitMb());
            status.put("performance", performanceStatus);
            
            return ResponseEntity.ok(status);
        });
    }

    @GetMapping("/health")
    @Operation(summary = "获取追踪系统健康状态", description = "获取追踪系统的详细健康检查信息")
    @ApiResponse(responseCode = "200", description = "成功返回健康状态")
    public Mono<ResponseEntity<Health>> getTracingHealth() {
        return Mono.fromCallable(() -> {
            Health health = performanceMonitor.health();
            return ResponseEntity.ok(health);
        });
    }

    @GetMapping("/config")
    @Operation(summary = "获取追踪配置", description = "获取当前的追踪系统配置")
    @ApiResponse(responseCode = "200", description = "成功返回追踪配置")
    public Mono<ResponseEntity<TracingConfiguration>> getTracingConfiguration() {
        return Mono.fromCallable(() -> ResponseEntity.ok(tracingConfiguration));
    }

    @PutMapping("/config")
    @Operation(summary = "更新追踪配置", description = "更新追踪系统配置（仅支持部分配置的运行时更新）")
    @ApiResponse(responseCode = "200", description = "成功更新配置")
    @ApiResponse(responseCode = "400", description = "配置无效")
    public Mono<ResponseEntity<Map<String, Object>>> updateTracingConfiguration(
            @RequestBody TracingConfiguration newConfig) {
        return Mono.fromCallable(() -> {
            try {
                // 仅更新支持运行时修改的配置项
                updateRuntimeConfiguration(newConfig);
                
                Map<String, Object> response = new HashMap<>();
                response.put("message", "追踪配置更新成功");
                response.put("timestamp", LocalDateTime.now());
                response.put("updatedFields", List.of("sampling", "logging", "performance"));
                
                log.info("追踪配置已更新");
                return ResponseEntity.ok(response);
                
            } catch (Exception e) {
                log.error("更新追踪配置失败", e);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "配置更新失败: " + e.getMessage());
                errorResponse.put("timestamp", LocalDateTime.now());
                return ResponseEntity.badRequest().body(errorResponse);
            }
        });
    }

    @PostMapping("/sampling/refresh")
    @Operation(summary = "刷新采样策略", description = "刷新并重新加载采样策略配置")
    @ApiResponse(responseCode = "200", description = "成功刷新采样策略")
    public Mono<ResponseEntity<Map<String, String>>> refreshSamplingStrategy() {
        return Mono.fromCallable(() -> {
            try {
                samplingStrategyManager.updateSamplingConfiguration(tracingConfiguration.getSampling());
                
                Map<String, String> response = new HashMap<>();
                response.put("message", "采样策略已刷新");
                response.put("timestamp", LocalDateTime.now().toString());
                
                log.info("采样策略已刷新");
                return ResponseEntity.ok(response);
                
            } catch (Exception e) {
                log.error("刷新采样策略失败", e);
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "刷新失败: " + e.getMessage());
                errorResponse.put("timestamp", LocalDateTime.now().toString());
                return ResponseEntity.internalServerError().body(errorResponse);
            }
        });
    }

    @GetMapping("/stats")
    @Operation(summary = "获取追踪统计信息", description = "获取追踪系统的综合统计信息")
    @ApiResponse(responseCode = "200", description = "成功返回统计信息")
    public Mono<ResponseEntity<Map<String, Object>>> getTracingStats() {
        return tracingService.getPerformanceStats()
            .map(stats -> {
                Map<String, Object> enhancedStats = new HashMap<>(stats);
                
                // 添加配置信息
                enhancedStats.put("configInfo", Map.of(
                    "enabled", tracingConfiguration.isEnabled(),
                    "serviceName", tracingConfiguration.getServiceName(),
                    "exporterType", tracingConfiguration.getExporter().getType(),
                    "globalSamplingRatio", tracingConfiguration.getSampling().getRatio()
                ));
                
                // 添加时间戳
                enhancedStats.put("timestamp", LocalDateTime.now());
                
                return ResponseEntity.ok(enhancedStats);
            })
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @PostMapping("/enable")
    @Operation(summary = "启用追踪", description = "启用追踪系统（运行时开关）")
    @ApiResponse(responseCode = "200", description = "成功启用追踪")
    public Mono<ResponseEntity<Map<String, String>>> enableTracing() {
        return Mono.fromCallable(() -> {
            tracingConfiguration.setEnabled(true);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "追踪系统已启用");
            response.put("status", "enabled");
            response.put("timestamp", LocalDateTime.now().toString());
            
            log.info("追踪系统已启用");
            return ResponseEntity.ok(response);
        });
    }

    @PostMapping("/disable")
    @Operation(summary = "禁用追踪", description = "禁用追踪系统（运行时开关）")
    @ApiResponse(responseCode = "200", description = "成功禁用追踪")
    public Mono<ResponseEntity<Map<String, String>>> disableTracing() {
        return Mono.fromCallable(() -> {
            tracingConfiguration.setEnabled(false);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "追踪系统已禁用");
            response.put("status", "disabled");
            response.put("timestamp", LocalDateTime.now().toString());
            
            log.info("追踪系统已禁用");
            return ResponseEntity.ok(response);
        });
    }

    @GetMapping("/export")
    @Operation(summary = "导出追踪数据", description = "导出指定时间范围内的追踪数据")
    @ApiResponse(responseCode = "200", description = "成功导出数据")
    public Mono<ResponseEntity<Map<String, Object>>> exportTracingData(
            @Parameter(description = "开始时间戳（毫秒）") @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间戳（毫秒）") @RequestParam(required = false) Long endTime,
            @Parameter(description = "导出格式") @RequestParam(defaultValue = "json") String format) {
        
        return Mono.fromCallable(() -> {
            Map<String, Object> exportData = new HashMap<>();
            
            // 设置默认时间范围（最近1小时）
            long now = System.currentTimeMillis();
            long start = startTime != null ? startTime : now - 3600000; // 1小时前
            long end = endTime != null ? endTime : now;
            
            exportData.put("exportInfo", Map.of(
                "startTime", start,
                "endTime", end,
                "format", format,
                "exportedAt", now
            ));
            
            // 这里应该实际获取追踪数据，暂时返回占位符
            exportData.put("data", "追踪数据导出功能需要进一步实现");
            exportData.put("message", "导出功能已触发，实际数据获取需要集成追踪存储");
            
            return ResponseEntity.ok(exportData);
        });
    }

    @PostMapping("/clear-cache")
    @Operation(summary = "清理追踪缓存", description = "清理追踪系统的内存缓存")
    @ApiResponse(responseCode = "200", description = "成功清理缓存")
    public Mono<ResponseEntity<Map<String, String>>> clearTracingCache() {
        return tracingService.triggerPerformanceOptimization()
            .then(Mono.fromCallable(() -> {
                Map<String, String> response = new HashMap<>();
                response.put("message", "追踪缓存清理已触发");
                response.put("timestamp", LocalDateTime.now().toString());
                
                log.info("追踪缓存清理已触发");
                return ResponseEntity.ok(response);
            }))
            .onErrorReturn(ResponseEntity.internalServerError()
                .body(Map.of("error", "缓存清理失败", "timestamp", LocalDateTime.now().toString())));
    }

    /**
     * 更新运行时配置
     */
       private void updateRuntimeConfiguration(TracingConfiguration newConfig) {
        // 更新采样配置
        if (newConfig.getSampling() != null) {
            tracingConfiguration.setSampling(newConfig.getSampling());
            samplingStrategyManager.updateSamplingConfiguration(newConfig.getSampling());
        }
        
        // 更新日志配置
        if (newConfig.getLogging() != null) {
            tracingConfiguration.setLogging(newConfig.getLogging());
        }
        
        // 更新性能配置（部分支持）
        if (newConfig.getPerformance() != null) {
            TracingConfiguration.PerformanceConfig currentPerf = tracingConfiguration.getPerformance();
            TracingConfiguration.PerformanceConfig newPerf = newConfig.getPerformance();
            
            // 只更新可以运行时修改的配置
            if (newPerf.getBuffer() != null) {
                currentPerf.setBuffer(newPerf.getBuffer());
            }
            if (newPerf.getBatch() != null) {
                currentPerf.setBatch(newPerf.getBatch());
            }
            // 添加线程池配置更新
            if (newPerf.getThreadPool() != null) {
                currentPerf.setThreadPool(newPerf.getThreadPool());
            }
            // 添加内存配置更新
            if (newPerf.getMemory() != null) {
                currentPerf.setMemory(newPerf.getMemory());
            }
            // 添加异步处理配置更新
            currentPerf.setAsyncProcessing(newPerf.isAsyncProcessing());
        }

         // 更新导出器配置
        if (newConfig.getExporter() != null) {
            tracingConfiguration.setExporter(newConfig.getExporter());
        }
        
        // 更新监控配置
        if (newConfig.getMonitoring() != null) {
            tracingConfiguration.setMonitoring(newConfig.getMonitoring());
        }
    }
}