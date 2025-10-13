package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.tracing.query.TraceQueryService;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
@Tag(name = "追踪查询", description = "追踪数据查询和分析API")
public class TracingQueryController {

    private final TraceQueryService traceQueryService;
    
    // 手动添加构造函数
    public TracingQueryController(TraceQueryService traceQueryService) {
        this.traceQueryService = traceQueryService;
    }
    
    @GetMapping("/trace/{traceId}")
    @Operation(summary = "获取追踪链路详情", description = "根据traceId获取完整的追踪链路信息")
    @ApiResponse(responseCode = "200", description = "成功返回追踪链路")
    @ApiResponse(responseCode = "404", description = "追踪链路不存在")
    public Mono<ResponseEntity<Map<String, Object>>> getTraceChain(
            @Parameter(description = "追踪ID") @PathVariable String traceId) {
        
        return traceQueryService.getTraceChain(traceId)
            .map(traceChain -> {
                if (traceChain == null) {
                    return ResponseEntity.notFound().<Map<String, Object>>build();
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", traceChain);
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().<Map<String, Object>>build());
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
            .onErrorReturn(ResponseEntity.internalServerError().<Map<String, Object>>build());
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
            .onErrorReturn(ResponseEntity.internalServerError().<Map<String, Object>>build());
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
            .onErrorReturn(ResponseEntity.internalServerError().<Map<String, Object>>build());
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
            .onErrorReturn(ResponseEntity.internalServerError().<Map<String, Object>>build());
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
            .onErrorReturn(ResponseEntity.internalServerError().<Map<String, Object>>build());
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
            .onErrorReturn(ResponseEntity.internalServerError().<Map<String, Object>>build());
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
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                
                // 构建延迟分析数据结构
                Map<String, Object> latencyData = new HashMap<>();
                
                // 服务延迟分布数据
                List<Map<String, Object>> distributionData = new ArrayList<>();
                for (Map<String, Object> service : services) {
                    Map<String, Object> serviceData = new HashMap<>();
                    serviceData.put("service", service.get("name"));
                    serviceData.put("avgLatency", service.get("avgDuration"));
                    serviceData.put("p95Latency", service.get("p95Duration"));
                    serviceData.put("p99Latency", service.get("p99Duration"));
                    distributionData.add(serviceData);
                }
                latencyData.put("distribution", distributionData);
                
                // 生成延迟趋势数据（模拟）
                List<Map<String, Object>> trendData = generateLatencyTimeSeries();
                latencyData.put("trend", trendData);
                
                response.put("data", latencyData);
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().<Map<String, Object>>build());
    }

    @GetMapping("/performance/errors")
    @Operation(summary = "获取错误分析", description = "获取错误分析数据")
    @ApiResponse(responseCode = "200", description = "成功返回错误分析")
    public Mono<ResponseEntity<Map<String, Object>>> getErrorAnalysis(
            @Parameter(description = "开始时间") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) String endTime) {
        
        return traceQueryService.getServiceStatistics()
            .map(services -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                
                // 构建错误分析数据结构
                Map<String, Object> errorData = new HashMap<>();
                
                // 错误率分布数据 - 适配前端期望的字段名
                List<Map<String, Object>> errorRateDistribution = new ArrayList<>();
                for (Map<String, Object> service : services) {
                    Map<String, Object> serviceData = new HashMap<>();
                    serviceData.put("service", service.get("name"));
                    serviceData.put("errorRate", service.get("errorRate"));
                    serviceData.put("totalRequests", service.get("traces"));
                    serviceData.put("errorCount", service.get("errors"));
                    errorRateDistribution.add(serviceData);
                }
                errorData.put("errorRateDistribution", errorRateDistribution);
                
                // 生成错误趋势数据（模拟）
                List<Map<String, Object>> errorTrend = generateErrorTimeSeries();
                errorData.put("errorTrend", errorTrend);
                
                // 常见错误数据（模拟）
                List<Map<String, Object>> commonErrors = new ArrayList<>();
                errorData.put("commonErrors", commonErrors);
                
                response.put("data", errorData);
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().<Map<String, Object>>build());
    }

