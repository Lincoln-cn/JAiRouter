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
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.security.service.ExtendedSecurityAuditService;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 扩展的安全审计控制器
 * 在现有SecurityAuditController基础上添加JWT和API Key审计查询功能
 */
@Slf4j
@RestController
@RequestMapping("/api/security/audit/extended")
@RequiredArgsConstructor
@Tag(name = "扩展安全审计", description = "JWT和API Key审计查询接口")
@ConditionalOnProperty(name = "jairouter.security.audit.extended.enabled", havingValue = "true", matchIfMissing = true)
public class ExtendedSecurityAuditController {
    
    private final ExtendedSecurityAuditService extendedAuditService;
    
    /**
     * 查询JWT令牌相关审计事件
     */
    @GetMapping("/jwt-tokens")
    @Operation(summary = "查询JWT令牌审计事件", description = "查询JWT令牌相关的审计事件，包括颁发、刷新、撤销、验证等")
    public Mono<RouterResponse<ExtendedAuditQueryResponse>> queryJwtTokenAuditEvents(
            @Parameter(description = "开始时间") 
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime startTime,
            
            @Parameter(description = "结束时间") 
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime endTime,
            
            @Parameter(description = "用户ID") 
            @RequestParam(required = false) 
            String userId,
            
            @Parameter(description = "令牌ID") 
            @RequestParam(required = false) 
            String tokenId,
            
            @Parameter(description = "IP地址") 
            @RequestParam(required = false) 
            String ipAddress,
            
            @Parameter(description = "操作是否成功") 
            @RequestParam(required = false) 
            Boolean success,
            
            @Parameter(description = "页码，从0开始") 
            @RequestParam(defaultValue = "0") 
            int page,
            
            @Parameter(description = "每页大小") 
            @RequestParam(defaultValue = "20") 
            int size) {
        
        // 设置默认时间范围
        final LocalDateTime finalStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        final LocalDateTime finalEndTime = endTime != null ? endTime : LocalDateTime.now();
        final int finalSize = Math.min(size, 100);
        
        // 构建查询条件
        AuditEventQuery query = new AuditEventQuery();
        query.setStartTime(finalStartTime);
        query.setEndTime(finalEndTime);
        query.setUserId(userId);
        query.setResourceId(tokenId);
        query.setIpAddress(ipAddress);
        query.setSuccess(success);
        query.setPage(page);
        query.setSize(finalSize);
        
        // 设置JWT事件类型
        query.setEventTypes(List.of(
            AuditEventType.JWT_TOKEN_ISSUED,
            AuditEventType.JWT_TOKEN_REFRESHED,
            AuditEventType.JWT_TOKEN_REVOKED,
            AuditEventType.JWT_TOKEN_VALIDATED,
            AuditEventType.JWT_TOKEN_EXPIRED
        ));
        
        return extendedAuditService.findAuditEvents(query)
                .collectList()
                .map(events -> {
                    ExtendedAuditQueryResponse response = ExtendedAuditQueryResponse.builder()
                            .events(events)
                            .page(page)
                            .size(finalSize)
                            .totalElements(events.size())
                            .startTime(finalStartTime)
                            .endTime(finalEndTime)
                            .eventCategory("JWT_TOKEN")
                            .build();
                    
                    return RouterResponse.success(response);
                })
                .doOnSuccess(response -> log.debug("查询JWT令牌审计事件完成: page={}, size={}, eventCount={}", 
                        page, finalSize, response.getData().getEvents().size()));
    }
    
