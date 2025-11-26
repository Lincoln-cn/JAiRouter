package org.unreal.modelrouter.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.store.entity.JwtBlacklistEntity;
import org.unreal.modelrouter.store.repository.JwtBlacklistRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于 H2 数据库的 JWT 黑名单服务
 * 提供三层保障：H2 数据库 + 本地缓存 + Redis（可选）
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.blacklist-enabled", havingValue = "true", matchIfMissing = true)
public class H2JwtBlacklistService {
    
    private final JwtBlacklistRepository blacklistRepository;
    
    // 本地缓存作为第一层快速检查
    private final ConcurrentMap<String, Long> localBlacklistCache = new ConcurrentHashMap<>();
    
    // 最大本地缓存大小
    private static final int MAX_LOCAL_CACHE_SIZE = 10000;
    
    /**
     * 将令牌加入黑名单
     * 
     * @param tokenHash 令牌哈希
     * @param userId 用户ID
     * @param ttlSeconds 过期时间（秒）
     * @param reason 撤销原因
     * @param revokedBy 撤销者
     * @return 操作结果
     */
    public Mono<Boolean> addToBlacklist(String tokenHash, String userId, long ttlSeconds, String reason, String revokedBy) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            log.warn("尝试将空的tokenHash加入黑名单");
            return Mono.just(false);
        }
        
        String trimmedTokenHash = tokenHash.trim();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(ttlSeconds);
        
        // 1. 立即加入本地缓存
        long expirationTime = System.currentTimeMillis() + (ttlSeconds * 1000);
        localBlacklistCache.put(trimmedTokenHash, expirationTime);
        cleanupLocalCache();
        
        // 2. 保存到 H2 数据库
        JwtBlacklistEntity entity = JwtBlacklistEntity.builder()
                .tokenHash(trimmedTokenHash)
                .userId(userId)
                .revokedAt(now)
                .expiresAt(expiresAt)
                .reason(reason)
                .revokedBy(revokedBy)
                .createdAt(now)
                .build();
        
        return blacklistRepository.save(entity)
                .map(saved -> {
                    log.debug("令牌已成功加入H2黑名单: tokenHash={}, userId={}, ttl={}s", 
                             trimmedTokenHash, userId, ttlSeconds);
                    return true;
                })
                .onErrorResume(ex -> {
                    log.error("令牌加入H2黑名单失败: tokenHash={}, error={}", trimmedTokenHash, ex.getMessage());
                    // H2失败时，至少本地缓存已经生效
                    return Mono.just(true);
                });
    }
    
    /**
     * 将令牌加入黑名单（简化版本）
     */
    public Mono<Boolean> addToBlacklist(String tokenHash, long ttlSeconds) {
        return addToBlacklist(tokenHash, null, ttlSeconds, null, null);
    }
    
    /**
     * 检查令牌是否在黑名单中
     * 使用多重检查策略：本地缓存 -> H2 数据库
     * 
     * @param tokenHash 令牌哈希
     * @return 是否在黑名单中
     */
    public Mono<Boolean> isBlacklisted(String tokenHash) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            return Mono.just(false);
        }
        
        String trimmedTokenHash = tokenHash.trim();
        
        // 1. 首先检查本地缓存
        Long expirationTime = localBlacklistCache.get(trimmedTokenHash);
        if (expirationTime != null) {
            if (System.currentTimeMillis() < expirationTime) {
                log.debug("令牌在本地黑名单缓存中: tokenHash={}", trimmedTokenHash);
                return Mono.just(true);
            } else {
                // 本地缓存过期，移除
                localBlacklistCache.remove(trimmedTokenHash);
            }
        }
        
        // 2. 检查 H2 数据库
        return blacklistRepository.existsByTokenHash(trimmedTokenHash)
                .map(exists -> {
                    if (exists) {
                        log.debug("令牌在H2黑名单中: tokenHash={}", trimmedTokenHash);
                        // 同步到本地缓存
                        localBlacklistCache.put(trimmedTokenHash, System.currentTimeMillis() + 3600000); // 1小时
                        return true;
                    }
                    return false;
                })
                .onErrorResume(ex -> {
                    log.warn("检查H2黑名单状态时发生异常: tokenHash={}, error={}", trimmedTokenHash, ex.getMessage());
                    // H2异常时，依赖本地缓存
                    Long localExpiration = localBlacklistCache.get(trimmedTokenHash);
                    if (localExpiration != null && System.currentTimeMillis() < localExpiration) {
                        log.info("H2异常，使用本地缓存判断令牌黑名单状态: tokenHash={}", trimmedTokenHash);
                        return Mono.just(true);
                    }
                    log.warn("H2异常且本地缓存无记录，令牌状态未知: tokenHash={}", trimmedTokenHash);
                    return Mono.just(false);
                });
    }
    
    /**
     * 从黑名单中移除令牌
     * 
     * @param tokenHash 令牌哈希
     * @return 操作结果
     */
    public Mono<Boolean> removeFromBlacklist(String tokenHash) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            return Mono.just(false);
        }
        
        String trimmedTokenHash = tokenHash.trim();
        
        // 1. 从本地缓存移除
        localBlacklistCache.remove(trimmedTokenHash);
        
        // 2. 从 H2 数据库移除
        return blacklistRepository.deleteByTokenHash(trimmedTokenHash)
                .map(deleted -> {
                    log.debug("令牌已从H2黑名单移除: tokenHash={}, deletedCount={}", trimmedTokenHash, deleted);
                    return deleted > 0;
                })
                .onErrorResume(ex -> {
                    log.warn("从H2黑名单移除令牌时发生异常: tokenHash={}, error={}", trimmedTokenHash, ex.getMessage());
                    return Mono.just(false);
                });
    }
    
    /**
     * 清理过期的黑名单记录
     * 
     * @return 清理的记录数
     */
    public Mono<Long> cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        
        return blacklistRepository.deleteExpiredTokens(now)
                .doOnSuccess(count -> {
                    log.info("清理过期黑名单记录完成，删除了 {} 条记录", count);
                    
                    // 同时清理本地缓存
                    long currentTime = System.currentTimeMillis();
                    localBlacklistCache.entrySet().removeIf(entry -> entry.getValue() < currentTime);
                })
                .doOnError(e -> log.error("清理过期黑名单记录失败: {}", e.getMessage(), e));
    }
    
    /**
     * 获取黑名单统计信息
     * 
     * @return 统计信息
     */
    public Mono<BlacklistStats> getStats() {
        LocalDateTime now = LocalDateTime.now();
        
        return blacklistRepository.countActiveBlacklistEntries(now)
                .map(h2Count -> {
                    BlacklistStats stats = new BlacklistStats();
                    
                    // 本地缓存统计
                    cleanupLocalCache();
                    stats.setLocalCacheSize(localBlacklistCache.size());
                    stats.setLocalCacheMaxSize(MAX_LOCAL_CACHE_SIZE);
                    
                    // H2 统计
                    stats.setH2Size(h2Count.intValue());
                    stats.setH2Available(true);
                    
                    return stats;
                })
                .onErrorResume(ex -> {
                    log.warn("获取H2黑名单统计失败: {}", ex.getMessage());
                    BlacklistStats stats = new BlacklistStats();
                    stats.setLocalCacheSize(localBlacklistCache.size());
                    stats.setLocalCacheMaxSize(MAX_LOCAL_CACHE_SIZE);
                    stats.setH2Available(false);
                    return Mono.just(stats);
                });
    }
    
    /**
     * 启动时从 H2 恢复黑名单到本地缓存
     */
    public Mono<Void> loadBlacklistFromH2() {
        LocalDateTime now = LocalDateTime.now();
        
        return blacklistRepository.findAll()
                .filter(entity -> entity.getExpiresAt().isAfter(now))
                .doOnNext(entity -> {
                    long expirationTime = entity.getExpiresAt()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli();
                    localBlacklistCache.put(entity.getTokenHash(), expirationTime);
                })
                .then()
                .doOnSuccess(v -> log.info("从H2恢复了 {} 条黑名单记录到本地缓存", localBlacklistCache.size()))
                .doOnError(e -> log.error("从H2恢复黑名单失败: {}", e.getMessage(), e));
    }
    
    /**
     * 清理本地缓存中的过期条目
     */
    private void cleanupLocalCache() {
        if (localBlacklistCache.size() <= MAX_LOCAL_CACHE_SIZE) {
            // 只清理过期条目
            long currentTime = System.currentTimeMillis();
            localBlacklistCache.entrySet().removeIf(entry -> entry.getValue() < currentTime);
        } else {
            // 缓存过大，清理过期条目和最老的条目
            long currentTime = System.currentTimeMillis();
            localBlacklistCache.entrySet().removeIf(entry -> entry.getValue() < currentTime);
            
            // 如果还是太大，移除最老的条目
            while (localBlacklistCache.size() > MAX_LOCAL_CACHE_SIZE) {
                String oldestKey = localBlacklistCache.entrySet().stream()
                        .min((e1, e2) -> Long.compare(e1.getValue(), e2.getValue()))
                        .map(entry -> entry.getKey())
                        .orElse(null);
                
                if (oldestKey != null) {
                    localBlacklistCache.remove(oldestKey);
                } else {
                    break;
                }
            }
        }
    }
    
    /**
     * 黑名单统计信息
     */
    public static class BlacklistStats {
        private int localCacheSize;
        private int localCacheMaxSize;
        private int h2Size;
        private boolean h2Available;
        
        // Getters and Setters
        public int getLocalCacheSize() { return localCacheSize; }
        public void setLocalCacheSize(int localCacheSize) { this.localCacheSize = localCacheSize; }
        
        public int getLocalCacheMaxSize() { return localCacheMaxSize; }
        public void setLocalCacheMaxSize(int localCacheMaxSize) { this.localCacheMaxSize = localCacheMaxSize; }
        
        public int getH2Size() { return h2Size; }
        public void setH2Size(int h2Size) { this.h2Size = h2Size; }
        
        public boolean isH2Available() { return h2Available; }
        public void setH2Available(boolean h2Available) { this.h2Available = h2Available; }
    }
}
