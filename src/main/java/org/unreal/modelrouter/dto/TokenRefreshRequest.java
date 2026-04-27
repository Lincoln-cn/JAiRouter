package org.unreal.modelrouter.dto;

public class TokenRefreshRequest {
    private String token;

    public TokenRefreshRequest() {
    }

    public String getToken() {
        return this.token;
    }

    public void setToken(final String token) {
        this.token = token;
    }

    public String toString() {
        return "TokenRefreshRequest(token=" + this.getToken() + ")";
    }
}
