package org.unreal.modelrouter.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.BatchTokenRevokeRequest;
import org.unreal.modelrouter.common.dto.JwtApiResponse;
import org.unreal.modelrouter.common.dto.JwtTokenInfo;
import org.unreal.modelrouter.common.dto.PagedResult;
import org.unreal.modelrouter.common.dto.TokenRevokeRequest;
import org.unreal.modelrouter.common.dto.TokenStatus;
import org.unreal.modelrouter.common.dto.TokenValidationRequest;
import org.unreal.modelrouter.common.dto.TokenValidationResponse;
import org.unreal.modelrouter.auth.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.auth.security.service.JwtTokenRefreshService;
import org.unreal.modelrouter.auth.security.service.JwtPersistenceService;
import org.unreal.modelrouter.auth.security.service.JwtCleanupService;
import org.unreal.modelrouter.auth.security.service.JwtTokenManagementService;
import org.unreal.modelrouter.auth.security.service.JwtTokenQueryService;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * JWT令牌管理控制器
 * 提供令牌撤销、验证、查询等管理功能的REST API
 * 
 * @see JwtAuthController 认证相关功能（登录、刷新）
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/jwt")
@RequiredArgsConstructor
@Tag(name = "JWT Token Management", description = "JWT令牌管理API")
@ConditionalOnProperty(name = "jairouter.security.jwt.enabled", havingValue = "true")
public class JwtTokenController {

    private final JwtTokenRefreshService tokenRefreshService;
    private final JwtTokenValidator jwtTokenValidator;

    @Autowired(required = false)
    private JwtPersistenceService jwtPersistenceService;

    @Autowired(required = false)
    private JwtCleanupService jwtCleanupService;

    @Autowired(required = false)
    private JwtTokenManagementService jwtTokenManagementService;

