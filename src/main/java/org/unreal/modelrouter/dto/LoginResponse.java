package org.unreal.modelrouter.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String tokenType = "Bearer";
    private long expiresIn;
}