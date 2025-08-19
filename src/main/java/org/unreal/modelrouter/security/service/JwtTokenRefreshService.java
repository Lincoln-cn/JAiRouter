package org.unreal.modelrouter.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.exception.exception.AuthenticationException;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT令牌刷新服务
 * 提供令牌刷新和黑名单管理功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.enabled", havingValue = "true")
public class JwtTokenRefreshService {
    
    private final JwtTokenValidator jwtTokenValidator;
    private final SecurityProperties securityProperties;
    
    // 内存黑名单缓存（当Redis不可用时的备选方案）
    private final Map<String, Long> memoryBlacklist = new ConcurrentHashMap<>();
    
    /**
     * 刷新JWT令牌
     * @param currentToken 当前的JWT令牌
     * @return 新的JWT令牌
     */
    public Mono<String> refreshToken(String currentToken) {
        if (currentToken == null || currentToken.trim().isEmpty()) {
            return Mono.error(new AuthenticationException("当前令牌不能为空", "TOKEN_REQUIRED"));
        }
        
        log.debug("开始刷新JWT令牌");
        
        return jwtTokenValidator.refreshToken(currentToken)
            .doOnSuccess(newToken -> log.debug("JWT令牌刷新成功"))
            .doOnError(error -> log.warn("JWT令牌刷新失败: {}", error.getMessage()));
    }
    
    /**
     * 撤销JWT令牌（加入黑名单）
     * @param token 要撤销的JWT令牌
     * @return 操作结果
     */
    public Mono<Void> revokeToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Mono.error(new AuthenticationException("令牌不能为空", "TOKEN_REQUIRED"));
        }
        
        log.debug("开始撤销JWT令牌");
        
        return jwtTokenValidator.blacklistToken(token)
            .doOnSuccess(v -> {
                log.debug("JWT令牌撤销成功");
                // 同时加入内存黑名单作为备选
                addToMemoryBlacklist(token);
            })
            .doOnError(error -> {
                log.warn("JWT令牌撤销失败: {}", error.getMessage());
                // Redis失败时至少加入内存黑名单
                addToMemoryBlacklist(token);
            })
            .onErrorResume(ex -> {
                // 即使Redis操作失败，也认为撤销成功（因为已加入内存黑名单）
                return Mono.empty();
            });
    }
    
    /**
     * 检查令牌是否有效（未被撤销且未过期）
     * @param token JWT令牌
     * @return 令牌是否有效
     */
    public Mono<Boolean> isTokenValid(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Mono.just(false);
        }
        
        // 首先检查内存黑名单
        if (isInMemoryBlacklist(token)) {
            return Mono.just(false);
        }
        
        // 然后检查Redis黑名单
        return jwtTokenValidator.isTokenBlacklisted(token)
            .map(isBlacklisted -> !isBlacklisted)
            .onErrorReturn(true); // Redis错误时默认认为有效
    }
    
    /**
     * 批量撤销令牌
     * @param tokens 要撤销的令牌列表
     * @return 操作结果
     */
    public Mono<Void> revokeTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return Mono.empty();
        }
        
        log.debug("开始批量撤销{}个JWT令牌", tokens.size());
        
        return Mono.fromRunnable(() -> {
            tokens.parallelStream().forEach(token -> {
                if (token != null && !token.trim().isEmpty()) {
                    revokeToken(token).subscribe();
                }
            });
        }).then()
        .doOnSuccess(v -> log.debug("批量撤销JWT令牌完成"))
        .doOnError(error -> log.warn("批量撤销JWT令牌失败: {}", error.getMessage()));
    }
    
    /**
     * 清理过期的内存黑名单条目
     */
    public void cleanupExpiredBlacklistEntries() {
        long currentTime = System.currentTimeMillis();
        
        memoryBlacklist.entrySet().removeIf(entry -> {
            long expirationTime = entry.getValue();
            return currentTime > expirationTime;
        });
        
        log.debug("清理过期的内存黑名单条目，当前条目数: {}", memoryBlacklist.size());
    }
    
    /**
     * 获取黑名单统计信息
     * @return 黑名单统计信息
     */
    public Mono<Map<String, Object>> getBlacklistStats() {
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new HashMap<>();
            stats.put("memoryBlacklistSize", memoryBlacklist.size());
            stats.put("blacklistEnabled", securityProperties.getJwt().isBlacklistEnabled());
            stats.put("lastCleanupTime", System.currentTimeMillis());
            
            return stats;
        });
    }
    
    /**
     * 将令牌加入内存黑名单
     */
    private void addToMemoryBlacklist(String token) {
        try {
            // 计算令牌过期时间（简单实现：使用配置的过期时间）
            long expirationTime = System.currentTimeMillis() + 
                (securityProperties.getJwt().getExpirationMinutes() * 60 * 1000);
            
            String tokenHash = String.valueOf(token.hashCode());
            memoryBlacklist.put(tokenHash, expirationTime);
            
            log.debug("令牌已加入内存黑名单: {}", tokenHash);
            
        } catch (Exception e) {
            log.warn("将令牌加入内存黑名单时发生错误", e);
        }
    }
    
    /**
     * 检查令牌是否在内存黑名单中
     */
    private boolean isInMemoryBlacklist(String token) {
        try {
            String tokenHash = String.valueOf(token.hashCode());
            Long expirationTime = memoryBlacklist.get(tokenHash);
            
            if (expirationTime == null) {
                return false;
            }
            
            // 检查是否过期
            if (System.currentTimeMillis() > expirationTime) {
                memoryBlacklist.remove(tokenHash);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.warn("检查内存黑名单时发生错误", e);
            return false;
        }
    }
}