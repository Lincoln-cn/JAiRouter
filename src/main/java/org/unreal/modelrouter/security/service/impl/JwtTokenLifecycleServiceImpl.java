package org.unreal.modelrouter.security.service.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.dto.JwtTokenInfo;
import org.unreal.modelrouter.dto.TokenStatus;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.security.service.JwtPersistenceService;
import org.unreal.modelrouter.security.service.JwtTokenLifecycleService;
import org.unreal.modelrouter.util.TokenHashUtils;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JWT令牌生命周期管理服务实现
 * 提供令牌状态更新、过期处理和元数据管理功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.persistence.enabled", havingValue = "true")
public class JwtTokenLifecycleServiceImpl implements JwtTokenLifecycleService {
    
    private final SecurityProperties securityProperties;
    
    @Autowired(required = false)
    private JwtPersistenceService jwtPersistenceService;
    
    @Override
    public Mono<Void> updateTokenStatus(String tokenHash, TokenStatus newStatus, String reason, String updatedBy) {
        if (jwtPersistenceService == null) {
            log.warn("JWT持久化服务未启用，无法更新令牌状态");
            return Mono.empty();
        }
        
        return jwtPersistenceService.findByTokenHash(tokenHash)
            .flatMap(tokenInfo -> {
                TokenStatus oldStatus = tokenInfo.getStatus();
                
                // 更新状态信息
                tokenInfo.setStatus(newStatus);
                tokenInfo.setUpdatedAt(LocalDateTime.now());
                
                // 根据状态类型设置相应字段
                if (newStatus == TokenStatus.REVOKED) {
                    tokenInfo.setRevokeReason(reason);
                    tokenInfo.setRevokedBy(updatedBy);
                    tokenInfo.setRevokedAt(LocalDateTime.now());
                } else if (newStatus == TokenStatus.EXPIRED) {
                    // 过期状态通常由系统自动设置
                    if (tokenInfo.getExpiresAt() == null) {
                        tokenInfo.setExpiresAt(LocalDateTime.now());
                    }
                }
                
                // 添加状态变更到元数据
                Map<String, Object> metadata = tokenInfo.getMetadata();
                if (metadata == null) {
                    metadata = new HashMap<>();
                    tokenInfo.setMetadata(metadata);
                }
                
                Map<String, Object> statusChange = new HashMap<>();
                statusChange.put("oldStatus", oldStatus != null ? oldStatus.name() : null);
                statusChange.put("newStatus", newStatus.name());
                statusChange.put("reason", reason);
                statusChange.put("updatedBy", updatedBy);
                statusChange.put("timestamp", LocalDateTime.now().toString());
                
                List<Map<String, Object>> statusHistory = (List<Map<String, Object>>) metadata.get("statusHistory");
                if (statusHistory == null) {
                    statusHistory = new ArrayList<>();
                    metadata.put("statusHistory", statusHistory);
                }
                statusHistory.add(statusChange);
                
                return jwtPersistenceService.saveToken(tokenInfo);
            })
            .doOnSuccess(v -> log.debug("令牌状态更新成功: tokenHash={}, newStatus={}", tokenHash, newStatus))
            .doOnError(error -> log.warn("令牌状态更新失败: tokenHash={}, error={}", tokenHash, error.getMessage()));
    }    

    @Override
    public Mono<Long> updateExpiredTokens() {
        if (jwtPersistenceService == null) {
            log.warn("JWT持久化服务未启用，无法更新过期令牌");
            return Mono.just(0L);
        }
        
        AtomicLong updatedCount = new AtomicLong(0);
        
        // 分页查询所有活跃令牌并检查是否过期
        return updateExpiredTokensRecursive(0, 100, updatedCount)
            .doOnSuccess(count -> log.info("过期令牌状态更新完成，共更新{}个令牌", count))
            .doOnError(error -> log.error("更新过期令牌状态时发生错误", error));
    }
    
