package org.unreal.modelrouter.monitor.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.PageRequest;
import org.unreal.modelrouter.auth.audit.ConfigChangeAuditEntity;
import org.unreal.modelrouter.auth.audit.ConfigChangeAuditRepository;
import org.unreal.modelrouter.persistence.jpa.entity.ConfigAuditLogEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ConfigAuditLogRepository;
import org.unreal.modelrouter.monitor.tracing.helper.ConsoleOperationNames;
import org.unreal.modelrouter.monitor.tracing.helper.ServiceNameResolver;
import org.unreal.modelrouter.monitor.tracing.query.TraceArchiveService;
import org.unreal.modelrouter.monitor.tracing.query.TraceQueryService;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final ConfigChangeAuditRepository configChangeAuditRepository;
    private final TraceArchiveService traceArchiveService;
    private final ConfigAuditLogRepository configAuditLogRepository;
    private final tools.jackson.databind.ObjectMapper auditObjectMapper;

    // 手动添加构造函数
    public TracingQueryController(
            final TraceQueryService traceQueryService,
            final ConfigChangeAuditRepository configChangeAuditRepository,
            final TraceArchiveService traceArchiveService,
            final ConfigAuditLogRepository configAuditLogRepository,
            final tools.jackson.databind.ObjectMapper auditObjectMapper) {
        this.traceQueryService = traceQueryService;
        this.configChangeAuditRepository = configChangeAuditRepository;
        this.configAuditLogRepository = configAuditLogRepository;
        this.auditObjectMapper = auditObjectMapper;
        this.traceArchiveService = traceArchiveService;
    }
    
    @GetMapping("/trace/{traceId}")
    @Operation(summary = "获取追踪链路详情", description = "根据traceId获取完整的追踪链路信息")
    @ApiResponse(responseCode = "200", description = "成功返回追踪链路")
    @ApiResponse(responseCode = "404", description = "追踪链路不存在")
    public Mono<ResponseEntity<Map<String, Object>>> getTraceChain(
            @Parameter(description = "追踪ID") @PathVariable final String traceId) {
        
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
            @Parameter(description = "开始时间戳（毫秒）") @RequestParam(required = false) final Long startTime,
            @Parameter(description = "结束时间戳（毫秒）") @RequestParam(required = false) final Long endTime,
            @Parameter(description = "服务名") @RequestParam(required = false) final String serviceName,
            @Parameter(description = "操作名") @RequestParam(required = false) final String operationName,
            @Parameter(description = "追踪ID") @RequestParam(required = false) final String traceId,
            @Parameter(description = "最小持续时间（毫秒）") @RequestParam(defaultValue = "0") final double minDuration,
            @Parameter(description = "最大持续时间（毫秒）") @RequestParam(defaultValue = "0") final double maxDuration,
            @Parameter(description = "是否有错误") @RequestParam(required = false) final Boolean hasError,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") final int page,
            @Parameter(description = "页大小") @RequestParam(defaultValue = "20") final int size) {
        
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

    @GetMapping("/archived/recent")
    @Operation(summary = "获取最近的已归档异常链路", description = "从持久化归档中读取异常链路（#1155）")
    @ApiResponse(responseCode = "200", description = "成功返回归档记录")
    public Mono<ResponseEntity<Map<String, Object>>> getArchivedRecent(
            @Parameter(description = "返回条数，1-200") @RequestParam(defaultValue = "50") final int limit) {

        final int safeLimit = Math.max(1, Math.min(200, limit));
        return Mono.fromCallable(() -> traceArchiveService.recent(safeLimit).stream()
                .map(entity -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("traceId", entity.getTraceId());
                    row.put("serviceName", entity.getService());
                    row.put("operationName", entity.getOperation());
                    row.put("startTime", entity.getCreatedAt() == null ? 0L : entity.getCreatedAt().toEpochMilli());
                    row.put("duration", entity.getDurationMs() == null ? 0.0 : entity.getDurationMs());
                    row.put("statusCode", entity.getStatusCode());
                    row.put("errorCount", entity.getErrorCount() == null ? 0 : entity.getErrorCount());
                    row.put("hasError", true);
                    row.put("archived", true);
                    return row;
                })
                .collect(Collectors.toList()))
            .subscribeOn(Schedulers.boundedElastic())
            .map(rows -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", rows);
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().<Map<String, Object>>build());
    }

    @GetMapping("/recent")
    @Operation(summary = "获取最近的追踪", description = "获取最近的追踪记录")
    @ApiResponse(responseCode = "200", description = "成功返回最近追踪")
    public Mono<ResponseEntity<Map<String, Object>>> getRecentTraces(
            @Parameter(description = "限制数量") @RequestParam(defaultValue = "50") final int limit) {
        
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

    @GetMapping("/console-operations")
    @Operation(summary = "查询控制台写操作", description = "按时间倒序返回最近的控制台写操作及其耗时、结果与操作人（#1154）")
    public Mono<ResponseEntity<Map<String, Object>>> getConsoleOperations(
            @Parameter(description = "返回条数，1-200") @RequestParam(defaultValue = "50") final int limit) {
        final int safeLimit = Math.max(1, Math.min(limit, 200));
        return traceQueryService.getRecentTraces(safeLimit * 5)
                .filter(summary -> ServiceNameResolver.SERVICE_CONSOLE.equals(summary.getServiceName()))
                .filter(summary -> ConsoleOperationNames.isReadable(summary.getOperationName()))
                .take(safeLimit)
                .collectList()
                .flatMap(summaries -> Mono.fromCallable(() -> enrichWithOperator(summaries))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(data -> ResponseEntity.ok(Map.<String, Object>of("success", true, "data", data)))
                .onErrorResume(error -> {
                    log.error("查询控制台操作失败", error);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(Map.<String, Object>of("success", false, "message", "查询控制台操作失败")));
                });
    }

    @GetMapping("/console-records")
    @Operation(summary = "查询管理操作记录",
            description = "以变更审计为主表（谁、何时、改了什么），追踪补耗时与状态；审计链路断开时该表只含存量数据（#1160）")
    public Mono<ResponseEntity<Map<String, Object>>> getConsoleRecords(
            @Parameter(description = "返回条数，1-200") @RequestParam(defaultValue = "50") final int limit) {
        final int safeLimit = Math.max(1, Math.min(limit, 200));
        return Mono.fromCallable(() -> {
            final List<ConfigAuditLogEntity> rows =
                    configAuditLogRepository.findRecent(PageRequest.of(0, safeLimit));
            final List<Map<String, Object>> records = new ArrayList<>(rows.size());
            for (ConfigAuditLogEntity row : rows) {
                records.add(toConsoleRecord(row));
            }
            return records;
        }).subscribeOn(Schedulers.boundedElastic())
                .map(data -> ResponseEntity.ok(Map.<String, Object>of("success", true, "data", data)))
                .onErrorResume(error -> {
                    log.error("查询管理操作记录失败", error);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(Map.<String, Object>of("success", false, "message", "查询管理操作记录失败")));
                });
    }

    /**
     * 把一条变更审计转成操作记录行。
     *
     * <p>{@code description} 是审计过滤器写入的 JSON，含 method / path / status / duration_ms / traceId；
     * 解析失败时这些字段为 null，界面按「不可用」显示，而不是伪造数值。</p>
     */
    private Map<String, Object> toConsoleRecord(final ConfigAuditLogEntity row) {
        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", row.getId());
        record.put("operation", row.getChangeType());
        record.put("path", row.getEntityId());
        record.put("operator", row.getUserId());
        record.put("timestamp", row.getTimestamp() != null ? row.getTimestamp().toEpochMilli() : 0L);
        final Map<String, Object> data = parseAuditData(row.getDescription());
        record.put("status", data.get("status"));
        record.put("duration", data.get("duration_ms"));
        record.put("traceId", row.getTraceId() != null ? row.getTraceId() : data.get("traceId"));
        return record;
    }

    private Map<String, Object> parseAuditData(final String description) {
        if (description == null || description.isEmpty()) {
            return Map.of();
        }
        try {
            final Map<String, Object> parsed = auditObjectMapper.readValue(description, Map.class);
            return parsed != null ? parsed : Map.of();
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    /**
     * 用 traceId 把「追踪里的耗时」与「审计里的操作人」对上（#1154）。
     *
     * <p>两个数据源天然不同步：追踪在内存里只留最近若干条，审计落库。用 traceId
     * 做左连接，查不到的 operator 就是 null，界面上显示「未知」而不是伪造一个。</p>
     */
    private List<Map<String, Object>> enrichWithOperator(final List<TraceQueryService.TraceSummary> summaries) {
        final List<String> traceIds = summaries.stream()
                .map(TraceQueryService.TraceSummary::getTraceId)
                .collect(Collectors.toList());
        final Map<String, String> operatorByTrace = new HashMap<>();
        if (!traceIds.isEmpty()) {
            for (final ConfigChangeAuditEntity audit : configChangeAuditRepository.findByTraceIdIn(traceIds)) {
                if (audit.getTraceId() != null && audit.getOperator() != null) {
                    operatorByTrace.putIfAbsent(audit.getTraceId(), audit.getOperator());
                }
            }
        }
        final List<Map<String, Object>> rows = new ArrayList<>(summaries.size());
        for (final TraceQueryService.TraceSummary summary : summaries) {
            final Map<String, Object> row = new HashMap<>();
            row.put("traceId", summary.getTraceId());
            row.put("operationName", summary.getOperationName());
            row.put("startTime", summary.getStartTime());
            row.put("duration", summary.getDuration());
            row.put("hasError", summary.isHasError());
            row.put("operator", operatorByTrace.get(summary.getTraceId()));
            rows.add(row);
        }
        return rows;
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
            @Parameter(description = "开始时间戳（毫秒）") @RequestParam(required = false) final Long startTime,
            @Parameter(description = "结束时间戳（毫秒）") @RequestParam(required = false) final Long endTime) {
        
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
            @RequestBody final TraceQueryService.TraceExportRequest exportRequest) {
        
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
            @Parameter(description = "保留小时数") @RequestParam(defaultValue = "24") final int retentionHours) {
        
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
            @Parameter(description = "服务名") @RequestParam(required = false) final String serviceName) {
        
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
            @Parameter(description = "开始时间") @RequestParam(required = false) final String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) final String endTime) {
        
        return Mono.fromCallable(() -> {
            // 基于现有的追踪统计数据生成性能统计
            long now = System.currentTimeMillis();
            long start = startTime != null ? parseTimeString(startTime) : now - 3600000;
            long end = endTime != null ? parseTimeString(endTime) : now;
            return new long[]{start, end};
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(timeRange -> {
            return traceQueryService.getTraceStatistics(timeRange[0], timeRange[1]);
        })
        .map(stats -> {
            Map<String, Object> performanceStats = new HashMap<>();
            performanceStats.put("totalRequests", stats.getTotalTraces());
            performanceStats.put("successfulRequests", stats.getSuccessfulTraces());
            performanceStats.put("errorRequests", stats.getErrorTraces());
            performanceStats.put("avgLatency", stats.getAvgDuration());
            performanceStats.put("maxLatency", stats.getMaxDuration());
            performanceStats.put("minLatency", stats.getMinDuration());
            performanceStats.put("errorRate", stats.getTotalTraces() > 0
                ? (double) stats.getErrorTraces() / stats.getTotalTraces() * 100 : 0.0);

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
            @Parameter(description = "开始时间") @RequestParam(required = false) final String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) final String endTime) {
        
        long endMs = endTime != null ? parseTimeString(endTime) : System.currentTimeMillis();
        long startMs = startTime != null ? parseTimeString(startTime) : endMs - 3600000L;
        long bucketMs = Math.max(60_000L, (endMs - startMs) / 24L);

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
                
                // 延迟趋势：按时间桶聚合本副本真实 trace（见 #1150）
                List<Map<String, Object>> trendData = traceQueryService.latencyTrend(startMs, endMs, bucketMs);
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
            @Parameter(description = "开始时间") @RequestParam(required = false) final String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) final String endTime) {
        
        long endMs = endTime != null ? parseTimeString(endTime) : System.currentTimeMillis();
        long startMs = startTime != null ? parseTimeString(startTime) : endMs - 3600000L;
        long bucketMs = Math.max(60_000L, (endMs - startMs) / 24L);

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
                
                // 错误趋势：按时间桶聚合本副本真实 trace（见 #1150）
                List<Map<String, Object>> errorTrend = traceQueryService.errorTrend(startMs, endMs, bucketMs);
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
            @Parameter(description = "开始时间") @RequestParam(required = false) final String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) final String endTime) {
        
        long endMs = endTime != null ? parseTimeString(endTime) : System.currentTimeMillis();
        long startMs = startTime != null ? parseTimeString(startTime) : endMs - 3600000L;
        long bucketMs = Math.max(60_000L, (endMs - startMs) / 24L);

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
                    serviceData.put("requestsPerSecond",
                            ((Number) service.get("traces")).doubleValue() / 3600.0);
                    serviceData.put("totalRequests", service.get("traces"));
                    requestDistribution.add(serviceData);
                }
                throughputData.put("requestDistribution", requestDistribution);
                
                // 吞吐趋势：按时间桶聚合本副本真实 trace（见 #1150）
                List<Map<String, Object>> qpsTrend = traceQueryService.throughputTrend(startMs, endMs, bucketMs);
                throughputData.put("qpsTrend", qpsTrend);
                
                response.put("data", throughputData);
                return ResponseEntity.ok(response);
            })
            .onErrorReturn(ResponseEntity.internalServerError().<Map<String, Object>>build());
    }

    // Helper methods for performance analysis

    private long parseTimeString(final String timeStr) {
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
                log.warn("无法解析时间字符串: {}, 使用当前时间", timeStr);
                return System.currentTimeMillis();
            }
        }
    }

}
