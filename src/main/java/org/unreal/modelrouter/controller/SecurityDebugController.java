package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.security.service.EnhancedJwtBlacklistService;
import org.unreal.modelrouter.security.util.ClientIpUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 安全调试控制器
 * 提供安全功能的调试和测试端点
 */
@Slf4j
@RestController
@RequestMapping("/api/debug/security")
@RequiredArgsConstructor
@Tag(name = "Security Debug", description = "安全功能调试API")
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true")
public class SecurityDebugController {
    
    @Autowired(required = false)
    private EnhancedJwtBlacklistService enhancedBlacklistService;
    
    /**
     * 获取客户端IP地址详情（调试用）
     */
    @GetMapping("/client-ip")
    @Operation(summary = "获取客户端IP地址详情", description = "获取客户端IP地址的详细信息，用于调试IP获取功能")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<Map<String, Object>>> getClientIpDetails(
            ServerWebExchange exchange,
            Authentication authentication) {
        
        log.debug("收到客户端IP调试请求: user={}", 
                authentication != null ? authentication.getName() : "anonymous");
        
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();
            
            // 获取最终IP
            String clientIp = ClientIpUtils.getClientIpAddress(exchange);
            result.put("clientIp", clientIp);
            
            // 获取详细信息
            String details = ClientIpUtils.getClientIpDetails(exchange);
            result.put("details", details);
            
            // 请求头信息
            Map<String, String> headers = new HashMap<>();
            exchange.getRequest().getHeaders().forEach((key, values) -> {
                if (key.toLowerCase().contains("forward") || 
                    key.toLowerCase().contains("real") || 
                    key.toLowerCase().contains("client") ||
                    key.toLowerCase().contains("proxy")) {
                    headers.put(key, String.join(", ", values));
                }
            });
            result.put("relevantHeaders", headers);
            
            // 远程地址信息
            if (exchange.getRequest().getRemoteAddress() != null) {
                result.put("remoteAddress", exchange.getRequest().getRemoteAddress().toString());
                result.put("remoteHostAddress", 
                    exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
            }
            
            result.put("timestamp", LocalDateTime.now());
            
            return RouterResponse.success(result, "客户端IP信息获取成功");
        });
    }
    
    /**
     * 测试JWT黑名单功能
     */
    @PostMapping("/blacklist/test")
    @Operation(summary = "测试JWT黑名单功能", description = "测试JWT令牌黑名单的添加和检查功能")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<Map<String, Object>>> testBlacklist(
            @RequestParam String tokenId,
            @RequestParam(defaultValue = "3600") long ttlSeconds,
            Authentication authentication) {
        
        log.debug("收到黑名单测试请求: user={}, tokenId={}, ttl={}", 
                authentication != null ? authentication.getName() : "anonymous", tokenId, ttlSeconds);
        
        if (enhancedBlacklistService == null) {
            return Mono.just(RouterResponse.<Map<String, Object>>error("增强黑名单服务未启用", "SERVICE_NOT_AVAILABLE"));
        }
        
        return enhancedBlacklistService.addToBlacklist(tokenId, ttlSeconds)
                .flatMap(addResult -> {
                    if (addResult) {
                        // 测试检查功能
                        return enhancedBlacklistService.isBlacklisted(tokenId)
                                .flatMap(isBlacklisted -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("tokenId", tokenId);
                                    result.put("ttlSeconds", ttlSeconds);
                                    result.put("addToBlacklistResult", addResult);
                                    result.put("isBlacklistedResult", isBlacklisted);
                                    result.put("timestamp", LocalDateTime.now());
                                    
                                    // 获取统计信息
                                    return enhancedBlacklistService.getStats()
                                            .map(stats -> {
                                                result.put("blacklistStats", stats);
                                                
                                                String message = String.format(
                                                    "黑名单测试完成 - 添加: %s, 检查: %s", 
                                                    addResult ? "成功" : "失败",
                                                    isBlacklisted ? "在黑名单中" : "不在黑名单中"
                                                );
                                                
                                                return RouterResponse.success(result, message);
                                            });
                                });
                    } else {
                        Map<String, Object> result = new HashMap<>();
                        result.put("tokenId", tokenId);
                        result.put("ttlSeconds", ttlSeconds);
                        result.put("addToBlacklistResult", false);
                        result.put("timestamp", LocalDateTime.now());
                        
                        return Mono.just(RouterResponse.<Map<String, Object>>error("添加到黑名单失败", "BLACKLIST_ADD_FAILED"));
                    }
                })
                .onErrorResume(ex -> {
                    log.error("黑名单测试失败: {}", ex.getMessage(), ex);
                    return Mono.just(RouterResponse.<Map<String, Object>>error("黑名单测试失败: " + ex.getMessage(), "BLACKLIST_TEST_FAILED"));
                });
    }
    
    /**
     * 获取黑名单统计信息
     */
    @GetMapping("/blacklist/stats")
    @Operation(summary = "获取黑名单统计信息", description = "获取增强黑名单服务的统计信息")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<EnhancedJwtBlacklistService.BlacklistStats>> getBlacklistStats(
            Authentication authentication) {
        
        log.debug("收到黑名单统计请求: user={}", 
                authentication != null ? authentication.getName() : "anonymous");
        
        if (enhancedBlacklistService == null) {
            return Mono.just(RouterResponse.<EnhancedJwtBlacklistService.BlacklistStats>error("增强黑名单服务未启用", "SERVICE_NOT_AVAILABLE"));
        }
        
        return enhancedBlacklistService.getStats()
                .map(stats -> RouterResponse.success(stats, "黑名单统计信息获取成功"))
                .onErrorResume(ex -> {
                    log.error("获取黑名单统计失败: {}", ex.getMessage(), ex);
                    return Mono.just(RouterResponse.<EnhancedJwtBlacklistService.BlacklistStats>error("获取黑名单统计失败: " + ex.getMessage(), "BLACKLIST_STATS_FAILED"));
                });
    }
    
    /**
     * 清理黑名单中的指定令牌（测试用）
     */
    @DeleteMapping("/blacklist/clean")
    @Operation(summary = "清理黑名单令牌", description = "从黑名单中移除指定的令牌（测试用）")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<Map<String, Object>>> cleanBlacklistToken(
            @RequestParam String tokenId,
            Authentication authentication) {
        
        log.debug("收到黑名单清理请求: user={}, tokenId={}", 
                authentication != null ? authentication.getName() : "anonymous", tokenId);
        
        if (enhancedBlacklistService == null) {
            return Mono.just(RouterResponse.<Map<String, Object>>error("增强黑名单服务未启用", "SERVICE_NOT_AVAILABLE"));
        }
        
        return enhancedBlacklistService.removeFromBlacklist(tokenId)
                .map(removeResult -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("tokenId", tokenId);
                    result.put("removeResult", removeResult);
                    result.put("timestamp", LocalDateTime.now());
                    
                    String message = removeResult ? "令牌已从黑名单移除" : "令牌移除失败";
                    return RouterResponse.success(result, message);
                })
                .onErrorResume(ex -> {
                    log.error("黑名单清理失败: {}", ex.getMessage(), ex);
                    return Mono.just(RouterResponse.<Map<String, Object>>error("黑名单清理失败: " + ex.getMessage(), "BLACKLIST_CLEAN_FAILED"));
                });
    }
}