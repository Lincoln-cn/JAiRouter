package org.unreal.modelrouter.monitor.callhistory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.auth.security.permission.DataScope;
import org.unreal.modelrouter.auth.security.permission.DataScopeResolver;
import org.unreal.modelrouter.auth.security.permission.PermissionChecks;
import org.unreal.modelrouter.auth.security.permission.PermissionCodes;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.monitor.callhistory.config.RecordLevel;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryQueryDTO;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.ApiCallHistoryEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;
import org.unreal.modelrouter.common.exception.ApiException;

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
// RBAC 由 SecurityConfiguration URL 规则保护（/api/call-history/** 的 GET 规则挂 callhistory:read；
// FULL 级别正文是否解密由本 controller 按 callhistory:full-content 逐主体裁剪）。
// 本 controller 返回同步 ResponseEntity，类级 @PreAuthorize 在 @EnableReactiveMethodSecurity 下
// 要求 Publisher 返回类型，真实请求会报 500（Reactor Context 错误）。
public class ApiCallHistoryController {

    private final ApiCallHistoryService callHistoryService;
    private final ApiCallHistoryRecorder callHistoryRecorder;
    private final SecurityAuditService securityAuditService;
    private final DataScopeResolver dataScopeResolver;


    /**
     * 解析当前请求的数据面范围。
     *
     * <p>持 {@code callhistory:read} ⇒ 不受限；只持 {@code callhistory:self:read} ⇒ 按并集过滤
     * （userId 命中或密钥属于本人）；两者都不持时 URL 规则已 403，这里按不受限兜底。</p>
     */
    private Mono<DataScope> scopeOf(final Authentication authentication) {
        return dataScopeResolver.resolveWithKeys(authentication,
                PermissionCodes.CALLHISTORY_READ, PermissionCodes.CALLHISTORY_SELF_READ);
    }

    /**
     * 单条记录是否落在当前范围内。
     */
    private static boolean inScope(final ApiCallHistoryEntity entity, final DataScope scope) {
        if (!scope.restricted()) {
            return true;
        }
        if (scope.filterByUser() && scope.userId().equals(entity.getUserId())) {
            return true;
        }
        return scope.filterByKeys() && entity.getApiKeyId() != null
                && scope.apiKeyIds().contains(entity.getApiKeyId());
    }

    /**
     * 记录调用历史（手动）
     */
    @PostMapping("/record")
    @Operation(summary = "记录调用历史", description = "手动记录一次 API 调用历史")
    public ResponseEntity<RouterResponse<Void>> record(
            @RequestBody final CallHistoryRecordDTO record,
            final Authentication authentication) {

        final String rejection = crossPrincipalWriteError(authentication, record);
        if (rejection != null) {
            log.warn("拒绝跨主体写入调用历史: caller={}, declaredApiKeyId={}",
                    authentication.getName(), record.getApiKeyId());
            return ResponseEntity.status(ApiException.resolveStatus(ErrorCode.FORBIDDEN.code()))
                    .body(RouterResponse.error(rejection, ErrorCode.FORBIDDEN.code()));
        }

        callHistoryService.record(record);
        return ResponseEntity.ok(RouterResponse.success(null, "调用历史记录成功"));
    }

