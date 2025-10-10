package org.unreal.modelrouter.security.service;

import org.unreal.modelrouter.dto.JwtTokenInfo;
import org.unreal.modelrouter.dto.TokenStatus;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * JWT令牌生命周期管理服务接口
 * 提供令牌状态更新、过期处理和元数据管理功能
 */
public interface JwtTokenLifecycleService {
    
    /**
     * 更新令牌状态
     * @param tokenHash 令牌哈希值
     * @param newStatus 新状态
     * @param reason 状态更新原因
     * @param updatedBy 更新者
     * @return 更新操作结果
     */
    Mono<Void> updateTokenStatus(String tokenHash, TokenStatus newStatus, String reason, String updatedBy);
    
    /**
     * 自动更新过期令牌的状态
     * @return 更新的令牌数量
     */
    Mono<Long> updateExpiredTokens();
    
    /**
     * 收集并存储令牌元数据
     * @param token JWT令牌
     * @param userId 用户ID
     * @param additionalMetadata 额外元数据
     * @return 操作结果
     */
    Mono<JwtTokenInfo> collectAndStoreTokenMetadata(String token, String userId, Map<String, Object> additionalMetadata);
    
    /**
     * 获取令牌的完整生命周期信息
     * @param tokenHash 令牌哈希值
     * @return 令牌生命周期信息
     */
    Mono<TokenLifecycleInfo> getTokenLifecycleInfo(String tokenHash);
    
    /**
     * 批量更新令牌状态
     * @param tokenHashes 令牌哈希列表
     * @param newStatus 新状态
     * @param reason 状态更新原因
     * @param updatedBy 更新者
     * @return 更新操作结果
     */
    Mono<Long> batchUpdateTokenStatus(java.util.List<String> tokenHashes, TokenStatus newStatus, String reason, String updatedBy);
    
    /**
     * 获取令牌生命周期统计信息
     * @return 统计信息
     */
    Mono<TokenLifecycleStats> getLifecycleStats();
    
    /**
     * 令牌生命周期信息
     */
    class TokenLifecycleInfo {
        private String tokenHash;
        private String userId;
        private TokenStatus currentStatus;
        private LocalDateTime issuedAt;
        private LocalDateTime expiresAt;
        private LocalDateTime lastStatusChange;
        private String lastChangeReason;
        private String lastChangedBy;
        private Map<String, Object> metadata;
        
        // Getters and Setters
        public String getTokenHash() { return tokenHash; }
        public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public TokenStatus getCurrentStatus() { return currentStatus; }
        public void setCurrentStatus(TokenStatus currentStatus) { this.currentStatus = currentStatus; }
        
        public LocalDateTime getIssuedAt() { return issuedAt; }
        public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
        
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
        
        public LocalDateTime getLastStatusChange() { return lastStatusChange; }
        public void setLastStatusChange(LocalDateTime lastStatusChange) { this.lastStatusChange = lastStatusChange; }
        
        public String getLastChangeReason() { return lastChangeReason; }
        public void setLastChangeReason(String lastChangeReason) { this.lastChangeReason = lastChangeReason; }
        
        public String getLastChangedBy() { return lastChangedBy; }
        public void setLastChangedBy(String lastChangedBy) { this.lastChangedBy = lastChangedBy; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    /**
     * 令牌生命周期统计信息
     */
    class TokenLifecycleStats {
        private long totalTokens;
        private long activeTokens;
        private long revokedTokens;
        private long expiredTokens;
        private LocalDateTime lastUpdateTime;
        private Map<String, Long> statusDistribution;
        
        // Getters and Setters
        public long getTotalTokens() { return totalTokens; }
        public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }
        
        public long getActiveTokens() { return activeTokens; }
        public void setActiveTokens(long activeTokens) { this.activeTokens = activeTokens; }
        
        public long getRevokedTokens() { return revokedTokens; }
        public void setRevokedTokens(long revokedTokens) { this.revokedTokens = revokedTokens; }
        
        public long getExpiredTokens() { return expiredTokens; }
        public void setExpiredTokens(long expiredTokens) { this.expiredTokens = expiredTokens; }
        
        public LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(LocalDateTime lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
        
        public Map<String, Long> getStatusDistribution() { return statusDistribution; }
        public void setStatusDistribution(Map<String, Long> statusDistribution) { this.statusDistribution = statusDistribution; }
    }
}