    /**
     * 查询API Key相关审计事件
     */
    @GetMapping("/api-keys")
    @Operation(summary = "查询API Key审计事件", description = "查询API Key相关的审计事件，包括创建、使用、撤销、过期等")
    public Mono<RouterResponse<ExtendedAuditQueryResponse>> queryApiKeyAuditEvents(
            @Parameter(description = "开始时间") 
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime startTime,
            
            @Parameter(description = "结束时间") 
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime endTime,
            
            @Parameter(description = "API Key ID") 
            @RequestParam(required = false) 
            String keyId,
            
            @Parameter(description = "创建者/操作者") 
            @RequestParam(required = false) 
            String operatorId,
            
            @Parameter(description = "IP地址") 
            @RequestParam(required = false) 
            String ipAddress,
            
            @Parameter(description = "操作是否成功") 
            @RequestParam(required = false) 
            Boolean success,
            
            @Parameter(description = "页码，从0开始") 
            @RequestParam(defaultValue = "0") 
            int page,
            
            @Parameter(description = "每页大小") 
            @RequestParam(defaultValue = "20") 
            int size) {
        
        // 设置默认时间范围
        final LocalDateTime finalStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        final LocalDateTime finalEndTime = endTime != null ? endTime : LocalDateTime.now();
        final int finalSize = Math.min(size, 100);
        
        // 构建查询条件
        AuditEventQuery query = new AuditEventQuery();
        query.setStartTime(finalStartTime);
        query.setEndTime(finalEndTime);
        query.setUserId(operatorId);
        query.setResourceId(keyId);
        query.setIpAddress(ipAddress);
        query.setSuccess(success);
        query.setPage(page);
        query.setSize(finalSize);
        
        // 设置API Key事件类型
        query.setEventTypes(List.of(
            AuditEventType.API_KEY_CREATED,
            AuditEventType.API_KEY_USED,
            AuditEventType.API_KEY_REVOKED,
            AuditEventType.API_KEY_EXPIRED,
            AuditEventType.API_KEY_UPDATED
        ));
        
        return extendedAuditService.findAuditEvents(query)
                .collectList()
                .map(events -> {
                    ExtendedAuditQueryResponse response = ExtendedAuditQueryResponse.builder()
                            .events(events)
                            .page(page)
                            .size(finalSize)
                            .totalElements(events.size())
                            .startTime(finalStartTime)
                            .endTime(finalEndTime)
                            .eventCategory("API_KEY")
                            .build();
                    
                    return RouterResponse.success(response);
                })
                .doOnSuccess(response -> log.debug("查询API Key审计事件完成: page={}, size={}, eventCount={}", 
                        page, finalSize, response.getData().getEvents().size()));
    }
    
    /**
     * 查询安全事件（可疑活动、安全告警等）
     */
    @GetMapping("/security-events")
    @Operation(summary = "查询安全事件", description = "查询安全相关事件，包括可疑活动、安全告警等")
    public Mono<RouterResponse<ExtendedAuditQueryResponse>> querySecurityEvents(
            @Parameter(description = "开始时间") 
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime startTime,
            
            @Parameter(description = "结束时间") 
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime endTime,
            
            @Parameter(description = "用户ID") 
            @RequestParam(required = false) 
            String userId,
            
            @Parameter(description = "IP地址") 
            @RequestParam(required = false) 
            String ipAddress,
            
            @Parameter(description = "页码，从0开始") 
            @RequestParam(defaultValue = "0") 
            int page,
            
            @Parameter(description = "每页大小") 
            @RequestParam(defaultValue = "20") 
            int size) {
        
        // 设置默认时间范围
        final LocalDateTime finalStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        final LocalDateTime finalEndTime = endTime != null ? endTime : LocalDateTime.now();
        final int finalSize = Math.min(size, 100);
        
        // 构建查询条件
        AuditEventQuery query = new AuditEventQuery();
        query.setStartTime(finalStartTime);
        query.setEndTime(finalEndTime);
        query.setUserId(userId);
        query.setIpAddress(ipAddress);
        query.setPage(page);
        query.setSize(finalSize);
        
        // 设置安全事件类型
        query.setEventTypes(List.of(
            AuditEventType.SECURITY_ALERT,
            AuditEventType.SUSPICIOUS_ACTIVITY,
            AuditEventType.AUTHENTICATION_FAILED,
            AuditEventType.AUTHORIZATION_FAILED
        ));
        
        return extendedAuditService.findAuditEvents(query)
                .collectList()
                .map(events -> {
                    ExtendedAuditQueryResponse response = ExtendedAuditQueryResponse.builder()
                            .events(events)
                            .page(page)
                            .size(finalSize)
                            .totalElements(events.size())
                            .startTime(finalStartTime)
                            .endTime(finalEndTime)
                            .eventCategory("SECURITY")
                            .build();
                    
                    return RouterResponse.success(response);
                })
                .doOnSuccess(response -> log.debug("查询安全事件完成: page={}, size={}, eventCount={}", 
                        page, finalSize, response.getData().getEvents().size()));
    }
    