    private Mono<Long> updateExpiredTokensRecursive(int page, int size, AtomicLong updatedCount) {
        return jwtPersistenceService.findAllTokens(page, size)
            .flatMap(tokens -> {
                if (tokens.isEmpty()) {
                    return Mono.just(updatedCount.get());
                }
                
                LocalDateTime now = LocalDateTime.now();
                List<Mono<Void>> updateTasks = new ArrayList<>();
                
                for (JwtTokenInfo token : tokens) {
                    if (token.getStatus() == TokenStatus.ACTIVE && 
                        token.getExpiresAt() != null && 
                        token.getExpiresAt().isBefore(now)) {
                        
                        Mono<Void> updateTask = updateTokenStatus(
                            token.getTokenHash(), 
                            TokenStatus.EXPIRED, 
                            "Automatic expiration", 
                            "system"
                        ).doOnSuccess(v -> updatedCount.incrementAndGet());
                        
                        updateTasks.add(updateTask);
                    }
                }
                
                if (updateTasks.isEmpty()) {
                    // 继续下一页
                    return updateExpiredTokensRecursive(page + 1, size, updatedCount);
                }
                
                return Mono.when(updateTasks)
                    .then(updateExpiredTokensRecursive(page + 1, size, updatedCount));
            });
    }
    
    @Override
    public Mono<JwtTokenInfo> collectAndStoreTokenMetadata(String token, String userId, Map<String, Object> additionalMetadata) {
        if (jwtPersistenceService == null) {
            log.warn("JWT持久化服务未启用，无法存储令牌元数据");
            return Mono.empty();
        }
        
        return Mono.fromCallable(() -> {
            try {
                // 解析令牌获取信息
                Claims claims = parseToken(token);
                String tokenHash = TokenHashUtils.hashToken(token);
                
                JwtTokenInfo tokenInfo = new JwtTokenInfo();
                tokenInfo.setId(UUID.randomUUID().toString());
                tokenInfo.setUserId(userId);
                tokenInfo.setTokenHash(tokenHash);
                tokenInfo.setTokenType("Bearer");
                tokenInfo.setStatus(TokenStatus.ACTIVE);
                
                // 从Claims中提取时间信息
                if (claims.getIssuedAt() != null) {
                    tokenInfo.setIssuedAt(convertToLocalDateTime(claims.getIssuedAt()));
                }
                if (claims.getExpiration() != null) {
                    tokenInfo.setExpiresAt(convertToLocalDateTime(claims.getExpiration()));
                }
                
                LocalDateTime now = LocalDateTime.now();
                tokenInfo.setCreatedAt(now);
                tokenInfo.setUpdatedAt(now);
                
                // 设置元数据
                Map<String, Object> metadata = new HashMap<>();
                if (additionalMetadata != null) {
                    metadata.putAll(additionalMetadata);
                }
                
                // 添加令牌相关的元数据
                metadata.put("jti", claims.getId());
                metadata.put("issuer", claims.getIssuer());
                metadata.put("subject", claims.getSubject());
                metadata.put("collectionTime", now.toString());
                
                tokenInfo.setMetadata(metadata);
                
                return tokenInfo;
                
            } catch (Exception e) {
                log.error("收集令牌元数据时发生错误", e);
                throw new RuntimeException("Failed to collect token metadata", e);
            }
        })
        .flatMap(jwtPersistenceService::saveToken)
        .then(jwtPersistenceService.findByTokenHash(TokenHashUtils.hashToken(token)))
        .doOnSuccess(tokenInfo -> log.debug("令牌元数据收集并存储成功: userId={}", userId))
        .doOnError(error -> log.warn("令牌元数据收集存储失败: {}", error.getMessage()));
    }   
 
    @Override
    public Mono<TokenLifecycleInfo> getTokenLifecycleInfo(String tokenHash) {
        if (jwtPersistenceService == null) {
            return Mono.empty();
        }
        
        return jwtPersistenceService.findByTokenHash(tokenHash)
            .map(tokenInfo -> {
                TokenLifecycleInfo lifecycleInfo = new TokenLifecycleInfo();
                lifecycleInfo.setTokenHash(tokenInfo.getTokenHash());
                lifecycleInfo.setUserId(tokenInfo.getUserId());
                lifecycleInfo.setCurrentStatus(tokenInfo.getStatus());
                lifecycleInfo.setIssuedAt(tokenInfo.getIssuedAt());
                lifecycleInfo.setExpiresAt(tokenInfo.getExpiresAt());
                lifecycleInfo.setLastStatusChange(tokenInfo.getUpdatedAt());
                lifecycleInfo.setLastChangeReason(tokenInfo.getRevokeReason());
                lifecycleInfo.setLastChangedBy(tokenInfo.getRevokedBy());
                lifecycleInfo.setMetadata(tokenInfo.getMetadata());
                
                return lifecycleInfo;
            })
            .doOnSuccess(info -> log.debug("获取令牌生命周期信息成功: tokenHash={}", tokenHash))
            .doOnError(error -> log.warn("获取令牌生命周期信息失败: tokenHash={}, error={}", tokenHash, error.getMessage()));
    }
    
