package org.unreal.modelrouter.common.dto;

public final class TokenValidationRequest {
    private String token;

    public TokenValidationRequest() {
    }

    public String getToken() {
        return this.token;
    }

    public void setToken(final String token) {
        this.token = token;
    }

    public String toString() {
        return "TokenValidationRequest(token=" + this.getToken() + ")";
    }
}
