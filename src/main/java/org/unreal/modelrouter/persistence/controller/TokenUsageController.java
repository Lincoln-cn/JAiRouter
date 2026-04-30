package org.unreal.modelrouter.persistence.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.monitor.dto.TokenUsageRecordDTO;
import org.unreal.modelrouter.monitor.dto.TokenUsageStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.TokenUsageEntity;
import org.unreal.modelrouter.monitor.service.TokenUsageService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Token 使用量统计控制器
 * 提供 Token 使用量的记录、查询和统计 REST API
 *
 * @author JAiRouter Team
 * @since 1.9.5
 */
@Slf4j
@RestController
@RequestMapping("/api/token-usage")
@RequiredArgsConstructor
@Tag(name = "Token 使用量统计", description = "Token 使用量记录、查询和统计接口")
public class TokenUsageController {

    private final TokenUsageService tokenUsageService;

    /**
     * 记录 Token 使用量
     */
    @PostMapping("/record")
    @Operation(summary = "记录 Token 使用量", description = "记录单次 AI 模型调用的 Token 使用量")
    public ResponseEntity<RouterResponse<Void>> recordTokenUsage(
            @RequestBody final TokenUsageRecordDTO record) {

        tokenUsageService.recordTokenUsage(record);
        return ResponseEntity.ok(RouterResponse.success(null, "Token 使用量记录成功"));
    }

    /**
     * 批量记录 Token 使用量
     */
    @PostMapping("/record/batch")
    @Operation(summary = "批量记录 Token 使用量", description = "批量记录 AI 模型调用的 Token 使用量")
    public ResponseEntity<RouterResponse<Void>> recordTokenUsageBatch(
            @RequestBody final List<TokenUsageRecordDTO> records) {

        tokenUsageService.recordTokenUsageBatch(records);
        return ResponseEntity.ok(RouterResponse.success(null, "批量记录成功"));
    }

    /**
     * 获取 Token 使用量统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取 Token 使用量统计信息", description = "获取指定时间范围内的 Token 使用量统计信息，包括按模型、服务类型、周、月等维度的统计")
    public ResponseEntity<RouterResponse<TokenUsageStatisticsDTO>> getTokenUsageStatistics(
            @Parameter(description = "开始时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,

            @Parameter(description = "结束时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime) {

        TokenUsageStatisticsDTO statistics = tokenUsageService.getTokenUsageStatistics(startTime, endTime);
        return ResponseEntity.ok(RouterResponse.success(statistics));
    }

    /**
     * 获取最近的使用记录
     */
    @GetMapping("/recent")
    @Operation(summary = "获取最近的使用记录", description = "获取最近发生的 Token 使用记录列表")
    public ResponseEntity<RouterResponse<List<TokenUsageEntity>>> getRecentUsage(
            @Parameter(description = "最大数量")
            @RequestParam(defaultValue = "20")
            int limit) {

        List<TokenUsageEntity> records = tokenUsageService.getRecentUsage(limit);
        return ResponseEntity.ok(RouterResponse.success(records));
    }

    /**
     * 获取指定模型的最近使用记录
     */
    @GetMapping("/recent/{modelName}")
    @Operation(summary = "获取指定模型的最近使用记录", description = "获取指定模型的最近使用记录列表")
    public ResponseEntity<RouterResponse<List<TokenUsageEntity>>> getRecentUsageByModel(
            @Parameter(description = "模型名称")
            @PathVariable String modelName,

            @Parameter(description = "最大数量")
            @RequestParam(defaultValue = "20")
            int limit) {

        List<TokenUsageEntity> records = tokenUsageService.getRecentUsageByModel(modelName, limit);
        return ResponseEntity.ok(RouterResponse.success(records));
    }