    /**
     * 写入前的属主校验（issue #698）.
     *
     * <p>本端点在 RBAC 豁免清单上（仅要求已认证，见 {@code RbacExemptEndpoints}），而请求体里的
     * {@code apiKeyId} / {@code userId} 完全由调用方控制（{@link CallHistoryRecordDTO}），服务层原样落库。
     * 若不校验，任何持有效 API Key 的主体都能把调用记录记到<b>他人</b>的 Key 上，污染调用历史、统计与控制台看板，
     * 且这种行为不会报错。</p>
     *
     * <p><b>收敛范围刻意最小</b>：仅当调用主体本身就是 API Key 时要求「只能写自己」
     * （{@link ApiKeyAuthentication#getName()} 返回的是 keyId）；JWT / 管理面主体保持既有行为。
     * 未声明 {@code apiKeyId} 时放行（无从矛盾）；主体为 {@code null} 时放行 —— 该端点仍要求认证，
     * 未认证请求在安全链上已被拒绝。与 token-usage 的属主校验（issue #254）同口径。</p>
     *
     * @param authentication 当前认证主体，可为 {@code null}
     * @param record         请求体，可为 {@code null}
     * @return 拒绝原因；{@code null} 表示放行
     */
    private String crossPrincipalWriteError(final Authentication authentication, final CallHistoryRecordDTO record) {
        if (!(authentication instanceof ApiKeyAuthentication)) {
            return null;
        }
        final String ownKeyId = authentication.getName();
        final String claimedApiKeyId = record != null ? record.getApiKeyId() : null;
        if (ownKeyId == null || claimedApiKeyId == null || ownKeyId.equals(claimedApiKeyId)) {
            return null;
        }
        return "API Key 主体只能写入自身的调用记录（声明的 apiKeyId 与当前 Key 不一致，已拒绝）";
    }

    /**
     * 分页查询调用历史
     *
     * <p>列表不返回正文：无 {@code callhistory:full-content} 的主体拿到的加密字段为 {@code null}，
     * 只保留摘要列（正文解密只发生在 {@code /{id}/detail}，见 {@link #getDetail}）。</p>
     */
    @GetMapping
    @Operation(summary = "分页查询调用历史", description = "根据条件分页查询 API 调用历史")
    public Mono<ResponseEntity<RouterResponse<Page<ApiCallHistoryEntity>>>> query(
            @Parameter(description = "查询参数")
            @ModelAttribute final CallHistoryQueryDTO query,
            final Authentication authentication) {

        final CallHistoryQueryDTO effective = query == null ? new CallHistoryQueryDTO() : query;
        return scopeOf(authentication).map(scope -> {
            final Page<ApiCallHistoryEntity> result = callHistoryService.query(effective, scope);
            redactBodies(authentication, result.getContent());
            return ResponseEntity.ok(RouterResponse.success(result));
        });
    }

    /**
     * 根据 traceId 查询调用链路
     */
    @GetMapping("/trace/{traceId}")
    @Operation(summary = "根据 traceId 查询调用链路", description = "查询同一 traceId 下的所有调用记录")
    public Mono<ResponseEntity<RouterResponse<List<ApiCallHistoryEntity>>>> findByTraceId(
            @Parameter(description = "追踪 ID")
            @PathVariable final String traceId,
            final Authentication authentication) {

        return scopeOf(authentication).map(scope -> {
            final List<ApiCallHistoryEntity> records = callHistoryService.findByTraceId(traceId).stream()
                    .filter(record -> inScope(record, scope))
                    .toList();
            redactBodies(authentication, new java.util.ArrayList<>(records));
            return ResponseEntity.ok(RouterResponse.success(records));
        });
    }

