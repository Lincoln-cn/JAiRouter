package org.unreal.modelrouter.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.auth.security.service.SlaAccessService;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.exception.ApiException;

import java.util.List;
import java.util.Map;

/**
 * SLA 接入管理端（issue #1174）.
 *
 * <p>给人用：管理员在这里建账号、签发一次性令牌、吊销、删除。权限码是
 * {@code system:sla:read}（看账号与凭据状态）与 {@code system:sla:manage}（签发/吊销/删除），
 * 不借用 {@code system:accounts:*} —— 「谁能管人账号」与「谁能发外部工具凭据」是两件事。</p>
 *
 * <p>一次性令牌的明文只在这个接口的响应里出现一次，之后库里只有 SHA-256 摘要；吊销不删行，
 * 保留 {@code revoked_at} 是为了让审计能对上「这条凭据什么时候被谁拔掉」。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/security/sla")
@Tag(name = "SLA 接入管理", description = "SLA 服务账号与一次性接入令牌的签发/吊销")
public class SlaAdminController {

    private final SlaAccessService slaAccessService;

    /**
     * 列出全部 SLA 账号。
     *
     * @return 账号视图列表（含有效凭据数与未消费令牌数）
     */
    @GetMapping("/accounts")
    @Operation(summary = "列出 SLA 服务账号")
    public RouterResponse<List<SlaAccessService.AccountView>> listAccounts() {
        return RouterResponse.success(slaAccessService.listAccounts());
    }

    /**
     * 新建 SLA 账号。
     *
     * @param request        账号名与角色模板
     * @param authentication 当前操作人
     * @return 账号视图
     */
    @PostMapping("/accounts")
    @Operation(summary = "新建 SLA 服务账号",
            description = "角色模板只有 SLA_READONLY 与 SLA_OPERATOR；后者含控制面配置写权限。")
    public RouterResponse<SlaAccessService.AccountView> createAccount(
            @RequestBody final CreateAccountRequest request, final Authentication authentication) {
        return RouterResponse.success(slaAccessService.createAccount(
                request.name(), request.roleTemplate(), operatorOf(authentication)));
    }

    /**
     * 为账号签发一次性接入令牌。
     *
     * @param accountId      账号主键
     * @param request        有效期与可选绑定 IP
     * @param authentication 当前操作人
     * @return 明文令牌（只此一次）与过期时间
     */
    @PostMapping("/accounts/{accountId}/bootstrap")
    @Operation(summary = "签发一次性接入令牌",
            description = "明文只返回一次；默认 15 分钟有效、只能使用一次；可绑定来源 IP。")
    public RouterResponse<SlaAccessService.IssueResult> issueBootstrap(
            @PathVariable final String accountId,
            @RequestBody(required = false) final IssueRequest request,
            final Authentication authentication) {
        final IssueRequest effective = request == null ? new IssueRequest(null, null) : request;
        return RouterResponse.success(slaAccessService.issueBootstrap(
                accountId, effective.ttlMinutes(), effective.allowedIp(), operatorOf(authentication)));
    }

    /**
     * 吊销账号名下全部刷新凭据（账号保留，用于「先发新再吊销旧」的轮换）。
     *
     * @param accountId      账号主键
     * @param authentication 当前操作人
     * @return 吊销数量
     */
    @PostMapping("/accounts/{accountId}/revoke")
    @Operation(summary = "吊销账号名下全部凭据",
            description = "同时吊销刷新凭据、作废未消费的接入令牌、停用 jwt 主体："
                    + "已签发的访问令牌在下一个请求即被拒。重新接入需再签发一枚接入令牌。")
    public RouterResponse<Map<String, Object>> revokeAll(
            @PathVariable final String accountId, final Authentication authentication) {
        final int revoked = slaAccessService.revokeAll(accountId, operatorOf(authentication));
        return RouterResponse.success(Map.of("revoked", revoked));
    }

    /**
     * 启用或禁用 SLA 账号（issue #1212）。
     *
     * <p>禁用等价于「吊销 + 封存」：吊销全部凭据、作废未消费接入令牌、停用 {@code jwt_accounts}
     * 主体，已签发的访问令牌随即失效。启用只恢复账号开关，不会自动恢复主体 —— 重新接入需要
     * 重新签发一枚接入令牌，让「谁能回来」这件事始终有一条签发记录可查。</p>
     *
     * @param accountId      账号主键
     * @param request        目标状态（缺字段按入参不合法拒绝，不默认成禁用）
     * @param authentication 当前操作人
     * @return 账号最新视图
     */
    @PostMapping("/accounts/{accountId}/enabled")
    @Operation(summary = "启用或禁用 SLA 服务账号",
            description = "禁用会连带吊销凭据并停用 jwt 主体，已签发访问令牌随即失效；重新启用不会自动恢复主体。")
    public RouterResponse<SlaAccessService.AccountView> setAccountEnabled(
            @PathVariable final String accountId,
            @RequestBody final EnabledRequest request,
            final Authentication authentication) {
        if (request == null || request.enabled() == null) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST.code(),
                    "缺少 enabled 字段（true 启用 / false 禁用）", ErrorCode.INVALID_REQUEST.status());
        }
        return RouterResponse.success(slaAccessService.setAccountEnabled(
                accountId, request.enabled(), operatorOf(authentication)));
    }

    /**
     * 删除 SLA 账号及其全部凭据。
     *
     * @param accountId      账号主键
     * @param authentication 当前操作人
     * @return 空响应
     */
    @DeleteMapping("/accounts/{accountId}")
    @Operation(summary = "删除 SLA 服务账号",
            description = "同时删除其一次性令牌、刷新凭据与 jwt_accounts 里的 sla: 主体。")
    public RouterResponse<Void> deleteAccount(
            @PathVariable final String accountId, final Authentication authentication) {
        slaAccessService.deleteAccount(accountId, operatorOf(authentication));
        return RouterResponse.success();
    }

    private static String operatorOf(final Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "unknown";
        }
        return authentication.getName();
    }

    /**
     * 新建账号请求体。
     *
     * @param name         账号名
     * @param roleTemplate 角色模板（{@code SLA_READONLY} / {@code SLA_OPERATOR}）
     */
    public record CreateAccountRequest(String name, String roleTemplate) {
    }

    /**
     * 启用/禁用请求体。
     *
     * @param enabled {@code true} 启用、{@code false} 禁用；为空视为入参不合法
     */
    public record EnabledRequest(Boolean enabled) {
    }

    /**
     * 签发一次性令牌请求体。
     *
     * @param ttlMinutes 有效期分钟数（为空用 15 分钟）
     * @param allowedIp  绑定的来源 IP（为空不限制）
     */
    public record IssueRequest(Integer ttlMinutes, String allowedIp) {
    }
}
