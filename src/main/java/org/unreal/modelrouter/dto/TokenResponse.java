package org.unreal.modelrouter.dto;

import java.time.LocalDateTime;

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

    public String toString() {
        return "TokenResponse(token=" + this.getToken() + ", tokenType=" + this.getTokenType() + ", message=" + this.getMessage() + ", timestamp=" + this.getTimestamp() + ")";
    }
}
