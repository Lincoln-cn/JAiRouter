package org.unreal.modelrouter.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.auth.security.service.SlaAccessService;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.PagedResult;
import org.unreal.modelrouter.common.exception.ApiException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
 *
 * <p>issue #1215：本组端点全部是阻塞的 JPA 调用，方法签名因此返回 {@link Mono} 并把实际工作
 * 放到 {@code boundedElastic}——直接在事件循环线程上跑数据库查询，会把整条 Netty 线程卡住。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/security/sla")
@Tag(name = "SLA 接入管理", description = "SLA 服务账号与一次性接入令牌的签发/吊销")
public class SlaAdminController {

    /** 列表默认每页条数。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 列表单页上限：再大就不该走列表接口了。 */
    private static final int MAX_PAGE_SIZE = 100;

    private final SlaAccessService slaAccessService;

    /**
     * 分页列出 SLA 账号。
     *
     * @param page    页码（从 0 开始，默认 0）
     * @param size    每页条数（1~100，默认 20）
     * @param keyword 名称关键字（可选）
     * @return 分页后的账号视图
     */
    @GetMapping("/accounts")
    @Operation(summary = "分页列出 SLA 服务账号")
    public Mono<RouterResponse<SlaAccountPage>> listAccounts(
            @Parameter(description = "页码，从 0 开始")
            @RequestParam(required = false) final Integer page,
            @Parameter(description = "每页条数，1~100")
            @RequestParam(required = false) final Integer size,
            @Parameter(description = "账号名关键字")
            @RequestParam(required = false) final String keyword) {
        return Mono.fromCallable(() -> {
                    final int effectivePage = page == null ? 0 : page;
                    final int effectiveSize = size == null ? DEFAULT_PAGE_SIZE : size;
                    if (effectivePage < 0) {
                        throw ApiException.of(ErrorCode.INVALID_PAGE.code(),
                                "page 不能小于 0", ErrorCode.INVALID_PAGE.status());
                    }
                    if (effectiveSize < 1 || effectiveSize > MAX_PAGE_SIZE) {
                        throw ApiException.of(ErrorCode.INVALID_SIZE.code(),
                                "size 必须在 1~" + MAX_PAGE_SIZE + " 之间", ErrorCode.INVALID_SIZE.status());
                    }
                    return RouterResponse.success(new SlaAccountPage(
                            slaAccessService.listAccounts(effectivePage, effectiveSize, keyword),
                            slaAccessService.summarize()));
                })
                .subscribeOn(Schedulers.boundedElastic());
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
    public Mono<RouterResponse<SlaAccessService.AccountView>> createAccount(
            @RequestBody final CreateAccountRequest request, final Authentication authentication) {
        return Mono.fromCallable(() -> RouterResponse.success(slaAccessService.createAccount(
                        request.name(), request.roleTemplate(), operatorOf(authentication))))
                .subscribeOn(Schedulers.boundedElastic());
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
            description = "明文只返回一次；默认 15 分钟有效、最长 60 分钟、只能使用一次；可绑定来源 IP 或网段。")
    public Mono<RouterResponse<SlaAccessService.IssueResult>> issueBootstrap(
            @PathVariable final String accountId,
            @RequestBody(required = false) final IssueRequest request,
            final Authentication authentication) {
        final IssueRequest effective = request == null ? new IssueRequest(null, null) : request;
        return Mono.fromCallable(() -> RouterResponse.success(slaAccessService.issueBootstrap(
                        accountId, effective.ttlMinutes(), effective.allowedIp(), operatorOf(authentication))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 吊销账号名下全部凭据。
     *
     * @param accountId      账号主键
     * @param authentication 当前操作人
     * @return 吊销数量
     */
    @PostMapping("/accounts/{accountId}/revoke")
    @Operation(summary = "吊销账号名下全部凭据",
            description = "同时吊销刷新凭据、作废未消费的接入令牌、停用 jwt 主体："
                    + "已签发的访问令牌在下一个请求即被拒。重新接入需再签发一枚接入令牌。")
    public Mono<RouterResponse<Map<String, Object>>> revokeAll(
            @PathVariable final String accountId, final Authentication authentication) {
        return Mono.fromCallable(() -> {
                    final int revoked = slaAccessService.revokeAll(accountId, operatorOf(authentication));
                    return RouterResponse.<Map<String, Object>>success(Map.of("revoked", revoked));
                })
                .subscribeOn(Schedulers.boundedElastic());
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
    public Mono<RouterResponse<SlaAccessService.AccountView>> setAccountEnabled(
            @PathVariable final String accountId,
            @RequestBody final EnabledRequest request,
            final Authentication authentication) {
        return Mono.fromCallable(() -> {
                    if (request == null || request.enabled() == null) {
                        throw ApiException.of(ErrorCode.INVALID_REQUEST.code(),
                                "缺少 enabled 字段（true 启用 / false 禁用）",
                                ErrorCode.INVALID_REQUEST.status());
                    }
                    return RouterResponse.success(slaAccessService.setAccountEnabled(
                            accountId, request.enabled(), operatorOf(authentication)));
                })
                .subscribeOn(Schedulers.boundedElastic());
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
    public Mono<RouterResponse<Void>> deleteAccount(
            @PathVariable final String accountId, final Authentication authentication) {
        return Mono.fromCallable(() -> {
                    slaAccessService.deleteAccount(accountId, operatorOf(authentication));
                    return RouterResponse.<Void>success();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static String operatorOf(final Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "unknown";
        }
        return authentication.getName();
    }

    /**
     * 列表响应：当前页 + 全量汇总（issue #1219）。
     *
     * <p>汇总与列表一起返回：分页之后前端不能再拿「当前页求和」充总数。</p>
     *
     * @param page    当前页
     * @param summary 全量汇总指标
     */
    public record SlaAccountPage(PagedResult<SlaAccessService.AccountView> page,
                                 SlaAccessService.AccountSummary summary) {
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
     * @param ttlMinutes 有效期分钟数（为空用 15 分钟，超过 60 按 60 夹取）
     * @param allowedIp  绑定的来源 IP 或网段（为空不限制）
     */
    public record IssueRequest(Integer ttlMinutes, String allowedIp) {
    }
}
