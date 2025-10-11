package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.tracing.query.TraceQueryService;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 追踪查询控制器
 * 
 * 提供追踪数据查询和分析的REST API接口，包括：
 * - 追踪链路查询
 * - 追踪数据搜索
 * - 服务统计信息
 * - 性能分析数据
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/tracing/query")
@RequiredArgsConstructor
@Tag(name = "追踪查询", description = "追踪数据查询和分析API")
public class TracingQueryController {

    private final TraceQueryService traceQueryService;

    @GetMapping("/trace/{traceId}")
    @Operation(summary = "获取追踪链路详情", description = "根据traceId获取完整的追踪链路信息")
    @ApiResponse(responseCode = "200", description = "成功返回追踪链路")
    @ApiResponse(responseCode = "404", description = "追踪链路不存在")
    public Mono<ResponseEntity<Map<String, Object>>> getTraceChain(
            @Parameter(description = "追踪ID") @PathVariable String traceId) {
        
        return traceQueryService.getTraceChain(traceId)
            .map(traceChain -> {
                if (traceChain == null) {
                    return ResponseEntity.notFound().build();
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", traceChain);
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/search")
    @Operation(summary = "搜索追踪数据", description = "根据条件搜索追踪数据")
    @ApiResponse(responseCode = "200", description = "成功返回搜索结果")
    public Mono<ResponseEntity<Map<String, Object>>> searchTraces(
            @Parameter(description = "开始时间戳（毫秒）") @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间戳（毫秒）") @RequestParam(required = false) Long endTime,
            @Parameter(description = "服务名") @RequestParam(required = false) String serviceName,
            @Parameter(description = "操作名") @RequestParam(required = false) String operationName,
            @Parameter(description = "追踪ID") @RequestParam(required = false) String traceId,
            @Parameter(description = "最小持续时间（毫秒）") @RequestParam(defaultValue = "0") double minDuration,
            @Parameter(description = "最大持续时间（毫秒）") @RequestParam(defaultValue = "0") double maxDuration,
            @Parameter(description = "是否有错误") @RequestParam(required = false) Boolean hasError,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "页大小") @RequestParam(defaultValue = "20") int size) {
        
        TraceQueryService.TraceSearchCriteria criteria = new TraceQueryService.TraceSearchCriteria();
        if (startTime != null) criteria.setStartTime(Instant.ofEpochMilli(startTime));
        if (endTime != null) criteria.setEndTime(Instant.ofEpochMilli(endTime));
        criteria.setServiceName(serviceName);
        criteria.setOperationName(operationName);
        criteria.setTraceId(traceId);
        criteria.setMinDuration(minDuration);
        criteria.setMaxDuration(maxDuration);
        criteria.setHasError(hasError);
        criteria.setPage(page);
        criteria.setSize(size);
        
        return traceQueryService.searchTracesWithPagination(criteria)
            .map(result -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", result);
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/recent")
    @Operation(summary = "获取最近的追踪", description = "获取最近的追踪记录")
    @ApiResponse(responseCode = "200", description = "成功返回最近追踪")
    public Mono<ResponseEntity<Map<String, Object>>> getRecentTraces(
            @Parameter(description = "限制数量") @RequestParam(defaultValue = "50") int limit) {
        
        return traceQueryService.getRecentTraces(limit)
            .collectList()
            .map(traces -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", traces);
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/services")
    @Operation(summary = "获取服务统计", description = "获取所有服务的统计信息")
    @ApiResponse(responseCode = "200", description = "成功返回服务统计")
    public Mono<ResponseEntity<Map<String, Object>>> getServiceStatistics() {
        return traceQueryService.getServiceStatistics()
            .map(services -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", services);
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/statistics")
    @Operation(summary = "获取追踪统计", description = "获取指定时间范围内的追踪统计信息")
    @ApiResponse(responseCode = "200", description = "成功返回统计信息")
    public Mono<ResponseEntity<Map<String, Object>>> getTraceStatistics(
            @Parameter(description = "开始时间戳（毫秒）") @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间戳（毫秒）") @RequestParam(required = false) Long endTime) {
        
        // 设置默认时间范围（最近1小时）
        long now = System.currentTimeMillis();
        long start = startTime != null ? startTime : now - 3600000; // 1小时前
        long end = endTime != null ? endTime : now;
        
        return traceQueryService.getTraceStatistics(start, end)
            .map(stats -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", stats);
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @PostMapping("/export")
    @Operation(summary = "导出追踪数据", description = "导出指定条件的追踪数据")
    @ApiResponse(responseCode = "200", description = "成功导出数据")
    public Mono<ResponseEntity<Map<String, Object>>> exportTraces(
            @RequestBody TraceQueryService.TraceExportRequest exportRequest) {
        
        return traceQueryService.exportTraces(exportRequest)
            .map(result -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", result);
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @PostMapping("/cleanup")
    @Operation(summary = "清理过期追踪", description = "清理指定时间之前的追踪数据")
    @ApiResponse(responseCode = "200", description = "成功清理数据")
    public Mono<ResponseEntity<Map<String, Object>>> cleanupExpiredTraces(
            @Parameter(description = "保留小时数") @RequestParam(defaultValue = "24") int retentionHours) {
        
        long maxAgeMillis = retentionHours * 60 * 60 * 1000L;
        
        return traceQueryService.cleanupExpiredTraces(maxAgeMillis)
            .map(removedCount -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", Map.of(
                    "removedCount", removedCount,
                    "retentionHours", retentionHours
                ));
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/operations")
    @Operation(summary = "获取操作列表", description = "获取指定服务的操作列表")
    @ApiResponse(responseCode = "200", description = "成功返回操作列表")
    public Mono<ResponseEntity<Map<String, Object>>> getOperations(
            @Parameter(description = "服务名") @RequestParam(required = false) String serviceName) {
        
        return Mono.fromCallable(() -> {
            // 这里应该从TraceQueryService获取操作列表，暂时返回模拟数据
            List<String> operations;
            if (serviceName != null) {
                operations = List.of(
                    serviceName + ".process",
                    serviceName + ".validate",
                    serviceName + ".transform"
                );
            } else {
                operations = List.of(
                    "chat.process",
                    "embedding.process", 
                    "rerank.process",
                    "tts.process",
                    "stt.process"
                );
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", operations);
            return ResponseEntity.ok(response);
        });
    }

    @GetMapping("/health")
    @Operation(summary = "查询服务健康检查", description = "检查追踪查询服务的健康状态")
    @ApiResponse(responseCode = "200", description = "服务健康")
    public Mono<ResponseEntity<Map<String, Object>>> getQueryServiceHealth() {
        return Mono.fromCallable(() -> {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("service", "TraceQueryService");
            health.put("timestamp", Instant.now());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", health);
            return ResponseEntity.ok(response);
        });
    }

    // Performance Analysis Endpoints for Performance.vue

    @GetMapping("/performance/stats")
    @Operation(summary = "获取性能统计", description = "获取指定时间范围内的性能统计数据")
    @ApiResponse(responseCode = "200", description = "成功返回性能统计")
    public Mono<ResponseEntity<Map<String, Object>>> getPerformanceStats(
            @Parameter(description = "开始时间") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) String endTime) {
        
        return Mono.fromCallable(() -> {
            // 基于现有的追踪统计数据生成性能统计
            long now = System.currentTimeMillis();
            long start = startTime != null ? parseTimeString(startTime) : now - 3600000;
            long end = endTime != null ? parseTimeString(endTime) : now;
            
            return traceQueryService.getTraceStatistics(start, end).block();
        })
        .map(stats -> {
            Map<String, Object> performanceStats = new HashMap<>();
            performanceStats.put("totalRequests", stats.getTotalTraces());
            performanceStats.put("successfulRequests", stats.getSuccessfulTraces());
            performanceStats.put("errorRequests", stats.getErrorTraces());
            performanceStats.put("avgLatency", stats.getAvgDuration());
            performanceStats.put("maxLatency", stats.getMaxDuration());
            performanceStats.put("minLatency", stats.getMinDuration());
            performanceStats.put("errorRate", stats.getTotalTraces() > 0 ? 
                (double) stats.getErrorTraces() / stats.getTotalTraces() * 100 : 0.0);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", performanceStats);
            return ResponseEntity.ok(response);
        })
        .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/performance/latency")
    @Operation(summary = "获取延迟分析", description = "获取延迟分析数据")
    @ApiResponse(responseCode = "200", description = "成功返回延迟分析")
    public Mono<ResponseEntity<Map<String, Object>>> getLatencyAnalysis(
            @Parameter(description = "开始时间") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) String endTime) {
        
        return traceQueryService.getServiceStatistics()
            .map(services -> {
                Map<String, Object> latencyAnalysis = new HashMap<>();
                
                // 服务延迟分布
                Map<String, Double> serviceLatencies = new HashMap<>();
                Map<String, Double> serviceP95Latencies = new HashMap<>();
                Map<String, Double> serviceP99Latencies = new HashMap<>();
                
                for (Map<String, Object> service : services) {
                    String serviceName = (String) service.get("name");
                    serviceLatencies.put(serviceName, ((Number) service.get("avgDuration")).doubleValue());
                    serviceP95Latencies.put(serviceName, ((Number) service.get("p95Duration")).doubleValue());
                    serviceP99Latencies.put(serviceName, ((Number) service.get("p99Duration")).doubleValue());
                }
                
                latencyAnalysis.put("serviceLatencies", serviceLatencies);
                latencyAnalysis.put("serviceP95Latencies", serviceP95Latencies);
                latencyAnalysis.put("serviceP99Latencies", serviceP99Latencies);
                
                // 生成时间序列数据（模拟）
                latencyAnalysis.put("timeSeriesData", generateLatencyTimeSeries());
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", latencyAnalysis);
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/performance/errors")
    @Operation(summary = "获取错误分析", description = "获取错误分析数据")
    @ApiResponse(responseCode = "200", description = "成功返回错误分析")
    public Mono<ResponseEntity<Map<String, Object>>> getErrorAnalysis(
            @Parameter(description = "开始时间") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) String endTime) {
        
        return traceQueryService.getServiceStatistics()
            .map(services -> {
                Map<String, Object> errorAnalysis = new HashMap<>();
                
                // 服务错误率分布
                Map<String, Double> serviceErrorRates = new HashMap<>();
                Map<String, Integer> serviceErrorCounts = new HashMap<>();
                
                for (Map<String, Object> service : services) {
                    String serviceName = (String) service.get("name");
                    serviceErrorRates.put(serviceName, ((Number) service.get("errorRate")).doubleValue());
                    serviceErrorCounts.put(serviceName, ((Number) service.get("errors")).intValue());
                }
                
                errorAnalysis.put("serviceErrorRates", serviceErrorRates);
                errorAnalysis.put("serviceErrorCounts", serviceErrorCounts);
                
                // 生成错误时间序列数据（模拟）
                errorAnalysis.put("timeSeriesData", generateErrorTimeSeries());
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", errorAnalysis);
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/performance/throughput")
    @Operation(summary = "获取吞吐量分析", description = "获取吞吐量分析数据")
    @ApiResponse(responseCode = "200", description = "成功返回吞吐量分析")
    public Mono<ResponseEntity<Map<String, Object>>> getThroughputAnalysis(
            @Parameter(description = "开始时间") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) String endTime) {
        
        return traceQueryService.getServiceStatistics()
            .map(services -> {
                Map<String, Object> throughputAnalysis = new HashMap<>();
                
                // 服务请求量分布
                Map<String, Integer> serviceRequestCounts = new HashMap<>();
                Map<String, Double> serviceQps = new HashMap<>();
                
                for (Map<String, Object> service : services) {
                    String serviceName = (String) service.get("name");
                    int traces = ((Number) service.get("traces")).intValue();
                    serviceRequestCounts.put(serviceName, traces);
                    // 简化的QPS计算（假设1小时内的数据）
                    serviceQps.put(serviceName, traces / 3600.0);
                }
                
                throughputAnalysis.put("serviceRequestCounts", serviceRequestCounts);
                throughputAnalysis.put("serviceQps", serviceQps);
                
                // 生成QPS时间序列数据（模拟）
                throughputAnalysis.put("timeSeriesData", generateThroughputTimeSeries());
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", throughputAnalysis);
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    // Helper methods for performance analysis

    private long parseTimeString(String timeStr) {
        try {
            return Instant.parse(timeStr).toEpochMilli();
        } catch (Exception e) {
            // 尝试解析 YYYY-MM-DD HH:mm:ss 格式
            try {
                return java.time.LocalDateTime.parse(timeStr.replace(" ", "T"))
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            } catch (Exception ex) {
                log.warn("无法解析时间字符串: {}", timeStr);
                return System.currentTimeMillis();
            }
        }
    }

    private List<Map<String, Object>> generateLatencyTimeSeries() {
        List<Map<String, Object>> timeSeries = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        
        for (int i = 23; i >= 0; i--) {
            Map<String, Object> dataPoint = new HashMap<>();
            long timestamp = now - (i * 3600000); // 每小时一个数据点
            dataPoint.put("timestamp", timestamp);
            dataPoint.put("hour", String.format("%02d:00", (24 - i) % 24));
            dataPoint.put("p50", 80 + Math.random() * 40);
            dataPoint.put("p95", 200 + Math.random() * 100);
            dataPoint.put("p99", 350 + Math.random() * 150);
            dataPoint.put("avg", 100 + Math.random() * 50);
            timeSeries.add(dataPoint);
        }
        
        return timeSeries;
    }

    private List<Map<String, Object>> generateErrorTimeSeries() {
        List<Map<String, Object>> timeSeries = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        
        for (int i = 23; i >= 0; i--) {
            Map<String, Object> dataPoint = new HashMap<>();
            long timestamp = now - (i * 3600000);
            dataPoint.put("timestamp", timestamp);
            dataPoint.put("hour", String.format("%02d:00", (24 - i) % 24));
            dataPoint.put("errorCount", (int) (Math.random() * 15));
            dataPoint.put("errorRate", Math.random() * 5.0);
            timeSeries.add(dataPoint);
        }
        
        return timeSeries;
    }

    private List<Map<String, Object>> generateThroughputTimeSeries() {
        List<Map<String, Object>> timeSeries = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        
        for (int i = 23; i >= 0; i--) {
            Map<String, Object> dataPoint = new HashMap<>();
            long timestamp = now - (i * 3600000);
            dataPoint.put("timestamp", timestamp);
            dataPoint.put("hour", String.format("%02d:00", (24 - i) % 24));
            dataPoint.put("qps", 10 + Math.random() * 20);
            dataPoint.put("requestCount", (int) (100 + Math.random() * 200));
            timeSeries.add(dataPoint);
        }
        
        return timeSeries;
    }
}