    /**
     * 使用复杂查询条件查询审计事件
     */
    @PostMapping("/query")
    @Operation(summary = "复杂条件查询审计事件", description = "支持复杂查询条件的审计事件查询")
    public Mono<RouterResponse<ExtendedAuditQueryResponse>> queryAuditEventsAdvanced(
            @RequestBody AuditEventQuery query) {
        
        // 验证和设置默认值
        if (query.getStartTime() == null) {
            query.setStartTime(LocalDateTime.now().minusDays(7));
        }
        if (query.getEndTime() == null) {
            query.setEndTime(LocalDateTime.now());
        }
        if (query.getSize() <= 0 || query.getSize() > 100) {
            query.setSize(20);
        }
        if (query.getPage() < 0) {
            query.setPage(0);
        }
        
        return extendedAuditService.findAuditEvents(query)
                .collectList()
                .map(events -> {
                    ExtendedAuditQueryResponse response = ExtendedAuditQueryResponse.builder()
                            .events(events)
                            .page(query.getPage())
                            .size(query.getSize())
                            .totalElements(events.size())
                            .startTime(query.getStartTime())
                            .endTime(query.getEndTime())
                            .eventCategory("ALL")
                            .build();
                    
                    return RouterResponse.success(response);
                });
    }
    
    /**
     * 生成安全报告
     */
    @GetMapping("/reports/security")
    @Operation(summary = "生成安全报告", description = "生成包含JWT和API Key操作统计的安全报告")
    public Mono<RouterResponse<SecurityReport>> generateSecurityReport(
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
        
        return extendedAuditService.generateSecurityReport(finalStartTime, finalEndTime)
                .map(RouterResponse::success)
                .doOnSuccess(response -> log.info("生成安全报告完成: startTime={}, endTime={}, " +
                        "jwtOperations={}, apiKeyOperations={}, failedAuth={}, suspiciousActivities={}", 
                        finalStartTime, finalEndTime,
                        response.getData().getTotalJwtOperations(),
                        response.getData().getTotalApiKeyOperations(),
                        response.getData().getFailedAuthentications(),
                        response.getData().getSuspiciousActivities()));
    }
    
    /**
     * 获取用户的审计事件
     */
    @GetMapping("/users/{userId}/events")
    @Operation(summary = "获取用户审计事件", description = "获取指定用户的审计事件")
    public Mono<RouterResponse<ExtendedAuditQueryResponse>> getUserAuditEvents(
            @Parameter(description = "用户ID") 
            @PathVariable String userId,
            
            @Parameter(description = "开始时间") 
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime startTime,
            
            @Parameter(description = "结束时间") 
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime endTime,
            
            @Parameter(description = "限制数量") 
            @RequestParam(defaultValue = "50") 
            int limit) {
        
        // 设置默认时间范围
        final LocalDateTime finalStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(30);
        final LocalDateTime finalEndTime = endTime != null ? endTime : LocalDateTime.now();
        final int finalLimit = Math.min(limit, 200);
        
        return extendedAuditService.getUserAuditEvents(userId, finalStartTime, finalEndTime, finalLimit)
                .collectList()
                .map(events -> {
                    ExtendedAuditQueryResponse response = ExtendedAuditQueryResponse.builder()
                            .events(events)
                            .page(0)
                            .size(events.size())
                            .totalElements(events.size())
                            .startTime(finalStartTime)
                            .endTime(finalEndTime)
                            .eventCategory("USER_SPECIFIC")
                            .build();
                    
                    return RouterResponse.success(response);
                })
                .doOnSuccess(response -> log.debug("获取用户审计事件完成: userId={}, eventCount={}", 
                        userId, response.getData().getEvents().size()));
    }
    
