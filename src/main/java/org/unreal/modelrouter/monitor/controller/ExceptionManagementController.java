package org.unreal.modelrouter.monitor.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.ExceptionEventDTO;
import org.unreal.modelrouter.common.dto.ExceptionQueryRequest;
import org.unreal.modelrouter.common.dto.ExceptionStatisticsDTO;
import org.unreal.modelrouter.common.dto.PagedResult;
import org.unreal.modelrouter.service.ExceptionManagementService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 异常管理控制器
 * 提供异常事件的查询、统计和管理 REST API
 * 
 * @author JAiRouter Team
 * @since 1.9.2
 */
@Slf4j
@RestController
@RequestMapping("/api/exceptions")
@RequiredArgsConstructor
@Tag(name = "异常管理", description = "异常事件查询、统计和管理接口")
public class ExceptionManagementController {

    private final ExceptionManagementService exceptionManagementService;

    /**
     * 查询异常事件列表
     */
    @GetMapping
    @Operation(summary = "查询异常事件列表", description = "根据条件查询异常事件，支持分页、筛选和排序")
    public ResponseEntity<RouterResponse<PagedResult<ExceptionEventDTO>>> queryExceptionEvents(
            @Parameter(description = "开始时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            final LocalDateTime startTime,

            @Parameter(description = "结束时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            final LocalDateTime endTime,

            @Parameter(description = "异常类型（支持模糊匹配）")
            @RequestParam(required = false)
            final String exceptionType,

            @Parameter(description = "操作名称（支持模糊匹配）")
            @RequestParam(required = false)
            final String operation,

            @Parameter(description = "错误代码")
            @RequestParam(required = false)
            final String errorCode,

            @Parameter(description = "错误分类")
            @RequestParam(required = false)
            final String errorCategory,

            @Parameter(description = "追踪 ID")
            @RequestParam(required = false)
            final String traceId,

            @Parameter(description = "客户端 IP")
            @RequestParam(required = false)
            final String clientIp,

            @Parameter(description = "是否仅查询聚合事件")
            @RequestParam(required = false)
            final Boolean aggregatedOnly,

            @Parameter(description = "页码，从 0 开始")
            @RequestParam(defaultValue = "0")
            final int page,

            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20")
            final int size,

            @Parameter(description = "排序字段")
            @RequestParam(defaultValue = "occurredAt")
            final String sortBy,

            @Parameter(description = "排序方向 (asc 或 desc)")
            @RequestParam(defaultValue = "desc")
            final String sortDirection) {

        // 构建查询请求
        ExceptionQueryRequest request = ExceptionQueryRequest.builder()
                .startTime(startTime)
                .endTime(endTime)
                .exceptionType(exceptionType)
                .operation(operation)
                .errorCode(errorCode)
                .errorCategory(errorCategory)
                .traceId(traceId)
                .clientIp(clientIp)
                .aggregatedOnly(aggregatedOnly)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .build();

        // 执行查询
        PagedResult<ExceptionEventDTO> result = exceptionManagementService.queryExceptionEvents(request);

        return ResponseEntity.ok(RouterResponse.success(result));
    }

    /**
     * 根据 ID 查询异常事件详情
     */
    @GetMapping("/{eventId}")
    @Operation(summary = "查询异常事件详情", description = "根据事件 ID 查询异常事件的完整信息")
    public ResponseEntity<RouterResponse<ExceptionEventDTO>> getExceptionEvent(
            @Parameter(description = "事件 ID")
            @PathVariable final String eventId) {

        ExceptionEventDTO event = exceptionManagementService.getExceptionEventById(eventId);
        
        if (event == null) {
            return ResponseEntity.ok(RouterResponse.error("事件不存在", "404"));
        }

        return ResponseEntity.ok(RouterResponse.success(event));
    }

    /**
     * 获取异常统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取异常统计信息", description = "获取指定时间范围内的异常统计信息，包括按类型、分类、操作等维度的统计")
    public ResponseEntity<RouterResponse<ExceptionStatisticsDTO>> getExceptionStatistics(
            @Parameter(description = "开始时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            final LocalDateTime startTime,

            @Parameter(description = "结束时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            final LocalDateTime endTime) {

        ExceptionStatisticsDTO statistics = exceptionManagementService.getExceptionStatistics(startTime, endTime);
        return ResponseEntity.ok(RouterResponse.success(statistics));
    }

    /**
     * 获取最近的异常事件
     */
    @GetMapping("/recent")
    @Operation(summary = "获取最近的异常事件", description = "获取最近发生的异常事件列表")
    public ResponseEntity<RouterResponse<List<ExceptionEventDTO>>> getRecentExceptionEvents(
            @Parameter(description = "最大数量")
            @RequestParam(defaultValue = "10")
            final int limit) {

        List<ExceptionEventDTO> events = exceptionManagementService.getRecentExceptionEvents(limit);
        return ResponseEntity.ok(RouterResponse.success(events));
    }

    /**
     * 获取指定类型的最近异常事件
     */
    @GetMapping("/recent/{exceptionType}")
    @Operation(summary = "获取指定类型的最近异常事件", description = "获取指定异常类型的最近事件列表")
    public ResponseEntity<RouterResponse<List<ExceptionEventDTO>>> getRecentExceptionEventsByType(
            @Parameter(description = "异常类型")
            @PathVariable final String exceptionType,

            @Parameter(description = "最大数量")
            @RequestParam(defaultValue = "10")
            final int limit) {

        List<ExceptionEventDTO> events = exceptionManagementService.getRecentExceptionEventsByType(exceptionType, limit);
        return ResponseEntity.ok(RouterResponse.success(events));
    }

    /**
     * 删除过期异常事件
     */
    @DeleteMapping("/cleanup")
    @Operation(summary = "删除过期异常事件", description = "删除指定时间之前的异常事件，可选择仅删除已聚合的事件")
    public ResponseEntity<RouterResponse<Map<String, Object>>> deleteOldExceptionEvents(
            @Parameter(description = "截止时间 (ISO-8601)")
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            final LocalDateTime cutoffTime,

            @Parameter(description = "是否仅删除已聚合的事件")
            @RequestParam(defaultValue = "false")
            final boolean aggregatedOnly) {

        int deletedCount;
        if (aggregatedOnly) {
            deletedCount = exceptionManagementService.deleteAggregatedExceptionEvents(cutoffTime);
        } else {
            deletedCount = exceptionManagementService.deleteOldExceptionEvents(cutoffTime);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        result.put("cutoffTime", cutoffTime);
        result.put("aggregatedOnly", aggregatedOnly);

        return ResponseEntity.ok(RouterResponse.success(result, "成功删除 " + deletedCount + " 条异常事件"));
    }

    /**
     * 获取异常管理仪表盘数据
     */
    @GetMapping("/dashboard")
    @Operation(summary = "获取仪表盘数据", description = "获取异常管理仪表盘所需的核心数据")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getDashboardData(
            @Parameter(description = "开始时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            final LocalDateTime startTime,

            @Parameter(description = "结束时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            final LocalDateTime endTime) {

        // 获取统计信息
        ExceptionStatisticsDTO statistics = exceptionManagementService.getExceptionStatistics(startTime, endTime);
        
        // 获取最近事件
        List<ExceptionEventDTO> recentEvents = exceptionManagementService.getRecentExceptionEvents(5);

        // 构建仪表盘数据
        Map<String, Object> dashboardData = new HashMap<>();
        dashboardData.put("statistics", statistics);
        dashboardData.put("recentEvents", recentEvents);
        dashboardData.put("timeRange", Map.of(
                "startTime", statistics.getStartTime(),
                "endTime", statistics.getEndTime()
        ));

        return ResponseEntity.ok(RouterResponse.success(dashboardData));
    }
}