    /**
     * 查询单条记录详情（按主体裁剪正文）
     *
     * <p>只有持 {@code callhistory:full-content} 的主体才会解密正文，并在记录级别为 FULL 时
     * 记 {@code FULL_CONTENT_ACCESS} 审计事件；其余主体（含仅持 {@code callhistory:read} 的运维）
     * 拿到加密字段为 {@code null} 的记录，摘要列照常可见，且不产生「解密查看」审计噪音。</p>
     */
    @GetMapping("/{id}/detail")
    @Operation(summary = "查询记录详情", description = "根据 ID 查询单条调用历史记录；"
            + "持 callhistory:full-content 时自动解密正文，否则只返回摘要")
    public Mono<ResponseEntity<RouterResponse<ApiCallHistoryEntity>>> getDetail(
            @Parameter(description = "记录 ID")
            @PathVariable final Long id,
            final Authentication authentication) {

        final boolean fullContent = PermissionChecks.hasPermission(
                authentication, PermissionCodes.CALLHISTORY_FULL_CONTENT);

        return scopeOf(authentication).map(scope -> callHistoryService.findById(id)
                // 不在范围内一律按「不存在」处理：返回 403 会泄露「这条记录确实存在」这一信息
                .filter(entity -> inScope(entity, scope))
                .map(entity -> {
                    if (fullContent) {
                        // 解密查看 FULL 级别正文前先落审计，口径与拆码前一致
                        if (RecordLevel.FULL.name().equals(entity.getRecordLevel())) {
                            emitFullContentAccessAudit(entity.getId(), authentication);
                        }
                        callHistoryService.decryptBodies(entity);
                    } else {
                        redactBodies(authentication, entity);
                    }
                    return ResponseEntity.ok(RouterResponse.success(entity));
                })
                .orElseGet(() -> ResponseEntity
                        .status(ApiException.resolveStatus(ErrorCode.NOT_FOUND.code()))
                        .body(RouterResponse.error("记录不存在: id=" + id, ErrorCode.NOT_FOUND.code()))));
    }

    /**
     * 获取最近的调用记录
     */
    @GetMapping("/recent")
    @Operation(summary = "获取最近的调用记录", description = "获取最近发生的 API 调用记录")
    public Mono<ResponseEntity<RouterResponse<List<ApiCallHistoryEntity>>>> findRecent(
            @Parameter(description = "最大数量")
            @RequestParam(defaultValue = "20") final int limit,
            final Authentication authentication) {

        return scopeOf(authentication).map(scope -> {
            final List<ApiCallHistoryEntity> records = callHistoryService.findRecent(limit, scope);
            redactBodies(authentication, new java.util.ArrayList<>(records));
            return ResponseEntity.ok(RouterResponse.success(records));
        });
    }

