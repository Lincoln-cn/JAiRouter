package org.unreal.modelrouter.monitor.callhistory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryQueryDTO;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.ApiCallHistoryEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API 调用历史控制器
 * 提供调用历史的记录、查询、统计和清理 REST API
 *
 * @author JAiRouter Team
 * @since 2.7.8
 */
@Slf4j
@RestController
@RequestMapping("/api/call-history")
@RequiredArgsConstructor
@Tag(name = "API 调用历史", description = "API 调用历史记录、查询和统计接口")
public class ApiCallHistoryController {

    private final ApiCallHistoryService callHistoryService;
    private final ApiCallHistoryRecorder callHistoryRecorder;

    /**
     * 记录调用历史（手动）
     */
    @PostMapping("/record")
    @Operation(summary = "记录调用历史", description = "手动记录一次 API 调用历史")
    public ResponseEntity<RouterResponse<Void>> record(
            @RequestBody final CallHistoryRecordDTO record) {

        callHistoryService.record(record);
        return ResponseEntity.ok(RouterResponse.success(null, "调用历史记录成功"));
    }

    /**
     * 分页查询调用历史
     */
    @GetMapping
    @Operation(summary = "分页查询调用历史", description = "根据条件分页查询 API 调用历史")
    public ResponseEntity<RouterResponse<Page<ApiCallHistoryEntity>>> query(
            @Parameter(description = "查询参数")
            @ModelAttribute CallHistoryQueryDTO query) {

        if (query == null) {
            query = new CallHistoryQueryDTO();
        }

        Page<ApiCallHistoryEntity> result = callHistoryService.query(query);
        return ResponseEntity.ok(RouterResponse.success(result));
    }

    /**
     * 根据 traceId 查询调用链路
     */
    @GetMapping("/trace/{traceId}")
    @Operation(summary = "根据 traceId 查询调用链路", description = "查询同一 traceId 下的所有调用记录")
    public ResponseEntity<RouterResponse<List<ApiCallHistoryEntity>>> findByTraceId(
            @Parameter(description = "追踪 ID")
            @PathVariable String traceId) {

        List<ApiCallHistoryEntity> records = callHistoryService.findByTraceId(traceId);
        return ResponseEntity.ok(RouterResponse.success(records));
    }

    /**
     * 获取最近的调用记录
     */
    @GetMapping("/recent")
    @Operation(summary = "获取最近的调用记录", description = "获取最近发生的 API 调用记录")
    public ResponseEntity<RouterResponse<List<ApiCallHistoryEntity>>> findRecent(
            @Parameter(description = "最大数量")
            @RequestParam(defaultValue = "20") int limit) {

        List<ApiCallHistoryEntity> records = callHistoryService.findRecent(limit);
        return ResponseEntity.ok(RouterResponse.success(records));
    }

    /**
     * 查询错误调用
     */
    @GetMapping("/errors")
    @Operation(summary = "查询错误调用", description = "查询失败的 API 调用记录")
    public ResponseEntity<RouterResponse<List<ApiCallHistoryEntity>>> findErrors(
            @Parameter(description = "开始时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,

            @Parameter(description = "结束时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,

            @Parameter(description = "最大数量")
            @RequestParam(defaultValue = "50") int limit) {

        List<ApiCallHistoryEntity> records = callHistoryService.findErrors(startTime, endTime, limit);
        return ResponseEntity.ok(RouterResponse.success(records));
    }

    /**
     * 查询慢调用
     */
    @GetMapping("/slow")
    @Operation(summary = "查询慢调用", description = "查询响应时间超过阈值的 API 调用记录")
    public ResponseEntity<RouterResponse<List<ApiCallHistoryEntity>>> findSlowCalls(
            @Parameter(description = "慢调用阈值 (毫秒)")
            @RequestParam(required = false) Long threshold,

            @Parameter(description = "开始时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,

            @Parameter(description = "结束时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,

            @Parameter(description = "最大数量")
            @RequestParam(defaultValue = "50") int limit) {

        List<ApiCallHistoryEntity> records = callHistoryService.findSlowCalls(threshold, startTime, endTime, limit);
        return ResponseEntity.ok(RouterResponse.success(records));
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取统计信息", description = "获取 API 调用历史的多维度统计信息")
    public ResponseEntity<RouterResponse<CallHistoryStatisticsDTO>> getStatistics(
            @Parameter(description = "开始时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,

            @Parameter(description = "结束时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        CallHistoryStatisticsDTO statistics = callHistoryService.getStatistics(startTime, endTime);
        return ResponseEntity.ok(RouterResponse.success(statistics));
    }

    /**
     * 获取仪表盘数据
     */
    @GetMapping("/dashboard")
    @Operation(summary = "获取仪表盘数据", description = "获取调用历史仪表盘所需的核心数据")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getDashboard(
            @Parameter(description = "开始时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,

            @Parameter(description = "结束时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        // 统计信息
        CallHistoryStatisticsDTO statistics = callHistoryService.getStatistics(startTime, endTime);

        // 最近调用
        List<ApiCallHistoryEntity> recentCalls = callHistoryService.findRecent(10);

        // 构建仪表盘数据
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("statistics", statistics);
        dashboard.put("recentCalls", recentCalls);
        dashboard.put("recorderStats", Map.of(
                "bufferSize", callHistoryRecorder.getBufferSize(),
                "totalRecords", callHistoryRecorder.getTotalRecords(),
                "droppedRecords", callHistoryRecorder.getDroppedRecords()
        ));

        return ResponseEntity.ok(RouterResponse.success(dashboard));
    }

    /**
     * 清理过期数据
     */
    @DeleteMapping("/cleanup")
    @Operation(summary = "清理过期数据", description = "删除指定时间之前的调用历史记录")
    public ResponseEntity<RouterResponse<Map<String, Object>>> cleanup(
            @Parameter(description = "截止时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cutoffTime) {

        int deletedCount;
        if (cutoffTime != null) {
            deletedCount = callHistoryService.cleanup(cutoffTime);
        } else {
            deletedCount = callHistoryService.cleanupByRetentionDays();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        result.put("cutoffTime", cutoffTime);

        return ResponseEntity.ok(RouterResponse.success(result, "成功删除 " + deletedCount + " 条调用历史记录"));
    }

    /**
     * 获取总记录数
     */
    @GetMapping("/count")
    @Operation(summary = "获取总记录数", description = "获取调用历史的总记录数")
    public ResponseEntity<RouterResponse<Map<String, Object>>> count() {
        long count = callHistoryService.countAll();

        Map<String, Object> result = new HashMap<>();
        result.put("count", count);

        return ResponseEntity.ok(RouterResponse.success(result));
    }
}
