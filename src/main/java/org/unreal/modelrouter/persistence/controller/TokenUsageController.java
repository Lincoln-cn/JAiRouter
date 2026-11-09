package org.unreal.modelrouter.persistence.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.common.constants.ErrorCode;
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
            @RequestBody final TokenUsageRecordDTO record,
            final Authentication authentication) {

        final String rejection = crossPrincipalWriteError(authentication, record.getApiKeyId());
        if (rejection != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(RouterResponse.error(rejection, ErrorCode.FORBIDDEN_TOKEN_USAGE.code()));
        }

        tokenUsageService.recordTokenUsage(record);
        return ResponseEntity.ok(RouterResponse.success(null, "Token 使用量记录成功"));
    }

    /**
     * 批量记录 Token 使用量
     */
    @PostMapping("/record/batch")
    @Operation(summary = "批量记录 Token 使用量", description = "批量记录 AI 模型调用的 Token 使用量")
    public ResponseEntity<RouterResponse<Void>> recordTokenUsageBatch(
            @RequestBody final List<TokenUsageRecordDTO> records,
            final Authentication authentication) {

        if (records != null) {
            for (final TokenUsageRecordDTO record : records) {
                final String rejection = crossPrincipalWriteError(authentication, record.getApiKeyId());
                if (rejection != null) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(RouterResponse.error(rejection, ErrorCode.FORBIDDEN_TOKEN_USAGE.code()));
                }
            }
        }

        tokenUsageService.recordTokenUsageBatch(records);
        return ResponseEntity.ok(RouterResponse.success(null, "批量记录成功"));
    }

    /**
     * 写入前的属主校验（issue #254 / #1059）.
     *
     * <p>本端点在 RBAC 豁免清单上（仅要求已认证，见 {@code RbacExemptEndpoints}），而请求体里的
     * {@code apiKeyId} / {@code userId} 等属主字段完全由调用方控制（{@link TokenUsageRecordDTO}），
     * 服务层原样落库。若不校验，任何持有效凭证的主体都能把用量记到<b>他人</b>的 key 上，
     * 污染统计与计费数据。配额强制不受影响 —— {@code auth.security.quota} 不读该表。</p>
     *
     * <p><b>API Key 主体</b>（issue #254）：只能上报自己（{@link ApiKeyAuthentication#getName()}
     * 返回的就是 keyId）。</p>
     *
     * <p><b>其它主体</b>（issue #1059）：该端点是<b>摄取面</b>，仓内记录走进程内
     * {@code TokenUsageRecorder}、不经此端点（见 {@code docs/zh/security/rbac-permissions.md}），
     * 因此只保留管理员代报 —— 普通 JWT 账号（USER / VIEWER）调此端点即 403。修复前任意
     * 已认证账号都能用任意 {@code apiKeyId} 代报用量。</p>
     *
     * <p>主体为 {@code null} 时放行：该端点仍要求认证，未认证请求在安全链上已被拒绝，
     * 此处不会成为绕过点。</p>
     *
     * @param authentication  当前认证主体，可为 {@code null}
     * @param claimedApiKeyId 请求体声明的 apiKeyId，可为 {@code null}
     * @return 拒绝原因；{@code null} 表示放行
     */
    private String crossPrincipalWriteError(final Authentication authentication, final String claimedApiKeyId) {
        if (authentication == null) {
            return null;
        }
        if (authentication instanceof ApiKeyAuthentication) {
            final String ownKeyId = authentication.getName();
            if (ownKeyId == null || claimedApiKeyId == null || ownKeyId.equals(claimedApiKeyId)) {
                return null;
            }
            return "API Key 主体只能上报自身的 Token 用量（声明他人 apiKeyId 已拒绝）";
        }
        if (hasAdministratorAuthority(authentication)) {
            return null;
        }
        return "Token 用量摄取面只接受 API Key 主体或管理员代报（当前主体无此权限）";
    }

    /**
     * 判断主体是否具备管理员权限（{@code ROLE_ADMIN}）。
     *
     * <p>刻意不引入 {@code PermissionCodes} 依赖：本类在 persistence 层，角色判定用安全链
     * 已在用的 {@code ROLE_} 前缀即可，且不影响将来权限码迁移。</p>
     *
     * @param authentication 当前认证主体
     * @return 含 {@code ROLE_ADMIN} 时返回 {@code true}
     */
    private static boolean hasAdministratorAuthority(final Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
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
