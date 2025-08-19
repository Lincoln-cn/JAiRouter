package org.unreal.modelrouter.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
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
}
