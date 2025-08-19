package org.unreal.modelrouter.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TokenValidationResponse {
    private boolean valid;
    private String userId;
    private String message;
    private LocalDateTime timestamp;
}
