package org.unreal.modelrouter.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.auth.security.ratelimit.LoginBruteForceGuard;
import org.unreal.modelrouter.auth.security.ratelimit.LoginGuardConfigService;
import org.unreal.modelrouter.auth.security.ratelimit.LoginGuardSettings;
import org.unreal.modelrouter.common.controller.response.RouterResponse;

import java.util.List;
import java.util.Map;

/**
 * 登录保护配置与解锁接口（issue #1176）.
 *
 * <p>为什么需要：{@code LoginBruteForceGuard} 的四个阈值原先只来自 {@code @Value}，
 * 改一次要改配置文件 + 重启；429 响应也没有剩余时间，被锁的人只能盲等 15 分钟。
 * 这里把「阈值」做成可持久化、可热改的运行时配置，并提供「谁被锁了 / 手动解锁」。</p>
 *
 * <p>权限：{@code GET} 走 {@code security:loginguard:read}，写与解锁走
 * {@code security:loginguard:manage}（见 {@code PermissionRuleRegistry}）。两个码都进
 * {@code MANAGE_PAIRED_READ_TO_WRITE} 配对表，所以 VIEWER 与自定义只读角色不会凭空拿到。</p>
 *
 * <p>注意：解锁只清本实例的内存计数。多副本部署时其它副本的计数仍在 —— 这是既有架构限制
 * （计数不共享），不是本接口的疏漏。</p>
 *
 * @since 2026-11（issue #1176）
 */
@RestController
@RequestMapping("/api/security/login-guard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "登录保护", description = "登录暴力破解频控的阈值配置与被锁主体管理")
public class LoginGuardController {

    private final LoginGuardConfigService configService;

    /**
     * 当前生效的阈值配置。
     *
     * @return 配置快照（含最后修改人与时间）
     */
    @GetMapping
    @Operation(summary = "查询登录保护配置", description = "返回当前生效的阈值；首次由 YAML 落库，之后以数据库为准")
    public RouterResponse<LoginGuardConfigService.ConfigView> current() {
        return RouterResponse.success(configService.current());
    }

    /**
     * 修改阈值配置（写库 + 立即热改 + 审计）。
     *
     * @param request 四个阈值
     * @param authentication 操作人
     * @return 热改后的配置快照
     */
    @PutMapping
    @Operation(summary = "修改登录保护配置", description = "四个阈值必须同时提供；非法值返回 400")
    public RouterResponse<LoginGuardConfigService.ConfigView> update(
            @RequestBody final LoginGuardUpdateRequest request,
            final Authentication authentication) {
        final LoginGuardSettings settings = LoginGuardConfigService.validated(
                request.maxFailuresPerPrincipal(), request.maxFailuresPerIp(),
                request.windowMinutes(), request.lockMinutes());
        return RouterResponse.success(configService.update(settings, operatorOf(authentication)));
    }

    /**
     * 当前仍被锁定的主体。
     *
     * @return 被锁主体列表（按剩余时间升序）
     */
    @GetMapping("/locked")
    @Operation(summary = "查询当前被锁定的主体", description = "只返回仍在锁定窗口内的条目")
    public RouterResponse<List<LoginBruteForceGuard.LockedPrincipal>> locked() {
        return RouterResponse.success(configService.lockedPrincipals());
    }

    /**
     * 手动解除登录锁定。
     *
     * <p>参数组合语义见 {@link LoginBruteForceGuard#unlock(String, String)}：两者都给只解该组合，
     * 只给其一只解对应维度，都不给则清空全部。</p>
     *
     * @param request 用户名与/或 IP
     * @param authentication 操作人
     * @return 清除的计数键数量
     */
    @PostMapping("/unlock")
    @Operation(summary = "解除登录锁定", description = "不传参数表示全部解锁；返回清除的计数键数量")
    public RouterResponse<Map<String, Object>> unlock(
            @RequestBody(required = false) final LoginGuardUnlockRequest request,
            final Authentication authentication) {
        final String username = request == null ? null : request.username();
        final String ip = request == null ? null : request.ip();
        final int cleared = configService.unlock(username, ip, operatorOf(authentication));
        return RouterResponse.success(Map.of("cleared", cleared));
    }

    private static String operatorOf(final Authentication authentication) {
        return authentication == null ? "unknown" : authentication.getName();
    }

    /**
     * 修改配置的请求体。
     *
     * @param maxFailuresPerPrincipal 按 IP + 用户名的失败阈值
     * @param maxFailuresPerIp        按 IP 的失败阈值
     * @param windowMinutes           计数窗口（分钟）
     * @param lockMinutes             锁定时长（分钟）
     */
    public record LoginGuardUpdateRequest(Integer maxFailuresPerPrincipal, Integer maxFailuresPerIp,
            Integer windowMinutes, Integer lockMinutes) {
    }

    /**
     * 解锁请求体。
     *
     * @param username 用户名（可空）
     * @param ip       客户端 IP（可空）
     */
    public record LoginGuardUnlockRequest(String username, String ip) {
    }
}
