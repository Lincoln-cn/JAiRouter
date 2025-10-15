package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * JWT令牌信息类 - 统一的令牌信息模型
 * 合并了TokenResponse、UserTokenInfo和JwtTokenInfo的功能
 * 用于持久化存储JWT令牌的详细信息和API响应
 */
public class JwtTokenInfo {
    
    // 基础令牌信息 (来自TokenResponse)
    private String token;           // 令牌值
    private String tokenType;       // 令牌类型 (Bearer)
    private String message;         // 响应消息
    private LocalDateTime timestamp;// 响应时间戳
    
    // 令牌标识和用户信息
    private String id;              // 令牌ID (UUID)
    private String userId;          // 用户ID
    private String tokenHash;       // 令牌哈希值 (SHA-256)
    
    // 时间信息
    private LocalDateTime issuedAt; // 颁发时间
    private LocalDateTime expiresAt;// 过期时间

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;// 创建时间

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;// 更新时间
    
    // 状态信息
    private TokenStatus status;     // 令牌状态 (ACTIVE, REVOKED, EXPIRED)
    
    // 撤销信息
    private String revokeReason;    // 撤销原因
    private LocalDateTime revokedAt;// 撤销时间
    private String revokedBy;       // 撤销者
    
    // 上下文信息
    private String deviceInfo;      // 设备信息
    private String ipAddress;       // IP地址
    private String userAgent;       // 用户代理
    
    // 扩展信息
    private Map<String, Object> metadata; // 额外元数据

    public JwtTokenInfo() {
    }

    public JwtTokenInfo(String token, String tokenType, String message, LocalDateTime timestamp) {
        this.token = token;
        this.tokenType = tokenType;
        this.message = message;
        this.timestamp = timestamp;
    }
    
    // 兼容UserTokenInfo的构造函数
    public JwtTokenInfo(String userId, String token, LocalDateTime issuedAt, LocalDateTime expiresAt, TokenStatus status) {
        this.userId = userId;
        this.token = token;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.status = status;
        this.tokenType = "Bearer";
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    
    // 基础令牌信息 (TokenResponse兼容)
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    // 令牌标识和用户信息
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    // 时间信息
    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // 状态信息
    public TokenStatus getStatus() {
        return status;
    }

    public void setStatus(TokenStatus status) {
        this.status = status;
    }

    // 撤销信息
    public String getRevokeReason() {
        return revokeReason;
    }

    public void setRevokeReason(String revokeReason) {
        this.revokeReason = revokeReason;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(String revokedBy) {
        this.revokedBy = revokedBy;
    }

    // 上下文信息
    public String getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    // 扩展信息
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    // 便利方法
    
    /**
     * 检查令牌是否活跃
     */
    public boolean isActive() {
        return TokenStatus.ACTIVE.equals(this.status);
    }
    
    /**
     * 检查令牌是否已撤销
     */
    public boolean isRevoked() {
        return TokenStatus.REVOKED.equals(this.status);
    }
    
    /**
     * 检查令牌是否已过期
     */
    public boolean isExpired() {
        return TokenStatus.EXPIRED.equals(this.status) || 
               (this.expiresAt != null && this.expiresAt.isBefore(LocalDateTime.now()));
    }
    
    /**
     * 获取状态字符串 (兼容UserTokenInfo)
     */
    public String getStatusString() {
        return this.status != null ? this.status.name().toLowerCase() : "unknown";
    }
    
    /**
     * 设置状态字符串 (兼容UserTokenInfo)
     */
    public void setStatusString(String status) {
        if (status != null) {
            try {
                this.status = TokenStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                // 兼容旧的字符串状态
                if ("active".equalsIgnoreCase(status)) {
                    this.status = TokenStatus.ACTIVE;
                } else if ("revoked".equalsIgnoreCase(status)) {
                    this.status = TokenStatus.REVOKED;
                } else if ("expired".equalsIgnoreCase(status)) {
                    this.status = TokenStatus.EXPIRED;
                }
            }
        }
    }

    @Override
    public String toString() {
        return "JwtTokenInfo{" +
                "token='" + (token != null ? token.substring(0, Math.min(token.length(), 20)) + "..." : null) + '\'' +
                ", tokenType='" + tokenType + '\'' +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                ", id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", tokenHash='" + (tokenHash != null ? tokenHash.substring(0, Math.min(tokenHash.length(), 16)) + "..." : null) + '\'' +
                ", issuedAt=" + issuedAt +
                ", expiresAt=" + expiresAt +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", status=" + status +
                ", revokeReason='" + revokeReason + '\'' +
                ", revokedAt=" + revokedAt +
                ", revokedBy='" + revokedBy + '\'' +
                ", deviceInfo='" + deviceInfo + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", userAgent='" + userAgent + '\'' +
                ", metadata=" + metadata +
                '}';
    }
}