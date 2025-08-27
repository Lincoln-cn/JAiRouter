package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.tracing.TracingService;
import org.unreal.modelrouter.tracing.async.AsyncTracingProcessor;
import org.unreal.modelrouter.tracing.memory.TracingMemoryManager;
import org.unreal.modelrouter.tracing.performance.TracingPerformanceMonitor;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 追踪性能监控控制器
 * 
 * 提供追踪系统性能监控的REST API接口，包括：
 * - 性能统计信息查询
 * - 性能瓶颈检测
 * - 优化建议获取
 * - 手动性能调优触发
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/tracing/performance")
@RequiredArgsConstructor
@Tag(name = "追踪性能监控", description = "追踪系统性能监控相关API")
public class TracingPerformanceController {

    private final TracingService tracingService;
    private final TracingPerformanceMonitor performanceMonitor;
    private final AsyncTracingProcessor asyncTracingProcessor;
    private final TracingMemoryManager memoryManager;

    @GetMapping("/stats")
    @Operation(summary = "获取性能统计信息", description = "获取追踪系统的综合性能统计信息")
    @ApiResponse(responseCode = "200", description = "成功返回性能统计信息")
    public Mono<ResponseEntity<Map<String, Object>>> getPerformanceStats() {
        return tracingService.getPerformanceStats()
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/processing-stats")
    @Operation(summary = "获取异步处理统计", description = "获取异步追踪数据处理器的详细统计信息")
    @ApiResponse(responseCode = "200", description = "成功返回处理统计信息")
    public Mono<ResponseEntity<AsyncTracingProcessor.ProcessingStats>> getProcessingStats() {
        return Mono.fromCallable(() -> asyncTracingProcessor.getProcessingStats())
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/memory-stats")
    @Operation(summary = "获取内存使用统计", description = "获取追踪系统的内存使用统计信息")
    @ApiResponse(responseCode = "200", description = "成功返回内存统计信息")
    public Mono<ResponseEntity<TracingMemoryManager.MemoryStats>> getMemoryStats() {
        return Mono.fromCallable(() -> memoryManager.getMemoryStats())
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/health")
    @Operation(summary = "获取追踪系统健康状态", description = "获取追踪系统的详细健康检查信息")
    @ApiResponse(responseCode = "200", description = "成功返回健康状态")
    public Mono<ResponseEntity<Health>> getTracingHealth() {
        return Mono.fromCallable(() -> performanceMonitor.health())
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/bottlenecks")
    @Operation(summary = "检测性能瓶颈", description = "检测追踪系统当前的性能瓶颈")
    @ApiResponse(responseCode = "200", description = "成功返回瓶颈检测结果")
    public Mono<ResponseEntity<List<TracingPerformanceMonitor.PerformanceBottleneck>>> detectBottlenecks() {
        return performanceMonitor.detectBottlenecks()
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/suggestions")
    @Operation(summary = "获取优化建议", description = "获取基于当前性能状态的优化建议")
    @ApiResponse(responseCode = "200", description = "成功返回优化建议")
    public Mono<ResponseEntity<List<TracingPerformanceMonitor.OptimizationSuggestion>>> getOptimizationSuggestions() {
        return performanceMonitor.getOptimizationSuggestions()
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/report")
    @Operation(summary = "生成性能报告", description = "生成追踪系统的综合性能报告")
    @ApiResponse(responseCode = "200", description = "成功返回性能报告")
    public Mono<ResponseEntity<TracingPerformanceMonitor.PerformanceReport>> generatePerformanceReport() {
        return performanceMonitor.generatePerformanceReport()
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @PostMapping("/optimize")
    @Operation(summary = "触发性能优化", description = "手动触发追踪系统的性能优化")
    @ApiResponse(responseCode = "200", description = "成功触发性能优化")
    public Mono<ResponseEntity<Map<String, String>>> triggerOptimization() {
        return tracingService.triggerPerformanceOptimization()
            .then(Mono.fromCallable(() -> {
                Map<String, String> result = new HashMap<>();
                result.put("status", "success");
                result.put("message", "性能优化已触发");
                return ResponseEntity.ok(result);
            }))
            .onErrorReturn(ResponseEntity.internalServerError()
                .body(Map.of("status", "error", "message", "性能优化失败")));
    }

    @PostMapping("/tuning")
    @Operation(summary = "执行性能调优", description = "执行指定的性能调优操作")
    @ApiResponse(responseCode = "200", description = "成功执行性能调优")
    public Mono<ResponseEntity<TracingPerformanceMonitor.TuningResult>> performTuning(
            @RequestBody List<String> tuningActions) {
        return performanceMonitor.performPerformanceTuning(tuningActions)
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @PostMapping("/memory/gc")
    @Operation(summary = "触发垃圾回收", description = "手动触发内存垃圾回收")
    @ApiResponse(responseCode = "200", description = "成功触发垃圾回收")
    public Mono<ResponseEntity<TracingMemoryManager.GCResult>> triggerGarbageCollection() {
        return memoryManager.performGarbageCollection()
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @PostMapping("/memory/check")
    @Operation(summary = "执行内存检查", description = "手动执行内存使用检查")
    @ApiResponse(responseCode = "200", description = "成功执行内存检查")
    public Mono<ResponseEntity<TracingMemoryManager.MemoryCheckResult>> performMemoryCheck() {
        return memoryManager.performMemoryCheck()
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @PostMapping("/processing/flush")
    @Operation(summary = "刷新处理缓冲区", description = "强制刷新异步追踪数据处理缓冲区")
    @ApiResponse(responseCode = "200", description = "成功刷新缓冲区")
    public Mono<ResponseEntity<Map<String, String>>> flushProcessingBuffer() {
        return asyncTracingProcessor.flush()
            .then(Mono.fromCallable(() -> {
                Map<String, String> result = new HashMap<>();
                result.put("status", "success");
                result.put("message", "处理缓冲区已刷新");
                return ResponseEntity.ok(result);
            }))
            .onErrorReturn(ResponseEntity.internalServerError()
                .body(Map.of("status", "error", "message", "刷新缓冲区失败")));
    }

    @GetMapping("/metrics/dashboard")
    @Operation(summary = "获取监控仪表板数据", description = "获取用于监控仪表板的关键指标数据")
    @ApiResponse(responseCode = "200", description = "成功返回仪表板数据")
    public Mono<ResponseEntity<Map<String, Object>>> getDashboardMetrics() {
        return Mono.fromCallable(() -> {
            Map<String, Object> dashboard = new HashMap<>();
            
            // 处理器统计
            AsyncTracingProcessor.ProcessingStats processingStats = asyncTracingProcessor.getProcessingStats();
            dashboard.put("processing", Map.of(
                "processed_count", processingStats.getProcessedCount(),
                "dropped_count", processingStats.getDroppedCount(),
                "queue_size", processingStats.getQueueSize(),
                "success_rate", processingStats.getSuccessRate(),
                "drop_rate", processingStats.getDropRate(),
                "is_running", processingStats.isRunning()
            ));
            
            // 内存统计
            TracingMemoryManager.MemoryStats memoryStats = memoryManager.getMemoryStats();
            dashboard.put("memory", Map.of(
                "heap_used_mb", memoryStats.getUsedHeap() / (1024 * 1024),
                "heap_max_mb", memoryStats.getMaxHeap() / (1024 * 1024),
                "heap_usage_percent", Math.round(memoryStats.getHeapUsageRatio() * 100),
                "cache_size", memoryStats.getCacheSize(),
                "cache_hit_ratio", Math.round(memoryStats.getHitRatio() * 100),
                "pressure_level", memoryStats.getPressureLevel().name()
            ));
            
            // 健康状态
            Health health = performanceMonitor.health();
            dashboard.put("health", Map.of(
                "status", health.getStatus().getCode(),
                "details", health.getDetails()
            ));
            
            return ResponseEntity.ok(dashboard);
        })
        .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/alerts/active")
    @Operation(summary = "获取活跃告警", description = "获取当前活跃的性能告警信息")
    @ApiResponse(responseCode = "200", description = "成功返回活跃告警")
    public Mono<ResponseEntity<Map<String, Object>>> getActiveAlerts() {
        return performanceMonitor.detectBottlenecks()
            .map(bottlenecks -> {
                Map<String, Object> alerts = new HashMap<>();
                alerts.put("count", bottlenecks.size());
                alerts.put("alerts", bottlenecks.stream()
                    .filter(b -> b.getSeverity() == TracingPerformanceMonitor.Severity.HIGH ||
                                 b.getSeverity() == TracingPerformanceMonitor.Severity.CRITICAL)
                    .map(b -> Map.of(
                        "type", b.getType().name(),
                        "description", b.getDescription(),
                        "severity", b.getSeverity().name(),
                        "suggestions_count", b.getSuggestions().size()
                    ))
                    .toList()
                );
                return ResponseEntity.ok(alerts);
            })
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }
}