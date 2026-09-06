package org.unreal.modelrouter.persistence.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.monitor.dto.TokenUsageRecordDTO;
import org.unreal.modelrouter.monitor.dto.TokenUsageStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.TokenUsageEntity;
import org.unreal.modelrouter.monitor.service.TokenUsageService;

import java.time.LocalDateTime;
import java.util.List;

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

}