    @Override
    public Mono<Long> batchUpdateTokenStatus(List<String> tokenHashes, TokenStatus newStatus, String reason, String updatedBy) {
        if (jwtPersistenceService == null || tokenHashes == null || tokenHashes.isEmpty()) {
            return Mono.just(0L);
        }
        
        AtomicLong updatedCount = new AtomicLong(0);
        
        List<Mono<Void>> updateTasks = tokenHashes.stream()
            .map(tokenHash -> updateTokenStatus(tokenHash, newStatus, reason, updatedBy)
                .doOnSuccess(v -> updatedCount.incrementAndGet())
                .onErrorResume(ex -> {
                    log.warn("批量更新令牌状态失败: tokenHash={}, error={}", tokenHash, ex.getMessage());
                    return Mono.empty();
                }))
            .collect(ArrayList::new, (list, mono) -> list.add(mono), (list1, list2) -> list1.addAll(list2));
        
        return Mono.when(updateTasks)
            .thenReturn(updatedCount.get())
            .doOnSuccess(count -> log.info("批量更新令牌状态完成，共更新{}个令牌", count))
            .doOnError(error -> log.error("批量更新令牌状态时发生错误", error));
    }
    
    @Override
    public Mono<TokenLifecycleStats> getLifecycleStats() {
        if (jwtPersistenceService == null) {
            return Mono.just(createEmptyStats());
        }
        
        return Mono.zip(
            jwtPersistenceService.countActiveTokens(),
            jwtPersistenceService.countTokensByStatus(TokenStatus.REVOKED),
            jwtPersistenceService.countTokensByStatus(TokenStatus.EXPIRED)
        )
        .map(tuple -> {
            long activeCount = tuple.getT1();
            long revokedCount = tuple.getT2();
            long expiredCount = tuple.getT3();
            long totalCount = activeCount + revokedCount + expiredCount;
            
            TokenLifecycleStats stats = new TokenLifecycleStats();
            stats.setTotalTokens(totalCount);
            stats.setActiveTokens(activeCount);
            stats.setRevokedTokens(revokedCount);
            stats.setExpiredTokens(expiredCount);
            stats.setLastUpdateTime(LocalDateTime.now());
            
            Map<String, Long> statusDistribution = new HashMap<>();
            statusDistribution.put("ACTIVE", activeCount);
            statusDistribution.put("REVOKED", revokedCount);
            statusDistribution.put("EXPIRED", expiredCount);
            stats.setStatusDistribution(statusDistribution);
            
            return stats;
        })
        .doOnSuccess(stats -> log.debug("获取令牌生命周期统计信息成功: total={}, active={}", 
            stats.getTotalTokens(), stats.getActiveTokens()))
        .doOnError(error -> log.warn("获取令牌生命周期统计信息失败: {}", error.getMessage()))
        .onErrorReturn(createEmptyStats());
    }    
   
 /**
     * 解析JWT令牌
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
    
    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        String secret = securityProperties.getJwt().getSecret();
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("JWT签名密钥未配置");
        }
        
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    /**
     * 将Date转换为LocalDateTime
     */
    private LocalDateTime convertToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
    
    /**
     * 创建空的统计信息
     */
    private TokenLifecycleStats createEmptyStats() {
        TokenLifecycleStats stats = new TokenLifecycleStats();
        stats.setTotalTokens(0);
        stats.setActiveTokens(0);
        stats.setRevokedTokens(0);
        stats.setExpiredTokens(0);
        stats.setLastUpdateTime(LocalDateTime.now());
        stats.setStatusDistribution(new HashMap<>());
        return stats;
    }
}