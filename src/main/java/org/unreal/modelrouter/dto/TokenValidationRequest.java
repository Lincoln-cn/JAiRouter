package org.unreal.modelrouter.dto;

public class TokenValidationRequest {
    private String token;

    public TokenValidationRequest() {
    }

    public String getToken() {
        return this.token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String toString() {
        return "TokenValidationRequest(token=" + this.getToken() + ")";
    }
}
