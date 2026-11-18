package org.unreal.modelrouter.monitor.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.monitor.dto.UiAccessRequest;
import org.unreal.modelrouter.monitor.dto.UiPageViewSummary;
import org.unreal.modelrouter.monitor.service.UiAccessService;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 控制台页面访问统计（B4，2026-11-18）。
 *
 * <p>两个端点，权限面刻意分开：
 * <ul>
 *   <li>{@code POST /api/monitoring/ui-access}——<b>任何已认证用户</b>都能上报（{@code PermissionRule.authenticated}），
 *       因为上报的是「我自己看了哪一页」，用户本人就是被记录的对象；主体从认证态取，
 *       <b>不接受前端传入的 userId</b>，避免越权写别人的访问记录</li>
 *   <li>{@code GET /api/monitoring/ui-access/summary}——需要 {@code security:audit:read}，
 *       这是运营/审计视角的聚合数据</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 3.3.8
 */
@Slf4j
@RestController
@RequestMapping("/api/monitoring/ui-access")
@Tag(name = "控制台访问统计", description = "页面访问上报与热度聚合")
public class UiAccessController {

    private final UiAccessService uiAccessService;

    public UiAccessController(final UiAccessService uiAccessService) {
        this.uiAccessService = uiAccessService;
    }

    /**
     * 上报一次页面访问。
     *
     * <p>总是返回成功：上报是观测行为，失败不应该让前端感知，否则会在控制台刷错误日志。
     */
    @PostMapping
    @Operation(summary = "上报页面访问", description = "记录当前认证用户访问了哪个路由（不记 IP、不记查询参数）")
    public Mono<ResponseEntity<RouterResponse<Void>>> reportAccess(
            @RequestBody(required = false) final UiAccessRequest request,
            final Authentication authentication) {
        if (request == null) {
            return Mono.just(ResponseEntity.ok(RouterResponse.success()));
        }
        final String userId = authentication != null ? authentication.getName() : null;
        return uiAccessService.recordAccess(userId, request.path(), request.role())
                .thenReturn(ResponseEntity.ok(RouterResponse.success()));
    }

    /**
     * 页面访问热度（按页面 × 角色聚合）。
     */
    @GetMapping("/summary")
    @Operation(summary = "页面访问热度", description = "最近 N 天按页面与角色聚合的访问次数与去重用户数")
    public Mono<ResponseEntity<RouterResponse<List<UiPageViewSummary>>>> summary(
            @RequestParam(required = false) final Integer days,
            @RequestParam(required = false) final Integer limit) {
        return uiAccessService.summarize(days, limit)
                .map(list -> ResponseEntity.ok(RouterResponse.success(list)));
    }
}
