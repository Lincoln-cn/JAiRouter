package org.unreal.modelrouter.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JwtApiResponse {
    private boolean success;
    private String message;
    private LocalDateTime timestamp;
}
