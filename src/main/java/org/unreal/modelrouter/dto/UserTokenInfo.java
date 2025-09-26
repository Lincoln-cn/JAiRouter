package org.unreal.modelrouter.dto;

import java.time.LocalDateTime;

public class UserTokenInfo {

    private String userId;
    private String token;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private String status; // "active" or "revoked"

    public UserTokenInfo() {
    }

    public UserTokenInfo(String userId, String token, LocalDateTime issuedAt, LocalDateTime expiresAt, String status) {
        this.userId = userId;
        this.token = token;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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
