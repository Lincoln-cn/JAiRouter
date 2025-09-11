package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.tracing.query.TraceQueryService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 追踪数据查询控制器
 * 
 * 提供追踪数据查询和分析的REST API接口，包括：
 * - 基于traceId的完整链路查询
 * - 基于条件的追踪搜索
 * - 追踪统计和聚合分析
 * - 追踪数据导出功能
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/tracing/query")
@RequiredArgsConstructor
@Tag(name = "追踪数据查询", description = "追踪数据查询和分析API")
public class TracingQueryController {

    private final TraceQueryService traceQueryService;

    @GetMapping("/trace/{traceId}")
    @Operation(summary = "查询追踪链路", description = "根据traceId查询完整的追踪链路信息")
    @ApiResponse(responseCode = "200", description = "成功返回追踪链路")
    @ApiResponse(responseCode = "404", description = "未找到指定的追踪")
    public Mono<ResponseEntity<TraceQueryService.TraceChain>> getTraceChain(
            @Parameter(description = "追踪ID") @PathVariable String traceId) {
        
        return traceQueryService.getTraceChain(traceId)
            .map(traceChain -> {
                if (traceChain != null) {
                    return ResponseEntity.ok(traceChain);
                } else {
                    return ResponseEntity.notFound().<TraceQueryService.TraceChain>build();
                }
            })
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    @Operation(summary = "搜索追踪数据", description = "根据指定条件搜索追踪数据")
    @ApiResponse(responseCode = "200", description = "成功返回搜索结果")
    public Flux<TraceQueryService.TraceSummary> searchTraces(
            @Parameter(description = "开始时间") @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            
            @Parameter(description = "结束时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            
            @Parameter(description = "服务名称") @RequestParam(required = false) String serviceName,
            
            @Parameter(description = "操作名称") @RequestParam(required = false) String operationName,
            
            @Parameter(description = "最小持续时间（毫秒）") @RequestParam(defaultValue = "0") double minDuration,
            
            @Parameter(description = "最大持续时间（毫秒）") @RequestParam(defaultValue = "0") double maxDuration,
            
            @Parameter(description = "是否包含错误") @RequestParam(required = false) Boolean hasError,
            
            @Parameter(description = "限制数量") @RequestParam(defaultValue = "100") int limit) {
        
        TraceQueryService.TraceSearchCriteria criteria = new TraceQueryService.TraceSearchCriteria();
        
        if (startTime != null) {
            criteria.setStartTime(startTime.toInstant(ZoneOffset.UTC));
        }
        if (endTime != null) {
            criteria.setEndTime(endTime.toInstant(ZoneOffset.UTC));
        }
        criteria.setServiceName(serviceName);
        criteria.setOperationName(operationName);
        criteria.setMinDuration(minDuration);
        criteria.setMaxDuration(maxDuration);
        criteria.setHasError(hasError);
        criteria.setLimit(limit);
        
        return traceQueryService.searchTraces(criteria);
    }

    @GetMapping("/recent")
    @Operation(summary = "获取最近追踪", description = "获取最近的追踪记录")
    @ApiResponse(responseCode = "200", description = "成功返回最近追踪")
    public Flux<TraceQueryService.TraceSummary> getRecentTraces(
            @Parameter(description = "限制数量") @RequestParam(defaultValue = "50") int limit) {
        
        return traceQueryService.getRecentTraces(Math.min(limit, 500)); // 最大限制500条
    }

    @GetMapping("/statistics")
    @Operation(summary = "获取追踪统计", description = "获取指定时间范围内的追踪统计信息")
    @ApiResponse(responseCode = "200", description = "成功返回统计信息")
    public Mono<ResponseEntity<TraceQueryService.TraceStatistics>> getTraceStatistics(
            @Parameter(description = "开始时间戳（毫秒）") @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间戳（毫秒）") @RequestParam(required = false) Long endTime) {
        
        // 设置默认时间范围（最近1小时）
        long now = System.currentTimeMillis();
        long start = startTime != null ? startTime : now - 3600000; // 1小时前
        long end = endTime != null ? endTime : now;
        
        return traceQueryService.getTraceStatistics(start, end)
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @PostMapping("/export")
    @Operation(summary = "导出追踪数据", description = "导出指定条件的追踪数据")
    @ApiResponse(responseCode = "200", description = "成功导出数据")
    public Mono<ResponseEntity<TraceQueryService.TraceExportResult>> exportTraces(
            @RequestBody ExportRequest request) {
        
        TraceQueryService.TraceExportRequest exportRequest = new TraceQueryService.TraceExportRequest(
            request.getStartTime(),
            request.getEndTime(),
            request.getFormat(),
            request.getMaxRecords()
        );
        
        return traceQueryService.exportTraces(exportRequest)
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/services")
    @Operation(summary = "获取服务列表", description = "获取所有有追踪数据的服务列表")
    @ApiResponse(responseCode = "200", description = "成功返回服务列表")
    public Mono<ResponseEntity<Map<String, Object>>> getServices() {
        return Mono.fromCallable(() -> {
            // 这里应该从实际的追踪数据中获取服务列表
            // 现在返回模拟数据
            Map<String, Object> services = new HashMap<>();
            services.put("services", List.of(
                "jairouter-gateway",
                "model-adapter-service",
                "load-balancer-service",
                "rate-limiter-service"
            ));
            services.put("count", 4);
            services.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(services);
        });
    }

    @GetMapping("/operations")
    @Operation(summary = "获取操作列表", description = "获取指定服务的操作列表")
    @ApiResponse(responseCode = "200", description = "成功返回操作列表")
    public Mono<ResponseEntity<Map<String, Object>>> getOperations(
            @Parameter(description = "服务名称") @RequestParam(required = false) String serviceName) {
        
        return Mono.fromCallable(() -> {
            // 这里应该从实际的追踪数据中获取操作列表
            // 现在返回模拟数据
            Map<String, Object> operations = new HashMap<>();
            
            if (serviceName != null) {
                operations.put("serviceName", serviceName);
                operations.put("operations", List.of(
                    "GET /v1/chat/completions",
                    "POST /v1/embeddings",
                    "GET /v1/models",
                    "POST /v1/rerank"
                ));
            } else {
                operations.put("operations", List.of(
                    "http.request",
                    "load.balance",
                    "rate.limit",
                    "circuit.break",
                    "adapter.call"
                ));
            }
            
            operations.put("count", ((List<?>) operations.get("operations")).size());
            operations.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(operations);
        });
    }

    @PostMapping("/cleanup")
    @Operation(summary = "清理过期数据", description = "清理过期的追踪数据")
    @ApiResponse(responseCode = "200", description = "成功清理数据")
    public Mono<ResponseEntity<Map<String, Object>>> cleanupExpiredTraces(
            @Parameter(description = "保留时间（小时）") @RequestParam(defaultValue = "24") int retentionHours) {
        
        long maxAgeMillis = retentionHours * 3600000L; // 转换为毫秒
        
        return traceQueryService.cleanupExpiredTraces(maxAgeMillis)
            .map(removedCount -> {
                Map<String, Object> result = new HashMap<>();
                result.put("removedCount", removedCount);
                result.put("retentionHours", retentionHours);
                result.put("timestamp", LocalDateTime.now());
                result.put("message", "清理完成，移除 " + removedCount + " 条过期记录");
                
                return ResponseEntity.ok(result);
            })
            .onErrorReturn(ResponseEntity.internalServerError()
                .body(Map.of("error", "清理失败", "timestamp", LocalDateTime.now())));
    }

    @GetMapping("/health")
    @Operation(summary = "查询服务健康状态", description = "获取追踪查询服务的健康状态")
    @ApiResponse(responseCode = "200", description = "成功返回健康状态")
    public Mono<ResponseEntity<Map<String, Object>>> getQueryServiceHealth() {
        return Mono.fromCallable(() -> {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("service", "TraceQueryService");
            health.put("timestamp", LocalDateTime.now());
            health.put("message", "追踪查询服务运行正常");
            
            return ResponseEntity.ok(health);
        });
    }

    // 请求DTO类
    @lombok.Data
    public static class ExportRequest {
        private Instant startTime;
        private Instant endTime;
        private String format = "json"; // json, csv
        private int maxRecords = 1000;
    }
}