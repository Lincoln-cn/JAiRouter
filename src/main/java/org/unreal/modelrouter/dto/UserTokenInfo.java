package org.unreal.modelrouter.dto;

import java.time.LocalDateTime;

/**
 * 用户令牌信息 DTO（旧版本）
 *
 * @deprecated 此类已被合并到 {@link JwtTokenInfo} 中，不再推荐使用。
 *             <p>迁移说明：</p>
 *             <ul>
 *               <li>UserTokenInfo 的所有字段已在 JwtTokenInfo 中提供</li>
 *               <li>JwtTokenInfo 使用 {@link TokenStatus} 枚举替代字符串状态</li>
 *               <li>提供更完整的令牌生命周期管理（颁发时间、过期时间、撤销记录）</li>
 *             </ul>
 *             <p>迁移示例：</p>
 *             <pre>{@code
 *             // 旧代码
 *             UserTokenInfo oldInfo = new UserTokenInfo(userId, token, issuedAt, expiresAt, "active");
 *             String status = oldInfo.getStatus(); // 返回字符串 "active"
 *             
 *             // 新代码 - 使用 JwtTokenInfo
 *             JwtTokenInfo newInfo = new JwtTokenInfo(userId, token, issuedAt, expiresAt, TokenStatus.ACTIVE);
 *             TokenStatus status = newInfo.getStatus(); // 返回枚举 TokenStatus.ACTIVE
 *             boolean isActive = newInfo.isActive(); // 直接使用 boolean 方法
 *             
 *             // 状态转换
 *             // "active" -> TokenStatus.ACTIVE
 *             // "revoked" -> TokenStatus.REVOKED
 *             // "expired" -> TokenStatus.EXPIRED
 *             }</pre>
 *             <p>此类将在 v3.0 版本中移除。</p>
 * @see JwtTokenInfo
 * @see TokenStatus
 * @since v2.5.1 标注废弃，计划 v3.0 移除
 */
@Deprecated(since = "2.5.1", forRemoval = true)
public class UserTokenInfo {

    private String userId;
    private String token;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private String status; // "active" or "revoked"

    public UserTokenInfo() {
    }

    public UserTokenInfo(final String userId,final String token,final LocalDateTime issuedAt,final LocalDateTime expiresAt,final String status) {
        this.userId = userId;
        this.token = token;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(final String token) {
        this.token = token;
    }

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

    /**
     * 获取状态字符串
     *
     * @deprecated 使用 {@link JwtTokenInfo#getStatus()} 返回 {@link TokenStatus} 枚举替代。
     *             枚举类型更安全，支持编译时检查。
     *             此方法将在 v3.0 版本中移除。
     * @return 状态字符串 ("active" 或 "revoked")
     * @since v2.5.1 标注废弃
     */
    @Deprecated(since = "2.5.1", forRemoval = true)
    public String getStatus() {
        return status;
    }

    /**
     * 设置状态字符串
     *
     * @deprecated 使用 {@link JwtTokenInfo#setStatus(TokenStatus)} 替代。
     *             使用枚举类型设置状态，避免字符串错误。
     *             此方法将在 v3.0 版本中移除。
     * @param status 状态字符串 ("active" 或 "revoked")
     * @since v2.5.1 标注废弃
     */
    @Deprecated(since = "2.5.1", forRemoval = true)
    public void setStatus(final String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "UserTokenInfo{"
                + "userId='" + userId + '\''
                + ", token='" + token + '\''
                + ", issuedAt=" + issuedAt
                + ", expiresAt=" + expiresAt
                + ", status='" + status + '\''
                + '}';
    }
}