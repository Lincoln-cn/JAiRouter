package org.unreal.modelrouter.dto;

public class TokenRevokeRequest {
    private String token;

    private String userId; // 可选，用于权限检查
    private String reason; // 可选，撤销原因

    public TokenRevokeRequest() {
    }

    public String getToken() {
        return this.token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUserId() {
        return this.userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getReason() {
        return this.reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String toString() {
        return "TokenRevokeRequest(token=" + this.getToken() + ", userId=" + this.getUserId() + ", reason=" + this.getReason() + ")";
    }
}
