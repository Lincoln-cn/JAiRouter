package org.unreal.modelrouter.dto;

import java.time.LocalDateTime;

/**
 * Token 响应 DTO（旧版本）
 *
 * @deprecated 此类已被合并到 {@link JwtTokenInfo} 中，不再推荐使用。
 *             <p>迁移说明：</p>
 *             <ul>
 *               <li>TokenResponse 的所有字段已在 JwtTokenInfo 中提供</li>
 *               <li>JwtTokenInfo 提供更完整的令牌生命周期管理</li>
 *               <li>支持状态追踪、撤销记录、设备信息等扩展功能</li>
 *             </ul>
 *             <p>迁移示例：</p>
 *             <pre>{@code
 *             // 旧代码
 *             TokenResponse response = new TokenResponse(token, "Bearer", "Success", LocalDateTime.now());
 *             
 *             // 新代码 - 使用 JwtTokenInfo
 *             JwtTokenInfo info = new JwtTokenInfo();
 *             info.setToken(token);
 *             info.setTokenType("Bearer");
 *             info.setMessage("Success");
 *             info.setTimestamp(LocalDateTime.now());
 *             info.setStatus(TokenStatus.ACTIVE);
 *             }</pre>
 *             <p>此类将在 v3.0 版本中移除。</p>
 * @see JwtTokenInfo
 * @see TokenStatus
 * @since v2.5.1 标注废弃，计划 v3.0 移除
 */
@Deprecated(since = "2.5.1", forRemoval = true)
public class TokenResponse {
    private String token;
    private String tokenType;
    private String message;
    private LocalDateTime timestamp;

    public TokenResponse() {
    }

    public TokenResponse(String token, String tokenType, String message, LocalDateTime timestamp) {
        this.token = token;
        this.tokenType = tokenType;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getToken() {
        return this.token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return this.tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "TokenResponse(token=" + this.getToken() + ", tokenType=" + this.getTokenType() 
                + ", message=" + this.getMessage() + ", timestamp=" + this.getTimestamp() + ")";
    }
}