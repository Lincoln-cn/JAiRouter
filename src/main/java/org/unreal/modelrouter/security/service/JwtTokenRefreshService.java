package org.unreal.modelrouter.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.dto.JwtTokenInfo;
import org.unreal.modelrouter.dto.TokenStatus;
import org.unreal.modelrouter.exception.AuthenticationException;
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.util.TokenHashUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    
    // 可选的持久化服务（当启用时自动注入）
    @Autowired(required = false)
    private JwtPersistenceService jwtPersistenceService;
    
    @Autowired(required = false)
    private JwtBlacklistService jwtBlacklistService;
    
    @Autowired(required = false)
    private JwtTokenLifecycleService jwtTokenLifecycleService;
    
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
            .flatMap(newToken -> {
                // 如果启用了持久化，保存新令牌并撤销旧令牌
                if (jwtPersistenceService != null) {
                    return saveTokenOnRefresh(currentToken, newToken)
                        .thenReturn(newToken);
                }
                return Mono.just(newToken);
            })
            .doOnSuccess(newToken -> log.debug("JWT令牌刷新成功"))
            .doOnError(error -> log.warn("JWT令牌刷新失败: {}", error.getMessage()));
    }
    
    /**
     * 撤销JWT令牌（加入黑名单）
     * @param token 要撤销的JWT令牌
     * @return 操作结果
     */
    public Mono<Void> revokeToken(String token) {
        return revokeToken(token, "Manual revocation", "system");
    }
    
    /**
     * 撤销JWT令牌（加入黑名单）
     * @param token 要撤销的JWT令牌
     * @param reason 撤销原因
     * @param revokedBy 撤销者
     * @return 操作结果
     */
    public Mono<Void> revokeToken(String token, String reason, String revokedBy) {
        if (token == null || token.trim().isEmpty()) {
            return Mono.error(new AuthenticationException("令牌不能为空", "TOKEN_REQUIRED"));
        }
        
        log.debug("开始撤销JWT令牌");
        
        return jwtTokenValidator.blacklistToken(token)
            .then(updateTokenStatusOnRevoke(token, reason, revokedBy))
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
        
        // 如果启用了持久化，检查持久化状态
        if (jwtPersistenceService != null) {
            String tokenHash = TokenHashUtils.hashToken(token);
            return jwtPersistenceService.findByTokenHash(tokenHash)
                .map(tokenInfo -> {
                    // 检查令牌状态
                    if (tokenInfo.getStatus() == TokenStatus.REVOKED) {
                        return false;
                    }
                    if (tokenInfo.getStatus() == TokenStatus.EXPIRED) {
                        return false;
                    }
                    // 检查是否过期
                    if (tokenInfo.getExpiresAt() != null && 
                        tokenInfo.getExpiresAt().isBefore(LocalDateTime.now())) {
                        return false;
                    }
                    return true;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 如果持久化中没有找到，检查Redis黑名单
                    return jwtTokenValidator.isTokenBlacklisted(token)
                        .map(isBlacklisted -> !isBlacklisted);
                }))
                .onErrorReturn(true); // 出错时默认认为有效
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
    
    /**
     * 保存令牌元数据（在令牌颁发时调用）
     * @param token JWT令牌
     * @param userId 用户ID
     * @param deviceInfo 设备信息
     * @param ipAddress IP地址
     * @param userAgent 用户代理
     * @return 保存操作结果
     */
    public Mono<Void> saveTokenMetadata(String token, String userId, String deviceInfo, String ipAddress, String userAgent) {
        if (jwtPersistenceService == null) {
            return Mono.empty(); // 未启用持久化时直接返回
        }
        
        return Mono.fromCallable(() -> {
            try {
                String tokenHash = TokenHashUtils.hashToken(token);
                
                JwtTokenInfo tokenInfo = new JwtTokenInfo();
                tokenInfo.setId(UUID.randomUUID().toString());
                tokenInfo.setUserId(userId);
                tokenInfo.setTokenHash(tokenHash);
                tokenInfo.setToken(null); // 不存储完整令牌，只存储哈希
                tokenInfo.setTokenType("Bearer");
                tokenInfo.setStatus(TokenStatus.ACTIVE);
                tokenInfo.setIssuedAt(LocalDateTime.now());
                
                // 从令牌中提取过期时间
                try {
                    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(
                        securityProperties.getJwt().getExpirationMinutes());
                    tokenInfo.setExpiresAt(expiresAt);
                } catch (Exception e) {
                    log.warn("无法从令牌中提取过期时间，使用默认值", e);
                    tokenInfo.setExpiresAt(LocalDateTime.now().plusMinutes(
                        securityProperties.getJwt().getExpirationMinutes()));
                }
                
                tokenInfo.setDeviceInfo(deviceInfo);
                tokenInfo.setIpAddress(ipAddress);
                tokenInfo.setUserAgent(userAgent);
                tokenInfo.setCreatedAt(LocalDateTime.now());
                tokenInfo.setUpdatedAt(LocalDateTime.now());
                
                return tokenInfo;
            } catch (Exception e) {
                log.error("创建令牌元数据时发生错误", e);
                throw new RuntimeException("Failed to create token metadata", e);
            }
        })
        .flatMap(jwtPersistenceService::saveToken)
        .doOnSuccess(v -> log.debug("令牌元数据保存成功: userId={}", userId))
        .doOnError(error -> log.warn("令牌元数据保存失败: {}", error.getMessage()));
    }
    
    /**
     * 在令牌刷新时保存新令牌并撤销旧令牌
     */
    private Mono<Void> saveTokenOnRefresh(String oldToken, String newToken) {
        return jwtTokenValidator.extractUserId(oldToken)
            .flatMap(userId -> {
                // 撤销旧令牌
                String oldTokenHash = TokenHashUtils.hashToken(oldToken);
                Mono<Void> revokeOld = jwtPersistenceService.updateTokenStatus(
                    oldTokenHash, TokenStatus.REVOKED)
                    .doOnError(error -> log.warn("更新旧令牌状态失败: {}", error.getMessage()))
                    .onErrorResume(ex -> Mono.empty());
                
                // 保存新令牌元数据
                Mono<Void> saveNew = saveTokenMetadata(newToken, userId, null, null, null)
                    .doOnError(error -> log.warn("保存新令牌元数据失败: {}", error.getMessage()))
                    .onErrorResume(ex -> Mono.empty());
                
                return Mono.when(revokeOld, saveNew);
            })
            .onErrorResume(ex -> {
                log.warn("令牌刷新时的持久化操作失败: {}", ex.getMessage());
                return Mono.empty(); // 不影响主要的刷新流程
            });
    }

    /**
     * 在令牌刷新时保存新令牌并撤销旧令牌（带上下文信息）
     */
    public Mono<Void> saveTokenOnRefreshWithContext(String oldToken, String newToken, String deviceInfo, String ipAddress, String userAgent) {
        return jwtTokenValidator.extractUserId(oldToken)
            .flatMap(userId -> {
                // 撤销旧令牌
                String oldTokenHash = TokenHashUtils.hashToken(oldToken);
                Mono<Void> revokeOld = jwtPersistenceService.updateTokenStatus(
                    oldTokenHash, TokenStatus.REVOKED)
                    .doOnError(error -> log.warn("更新旧令牌状态失败: {}", error.getMessage()))
                    .onErrorResume(ex -> Mono.empty());
                
                // 保存新令牌元数据（包含上下文信息）
                Mono<Void> saveNew = saveTokenMetadata(newToken, userId, deviceInfo, ipAddress, userAgent)
                    .doOnError(error -> log.warn("保存新令牌元数据失败: {}", error.getMessage()))
                    .onErrorResume(ex -> Mono.empty());
                
                return Mono.when(revokeOld, saveNew);
            })
            .onErrorResume(ex -> {
                log.warn("令牌刷新时的持久化操作失败: {}", ex.getMessage());
                return Mono.empty(); // 不影响主要的刷新流程
            });
    }
    
    /**
     * 在令牌撤销时更新持久化状态
     */
    private Mono<Void> updateTokenStatusOnRevoke(String token, String reason, String revokedBy) {
        if (jwtPersistenceService == null && jwtBlacklistService == null && jwtTokenLifecycleService == null) {
            return Mono.empty();
        }
        
        String tokenHash = TokenHashUtils.hashToken(token);
        
        // 优先使用生命周期服务进行状态更新
        Mono<Void> updateStatus = Mono.empty();
        if (jwtTokenLifecycleService != null) {
            updateStatus = jwtTokenLifecycleService.updateTokenStatus(tokenHash, TokenStatus.REVOKED, reason, revokedBy)
                .onErrorResume(ex -> {
                    log.warn("使用生命周期服务更新令牌状态失败: {}", ex.getMessage());
                    return Mono.empty();
                });
        } else if (jwtPersistenceService != null) {
            // 回退到直接使用持久化服务
            updateStatus = jwtPersistenceService.findByTokenHash(tokenHash)
                .flatMap(tokenInfo -> {
                    tokenInfo.setStatus(TokenStatus.REVOKED);
                    tokenInfo.setRevokeReason(reason);
                    tokenInfo.setRevokedBy(revokedBy);
                    tokenInfo.setRevokedAt(LocalDateTime.now());
                    tokenInfo.setUpdatedAt(LocalDateTime.now());
                    return jwtPersistenceService.saveToken(tokenInfo);
                })
                .onErrorResume(ex -> {
                    log.warn("更新令牌持久化状态失败: {}", ex.getMessage());
                    return Mono.empty();
                });
        }
        
        Mono<Void> addToBlacklist = Mono.empty();
        if (jwtBlacklistService != null) {
            addToBlacklist = jwtBlacklistService.addToBlacklist(tokenHash, reason, revokedBy)
                .onErrorResume(ex -> {
                    log.warn("添加到黑名单失败: {}", ex.getMessage());
                    return Mono.empty();
                });
        }
        
        return Mono.when(updateStatus, addToBlacklist);
    }
}