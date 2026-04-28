package org.unreal.modelrouter.auth.controller;

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
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 扩展的安全审计控制器
 * 提供JWT、API Key和安全事件的审计查询功能
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
    @Operation(summary = "查询JWT令牌审计事件")
    public Mono<RouterResponse<ExtendedAuditQueryResponse>> queryJwtTokenAuditEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime endTime,
            @RequestParam(required = false) final String userId,
            @RequestParam(required = false) final String tokenId,
            @RequestParam(required = false) final String ipAddress,
            @RequestParam(required = false) final Boolean success,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {

        final LocalDateTime finalStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        final LocalDateTime finalEndTime = endTime != null ? endTime : LocalDateTime.now();
        final int finalSize = Math.min(size, 100);

        AuditEventQuery query = buildQuery(finalStartTime, finalEndTime, userId, tokenId, ipAddress, success, page, finalSize);
        query.setEventTypes(List.of(
            AuditEventType.JWT_TOKEN_ISSUED, AuditEventType.JWT_TOKEN_REFRESHED,
            AuditEventType.JWT_TOKEN_REVOKED, AuditEventType.JWT_TOKEN_VALIDATED, AuditEventType.JWT_TOKEN_EXPIRED
        ));

        return extendedAuditService.countAuditEvents(query)
                .flatMap(total -> extendedAuditService.findAuditEvents(query)
                        .collectList()
                        .map(events -> buildResponse(events, page, finalSize, total, finalStartTime, finalEndTime, "JWT_TOKEN")))
                .map(RouterResponse::success);
    }

    /**
     * 查询API Key相关审计事件
     */
    @GetMapping("/api-keys")
    @Operation(summary = "查询API Key审计事件")
    public Mono<RouterResponse<ExtendedAuditQueryResponse>> queryApiKeyAuditEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime endTime,
            @RequestParam(required = false) final String keyId,
            @RequestParam(required = false) final String operatorId,
            @RequestParam(required = false) final String ipAddress,
            @RequestParam(required = false) final Boolean success,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {

        final LocalDateTime finalStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        final LocalDateTime finalEndTime = endTime != null ? endTime : LocalDateTime.now();
        final int finalSize = Math.min(size, 100);

        AuditEventQuery query = buildQuery(finalStartTime, finalEndTime, operatorId, keyId, ipAddress, success, page, finalSize);
        query.setEventTypes(List.of(
            AuditEventType.API_KEY_CREATED, AuditEventType.API_KEY_USED,
            AuditEventType.API_KEY_REVOKED, AuditEventType.API_KEY_EXPIRED, AuditEventType.API_KEY_UPDATED
        ));

        return extendedAuditService.countAuditEvents(query)
                .flatMap(total -> extendedAuditService.findAuditEvents(query)
                        .collectList()
                        .map(events -> buildResponse(events, page, finalSize, total, finalStartTime, finalEndTime, "API_KEY")))
                .map(RouterResponse::success);
    }

    /**
     * 查询安全事件（可疑活动、安全告警等）
     */
    @GetMapping("/security-events")
    @Operation(summary = "查询安全事件")
    public Mono<RouterResponse<ExtendedAuditQueryResponse>> querySecurityEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime endTime,
            @RequestParam(required = false) final String userId,
            @RequestParam(required = false) final String ipAddress,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {

        final LocalDateTime finalStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        final LocalDateTime finalEndTime = endTime != null ? endTime : LocalDateTime.now();
        final int finalSize = Math.min(size, 100);

        AuditEventQuery query = buildQuery(finalStartTime, finalEndTime, userId, null, ipAddress, null, page, finalSize);
        query.setEventTypes(List.of(
            AuditEventType.SECURITY_ALERT, AuditEventType.SUSPICIOUS_ACTIVITY,
            AuditEventType.AUTHENTICATION_FAILED, AuditEventType.AUTHORIZATION_FAILED
        ));

        return extendedAuditService.countAuditEvents(query)
                .flatMap(total -> extendedAuditService.findAuditEvents(query)
                        .collectList()
                        .map(events -> buildResponse(events, page, finalSize, total, finalStartTime, finalEndTime, "SECURITY")))
                .map(RouterResponse::success);
    }

    /**
     * 使用复杂查询条件查询审计事件
     */
    @PostMapping("/query")
    @Operation(summary = "复杂条件查询审计事件")
    public Mono<RouterResponse<ExtendedAuditQueryResponse>> queryAuditEventsAdvanced(@RequestBody final AuditEventQuery query) {
        if (query.getStartTime() == null) query.setStartTime(LocalDateTime.now().minusDays(7));
        if (query.getEndTime() == null) query.setEndTime(LocalDateTime.now());
        if (query.getSize() <= 0 || query.getSize() > 100) query.setSize(20);
        if (query.getPage() < 0) query.setPage(0);

        return extendedAuditService.countAuditEvents(query)
                .flatMap(total -> extendedAuditService.findAuditEvents(query)
                        .collectList()
                        .map(events -> buildResponse(events, query.getPage(), query.getSize(), total,
                                query.getStartTime(), query.getEndTime(), "ALL")))
                .map(RouterResponse::success);
    }

    /**
     * 生成安全报告
     */
    @GetMapping("/reports/security")
    @Operation(summary = "生成安全报告")
    public Mono<RouterResponse<SecurityReport>> generateSecurityReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime endTime) {

        final LocalDateTime finalStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(1);
        final LocalDateTime finalEndTime = endTime != null ? endTime : LocalDateTime.now();

        return extendedAuditService.generateSecurityReport(finalStartTime, finalEndTime)
                .map(RouterResponse::success);
    }

    /**
     * 获取用户的审计事件
     */
    @GetMapping("/users/{userId}/events")
    @Operation(summary = "获取用户审计事件")
    public Mono<RouterResponse<ExtendedAuditQueryResponse>> getUserAuditEvents(
            @PathVariable final String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime endTime,
            @RequestParam(defaultValue = "50") final int limit) {

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
                            .totalPages(1)
                            .startTime(finalStartTime)
                            .endTime(finalEndTime)
                            .eventCategory("USER_SPECIFIC")
                            .build();
                    return RouterResponse.success(response);
                });
    }

    /**
     * 获取IP地址的审计事件
     */
    @GetMapping("/ip-addresses/{ipAddress}/events")
    @Operation(summary = "获取IP地址审计事件")
    public Mono<RouterResponse<ExtendedAuditQueryResponse>> getIpAuditEvents(
            @PathVariable final String ipAddress,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime endTime,
            @RequestParam(defaultValue = "50") final int limit) {

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
                            .totalPages(1)
                            .startTime(finalStartTime)
                            .endTime(finalEndTime)
                            .eventCategory("IP_SPECIFIC")
                            .build();
                    return RouterResponse.success(response);
                });
    }

    /**
     * 批量记录审计事件
     */
    @PostMapping("/events/batch")
    @Operation(summary = "批量记录审计事件")
    public Mono<RouterResponse<Map<String, Object>>> batchRecordAuditEvents(@RequestBody final List<AuditEvent> auditEvents) {
        if (auditEvents == null || auditEvents.isEmpty()) {
            return Mono.just(RouterResponse.error("审计事件列表不能为空"));
        }
        if (auditEvents.size() > 100) {
            return Mono.just(RouterResponse.error("批量记录事件数量不能超过100条"));
        }

        return extendedAuditService.batchRecordAuditEvents(auditEvents)
                .then(Mono.just(RouterResponse.success(Map.of(
                    "message", "批量记录审计事件成功",
                    "recordedCount", auditEvents.size(),
                    "recordedAt", LocalDateTime.now()
                ))));
    }

    /**
     * 生成测试审计数据（仅用于开发和测试）
     */
    @PostMapping("/test-data/generate")
    @Operation(summary = "生成测试审计数据")
    public Mono<RouterResponse<Map<String, Object>>> generateTestAuditData() {
        LocalDateTime now = LocalDateTime.now();

        return extendedAuditService.auditTokenIssued("test-user-1", "token-123", "192.168.1.100", "Mozilla/5.0")
                .then(extendedAuditService.auditTokenValidated("test-user-1", "token-123", true, "192.168.1.100"))
                .then(extendedAuditService.auditTokenRefreshed("test-user-1", "token-123", "token-456", "192.168.1.100"))
                .then(extendedAuditService.auditApiKeyCreated("api-key-789", "admin-user", "192.168.1.101"))
                .then(extendedAuditService.auditApiKeyUsed("api-key-789", "/api/chat/completions", "192.168.1.102", true))
                .then(extendedAuditService.auditApiKeyUsed("api-key-789", "/api/embeddings", "192.168.1.103", false))
                .then(extendedAuditService.auditSecurityEvent("BRUTE_FORCE_ATTEMPT", "Multiple failed login attempts", "suspicious-user", "192.168.1.200"))
                .then(extendedAuditService.auditSuspiciousActivity("Unusual access pattern", "test-user-2", "192.168.1.201", "Multiple countries"))
                .then(extendedAuditService.auditTokenRevoked("test-user-2", "token-789", "Security breach", "admin-user"))
                .then(extendedAuditService.auditApiKeyRevoked("api-key-old", "Compromised", "admin-user"))
                .then(Mono.just(RouterResponse.success(Map.of(
                    "message", "测试审计数据生成成功",
                    "eventsGenerated", 10,
                    "generatedAt", now
                ))));
    }

    /**
     * 获取审计统计信息
     */
    @GetMapping("/statistics/extended")
    @Operation(summary = "获取扩展审计统计信息")
    public Mono<RouterResponse<Map<String, Object>>> getExtendedAuditStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime endTime) {

        final LocalDateTime finalStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(1);
        final LocalDateTime finalEndTime = endTime != null ? endTime : LocalDateTime.now();

        return extendedAuditService.generateSecurityReport(finalStartTime, finalEndTime)
                .map(report -> {
                    Map<String, Object> statistics = Map.of(
                        "reportPeriod", Map.of("startTime", report.getReportPeriodStart(), "endTime", report.getReportPeriodEnd()),
                        "jwtOperations", Map.of("total", report.getTotalJwtOperations()),
                        "apiKeyOperations", Map.of("total", report.getTotalApiKeyOperations()),
                        "securityEvents", Map.of(
                            "failedAuthentications", report.getFailedAuthentications(),
                            "suspiciousActivities", report.getSuspiciousActivities(),
                            "alerts", report.getAlerts().size()
                        ),
                        "operationsByType", report.getOperationsByType(),
                        "operationsByUser", report.getOperationsByUser(),
                        "topIpAddresses", report.getTopIpAddresses(),
                        "generatedAt", LocalDateTime.now()
                    );
                    return RouterResponse.success(statistics);
                });
    }

    // ========== 辅助方法 ==========

    private AuditEventQuery buildQuery(final LocalDateTime startTime, final LocalDateTime endTime,
                                        final String userId, final String resourceId, final String ipAddress,
                                        final Boolean success, final int page, final int size) {
        AuditEventQuery query = new AuditEventQuery();
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setUserId(userId);
        query.setResourceId(resourceId);
        query.setIpAddress(ipAddress);
        query.setSuccess(success);
        query.setPage(page);
        query.setSize(size);
        return query;
    }

    private ExtendedAuditQueryResponse buildResponse(final List<AuditEvent> events, final int page, final int size,
                                                      final long total, final LocalDateTime startTime,
                                                      final LocalDateTime endTime, final String category) {
        int totalPages = (int) Math.ceil((double) total / size);
        return ExtendedAuditQueryResponse.builder()
                .events(events)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .startTime(startTime)
                .endTime(endTime)
                .eventCategory(category)
                .build();
    }
}