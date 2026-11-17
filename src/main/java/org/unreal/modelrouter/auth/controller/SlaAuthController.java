package org.unreal.modelrouter.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.service.SlaAccessService;
import org.unreal.modelrouter.auth.security.util.ClientIpUtils;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import reactor.core.publisher.Mono;

/**
 * SLA 凭据换取入口（issue #1174）.
 *
 * <p>这一组端点专门给外部巡检工具用，<b>不面向人</b>：没有密码、没有用户名，只有
 * 「一次性接入令牌」与「长期刷新凭据」两段凭据。它们的鉴权在
 * {@code SecurityConfiguration} 里显式 permitAll（bootstrap）与 {@code RbacExemptEndpoints}
 * 豁免（refresh），因为调用方此时还没有任何凭据 —— 先有鸡还是先有蛋的问题只能在过滤链
 * 这一层解决，不能靠 {@code @PreAuthorize}。</p>
 *
 * <p>安全性落在四处：令牌只存哈希、一次性（乐观锁兜住并发双花）、默认 15 分钟、可绑来源 IP；
 * 刷新凭据可即时吊销。两条链路的失败都用 401 返回、文案不区分「不存在 / 已用 / 已过期」之外的
 * 细节 —— 但内部审计会分别记 {@code SLA_BOOTSTRAP_CONSUMED} / {@code SLA_REFRESH}，排查时看审计。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/sla")
@Tag(name = "SLA 接入", description = "外部巡检工具的凭据换取（一次性令牌 → 长期刷新）")
public class SlaAuthController {

    private final SlaAccessService slaAccessService;

    /**
     * 用一次性接入令牌换访问令牌与刷新凭据。
     *
     * @param request  含明文一次性令牌
     * @param exchange 用于取来源 IP
     * @return 访问令牌、刷新凭据与过期时间
     */
    @PostMapping("/bootstrap")
    @Operation(summary = "用一次性接入令牌换取访问令牌与刷新凭据",
            description = "令牌只能使用一次；可在签发时绑定来源 IP。响应里的 refreshToken 只返回一次，请立即落盘。")
    public Mono<RouterResponse<SlaAccessService.BootstrapResult>> bootstrap(
            @RequestBody final BootstrapRequest request, final ServerWebExchange exchange) {
        final String clientIp = ClientIpUtils.getClientIpAddress(exchange);
        return slaAccessService.bootstrap(request.token(), clientIp)
                .map(result -> RouterResponse.success(result));
    }

    /**
     * 用长期刷新凭据换新的访问令牌。
     *
     * @param request  含明文刷新凭据
     * @param exchange 用于取来源 IP（记录到凭据行，便于排查异地使用）
     * @return 新的访问令牌
     */
    @PostMapping("/refresh")
    @Operation(summary = "用刷新凭据换取新的访问令牌",
            description = "滚动续期：每次刷新都会延长刷新凭据的过期时间，凭据被吊销后立即失效。")
    public Mono<RouterResponse<SlaAccessService.RefreshResult>> refresh(
            @RequestBody final RefreshRequest request, final ServerWebExchange exchange) {
        final String clientIp = ClientIpUtils.getClientIpAddress(exchange);
        return slaAccessService.refresh(request.refreshToken(), clientIp)
                .map(result -> RouterResponse.success(result));
    }

    /**
     * bootstrap 请求体。
     *
     * @param token 明文一次性接入令牌
     */
    public record BootstrapRequest(String token) {
    }

    /**
     * refresh 请求体。
     *
     * @param refreshToken 明文刷新凭据
     */
    public record RefreshRequest(String refreshToken) {
    }
}
