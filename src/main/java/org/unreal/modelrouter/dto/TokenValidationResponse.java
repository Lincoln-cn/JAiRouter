package org.unreal.modelrouter.dto;

import java.time.LocalDateTime;

public class TokenValidationResponse {
    private boolean valid;
    private String userId;
    private String message;
    private LocalDateTime timestamp;

    public TokenValidationResponse() {
    }

    public boolean isValid() {
        return this.valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getUserId() {
        return this.userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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
        return "TokenValidationResponse(valid=" + this.isValid() + ", userId=" + this.getUserId() + ", message=" + this.getMessage() + ", timestamp=" + this.getTimestamp() + ")";
    }
}
