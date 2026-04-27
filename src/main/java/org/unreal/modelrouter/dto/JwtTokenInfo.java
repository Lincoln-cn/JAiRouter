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

    /**
     * 兼容 TokenResponse 的构造函数
     *
     * @deprecated 此构造函数仅为兼容 {@link TokenResponse} 而保留。
     *             请使用完整构造函数或 Builder 模式创建 JwtTokenInfo。
     *             推荐使用以下方式：
     *             <pre>{@code
     *             JwtTokenInfo info = new JwtTokenInfo();
     *             info.setToken(token);
     *             info.setTokenType("Bearer");
     *             info.setMessage("Success");
     *             info.setTimestamp(LocalDateTime.now());
     *             info.setUserId(userId);
     *             info.setStatus(TokenStatus.ACTIVE);
     *             }</pre>
     *             此构造函数将在 v3.0 版本中移除。
     * @param token 令牌值
     * @param tokenType 令牌类型
     * @param message 响应消息
     * @param timestamp 响应时间戳
     * @since v2.5.1 标注废弃
     */
    @Deprecated(since = "2.5.1", forRemoval = true)
    public JwtTokenInfo(final String token, final String tokenType,final String message,final LocalDateTime timestamp) {
        this.token = token;
        this.tokenType = tokenType;
        this.message = message;
        this.timestamp = timestamp;
    }

    /**
     * 兼容 UserTokenInfo 的构造函数
     *
     * @deprecated 此构造函数仅为兼容 {@link UserTokenInfo} 而保留。
     *             请使用 Builder 模式或 setter 方法创建 JwtTokenInfo，以便设置更多字段。
     *             推荐使用以下方式：
     *             <pre>{@code
     *             JwtTokenInfo info = new JwtTokenInfo();
     *             info.setUserId(userId);
     *             info.setToken(token);
     *             info.setIssuedAt(issuedAt);
     *             info.setExpiresAt(expiresAt);
     *             info.setStatus(TokenStatus.ACTIVE);
     *             info.setTokenType("Bearer");
     *             info.setCreatedAt(LocalDateTime.now());
     *             }</pre>
     *             此构造函数将在 v3.0 版本中移除。
     * @param userId 用户ID
     * @param token 令牌值
     * @param issuedAt 颁发时间
     * @param expiresAt 过期时间
     * @param status 令牌状态
     * @since v2.5.1 标注废弃
     */
    @Deprecated(since = "2.5.1", forRemoval = true)
    public JwtTokenInfo(final String userId, final String token,final LocalDateTime issuedAt,final LocalDateTime expiresAt,final TokenStatus status) {
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

    public void setToken(final String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(final String tokenType) {
        this.tokenType = tokenType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    // 令牌标识和用户信息
    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(final String tokenHash) {
        this.tokenHash = tokenHash;
    }

    // 时间信息
    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(final LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(final LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // 状态信息
    public TokenStatus getStatus() {
        return status;
    }

    public void setStatus(final TokenStatus status) {
        this.status = status;
    }

    // 撤销信息
    public String getRevokeReason() {
        return revokeReason;
    }

    public void setRevokeReason(final String revokeReason) {
        this.revokeReason = revokeReason;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(final LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(final String revokedBy) {
        this.revokedBy = revokedBy;
    }

    // 上下文信息
    public String getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(final String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(final String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(final String userAgent) {
        this.userAgent = userAgent;
    }

    // 扩展信息
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(final Map<String, Object> metadata) {
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
     *
     * @deprecated 此方法仅为兼容 {@link UserTokenInfo#getStatus()} 而保留。
     *             请使用 {@link #getStatus()} 返回 {@link TokenStatus} 枚举替代。
     *             枚举类型更安全，支持编译时检查。
     *             <p>迁移示例：</p>
     *             <pre>{@code
     *             // 旧代码
     *             String status = info.getStatusString(); // 返回 "active"
     *             
     *             // 新代码
     *             TokenStatus status = info.getStatus(); // 返回 TokenStatus.ACTIVE
     *             boolean isActive = info.isActive(); // 直接使用 boolean 方法
     *             }</pre>
     *             此方法将在 v3.0 版本中移除。
     * @return 状态字符串 (小写格式)
     * @since v2.5.1 标注废弃
     */
    @Deprecated(since = "2.5.1", forRemoval = true)
    public String getStatusString() {
        return this.status != null ? this.status.name().toLowerCase() : "unknown";
    }

    /**
     * 设置状态字符串 (兼容UserTokenInfo)
     *
     * @deprecated 此方法仅为兼容 {@link UserTokenInfo#setStatus(String)} 而保留。
     *             请使用 {@link #setStatus(TokenStatus)} 替代。
     *             使用枚举类型设置状态，避免字符串错误。
     *             <p>迁移示例：</p>
     *             <pre>{@code
     *             // 旧代码
     *             info.setStatusString("active"); // 字符串设置
     *             
     *             // 新代码
     *             info.setStatus(TokenStatus.ACTIVE); // 枚举设置
     *             }</pre>
     *             此方法将在 v3.0 版本中移除。
     * @param status 状态字符串 ("active", "revoked", "expired")
     * @since v2.5.1 标注废弃
     */
    @Deprecated(since = "2.5.1", forRemoval = true)
    public void setStatusString(final String status) {
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