package org.unreal.modelrouter.dto;

import java.time.LocalDateTime;

/**
 * 令牌黑名单条目
 * 用于存储被撤销的令牌信息
 */
public class TokenBlacklistEntry {
    
    private String tokenHash;       // 令牌哈希值
    private LocalDateTime expiresAt;// 过期时间
    private String reason;          // 加入黑名单原因
    private String addedBy;         // 添加者
    private LocalDateTime addedAt;  // 添加时间

    public TokenBlacklistEntry() {
    }

    public TokenBlacklistEntry(String tokenHash, LocalDateTime expiresAt, String reason, String addedBy, LocalDateTime addedAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.reason = reason;
        this.addedBy = addedBy;
        this.addedAt = addedAt;
    }

    // Getters and Setters
    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(LocalDateTime addedAt) {
        this.addedAt = addedAt;
    }

    @Override
    public String toString() {
        return "TokenBlacklistEntry{" +
                "tokenHash='" + tokenHash + '\'' +
                ", expiresAt=" + expiresAt +
                ", reason='" + reason + '\'' +
                ", addedBy='" + addedBy + '\'' +
                ", addedAt=" + addedAt +
                '}';
    }
}