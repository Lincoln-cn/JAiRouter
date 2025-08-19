package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.dto.SecurityAuditQueryRequest;
import org.unreal.modelrouter.dto.SecurityAuditQueryResponse;
import org.unreal.modelrouter.dto.SecurityStatisticsResponse;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.monitoring.security.SecurityAlertService;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 安全审计日志查询控制器
 * 提供安全日志的查询和分析接口
 */
@Slf4j
@RestController
@RequestMapping("/api/security/audit")
@RequiredArgsConstructor
@Tag(name = "安全审计", description = "安全审计日志查询和统计接口")
@ConditionalOnProperty(name = "jairouter.security.audit.api.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityAuditController {
    
    private final SecurityAuditService auditService;
    private final SecurityAlertService alertService;
    
    /**
     * 查询安全审计日志
     */
    @GetMapping("/logs")
    @Operation(summary = "查询安全审计日志", description = "根据条件查询安全审计日志，支持分页和过滤")
    public Mono<ResponseEntity<SecurityAuditQueryResponse>> queryAuditLogs(
            @Parameter(description = "开始时间") 
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime startTime,
            
            @Parameter(description = "结束时间") 
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime endTime,
            
            @Parameter(description = "事件类型") 
            @RequestParam(required = false) 
            String eventType,
            
            @Parameter(description = "用户ID") 
            @RequestParam(required = false) 
            String userId,
            
            @Parameter(description = "客户端IP") 
            @RequestParam(required = false) 
            String clientIp,
            
            @Parameter(description = "操作是否成功") 
            @RequestParam(required = false) 
            Boolean success,
            
            @Parameter(description = "页码，从0开始") 
            @RequestParam(defaultValue = "0") 
            int page,
            
            @Parameter(description = "每页大小") 
            @RequestParam(defaultValue = "20") 
            int size) {
        
        // 设置默认时间范围（如果未提供）
        final LocalDateTime finalStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        final LocalDateTime finalEndTime = endTime != null ? endTime : LocalDateTime.now();
        
        // 限制查询大小
        final int finalSize = Math.min(size, 100); // 最大100条
        final int finalPage = page;
        int limit = finalSize * (finalPage + 1); // 计算总限制数
        
        return auditService.queryEvents(finalStartTime, finalEndTime, eventType, userId, limit)
                .filter(event -> clientIp == null || clientIp.equals(event.getClientIp()))
                .filter(event -> success == null || success.equals(event.isSuccess()))
                .skip(finalPage * finalSize)
                .take(finalSize)
                .collectList()
                .map(events -> {
                    SecurityAuditQueryResponse response = SecurityAuditQueryResponse.builder()
                            .events(events)
                            .page(finalPage)
                            .size(finalSize)
                            .totalElements(events.size())
                            .startTime(finalStartTime)
                            .endTime(finalEndTime)
                            .build();
                    
                    return ResponseEntity.ok(response);
                })
                .doOnSuccess(response -> log.debug("查询安全审计日志完成: page={}, size={}, eventCount={}", 
                        finalPage, finalSize, response.getBody().getEvents().size()));
    }
    
    /**
     * 使用POST方法进行复杂查询
     */
    @PostMapping("/logs/query")
    @Operation(summary = "复杂条件查询安全审计日志", description = "支持更复杂的查询条件和过滤器")
    public Mono<ResponseEntity<SecurityAuditQueryResponse>> queryAuditLogsAdvanced(
            @RequestBody SecurityAuditQueryRequest request) {
        
        // 验证请求参数
        if (request.getStartTime() == null) {
            request.setStartTime(LocalDateTime.now().minusDays(7));
        }
        if (request.getEndTime() == null) {
            request.setEndTime(LocalDateTime.now());
        }
        if (request.getSize() <= 0 || request.getSize() > 100) {
            request.setSize(20);
        }
        if (request.getPage() < 0) {
            request.setPage(0);
        }
        
        final SecurityAuditQueryRequest finalRequest = request;
        int limit = finalRequest.getSize() * (finalRequest.getPage() + 1);
        
        return auditService.queryEvents(
                finalRequest.getStartTime(), 
                finalRequest.getEndTime(), 
                finalRequest.getEventType(), 
                finalRequest.getUserId(), 
                limit)
                .filter(event -> applyAdvancedFilters(event, finalRequest))
                .skip(finalRequest.getPage() * finalRequest.getSize())
                .take(finalRequest.getSize())
                .collectList()
                .map(events -> {
                    SecurityAuditQueryResponse response = SecurityAuditQueryResponse.builder()
                            .events(events)
                            .page(finalRequest.getPage())
                            .size(finalRequest.getSize())
                            .totalElements(events.size())
                            .startTime(finalRequest.getStartTime())
                            .endTime(finalRequest.getEndTime())
                            .build();
                    
                    return ResponseEntity.ok(response);
                });
    }
    
    /**
     * 获取安全统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取安全统计信息", description = "获取指定时间范围内的安全事件统计信息")
    public Mono<ResponseEntity<SecurityStatisticsResponse>> getSecurityStatistics(
            @Parameter(description = "开始时间") 
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime startTime,
            
            @Parameter(description = "结束时间") 
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime endTime) {
        
        // 设置默认时间范围
        final LocalDateTime finalStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(1);
        final LocalDateTime finalEndTime = endTime != null ? endTime : LocalDateTime.now();
        
        return auditService.getSecurityStatistics(finalStartTime, finalEndTime)
                .map(statistics -> {
                    // 获取告警统计
                    Map<String, Object> alertStats = alertService.getAlertStatistics();
                    
                    SecurityStatisticsResponse response = SecurityStatisticsResponse.builder()
                            .startTime(finalStartTime)
                            .endTime(finalEndTime)
                            .auditStatistics(statistics)
                            .alertStatistics(alertStats)
                            .generatedAt(LocalDateTime.now())
                            .build();
                    
                    return ResponseEntity.ok(response);
                })
                .doOnSuccess(response -> log.debug("获取安全统计信息完成: startTime={}, endTime={}", 
                        finalStartTime, finalEndTime));
    }
    
    /**
     * 清理过期日志
     */
    @DeleteMapping("/logs/cleanup")
    @Operation(summary = "清理过期审计日志", description = "清理超过指定保留期的审计日志")
    public Mono<ResponseEntity<Map<String, Object>>> cleanupExpiredLogs(
            @Parameter(description = "保留天数") 
            @RequestParam(defaultValue = "90") 
            int retentionDays) {
        
        // 限制保留天数范围
        final int finalRetentionDays = Math.max(1, Math.min(retentionDays, 365));
        
        return auditService.cleanupExpiredLogs(finalRetentionDays)
                .map(deletedCount -> {
                    Map<String, Object> result = Map.of(
                            "deletedCount", deletedCount,
                            "retentionDays", finalRetentionDays,
                            "cleanupTime", LocalDateTime.now()
                    );
                    
                    log.info("清理过期审计日志完成: deletedCount={}, retentionDays={}", 
                            deletedCount, finalRetentionDays);
                    
                    return ResponseEntity.ok(result);
                });
    }
    
    /**
     * 检查告警状态
     */
    @GetMapping("/alerts/check")
    @Operation(summary = "检查告警状态", description = "检查指定事件类型是否需要触发告警")
    public Mono<ResponseEntity<Map<String, Object>>> checkAlertStatus(
            @Parameter(description = "事件类型") 
            @RequestParam String eventType,
            
            @Parameter(description = "时间窗口（分钟）") 
            @RequestParam(defaultValue = "5") 
            int timeWindowMinutes,
            
            @Parameter(description = "告警阈值") 
            @RequestParam(defaultValue = "10") 
            int threshold) {
        
        return auditService.shouldTriggerAlert(eventType, timeWindowMinutes, threshold)
                .map(shouldAlert -> {
                    Map<String, Object> result = Map.of(
                            "eventType", eventType,
                            "timeWindowMinutes", timeWindowMinutes,
                            "threshold", threshold,
                            "shouldTriggerAlert", shouldAlert,
                            "checkTime", LocalDateTime.now()
                    );
                    
                    return ResponseEntity.ok(result);
                });
    }
    
    /**
     * 获取告警统计信息
     */
    @GetMapping("/alerts/statistics")
    @Operation(summary = "获取告警统计信息", description = "获取告警系统的统计信息")
    public Mono<ResponseEntity<Map<String, Object>>> getAlertStatistics() {
        return Mono.fromCallable(() -> {
            Map<String, Object> alertStats = alertService.getAlertStatistics();
            alertStats.put("retrievedAt", LocalDateTime.now());
            
            return ResponseEntity.ok(alertStats);
        });
    }
    
    /**
     * 重置告警统计
     */
    @PostMapping("/alerts/reset")
    @Operation(summary = "重置告警统计", description = "重置告警系统的统计信息")
    public Mono<ResponseEntity<Map<String, Object>>> resetAlertStatistics() {
        return Mono.fromRunnable(() -> {
            alertService.resetAlertStatistics();
            log.info("告警统计已重置");
        })
        .then(Mono.just(ResponseEntity.ok(Map.of(
                "message", "告警统计已重置",
                "resetTime", LocalDateTime.now()
        ))));
    }
    
    /**
     * 应用高级过滤器
     */
    private boolean applyAdvancedFilters(SecurityAuditEvent event, SecurityAuditQueryRequest request) {
        // 客户端IP过滤
        if (request.getClientIp() != null && !request.getClientIp().equals(event.getClientIp())) {
            return false;
        }
        
        // 成功/失败过滤
        if (request.getSuccess() != null && !request.getSuccess().equals(event.isSuccess())) {
            return false;
        }
        
        // 操作过滤
        if (request.getAction() != null && !request.getAction().equals(event.getAction())) {
            return false;
        }
        
        // 资源过滤
        if (request.getResource() != null && !request.getResource().equals(event.getResource())) {
            return false;
        }
        
        // 失败原因过滤
        if (request.getFailureReason() != null && 
            (event.getFailureReason() == null || 
             !event.getFailureReason().contains(request.getFailureReason()))) {
            return false;
        }
        
        return true;
    }
}