    @Autowired(required = false)
    private JwtTokenQueryService jwtTokenQueryService;

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("=== JwtTokenController initialized ===");
        log.info("JwtPersistenceService available: {}", jwtPersistenceService != null);
        log.info("JwtCleanupService available: {}", jwtCleanupService != null);
        log.info("JwtTokenManagementService available: {}", jwtTokenManagementService != null);
    }

    /**
     * 撤销JWT令牌
     */
    @PostMapping("/revoke")
    @Operation(summary = "撤销JWT令牌", description = "将指定的JWT令牌加入黑名单")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "令牌撤销成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足")
    })
    @PreAuthorize("hasRole('ADMIN') or authentication.name == #request.userId")
    public Mono<RouterResponse<JwtApiResponse>> revokeToken(
            @Parameter(description = "令牌撤销请求") @RequestBody final TokenRevokeRequest request,
            final Authentication authentication) {

        log.debug("收到JWT令牌撤销请求: user={}, targetToken={}",
                authentication != null ? authentication.getName() : "anonymous",
                request.getToken() != null ? request.getToken().substring(0, Math.min(10, request.getToken().length())) + "..." : "tokenHash");

        // 检查是否是tokenHash（通常是Base64编码，长度较短）还是完整token
        String tokenToRevoke = request.getToken();
        boolean isTokenHash = tokenToRevoke != null && !tokenToRevoke.contains(".") && tokenToRevoke.length() < 100;
        
        Mono<Void> revokeMono;
        if (isTokenHash) {
            // 直接使用tokenHash进行撤销
            revokeMono = jwtTokenManagementService != null
                    ? jwtTokenManagementService.revokeTokenByHash(tokenToRevoke, request.getReason(),
                            authentication != null ? authentication.getName() : "system")
                    : Mono.empty();
        } else {
            // 使用完整token进行撤销
            revokeMono = tokenRefreshService.revokeToken(tokenToRevoke)
                    .then(jwtTokenManagementService != null
                            ? jwtTokenManagementService.updateTokenStatus(tokenToRevoke, TokenStatus.REVOKED,
                                    request.getReason() != null ? request.getReason() : "手动撤销",
                                    authentication != null ? authentication.getName() : "system")
                            : Mono.empty());
        }

        return revokeMono
                .then(Mono.fromCallable(() -> {
                    JwtApiResponse response = new JwtApiResponse();
                    response.setSuccess(true);
                    response.setMessage("令牌撤销成功");
                    response.setTimestamp(LocalDateTime.now());

                    return RouterResponse.success(response, "令牌撤销成功");
                }))
                .onErrorResume(ex -> {
                    log.warn("JWT令牌撤销失败: {}", ex.getMessage());

                    JwtApiResponse response = new JwtApiResponse();
                    response.setSuccess(false);
                    response.setMessage("令牌撤销失败: " + ex.getMessage());
                    response.setTimestamp(LocalDateTime.now());

                    return Mono.just(RouterResponse.error("令牌撤销失败: " + ex.getMessage(), "TOKEN_REVOKE_FAILED"));
                });
    }

    /**
     * 批量撤销JWT令牌
     */
    @PostMapping("/revoke/batch")
    @Operation(summary = "批量撤销JWT令牌", description = "批量将多个JWT令牌加入黑名单")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "批量撤销成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<JwtApiResponse>> revokeTokensBatch(
            @Parameter(description = "批量令牌撤销请求") @RequestBody final BatchTokenRevokeRequest request,
            final Authentication authentication) {

        log.debug("收到批量JWT令牌撤销请求: user={}, tokenCount={}",
                authentication != null ? authentication.getName() : "anonymous",
                request.getTokens().size());

        // 检查是否是tokenHash还是完整token
        boolean areTokenHashes = request.getTokens().stream()
                .allMatch(token -> token != null && !token.contains(".") && token.length() < 100);
        
        Mono<Void> batchRevokeMono;
        if (areTokenHashes) {
            // 批量撤销tokenHash
            batchRevokeMono = jwtTokenManagementService != null
                    ? jwtTokenManagementService.batchRevokeTokensByHash(request.getTokens(), request.getReason(),
                            authentication != null ? authentication.getName() : "system")
                    : Mono.empty();
        } else {
            // 批量撤销完整token
            batchRevokeMono = tokenRefreshService.revokeTokens(request.getTokens())
                    .then(jwtTokenManagementService != null
                            ? jwtTokenManagementService.batchUpdateTokenStatus(request.getTokens(), TokenStatus.REVOKED,
                                    request.getReason() != null ? request.getReason() : "批量撤销",
                                    authentication != null ? authentication.getName() : "system")
                            : Mono.empty());
        }
        
        return batchRevokeMono
                .then(Mono.fromCallable(() -> {
                    JwtApiResponse response = new JwtApiResponse();
                    response.setSuccess(true);
                    response.setMessage(String.format("成功撤销%d个令牌", request.getTokens().size()));
                    response.setTimestamp(LocalDateTime.now());

                    return RouterResponse.success(response, "批量撤销令牌成功");
                }))
                .onErrorResume(ex -> {
                    log.warn("批量JWT令牌撤销失败: {}", ex.getMessage());

                    JwtApiResponse response = new JwtApiResponse();
                    response.setSuccess(false);
                    response.setMessage("批量撤销失败: " + ex.getMessage());
                    response.setTimestamp(LocalDateTime.now());

                    return Mono.just(RouterResponse.error("批量撤销失败: " + ex.getMessage(), "BATCH_TOKEN_REVOKE_FAILED"));
                });
    }

    /**
     * 验证JWT令牌
     */
    @PostMapping("/validate")
    @Operation(summary = "验证JWT令牌", description = "检查JWT令牌的有效性")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "令牌验证完成"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误")
    })
    public Mono<RouterResponse<TokenValidationResponse>> validateToken(
            @Parameter(description = "令牌验证请求") @RequestBody final TokenValidationRequest request) {

        log.debug("收到JWT令牌验证请求");

        return tokenRefreshService.isTokenValid(request.getToken())
                .flatMap(isValid -> {
                    if (isValid) {
                        // 令牌基本有效，进一步检查持久化状态
                        return (jwtTokenQueryService != null
                                ? jwtTokenQueryService.checkTokenPersistenceStatus(request.getToken())
                                : Mono.just(true))
                                .flatMap(persistenceValid -> {
                                    if (persistenceValid) {
                                        // 令牌有效，提取用户信息
                                        return jwtTokenValidator.extractUserId(request.getToken())
                                                .map(userId -> {
                                                    TokenValidationResponse response = new TokenValidationResponse();
                                                    response.setValid(true);
                                                    response.setUserId(userId);
                                                    response.setMessage("令牌有效");
                                                    response.setTimestamp(LocalDateTime.now());

                                                    return RouterResponse.success(response, "令牌有效");
                                                });
                                    } else {
                                        // 持久化状态显示令牌无效
                                        TokenValidationResponse response = new TokenValidationResponse();
                                        response.setValid(false);
                                        response.setMessage("令牌已被撤销或过期");
                                        response.setTimestamp(LocalDateTime.now());

                                        return Mono.just(RouterResponse.success(response, "令牌已被撤销或过期"));
                                    }
                                });
                    } else {
                        // 令牌无效
                        TokenValidationResponse response = new TokenValidationResponse();
                        response.setValid(false);
                        response.setMessage("令牌无效或已被撤销");
                        response.setTimestamp(LocalDateTime.now());

                        return Mono.just(RouterResponse.success(response, "令牌无效或已被撤销"));
                    }
                })
                .onErrorResume(ex -> {
                    log.warn("JWT令牌验证失败: {}", ex.getMessage());

                    TokenValidationResponse response = new TokenValidationResponse();
                    response.setValid(false);
                    response.setMessage("令牌验证失败: " + ex.getMessage());
                    response.setTimestamp(LocalDateTime.now());

                    return Mono.just(RouterResponse.success(response, "令牌验证失败"));
                });
    }

    /**
     * 获取黑名单统计信息
     */
    @GetMapping("/blacklist/stats")
    @Operation(summary = "获取黑名单统计", description = "获取JWT令牌黑名单的统计信息")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "统计信息获取成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<Map<String, Object>>> getBlacklistStats(final Authentication authentication) {

        log.debug("收到黑名单统计信息请求: user={}",
                authentication != null ? authentication.getName() : "anonymous");

        return tokenRefreshService.getBlacklistStats()
                .flatMap(stats -> {
                    // 如果持久化服务可用，添加持久化统计信息
                    if (jwtPersistenceService != null && jwtTokenQueryService != null) {
                        return jwtTokenQueryService.enhanceBlacklistStats(stats)
                                .map(enhancedStats -> RouterResponse.success(enhancedStats, "获取黑名单统计信息成功"));
                    } else {
                        return Mono.just(RouterResponse.success(stats, "获取黑名单统计信息成功"));
                    }
                })
                .onErrorResume(ex -> {
                    log.warn("获取黑名单统计信息失败: {}", ex.getMessage());
                    return Mono.just(RouterResponse.error("获取黑名单统计信息失败", "BLACKLIST_STATS_FAILED"));
                });
    }

    // ========================================
    // 新增的令牌管理API端点 (Task 7.1)
    // ========================================

    /**
     * 获取令牌列表
     */
    @GetMapping("/tokens")
    @Operation(summary = "获取令牌列表", description = "分页获取JWT令牌列表，支持按用户ID和状态过滤")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "令牌列表获取成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足")
    })
    @PreAuthorize("hasRole('ADMIN') or (hasRole('USER') and #userId == authentication.name)")
    public Mono<RouterResponse<PagedResult<JwtTokenInfo>>> getTokens(
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") final int page,
            @Parameter(description = "页大小") @RequestParam(defaultValue = "20") final int size,
            @Parameter(description = "用户ID过滤") @RequestParam(required = false) final String userId,
            @Parameter(description = "令牌状态过滤") @RequestParam(required = false) final TokenStatus status,
            final Authentication authentication) {

        log.debug("收到获取令牌列表请求: user={}, page={}, size={}, userId={}, status={}",
                authentication != null ? authentication.getName() : "anonymous", page, size, userId, status);

        // 检查服务可用性
        if (jwtPersistenceService == null) {
            return Mono.just(RouterResponse.error("令牌持久化服务未启用", "SERVICE_NOT_AVAILABLE"));
        }

        // 参数验证
        if (page < 0) {
            return Mono.just(RouterResponse.error("页码不能小于0", "INVALID_PAGE"));
        }
        if (size <= 0 || size > 100) {
            return Mono.just(RouterResponse.error("页大小必须在1-100之间", "INVALID_SIZE"));
        }

        // 根据过滤条件获取令牌列表
        Mono<PagedResult<JwtTokenInfo>> resultMono;
        
        if (userId != null && !userId.trim().isEmpty()) {
            // 按用户ID过滤
            resultMono = jwtPersistenceService.findTokensByUserId(userId.trim(), page, size)
                    .flatMap(tokens -> {
                        // 如果指定了状态过滤，进一步过滤
                        final java.util.List<JwtTokenInfo> filteredTokens = status != null 
                                ? tokens.stream()
                                    .filter(token -> status.equals(token.getStatus()))
                                    .toList()
                                : tokens;
                        
                        // 计算总数（简化实现，实际应该在服务层优化）
                        return jwtPersistenceService.countActiveTokens()
                                .map(totalCount -> new PagedResult<>(filteredTokens, page, size, totalCount));
                    });
        } else {
            // 获取所有令牌
            resultMono = jwtPersistenceService.findAllTokens(page, size)
                    .flatMap(tokens -> {
                        // 如果指定了状态过滤，进一步过滤
                        final java.util.List<JwtTokenInfo> filteredTokens = status != null 
                                ? tokens.stream()
                                    .filter(token -> status.equals(token.getStatus()))
                                    .toList()
                                : tokens;
                        
                        // 计算总数
                        Mono<Long> countMono = status != null 
                                ? jwtPersistenceService.countTokensByStatus(status)
                                : jwtPersistenceService.countActiveTokens();
                        
                        return countMono.map(totalCount -> new PagedResult<>(filteredTokens, page, size, totalCount));
                    });
        }

        return resultMono
                .map(result -> RouterResponse.success(result, "令牌列表获取成功"))
                .onErrorResume(ex -> {
                    log.warn("获取令牌列表失败: {}", ex.getMessage());
                    return Mono.just(RouterResponse.error("获取令牌列表失败: " + ex.getMessage(), "GET_TOKENS_FAILED"));
                });
    }

    /**
     * 获取令牌详情
     */
    @GetMapping("/tokens/{tokenId}")
    @Operation(summary = "获取令牌详情", description = "根据令牌ID获取JWT令牌的详细信息")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "令牌详情获取成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "令牌不存在"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<JwtTokenInfo>> getTokenDetails(
            @Parameter(description = "令牌ID") @PathVariable final String tokenId,
            final Authentication authentication) {

        log.debug("收到获取令牌详情请求: user={}, tokenId={}",
                authentication != null ? authentication.getName() : "anonymous", tokenId);

        // 检查服务可用性
        if (jwtPersistenceService == null) {
            return Mono.just(RouterResponse.error("令牌持久化服务未启用", "SERVICE_NOT_AVAILABLE"));
        }

        // 参数验证
        if (tokenId == null || tokenId.trim().isEmpty()) {
            return Mono.just(RouterResponse.error("令牌ID不能为空", "INVALID_TOKEN_ID"));
        }

        return jwtPersistenceService.findByTokenId(tokenId.trim())
                .map(tokenInfo -> RouterResponse.success(tokenInfo, "令牌详情获取成功"))
                .switchIfEmpty(Mono.just(RouterResponse.error("令牌不存在", "TOKEN_NOT_FOUND")))
                .onErrorResume(ex -> {
                    log.warn("获取令牌详情失败: tokenId={}, error={}", tokenId, ex.getMessage());
                    return Mono.just(RouterResponse.error("获取令牌详情失败: " + ex.getMessage(), "GET_TOKEN_DETAILS_FAILED"));
                });
    }

    /**
     * 手动清理过期令牌
     */
    @PostMapping("/cleanup")
    @Operation(summary = "手动清理过期令牌", description = "手动触发清理过期的JWT令牌和黑名单条目")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "清理操作完成"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "清理操作失败")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<JwtCleanupService.CleanupResult>> cleanupExpiredTokens(final Authentication authentication) {

        log.debug("收到手动清理过期令牌请求: user={}",
                authentication != null ? authentication.getName() : "anonymous");

        // 检查服务可用性
        if (jwtCleanupService == null) {
            return Mono.just(RouterResponse.error("令牌清理服务未启用", "SERVICE_NOT_AVAILABLE"));
        }

        return jwtCleanupService.performFullCleanup()
                .map(result -> {
                    String message = String.format("清理完成，删除了%d个过期令牌和%d个过期黑名单条目", 
                            result.getRemovedTokens(), result.getRemovedBlacklistEntries());
                    return RouterResponse.success(result, message);
                })
                .onErrorResume(ex -> {
                    log.warn("手动清理过期令牌失败: {}", ex.getMessage());
                    
                    // 创建失败的清理结果
                    JwtCleanupService.CleanupResult failedResult = new JwtCleanupService.CleanupResult();
                    failedResult.setSuccess(false);
                    failedResult.setErrorMessage(ex.getMessage());
                    failedResult.setStartTime(LocalDateTime.now());
                    failedResult.setEndTime(LocalDateTime.now());
                    
                    return Mono.just(RouterResponse.error("清理操作失败: " + ex.getMessage(), "CLEANUP_FAILED"));
                });
    }

    /**
     * 获取清理统计信息
     */
    @GetMapping("/cleanup/stats")
    @Operation(summary = "获取清理统计信息", description = "获取JWT令牌清理操作的统计信息")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "清理统计信息获取成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<JwtCleanupService.CleanupStats>> getCleanupStats(final Authentication authentication) {

        log.debug("收到获取清理统计信息请求: user={}",
                authentication != null ? authentication.getName() : "anonymous");

        // 检查服务可用性
        if (jwtCleanupService == null) {
            return Mono.just(RouterResponse.error("令牌清理服务未启用", "SERVICE_NOT_AVAILABLE"));
        }

        return jwtCleanupService.getCleanupStats()
                .map(stats -> RouterResponse.success(stats, "清理统计信息获取成功"))
                .onErrorResume(ex -> {
                    log.warn("获取清理统计信息失败: {}", ex.getMessage());
                    return Mono.just(RouterResponse.error("获取清理统计信息失败: " + ex.getMessage(), "GET_CLEANUP_STATS_FAILED"));
                });
    }

    // ========================================
    // 辅助方法
    // ========================================



    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress(final org.springframework.web.server.ServerWebExchange exchange) {
        return org.unreal.modelrouter.auth.security.util.ClientIpUtils.getClientIpAddress(exchange);
    }
}