    @GetMapping("/performance/throughput")
    @Operation(summary = "获取吞吐量分析", description = "获取吞吐量分析数据")
    @ApiResponse(responseCode = "200", description = "成功返回吞吐量分析")
    public Mono<ResponseEntity<Map<String, Object>>> getThroughputAnalysis(
            @Parameter(description = "开始时间") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) String endTime) {
        
        return traceQueryService.getServiceStatistics()
            .map(services -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                
                // 构建吞吐量分析数据结构
                Map<String, Object> throughputData = new HashMap<>();
                
                // 请求分布数据 - 适配前端期望的字段名
                List<Map<String, Object>> requestDistribution = new ArrayList<>();
                for (Map<String, Object> service : services) {
                    Map<String, Object> serviceData = new HashMap<>();
                    serviceData.put("service", service.get("name"));
                    serviceData.put("requestsPerSecond", ((Number) service.get("traces")).doubleValue() / 3600.0); // 简化计算
                    serviceData.put("totalRequests", service.get("traces"));
                    requestDistribution.add(serviceData);
                }
                throughputData.put("requestDistribution", requestDistribution);
                
                // 生成QPS趋势数据（模拟）
                List<Map<String, Object>> qpsTrend = generateThroughputTimeSeries();
                throughputData.put("qpsTrend", qpsTrend);
                
                response.put("data", throughputData);
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().<Map<String, Object>>build());
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
                System.out.println("无法解析时间字符串: " + timeStr);
                return System.currentTimeMillis();
            }
        }
    }

    private List<Map<String, Object>> generateLatencyTimeSeries() {
        List<Map<String, Object>> timeSeries = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        Random random = new SecureRandom();
        
        for (int i = 23; i >= 0; i--) {
            Map<String, Object> dataPoint = new HashMap<>();
            long timestamp = now - (i * 3600000); // 每小时一个数据点
            dataPoint.put("timestamp", timestamp);
            dataPoint.put("hour", String.format("%02d:00", (24 - i) % 24));
            dataPoint.put("p50", 80 + random.nextDouble() * 40);
            dataPoint.put("p95", 200 + random.nextDouble() * 100);
            dataPoint.put("p99", 350 + random.nextDouble() * 150);
            dataPoint.put("avg", 100 + random.nextDouble() * 50);
            timeSeries.add(dataPoint);
        }
        
        return timeSeries;
    }

    private List<Map<String, Object>> generateErrorTimeSeries() {
        List<Map<String, Object>> timeSeries = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        Random random = new SecureRandom();
        
        for (int i = 23; i >= 0; i--) {
            Map<String, Object> dataPoint = new HashMap<>();
            long timestamp = now - (i * 3600000);
            dataPoint.put("timestamp", timestamp);
            dataPoint.put("hour", String.format("%02d:00", (24 - i) % 24));
            dataPoint.put("errorCount", random.nextInt(15));
            dataPoint.put("errorRate", random.nextDouble() * 5.0);
            timeSeries.add(dataPoint);
        }
        
        return timeSeries;
    }

    private List<Map<String, Object>> generateThroughputTimeSeries() {
        List<Map<String, Object>> timeSeries = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        Random random = new SecureRandom();
        
        for (int i = 23; i >= 0; i--) {
            Map<String, Object> dataPoint = new HashMap<>();
            long timestamp = now - (i * 3600000);
            dataPoint.put("timestamp", timestamp);
            dataPoint.put("hour", String.format("%02d:00", (24 - i) % 24));
            dataPoint.put("qps", 10 + random.nextDouble() * 20);
            dataPoint.put("requestCount", 100 + random.nextInt(200));
            timeSeries.add(dataPoint);
        }
        
        return timeSeries;
    }
}