package org.unreal.modelrouter.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.JwtTokenInfo;
import org.unreal.modelrouter.common.dto.LoginRequest;
import org.unreal.modelrouter.common.dto.LoginResponse;
import org.unreal.modelrouter.common.dto.TokenRefreshRequest;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.service.AccountManager;
import org.unreal.modelrouter.auth.security.service.JwtTokenRefreshService;
import org.unreal.modelrouter.auth.security.service.JwtPersistenceService;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * JWT认证控制器
 * 提供用户登录和令牌刷新功能
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/jwt")
@RequiredArgsConstructor
@Tag(name = "JWT Authentication", description = "JWT认证API")
@ConditionalOnProperty(name = "jairouter.security.jwt.enabled", havingValue = "true")
public class JwtAuthController {

    private final JwtTokenRefreshService tokenRefreshService;
    private final AccountManager accountManager;
    private final SecurityProperties securityProperties;

    @Autowired(required = false)
    private JwtPersistenceService jwtPersistenceService;

    /**
     * 用户登录获取JWT令牌
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码登录获取JWT令牌")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登录成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "用户名或密码错误")
    })
    public Mono<RouterResponse<LoginResponse>> login(
            @Parameter(description = "登录请求") @RequestBody final LoginRequest request,
            final org.springframework.web.server.ServerWebExchange exchange) {

        log.info("收到用户登录请求: username={}", request.getUsername());

        String clientIp = getClientIpAddress(exchange);
        String userAgent = exchange != null ? exchange.getRequest().getHeaders().getFirst("User-Agent") : null;

        return accountManager.authenticateAndGenerateToken(request.getUsername(), request.getPassword(), securityProperties, clientIp, userAgent)
                .flatMap(token -> {
                    LoginResponse response = new LoginResponse();
                    response.setToken(token);
                    response.setTokenType("Bearer");
                    response.setExpiresIn(securityProperties.getJwt().getExpirationMinutes() * 60L);

                    if (jwtPersistenceService != null) {
                        log.info("保存令牌元数据到H2数据库: username={}, ip={}", request.getUsername(), clientIp);
                        return tokenRefreshService.saveTokenMetadata(token, request.getUsername(), userAgent, clientIp, userAgent)
                                .doOnSuccess(v -> log.info("✓ 令牌元数据已成功保存到H2数据库: username={}", request.getUsername()))
                                .then(Mono.just(RouterResponse.success(response, "登录成功")))
                                .onErrorResume(ex -> {
                                    log.error("✗ 保存令牌元数据失败: {}", ex.getMessage(), ex);
                                    return Mono.just(RouterResponse.success(response, "登录成功"));
                                });
                    } else {
                        log.warn("!!! JwtPersistenceService不可用，令牌未持久化 !!!");
                        return Mono.just(RouterResponse.success(response, "登录成功"));
                    }
                })
                .onErrorResume(ex -> {
                    log.warn("用户登录失败: {}", ex.getMessage());
                    return Mono.just(RouterResponse.error("登录失败: " + ex.getMessage(), "LOGIN_FAILED"));
                });
    }

    /**
     * 刷新JWT令牌
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新JWT令牌", description = "使用当前令牌获取新的JWT令牌")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "令牌刷新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "当前令牌无效或已过期"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误")
    })
    public Mono<RouterResponse<JwtTokenInfo>> refreshToken(
            @Parameter(description = "令牌刷新请求") @RequestBody final TokenRefreshRequest request,
            final Authentication authentication,
            final org.springframework.web.server.ServerWebExchange exchange) {

        log.debug("收到JWT令牌刷新请求: user={}", authentication != null ? authentication.getName() : "anonymous");

        String clientIp = getClientIpAddress(exchange);
        String userAgent = exchange != null ? exchange.getRequest().getHeaders().getFirst("User-Agent") : null;

        return tokenRefreshService.refreshToken(request.getToken(), clientIp, userAgent)
                .flatMap(newToken -> {
                    JwtTokenInfo response = new JwtTokenInfo();
                    response.setToken(newToken);
                    response.setTokenType("Bearer");
                    response.setMessage("令牌刷新成功");
                    response.setTimestamp(LocalDateTime.now());

                    if (jwtPersistenceService != null && authentication != null) {
                        return tokenRefreshService.saveTokenOnRefreshWithContext(request.getToken(), newToken, userAgent, clientIp, userAgent)
                                .then(Mono.just(RouterResponse.success(response, "令牌刷新成功")))
                                .onErrorResume(ex -> {
                                    log.warn("刷新时保存令牌元数据失败: {}", ex.getMessage());
                                    return Mono.just(RouterResponse.success(response, "令牌刷新成功"));
                                });
                    } else {
                        return Mono.just(RouterResponse.success(response, "令牌刷新成功"));
                    }
                })
                .onErrorResume(ex -> {
                    log.warn("JWT令牌刷新失败: {}", ex.getMessage());

                    JwtTokenInfo response = new JwtTokenInfo();
                    response.setMessage("令牌刷新失败: " + ex.getMessage());
                    response.setTimestamp(LocalDateTime.now());

                    return Mono.just(RouterResponse.error("令牌刷新失败: " + ex.getMessage(), "TOKEN_REFRESH_FAILED"));
                });
    }

    private String getClientIpAddress(final org.springframework.web.server.ServerWebExchange exchange) {
        return org.unreal.modelrouter.auth.security.util.ClientIpUtils.getClientIpAddress(exchange);
    }
}