    /**
     * 查询错误调用
     */
    @GetMapping("/errors")
    @Operation(summary = "查询错误调用", description = "查询失败的 API 调用记录")
    public Mono<ResponseEntity<RouterResponse<List<ApiCallHistoryEntity>>>> findErrors(
            @Parameter(description = "开始时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,

            @Parameter(description = "结束时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,

            @Parameter(description = "最大数量")
            @RequestParam(defaultValue = "50") final int limit,
            final Authentication authentication) {

        return scopeOf(authentication).map(scope -> {
            final List<ApiCallHistoryEntity> records = callHistoryService.findErrors(startTime, endTime, limit, scope);
            redactBodies(authentication, new java.util.ArrayList<>(records));
            return ResponseEntity.ok(RouterResponse.success(records));
        });
    }

    /**
     * 查询慢调用
     */
    @GetMapping("/slow")
    @Operation(summary = "查询慢调用", description = "查询响应时间超过阈值的 API 调用记录")
    public Mono<ResponseEntity<RouterResponse<List<ApiCallHistoryEntity>>>> findSlowCalls(
            @Parameter(description = "慢调用阈值 (毫秒)")
            @RequestParam(required = false) Long threshold,

            @Parameter(description = "开始时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,

            @Parameter(description = "结束时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,

            @Parameter(description = "最大数量")
            @RequestParam(defaultValue = "50") final int limit,
            final Authentication authentication) {

        return scopeOf(authentication).map(scope -> {
            final List<ApiCallHistoryEntity> records =
                    callHistoryService.findSlowCalls(threshold, startTime, endTime, limit, scope);
            redactBodies(authentication, new java.util.ArrayList<>(records));
            return ResponseEntity.ok(RouterResponse.success(records));
        });
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取统计信息", description = "获取 API 调用历史的多维度统计信息")
    public Mono<ResponseEntity<RouterResponse<CallHistoryStatisticsDTO>>> getStatistics(
            @Parameter(description = "开始时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime startTime,

            @Parameter(description = "结束时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime endTime,

            final Authentication authentication) {

        return scopeOf(authentication).map(scope -> ResponseEntity.ok(
                RouterResponse.success(callHistoryService.getStatistics(startTime, endTime, scope))));
    }

    /**
     * 获取仪表盘数据
     */
    @GetMapping("/dashboard")
    @Operation(summary = "获取仪表盘数据", description = "获取调用历史仪表盘所需的核心数据")
    public Mono<ResponseEntity<RouterResponse<Map<String, Object>>>> getDashboard(
            @Parameter(description = "开始时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,

            @Parameter(description = "结束时间 (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,

            final Authentication authentication) {

        return scopeOf(authentication).map(scope -> {
            // 统计信息（本人读时按并集过滤后聚合）
            final CallHistoryStatisticsDTO statistics = callHistoryService.getStatistics(startTime, endTime, scope);

            // 最近调用（同样按范围过滤）
            final List<ApiCallHistoryEntity> recentCalls = callHistoryService.findRecent(10, scope);
            redactBodies(authentication, new java.util.ArrayList<>(recentCalls));

            // 构建仪表盘数据
            final Map<String, Object> dashboard = new HashMap<>();
            dashboard.put("statistics", statistics);
            dashboard.put("recentCalls", recentCalls);
            dashboard.put("recorderStats", Map.of(
                    "bufferSize", callHistoryRecorder.getBufferSize(),
                    "totalRecords", callHistoryRecorder.getTotalRecords(),
                    "droppedRecords", callHistoryRecorder.getDroppedRecords()
            ));
            return ResponseEntity.ok(RouterResponse.success(dashboard));
        });
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

    /**
     * 按主体裁剪正文：无 {@code callhistory:full-content} 时清空加密字段，只保留摘要列。
     *
     * <p>加密字段对前端没有可读价值，但「列表 / 详情都带密文」会让审计口径含糊 ——
     * 正文是否可见应当只取决于 {@code callhistory:full-content}，而不是「能不能看到密文」。</p>
     *
     * @param authentication 当前认证主体
     * @param entity         待裁剪的记录，可为 {@code null}
     */
    private void redactBodies(final Authentication authentication, final ApiCallHistoryEntity entity) {
        if (entity == null
                || PermissionChecks.hasPermission(authentication, PermissionCodes.CALLHISTORY_FULL_CONTENT)) {
            return;
        }
        entity.setRequestBodyEncrypted(null);
        entity.setResponseBodyEncrypted(null);
    }

    /**
     * {@link #redactBodies} 的列表形态。
     */
    private void redactBodies(final Authentication authentication, final List<ApiCallHistoryEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        for (ApiCallHistoryEntity entity : entities) {
            redactBodies(authentication, entity);
        }
    }

    /**
     * 发送 FULL_CONTENT_ACCESS 审计事件
     *
     * <p>带上解密主体（{@code userId}）：运行态核对时发现只写 {@code additionalData.recordId}
     * 会让审计行没有「谁看的」，等于解了密却追溯不到人。</p>
     *
     * @param recordId       记录 ID
     * @param authentication 解密主体（可为 null）
     */
    private void emitFullContentAccessAudit(final Long recordId, final Authentication authentication) {
        try {
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(AuditEventType.FULL_CONTENT_ACCESS.name())
                    .userId(authentication != null ? authentication.getName() : null)
                    .resource("call-history")
                    .action("DETAIL")
                    .success(true)
                    .additionalData(Map.of("recordId", recordId))
                    .build();
            securityAuditService.recordEvent(event).subscribe(
                    v -> log.debug("FULL_CONTENT_ACCESS 审计事件已记录: recordId={}", recordId),
                    e -> log.warn("记录 FULL_CONTENT_ACCESS 审计事件失败: {}", e.getMessage())
            );
        } catch (Exception e) {
            log.warn("发送 FULL_CONTENT_ACCESS 审计事件异常: {}", e.getMessage());
        }
    }
}