    /**
     * 获取模型使用量排名
     */
    @GetMapping("/top/models")
    @Operation(summary = "获取模型使用量排名", description = "获取 Token 使用量最高的模型排名")
    public ResponseEntity<RouterResponse<List<Map<String, Object>>>> getTopModels(
            @Parameter(description = "开始时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,

            @Parameter(description = "结束时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime,

            @Parameter(description = "最大数量")
            @RequestParam(defaultValue = "10")
            int limit) {

        // 设置默认时间范围（最近 7 天）
        LocalDateTime effectiveStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        LocalDateTime effectiveEndTime = endTime != null ? endTime : LocalDateTime.now();

        List<Map<String, Object>> topModels = tokenUsageService.getTopModels(effectiveStartTime, effectiveEndTime, limit);
        return ResponseEntity.ok(RouterResponse.success(topModels));
    }

    /**
     * 获取服务类型使用量排名
     */
    @GetMapping("/top/services")
    @Operation(summary = "获取服务类型使用量排名", description = "获取 Token 使用量最高的服务类型排名")
    public ResponseEntity<RouterResponse<List<Map<String, Object>>>> getTopServiceTypes(
            @Parameter(description = "开始时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,

            @Parameter(description = "结束时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime,

            @Parameter(description = "最大数量")
            @RequestParam(defaultValue = "10")
            int limit) {

        // 设置默认时间范围（最近 7 天）
        LocalDateTime effectiveStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        LocalDateTime effectiveEndTime = endTime != null ? endTime : LocalDateTime.now();

        List<Map<String, Object>> topServiceTypes = tokenUsageService.getTopServiceTypes(effectiveStartTime, effectiveEndTime, limit);
        return ResponseEntity.ok(RouterResponse.success(topServiceTypes));
    }

    /**
     * 获取仪表盘数据
     */
    @GetMapping("/dashboard")
    @Operation(summary = "获取仪表盘数据", description = "获取 Token 使用量仪表盘所需的核心数据")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getDashboardData(
            @Parameter(description = "开始时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,

            @Parameter(description = "结束时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime) {

        // 设置默认时间范围（最近 7 天）
        LocalDateTime effectiveStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        LocalDateTime effectiveEndTime = endTime != null ? endTime : LocalDateTime.now();

        // 获取统计信息
        TokenUsageStatisticsDTO statistics = tokenUsageService.getTokenUsageStatistics(effectiveStartTime, effectiveEndTime);

        // 获取模型排名
        List<Map<String, Object>> topModels = tokenUsageService.getTopModels(effectiveStartTime, effectiveEndTime, 5);

        // 获取服务类型排名
        List<Map<String, Object>> topServiceTypes = tokenUsageService.getTopServiceTypes(effectiveStartTime, effectiveEndTime, 5);

        // 获取最近使用记录
        List<TokenUsageEntity> recentUsage = tokenUsageService.getRecentUsage(10);

        // 构建仪表盘数据
        Map<String, Object> dashboardData = new HashMap<>();
        dashboardData.put("statistics", statistics);
        dashboardData.put("topModels", topModels);
        dashboardData.put("topServiceTypes", topServiceTypes);
        dashboardData.put("recentUsage", recentUsage);
        dashboardData.put("timeRange", Map.of(
                "startTime", statistics.getStartTime(),
                "endTime", statistics.getEndTime()
        ));

        return ResponseEntity.ok(RouterResponse.success(dashboardData));
    }

    /**
     * 删除过期使用记录
     */
    @DeleteMapping("/cleanup")
    @Operation(summary = "删除过期使用记录", description = "删除指定时间之前的 Token 使用记录")
    public ResponseEntity<RouterResponse<Map<String, Object>>> deleteOldUsageRecords(
            @Parameter(description = "截止时间 (ISO-8601)")
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime cutoffTime) {

        int deletedCount = tokenUsageService.deleteOldUsageRecords(cutoffTime);

        Map<String, Object> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        result.put("cutoffTime", cutoffTime);

        return ResponseEntity.ok(RouterResponse.success(result, "成功删除 " + deletedCount + " 条 Token 使用记录"));
    }
}