    /**
     * 获取IP地址的审计事件
     */
    @GetMapping("/ip-addresses/{ipAddress}/events")
    @Operation(summary = "获取IP地址审计事件", description = "获取指定IP地址的审计事件")
    public Mono<RouterResponse<ExtendedAuditQueryResponse>> getIpAuditEvents(
            @Parameter(description = "IP地址") 
            @PathVariable String ipAddress,
            
            @Parameter(description = "开始时间") 
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime startTime,
            
            @Parameter(description = "结束时间") 
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime endTime,
            
            @Parameter(description = "限制数量") 
            @RequestParam(defaultValue = "50") 
            int limit) {
        
        // 设置默认时间范围
        final LocalDateTime finalStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        final LocalDateTime finalEndTime = endTime != null ? endTime : LocalDateTime.now();
        final int finalLimit = Math.min(limit, 200);
        
        return extendedAuditService.getIpAuditEvents(ipAddress, finalStartTime, finalEndTime, finalLimit)
                .collectList()
                .map(events -> {
                    ExtendedAuditQueryResponse response = ExtendedAuditQueryResponse.builder()
                            .events(events)
                            .page(0)
                            .size(events.size())
                            .totalElements(events.size())
                            .startTime(finalStartTime)
                            .endTime(finalEndTime)
                            .eventCategory("IP_SPECIFIC")
                            .build();
                    
                    return RouterResponse.success(response);
                })
                .doOnSuccess(response -> log.debug("获取IP地址审计事件完成: ipAddress={}, eventCount={}", 
                        ipAddress, response.getData().getEvents().size()));
    }
    
    /**
     * 批量记录审计事件
     */
    @PostMapping("/events/batch")
    @Operation(summary = "批量记录审计事件", description = "批量记录多个审计事件")
    public Mono<RouterResponse<Map<String, Object>>> batchRecordAuditEvents(
            @RequestBody List<AuditEvent> auditEvents) {
        
        if (auditEvents == null || auditEvents.isEmpty()) {
            return Mono.just(RouterResponse.error("审计事件列表不能为空"));
        }
        
        if (auditEvents.size() > 100) {
            return Mono.just(RouterResponse.error("批量记录事件数量不能超过100条"));
        }
        
        return extendedAuditService.batchRecordAuditEvents(auditEvents)
                .then(Mono.fromCallable(() -> {
                    Map<String, Object> result = Map.of(
                        "message", "批量记录审计事件成功",
                        "recordedCount", auditEvents.size(),
                        "recordedAt", LocalDateTime.now()
                    );
                    return RouterResponse.success(result);
                }))
                .doOnSuccess(response -> log.info("批量记录审计事件完成: count={}", auditEvents.size()));
    }
    /**
     * 获取审计统计信息
     */
    @GetMapping("/statistics/extended")
    @Operation(summary = "获取扩展审计统计信息", description = "获取包含JWT和API Key操作的详细统计信息")
    public Mono<RouterResponse<Map<String, Object>>> getExtendedAuditStatistics(
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
        
        return extendedAuditService.generateSecurityReport(finalStartTime, finalEndTime)
                .map(report -> {
                    Map<String, Object> statistics = Map.of(
                            "reportPeriod", Map.of(
                                    "startTime", report.getReportPeriodStart(),
                                    "endTime", report.getReportPeriodEnd()
                            ),
                            "jwtOperations", Map.of(
                                    "total", report.getTotalJwtOperations(),
                                    "byType", report.getOperationsByType().entrySet().stream()
                                            .filter(entry -> entry.getKey().startsWith("JWT_"))
                                            .collect(java.util.stream.Collectors.toMap(
                                                    Map.Entry::getKey,
                                                    Map.Entry::getValue
                                            ))
                            ),
                            "apiKeyOperations", Map.of(
                                    "total", report.getTotalApiKeyOperations(),
                                    "byType", report.getOperationsByType().entrySet().stream()
                                            .filter(entry -> entry.getKey().startsWith("API_KEY_"))
                                            .collect(java.util.stream.Collectors.toMap(
                                                    Map.Entry::getKey,
                                                    Map.Entry::getValue
                                            ))
                            ),
                            "securityEvents", Map.of(
                                    "failedAuthentications", report.getFailedAuthentications(),
                                    "suspiciousActivities", report.getSuspiciousActivities(),
                                    "alerts", report.getAlerts().size()
                            ),
                            "topUsers", report.getOperationsByUser(),
                            "topIpAddresses", report.getTopIpAddresses(),
                            "generatedAt", LocalDateTime.now()
                    );
                    
                    return RouterResponse.success(statistics);
                })
                .doOnSuccess(response -> log.debug("获取扩展审计统计信息完成: startTime={}, endTime={}", 
                        finalStartTime, finalEndTime));
    }
}