package org.unreal.modelrouter.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginResponse {
    private String token;
    private String tokenType = "Bearer";
    private long expiresIn;
    private String message;
    private LocalDateTime timestamp;
}