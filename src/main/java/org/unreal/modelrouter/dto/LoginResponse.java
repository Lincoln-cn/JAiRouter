package org.unreal.modelrouter.dto;

public class LoginResponse {
    private String token;
    private String tokenType = "Bearer";
    private long expiresIn;

    public LoginResponse() {
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

    public long getExpiresIn() {
        return this.expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String toString() {
        return "LoginResponse(token=" + this.getToken() + ", tokenType=" + this.getTokenType() + ", expiresIn=" + this.getExpiresIn() + ")";
    }
}